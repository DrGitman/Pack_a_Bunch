package com.packabunch.scan

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.packabunch.packing.Axis
import com.packabunch.packing.DepthEdgeObjects
import com.packabunch.packing.DetectionPoints
import com.packabunch.packing.FittedObject
import com.packabunch.packing.ItemScanState
import com.packabunch.packing.ItemTracker
import com.packabunch.packing.MeasuredDimensions
import com.packabunch.packing.OutlineGeometry
import com.packabunch.packing.OutlineSegment
import com.packabunch.packing.PlaneFrame
import com.packabunch.packing.PlanePoint
import com.packabunch.packing.PlanarPose
import com.packabunch.packing.ScanLabelConsensus
import com.packabunch.packing.ScanStateSmoother
import com.packabunch.packing.SensorBox
import com.packabunch.packing.ShapeFamily
import com.packabunch.packing.StandingObjects
import com.packabunch.packing.TwoViewSize
import com.packabunch.packing.YoloxDecode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** How sure the scan is of where the camera is. */
enum class TrackingStatus {
    /** Still looking for the card. Nothing is measured yet. */
    INITIALISING,

    /** The card is in view: the camera is placed and measuring works. */
    TRACKING,

    /** The card was found earlier and is now out of view or unusable. */
    LOST,
}

/** One object as the scan screen shows it. Changes only when something about it changes. */
data class ScanItem(
    val id: Int,
    val name: String?,
    val state: ItemScanState,
    val shape: ShapeFamily?,
    /** Small crop of the object itself, for the tag and the tray. Never leaves the phone. */
    val thumbnail: Bitmap?,
    /** The current fit, in the shared plan frame — what the live 3D preview draws. */
    val fit: FittedObject? = null,
    /** HarshdeepJ's front-and-side size: W and H from the front, D once a side has been seen. */
    val faceSize: FaceSize? = null,
)

data class FaceSize(val widthMm: Float, val depthMm: Float?, val heightMm: Float)

/**
 * Where an object's labels go, in 0..1 of the view, refreshed every frame: the tag above it,
 * and the W / D / H pills on the edges they measure (null when that edge is off screen or,
 * for D, when the object is round and W already says it).
 */
data class ScanAnchor(
    val id: Int,
    val x: Float,
    val y: Float,
    val width: Pair<Float, Float>? = null,
    val depth: Pair<Float, Float>? = null,
    val height: Pair<Float, Float>? = null,
    /** How much of the view's width the object spans, 0..1. Small objects get no pills. */
    val span: Float = 0f,
)

data class ItemScanUi(
    val status: TrackingStatus = TrackingStatus.INITIALISING,
    /** What is stopping measuring this frame, if anything. */
    val problem: ScanProblem? = null,
    /** The card, and so the surface it lies on, is in view — items are measured standing on it. */
    val surfaceFound: Boolean = false,
    val reference: Reference? = null,
    val items: List<ScanItem> = emptyList(),
    /** An object was in view but not started because the plan's limit was reached. */
    val capReached: Boolean = false,
    val maxItems: Int = ItemTracker.FREE_PLAN_MAX_ITEMS,
    val torchOn: Boolean = false,
    val torchAvailable: Boolean = false,
    val fatalError: String? = null,
    val scanHint: String? = null,
) {
    val measuredCount: Int get() = items.count { it.state is ItemScanState.Measured }
}

/** What the scan hands back when the person taps Done. */
data class ScannedItemResult(
    val trackId: Int,
    val name: String?,
    val dimensions: MeasuredDimensions,
    val shape: ShapeFamily,
    /** The object's own crop, for [com.packabunch.data.ItemPhotos]. Never leaves the phone. */
    val photo: Bitmap?,
    val fit: FittedObject,
)

/**
 * The item scan: a bank card on the table, the phone pointed at the things beside it, and each
 * one gets measured.
 *
 * ### Frame by frame
 * The camera hands over a picture several times a second. [SceneEngine] finds the card and
 * places the camera relative to it, and puts the depth model's output into millimetres. Every
 * processed frame, the objects standing on the card's surface are found (depth edges, the
 * standing-on-the-surface finder, ML Kit), sampled, tracked and fitted. Everything runs on the
 * camera's analysis thread; a slow frame makes the camera drop the next, never queue it.
 *
 * ### Coordinates
 * One world for the whole scan: the card's. Its surface is y = 0, so an object's height is just
 * its top's y, and because the card does not move, moving the phone round the items joins every
 * view into one 3D picture — what lets the fit see all sides.
 *
 * Nothing is uploaded.
 */
class ItemScanController(
    private val context: Context,
    private val density: Float,
    maxItems: Int = ItemTracker.FREE_PLAN_MAX_ITEMS,
) {

    private val _ui = MutableStateFlow(ItemScanUi(maxItems = maxItems))
    val ui: StateFlow<ItemScanUi> = _ui.asStateFlow()

    private val _anchors = MutableStateFlow<List<ScanAnchor>>(emptyList())

    /** Tag positions, per frame. Separate from [ui] so moving tags do not rebuild the screen. */
    val anchors: StateFlow<List<ScanAnchor>> = _anchors.asStateFlow()

    private val _outlines = MutableStateFlow(OutlineScene())

    /** The outlines over the camera, per frame. */
    val outlines: StateFlow<OutlineScene> = _outlines.asStateFlow()

    private val feed = CameraFeed(context)
    private val gravity = Gravity(context)
    private val engine = SceneEngine(context, gravity)
    private val detector = ItemScanDetector()
    private val diagnostics = ScanDiagnostics("items")

    /** The measuring engine: Python (packscan.py), with the Kotlin one as its fallback. */
    private val math = PythonEngine.lazy(context)
    private val tracker = ItemTracker(maxItems, math = math)
    /** One steady id per object in the picture, so one mouse stays one item as the phone moves. */
    private val lockOn = com.packabunch.packing.LockOnTracker()
    private val smoother = ScanStateSmoother()
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** What is drawn. Replaced wholesale, never mutated. */
    @Volatile private var drawn: List<TrackSnapshot> = emptyList()

    private val names = ConcurrentHashMap<Int, String>()
    private val nameEvidence = ConcurrentHashMap<Int, ScanLabelConsensus>()
    private val crops = ConcurrentHashMap<Int, Bitmap>()
    private val cropArea = ConcurrentHashMap<Int, Float>()
    private val measuredAt = ConcurrentHashMap<Int, Long>()
    private val faceViews = ConcurrentHashMap<Int, TwoViewSize>()
    /** Each object's traced outline, as points in the room (metres), drawn every frame. */
    private val outlines3d = ConcurrentHashMap<Int, DoubleArray>()

    /** YOLOX-Tiny, bundled, for names. Created on first use: loading takes a moment. */
    @Volatile private var yolo: YoloxDetector? = null
    private val yoloBusy = AtomicBoolean(false)

    /** Each object's box in this frame's upright picture, for naming and its photo. */
    @Volatile private var frameBoxes: Map<Int, FloatArray> = emptyMap()

    @Volatile private var viewW = 1
    @Volatile private var viewH = 1
    private var lastProcessedNs = 0L
    private var everSaw = false

    // -- lifecycle -------------------------------------------------------------------------------

    /** Starts the camera into [preview]. */
    fun start(owner: LifecycleOwner, preview: PreviewView) {
        gravity.start()
        feed.start(owner, preview, ::onFrame) { message -> _ui.value = _ui.value.copy(fatalError = message) }
    }

    fun stop() {
        feed.stop()
        gravity.stop()
    }

    fun release() {
        scope.cancel()
        feed.release()
        gravity.stop()
        detector.close()
        yolo?.close(); yolo = null
        engine.release()
    }

    fun setViewSize(width: Int, height: Int) { viewW = width.coerceAtLeast(1); viewH = height.coerceAtLeast(1) }

    fun setTorch(on: Boolean) {
        val ok = feed.setTorch(on)
        _ui.value = _ui.value.copy(torchOn = on && ok)
    }

    /** Forgets one object (the ✕ on its tag). */
    fun remove(id: Int) = synchronized(lock) {
        tracker.remove(id)
        publish(tracker.all.filter { it.isConfirmed }, capReached = false)
    }

    /** Throws everything away and starts again, keeping the camera running. */
    fun restart() = synchronized(lock) {
        tracker.clear()
        lockOn.clear()
        smoother.clear()
        names.clear(); nameEvidence.clear(); crops.clear(); cropArea.clear(); measuredAt.clear(); faceViews.clear(); outlines3d.clear()
        publish(emptyList(), capReached = false)
    }

    /** Everything measured, ready to become items. */
    fun results(): List<ScannedItemResult> = synchronized(lock) {
        tracker.all.mapNotNull { t ->
            val m = t.state as? ItemScanState.Measured ?: return@mapNotNull null
            ScannedItemResult(t.id, names[t.id] ?: t.label, m.dimensions, m.fit.shape, crops[t.id], m.fit)
        }
    }

    // -- frames (camera analysis thread) -------------------------------------------------------

    private fun onFrame(frame: CameraFrame) {
        if (_ui.value.torchAvailable != feed.hasTorch) _ui.value = _ui.value.copy(torchAvailable = feed.hasTorch)
        val process = frame.timestampNs - lastProcessedNs >= PROCESS_INTERVAL_NS
        val scene = engine.process(frame, wantDepth = process)
        val pose = scene.pose
        if (pose != null) everSaw = true
        val status = when {
            pose != null -> TrackingStatus.TRACKING
            everSaw -> TrackingStatus.LOST
            else -> TrackingStatus.INITIALISING
        }
        val u = _ui.value
        if (u.status != status || u.problem != scene.problem || u.surfaceFound != (pose != null) || u.reference != scene.reference) {
            _ui.value = u.copy(status = status, problem = scene.problem, surfaceFound = pose != null, reference = scene.reference)
        }
        if (process && pose != null && scene.depth != null) {
            lastProcessedNs = frame.timestampNs
            try { measure(scene, pose, scene.depth) } catch (t: Throwable) { Log.e(SCAN_TAG, "item frame", t) }
        }
        if (pose != null) draw(frame, pose) else { _outlines.value = OutlineScene(); _anchors.value = emptyList() }
    }

    private class DepthPixel(val index: Int, val u: Float, val v: Float, val mm: Int, val world: FloatArray, val edge: Boolean)

    private class BoxSamples(val box: ScanBox, val pixels: IntArray, val world: FloatArray, val central: BooleanArray, val edge: BooleanArray,
                             val depths: IntArray, val spanPx: Float, val focalPx: Float, val inMask: BooleanArray?,
                             /** The object's traced outline, x, y pairs in 0..1 of the picture, or null if it could not be traced. */
                             val hull: FloatArray? = null)

    private fun measure(scene: SceneFrame, pose: PlanarPose.Pose, depth: MetricDepth) {
        val frame = scene.camera
        val picture = frame.picture
        val g = depth.size
        // Every depth sample as a point in the card's world. Its y is its height above the surface.
        val above = IntArray(g * g)
        val pixels = ArrayList<DepthPixel>()
        fun at(x: Int, y: Int) = if (x in 0 until g && y in 0 until g) depth.mm[y * g + x] else 0
        for (y in 0 until g) for (x in 0 until g) {
            val mm = depth.mm[y * g + x]
            if (mm <= 0) continue
            val w = pose.unproject(depth.k, x.toDouble(), y.toDouble(), mm / 1000.0)
            above[y * g + x] = (w[1] * 1000).toInt()
            if (x % PIXEL_STEP != 0 || y % PIXEL_STEP != 0 || mm !in MIN_DEPTH_MM..MAX_DEPTH_MM) continue
            pixels += DepthPixel(y * g + x, x.toFloat() / g, y.toFloat() / g, mm,
                floatArrayOf(w[0].toFloat(), w[1].toFloat(), w[2].toFloat()),
                DetectionPoints.isEdge(mm, at(x - PIXEL_STEP, y), at(x + PIXEL_STEP, y), at(x, y - PIXEL_STEP), at(x, y + PIXEL_STEP)))
        }
        val world = FloatArray(pixels.size * 3); val uvs = FloatArray(pixels.size * 2)
        for ((i, p) in pixels.withIndex()) {
            world[3 * i] = p.world[0]; world[3 * i + 1] = p.world[1]; world[3 * i + 2] = p.world[2]
            uvs[2 * i] = p.u; uvs[2 * i + 1] = p.v
        }
        // HarshdeepJ's detection on the depth: regions between depth edges, closed underneath by the
        // surface, each sized by w·d/f with its true height from the scene.
        val background = BooleanArray(g * g) { depth.mm[it] > 0 && above[it] < StandingObjects.MIN_HEIGHT_M * 1000 }
        val faces = DepthEdgeObjects.find(depth.mm, g, g, depth.k.fx.toFloat(), depth.k.fy.toFloat(), background, above).map { o ->
            floatArrayOf(o.left.toFloat() / g, o.top.toFloat() / g, o.right.toFloat() / g, o.bottom.toFloat() / g) to o
        }
        // The fallback stage: anything standing up off the surface.
        val standing = StandingObjects.find(world, uvs, 0f)
        val camPos = pose.cameraPosition
        val cam = floatArrayOf(camPos[0].toFloat(), camPos[1].toFloat(), camPos[2].toFloat())
        // The camera's facing on the surface, for telling front views from side views.
        val yaw = Math.toDegrees(kotlin.math.atan2(pose.r[6], pose.r[8])).toFloat()

        val rotation = frame.rotationDegrees
        // YOLOX finds the objects as well as naming them: it is a detector first, and on the phone
        // it sees a mouse on a white desk that ML Kit and the depth both miss. Run once per pass.
        val upright = if (rotation == 0) picture.copy(Bitmap.Config.ARGB_8888, false)
            else Bitmap.createBitmap(picture, 0, 0, picture.width, picture.height, android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }, true)
        val yoloFound = try {
            (yolo ?: YoloxDetector(context).also { yolo = it }).detect(upright)
        } catch (e: Exception) {
            Log.w(SCAN_TAG, "yolox", e); emptyList()
        }
        val uw = upright.width.toFloat(); val uh = upright.height.toFloat()
        val yoloBoxes = yoloFound.map { d ->
            ScanBox(null, SensorBox.uprightToSensor(d.box[0] * uw, d.box[1] * uh, d.box[2] * uw, d.box[3] * uh,
                picture.width, picture.height, rotation), d.box, frame.timestampNs)
        }
        val mlkit = detector.detect(picture, rotation, frame.timestampNs)
        // YOLOX first (it knows what each thing is); ML Kit adds what YOLOX missed.
        val candidates = yoloBoxes.mapIndexed { i, b -> b to yoloFound[i].label } +
            mlkit.filter { m -> yoloBoxes.none { StandingObjects.overlap(it.sensor, m.sensor) > DEPTH_BOX_MATCH } }.map { it to null }
        val kept = candidates.filter { it.first.area <= MAX_BOX_AREA }
        // Lock on: each detection gets its object's steady id, which the item tracker then follows.
        val lockIds = lockOn.update(kept.map { (b, label) -> com.packabunch.packing.LockOnTracker.Detection(b.sensor, label) })
        val detected = kept.mapIndexed { i, (b, _) -> ScanBox(lockIds[i], b.sensor, b.upright, b.frameTimestampNs, b.faceMm) }
        // ML Kit's boxes, each carrying the HarshdeepJ size of the depth object it covers.
        val named = detected.map { b ->
            val face = faces.maxByOrNull { StandingObjects.overlap(b.sensor, it.first) }
                ?.takeIf { StandingObjects.overlap(b.sensor, it.first) > DEPTH_BOX_MATCH }?.second
            if (face == null) b else ScanBox(b.trackingId, b.sensor, b.upright, b.frameTimestampNs, floatArrayOf(face.widthMm, face.heightMm))
        }
        // The detector misses things; depth does not need light or a known class. Depth-edge objects
        // first; the standing finder only if those found nothing, as HarshdeepJ falls back stage by stage.
        val fromFaces: List<Pair<FloatArray, FloatArray?>> = faces.map { (box, o) -> box to floatArrayOf(o.widthMm, o.heightMm) }
        val fromStanding: List<Pair<FloatArray, FloatArray?>> = standing.map { it.box to null }
        // Only when no detector found anything: depth-only boxes beside a detected object were the
        // second and third copies of the same mouse.
        val fromDepth = (if (named.isEmpty()) fromFaces.ifEmpty { fromStanding } else emptyList())
            .map { (box, face) -> ScanBox(null, box, SensorBox.sensorToUpright(box, rotation), frame.timestampNs, face) }
            .filter { it.area <= MAX_BOX_AREA }
        val boxes = named + fromDepth
        val samples = samplesForBoxes(pixels, picture, boxes, picture.width, picture.height,
            floatArrayOf(frame.intrinsics.fx.toFloat(), frame.intrinsics.fy.toFloat()))
        synchronized(lock) { track(samples, cam, yaw) { u, v, mm -> pose.unproject(frame.intrinsics, u * picture.width, v * picture.height, mm / 1000.0) } }
        _ui.value = _ui.value.copy(scanHint = when {
            detector.failed && boxes.isEmpty() -> "Object detection couldn't run — reopen the scanner"
            boxes.isEmpty() -> "Show the whole object and leave a little gap between items"
            samples.isEmpty() -> "Hold steady — the depth is still settling"
            else -> null
        })
        diagnostics.record("frame", "faces=${faces.size} standing=${standing.size} mlkit=${mlkit.size} yolox=${yoloBoxes.size} " +
            "boxes=${boxes.size} samples=${samples.size} depthPx=${pixels.size} " +
            "cam=%.2f,%.2f,%.2f ".format(cam[0], cam[1], cam[2]) +
            faces.joinToString(prefix = "sizes=") { (_, o) -> "%.0fx%.0fmm@%dmm".format(o.widthMm, o.heightMm, o.depthMm) })

        // Names and photos from the same YOLOX pass that found the objects.
        try { nameAndCrop(upright, frameBoxes, yoloFound) } finally { upright.recycle() }
    }

    /** Depth samples, colour masks and detector boxes all from one picture. */
    private fun samplesForBoxes(pixels: List<DepthPixel>, picture: Bitmap, boxes: List<ScanBox>,
                                imageWidth: Int, imageHeight: Int, focal: FloatArray): List<BoxSamples> {
        if (boxes.isEmpty()) return emptyList()
        val small = Bitmap.createScaledBitmap(picture, TRACE_WIDTH,
            (picture.height * TRACE_WIDTH / picture.width.toFloat()).toInt().coerceAtLeast(1), true)
        // Each object's own pixels (its instance mask), smallest box first so a mug in front of a
        // carton keeps its own outline. Depth outside the mask is never measured as the object.
        val labels = try {
            val argb = IntArray(small.width * small.height)
            small.getPixels(argb, 0, small.width, 0, 0, small.width, small.height)
            val out = ByteArray(argb.size)
            for (index in boxes.indices.sortedBy { boxes[it].area }) {
                val mask = com.packabunch.packing.ColorMask.segment(argb, small.width, small.height, boxes[index].sensor) ?: continue
                for (i in mask.indices) if (mask[i] && out[i].toInt() == 0) out[i] = (index + 1).toByte()
            }
            out
        } catch (e: Exception) {
            Log.w(SCAN_TAG, "mask object", e)
            null
        }
        val present = BooleanArray(256)
        labels?.forEach { present[it.toInt() and 0xff] = true }
        val result = boxes.mapIndexedNotNull { index, box ->
            val b = box.sensor
            val w = b[2] - b[0]; val h = b[3] - b[1]
            val kept = pixels.filter { it.u in (b[0] + w * SHRINK)..(b[2] - w * SHRINK) && it.v in (b[1] + h * SHRINK)..(b[3] - h * SHRINK) }
            if (kept.isEmpty()) return@mapIndexedNotNull null
            val widthPx = w * imageWidth; val heightPx = h * imageHeight
            val useWidth = widthPx / focal[0] >= heightPx / focal[1]
            BoxSamples(box, kept.map { it.index }.toIntArray(), kept.flatMap { it.world.toList() }.toFloatArray(),
                BooleanArray(kept.size) { val p = kept[it]; p.u in (b[0] + w * .3f)..(b[2] - w * .3f) && p.v in (b[1] + h * .3f)..(b[3] - h * .3f) },
                BooleanArray(kept.size) { kept[it].edge }, kept.map { it.mm }.toIntArray(),
                if (useWidth) widthPx else heightPx, if (useWidth) focal[0] else focal[1],
                if (labels != null && present[index + 1]) BooleanArray(kept.size) {
                    val p = kept[it]
                    val x = (p.u * small.width).toInt().coerceIn(0, small.width - 1)
                    val y = (p.v * small.height).toInt().coerceIn(0, small.height - 1)
                    (labels[y * small.width + x].toInt() and 0xff) == index + 1
                } else null,
                if (labels != null && present[index + 1]) outlineOf(labels, small.width, small.height, index + 1) else null)
        }
        if (small !== picture) small.recycle()
        return result
    }

    // -- tracking ------------------------------------------------------------------------------

    private fun track(samples: List<BoxSamples>, cam: FloatArray, yaw: Float, place: (Double, Double, Double) -> DoubleArray) {
        val claimed = HashSet<Int>()
        val observations = ArrayList<ItemTracker.Observation>()
        val observedBoxes = ArrayList<ScanBox>()
        val observedOutlines = ArrayList<DoubleArray?>()
        val surface = PlaneFrame(0f, 0f, 0f, ALONG, UP)
        // Smallest boxes first: a mug in front of a carton keeps its own pixels.
        for (bs in samples.sortedBy { it.box.area }) {
            val keep = bs.pixels.indices.filter { bs.pixels[it] !in claimed }
            if (keep.size < DetectionPoints.MIN_SAMPLES) continue
            val list = keep.map { i ->
                DetectionPoints.Sample(surface.toPlane(bs.world[3 * i], bs.world[3 * i + 1], bs.world[3 * i + 2]), bs.central[i], bs.edge[i])
            }
            val picked = math.select(list, IntArray(keep.size) { bs.depths[keep[it]] }, bs.spanPx, bs.focalPx,
                bs.inMask?.let { m -> BooleanArray(keep.size) { m[keep[it]] } })
            if (picked.isEmpty()) continue
            for (j in picked) claimed += bs.pixels[keep[j]]
            val b = bs.box.sensor
            val whole = b[0] > EDGE_OF_PICTURE && b[1] > EDGE_OF_PICTURE && b[2] < 1 - EDGE_OF_PICTURE && b[3] < 1 - EDGE_OF_PICTURE
            observations += ItemTracker.Observation(bs.box.trackingId, picked.map { list[it].point }, surface.toPlane(cam[0], cam[1], cam[2]), whole)
            observedBoxes += bs.box
            // The outline, placed in the room at the object's own distance, so it stays on the
            // object while the phone moves between measuring passes.
            observedOutlines += bs.hull?.let { h ->
                val mm = picked.map { bs.depths[keep[it]] }.sorted()[picked.size / 2].toDouble()
                DoubleArray(h.size / 2 * 3).also { out ->
                    for (i in 0 until h.size / 2) {
                        val w = place(h[2 * i].toDouble(), h[2 * i + 1].toDouble(), mm)
                        out[3 * i] = w[0]; out[3 * i + 1] = w[1]; out[3 * i + 2] = w[2]
                    }
                }
            }
        }
        val update = tracker.update(observations, surface.toPlane(cam[0], cam[1], cam[2]))
        if (observations.isEmpty() && samples.isNotEmpty()) {
            _ui.value = _ui.value.copy(scanHint = "Object depth isn't clear of its surface — try another angle")
        }
        diagnostics.record("selected", "sampleBoxes=${samples.size}, observations=${observations.size}, tracks=" +
            update.visible.joinToString { t -> "${t.id}:${t.measurement.state::class.simpleName}" })
        for ((obsIndex, trackId) in update.assigned) {
            observedBoxes[obsIndex].faceMm?.let { f -> faceViews.getOrPut(trackId) { TwoViewSize() }.add(yaw, f[0], f[1]) }
            observedOutlines[obsIndex]?.let { outlines3d[trackId] = it }
        }
        frameBoxes = update.assigned.entries.associate { (obsIndex, trackId) -> trackId to observedBoxes[obsIndex].upright }
        publish(update.visible, update.capReached)
    }

    /** Hands the confirmed [tracks] to the screen and the drawing, each in its steadied state. */
    private fun publish(tracks: List<ItemTracker.Track>, capReached: Boolean) {
        val now = System.currentTimeMillis()
        smoother.retain(tracks.map { it.id }.toSet())
        val shown = tracks.associate { t -> t.id to smoother.smooth(t.id, t.state, now) }
        for (t in tracks) if (t.isMeasured) measuredAt.putIfAbsent(t.id, now)
        drawn = tracks.map { t -> TrackSnapshot(t.id, shown.getValue(t.id), t.fit, measuredAt[t.id]) }
        val items = tracks.map { t ->
            val face = faceViews[t.id]?.let { v ->
                val w = v.widthMm; val h = v.heightMm
                if (w != null && h != null) FaceSize(w, v.depthMm, h) else null
            }
            ScanItem(t.id, names[t.id], shown.getValue(t.id), t.fit?.shape, crops[t.id], t.fit, face)
        }
        val u = _ui.value
        _ui.value = u.copy(items = items, capReached = capReached || (u.capReached && tracks.size >= u.maxItems))
    }

    // -- naming ----------------------------------------------------------------------------------

    /**
     * Names every tracked object with YOLOX and keeps its best crop as its photo. A name must win
     * 3 of the last 5 looks before it shows; once earned it only changes if a different name wins.
     */
    private fun nameAndCrop(picture: Bitmap, tracks: Map<Int, FloatArray>, detections: List<YoloxDecode.Detection>) {
        if (!yoloBusy.compareAndSet(false, true)) return
        try {
            diagnostics.record("yolox", detections.joinToString { "${it.label}:%.2f".format(it.score) })
            for ((id, box) in tracks) {
                val area = (box[2] - box[0]) * (box[3] - box[1])
                cropUpright(picture, box)?.let { crop ->
                    if (area > (cropArea[id] ?: 0f)) { crops[id] = crop; cropArea[id] = area }
                }
                val match = detections.maxByOrNull { StandingObjects.overlap(it.box, box) }
                    ?.takeIf { StandingObjects.overlap(it.box, box) > NAME_MATCH }
                val agreed = nameEvidence.getOrPut(id) { ScanLabelConsensus() }.observe(match?.let { YoloxDecode.itemName(it.label) })
                if (agreed != null) names[id] = agreed
            }
        } finally {
            yoloBusy.set(false)
        }
        synchronized(lock) { publish(tracker.all.filter { it.isConfirmed }, _ui.value.capReached) }
    }

    // -- drawing ---------------------------------------------------------------------------------

    private class TrackSnapshot(val id: Int, val state: ItemScanState, val fit: FittedObject?, val measuredAtMs: Long?)

    /** Projects each object's fitted outline with this frame's camera and hands it to the screen. */
    private fun draw(frame: CameraFrame, pose: PlanarPose.Pose) {
        val mapping = ViewMapping(frame.rotationDegrees, frame.picture.width, frame.picture.height, viewW, viewH)
        val view = Projector(pose, frame.intrinsics, mapping)
        val strokes = ArrayList<OutlineStroke>(); val fills = ArrayList<OutlineFill>()
        val anchors = ArrayList<ScanAnchor>()
        val now = System.currentTimeMillis()
        val pf = PlaneFrame(0f, 0f, 0f, ALONG, UP)
        val camPos = pose.cameraPosition
        val cam = pf.toPlane(camPos[0].toFloat(), camPos[1].toFloat(), camPos[2].toFloat())
        for (t in drawn) {
            // The traced outline — the object's own silhouette, like instance segmentation draws
            // it — when there is one; the fitted shape only when the object could not be traced.
            val traced = outlines3d[t.id]?.takeIf { t.state !is ItemScanState.CannotMeasure }?.let { c ->
                val pts = FloatArray(c.size / 3 * 2 + 2)
                var ok = true
                for (i in 0 until c.size / 3) {
                    val px = pose.project(frame.intrinsics, doubleArrayOf(c[3 * i], c[3 * i + 1], c[3 * i + 2])) ?: run { ok = false; null } ?: break
                    val (x, y) = mapping.toView(px[0], px[1])
                    pts[2 * i] = x; pts[2 * i + 1] = y
                }
                if (ok) pts.also { it[it.size - 2] = it[0]; it[it.size - 1] = it[1] } else null
            }
            if (traced != null) {
                val st = t.state
                val stroke = when (st) {
                    is ItemScanState.Measured -> {
                        val progress = ((now - (t.measuredAtMs ?: now)) / OutlineStyle.DRAW_ON_MS.toFloat()).coerceIn(0f, 1f)
                        OutlineStroke(traced, OutlineStyle.MEASURED, revealPx = if (progress >= 1f) null else length(traced) * easeOut(progress))
                    }
                    is ItemScanState.NeedsAngle -> OutlineStroke(traced, OutlineStyle.ANOTHER_ANGLE_UNSEEN)
                    else -> OutlineStroke(traced, OutlineStyle.SCANNING, dashPhasePx = -((now % MARCH_PERIOD_MS) / MARCH_PERIOD_MS.toFloat()) *
                        (OutlineStyle.SCANNING.dashOnDp + OutlineStyle.SCANNING.dashOffDp) * density * 3f)
                }
                strokes += stroke
                var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var top = Float.MAX_VALUE
                for (i in 0 until traced.size / 2) { minX = minOf(minX, traced[2 * i]); maxX = maxOf(maxX, traced[2 * i]); top = minOf(top, traced[2 * i + 1]) }
                val tagX = ((minX + maxX) / 2).coerceIn(0f, viewW.toFloat()); val tagY = top - TAG_GAP_DP * density
                val fitNow = t.fit
                anchors += if (fitNow != null) labelAnchors(t.id, tagX, tagY, fitNow, cam, OutlineGeometry.of(fitNow, cam), pf, view).copy(span = (maxX - minX) / viewW)
                    else ScanAnchor(t.id, tagX / viewW, tagY / viewH, span = (maxX - minX) / viewW)
                continue
            }
            val fit = t.fit ?: continue
            val segments = OutlineGeometry.of(fit, cam)
            when (val st = t.state) {
                // No outline: a wrong outline would say "measured this". Only the tag, saying why.
                is ItemScanState.CannotMeasure -> Unit
                is ItemScanState.Measured -> {
                    val elapsed = now - (t.measuredAtMs ?: now)
                    val progress = (elapsed / OutlineStyle.DRAW_ON_MS.toFloat()).coerceIn(0f, 1f)
                    view.project(OutlineGeometry.footprint(fit), pf)?.let { poly ->
                        if (progress > 0.5f) fills += OutlineFill(poly, fadeAlpha(OutlineStyle.MEASURED_FILL_ARGB, (progress - 0.5f) * 2f))
                    }
                    for (l in polylines(segments, pf, view)) {
                        strokes += OutlineStroke(l, OutlineStyle.MEASURED, revealPx = if (progress >= 1f) null else length(l) * easeOut(progress))
                    }
                }
                is ItemScanState.NeedsAngle -> {
                    // Round things have one footprint length: an unseen depth is an unseen width.
                    val unseen = if (fit.shape == ShapeFamily.BOX || fit.shape == ShapeFamily.IRREGULAR) st.unseen
                    else st.unseen.map { if (it == Axis.DEPTH) Axis.WIDTH else it }.toSet()
                    for (l in polylines(segments.filter { it.axis !in unseen }, pf, view)) strokes += OutlineStroke(l, OutlineStyle.ANOTHER_ANGLE_SEEN)
                    for (l in polylines(segments.filter { it.axis in unseen }, pf, view)) strokes += OutlineStroke(l, OutlineStyle.ANOTHER_ANGLE_UNSEEN)
                }
                else -> {
                    // Backwards along the line reads as the dashes travelling forwards.
                    val phase = -((now % MARCH_PERIOD_MS) / MARCH_PERIOD_MS.toFloat()) *
                        (OutlineStyle.SCANNING.dashOnDp + OutlineStyle.SCANNING.dashOffDp) * density * 3f
                    for (l in polylines(segments, pf, view)) strokes += OutlineStroke(l, OutlineStyle.SCANNING, dashPhasePx = phase)
                }
            }
            tagPoint(segments, fit, pf, view)?.let { (x, y) -> anchors += labelAnchors(t.id, x, y, fit, cam, segments, pf, view) }
        }
        _outlines.value = OutlineScene(strokes, fills)
        _anchors.value = anchors
    }

    /** World points to view pixels for one frame. */
    private class Projector(val pose: PlanarPose.Pose, val k: PlanarPose.Intrinsics, val mapping: ViewMapping) {
        private val world = FloatArray(3)

        fun point(pf: PlaneFrame, p: PlanePoint): Pair<Float, Float>? {
            pf.toWorld(p, world)
            val c = pose.toCamera(doubleArrayOf(world[0].toDouble(), world[1].toDouble(), world[2].toDouble()))
            // Nearer than this, a corner projects thousands of pixels off screen and slashes across the view.
            if (c[2] <= MIN_DRAW_DISTANCE_M) return null
            return mapping.toView(k.fx * c[0] / c[2] + k.cx, k.fy * c[1] / c[2] + k.cy)
        }

        fun project(points: List<PlanePoint>, pf: PlaneFrame): FloatArray? {
            val out = FloatArray(points.size * 2)
            for ((i, p) in points.withIndex()) {
                val (x, y) = point(pf, p) ?: return null
                out[2 * i] = x; out[2 * i + 1] = y
            }
            return out
        }
    }

    /**
     * Pill positions. Width goes on the bottom edge nearest the camera, depth on the top edge
     * running away from it, height on the upright nearest the camera — the edges a person
     * would hold a tape against from where they are standing.
     */
    private fun labelAnchors(
        id: Int, tagX: Float, tagY: Float, fit: FittedObject, cam: PlanePoint,
        segments: List<OutlineSegment>, pf: PlaneFrame, view: Projector,
    ): ScanAnchor {
        fun dist(p: PlanePoint) = kotlin.math.hypot(p.xMm - cam.xMm, p.yMm - cam.yMm)
        fun mid(a: PlanePoint, b: PlanePoint) = PlanePoint((a.xMm + b.xMm) / 2, (a.yMm + b.yMm) / 2, (a.hMm + b.hMm) / 2)
        fun screen(p: PlanePoint) = view.point(pf, p)?.let { (x, y) ->
            if (x < 0 || y < 0 || x > viewW || y > viewH) null else x / viewW to y / viewH
        }
        val round = fit.shape == ShapeFamily.CYLINDER || fit.shape == ShapeFamily.TAPERED || fit.shape == ShapeFamily.SPHERE
        val width: PlanePoint?; val depth: PlanePoint?; val height: PlanePoint?
        if (round) {
            val dx = cam.xMm - fit.centreXMm; val dy = cam.yMm - fit.centreYMm
            val l = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
            val r = fit.bottomRadiusMm ?: (fit.widthMm / 2)
            width = PlanePoint(fit.centreXMm + dx / l * r, fit.centreYMm + dy / l * r, 0f)
            depth = null
            height = segments.filter { it.axis == Axis.HEIGHT }.minByOrNull { dist(mid(it.a, it.b)) }?.let { mid(it.a, it.b) }
        } else {
            width = segments.filter { it.axis == Axis.WIDTH && it.a.hMm < 1f }.minByOrNull { dist(mid(it.a, it.b)) }?.let { mid(it.a, it.b) }
            depth = segments.filter { it.axis == Axis.DEPTH && it.a.hMm > 1f }.minByOrNull { dist(mid(it.a, it.b)) }?.let { mid(it.a, it.b) }
            height = segments.filter { it.axis == Axis.HEIGHT }.minByOrNull { dist(it.a) }?.let { mid(it.a, it.b) }
        }
        val xs = OutlineGeometry.footprint(fit, 16).mapNotNull { view.point(pf, it)?.first }
        val span = if (xs.size >= 2) (xs.max() - xs.min()) / viewW else 0f
        return ScanAnchor(id, tagX / viewW, tagY / viewH,
            width = width?.let(::screen), depth = depth?.let(::screen), height = height?.let(::screen), span = span)
    }

    /** Top middle of the object's on-screen outline, or null when none of it is on screen. */
    private fun tagPoint(segments: List<OutlineSegment>, fit: FittedObject, pf: PlaneFrame, view: Projector): Pair<Float, Float>? {
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var top = Float.MAX_VALUE
        for (s in segments) for (p in listOf(s.a, s.b)) {
            val q = view.point(pf, p) ?: continue
            if (q.first !in 0f..viewW.toFloat() || q.second !in 0f..viewH.toFloat()) continue
            minX = minOf(minX, q.first); maxX = maxOf(maxX, q.first); top = minOf(top, q.second)
        }
        if (top == Float.MAX_VALUE) return null
        val centre = view.point(pf, PlanePoint(fit.centreXMm, fit.centreYMm, fit.heightMm))?.first ?: ((minX + maxX) / 2)
        return centre.coerceIn(minX, maxX) to (top - TAG_GAP_DP * density)
    }

    private fun polylines(segments: List<OutlineSegment>, pf: PlaneFrame, view: Projector): List<FloatArray> {
        // Chain segments that meet end to start so dashes run on around corners and curves.
        val out = ArrayList<FloatArray>()
        var current = ArrayList<PlanePoint>()
        fun flush() {
            if (current.size >= 2) view.project(current, pf)?.let { out += it }
            current = ArrayList()
        }
        for (s in segments) {
            if (current.isNotEmpty() && current.last() == s.a) current += s.b
            else { flush(); current += s.a; current += s.b }
        }
        flush()
        return out
    }

    /**
     * The traced outline of one object in its mask: its boundary's convex hull, in 0..1 of the
     * picture. The hull rather than every jag of the boundary, so the drawn outline is clean and
     * steady from frame to frame.
     */
    private fun outlineOf(labels: ByteArray, w: Int, h: Int, id: Int): FloatArray? {
        val pts = ArrayList<Pair<Float, Float>>()
        for (y in 0 until h) for (x in 0 until w) {
            if ((labels[y * w + x].toInt() and 0xff) != id) continue
            val edge = x == 0 || y == 0 || x == w - 1 || y == h - 1 ||
                (labels[y * w + x - 1].toInt() and 0xff) != id || (labels[y * w + x + 1].toInt() and 0xff) != id ||
                (labels[(y - 1) * w + x].toInt() and 0xff) != id || (labels[(y + 1) * w + x].toInt() and 0xff) != id
            if (edge) pts += (x + 0.5f) / w to (y + 0.5f) / h
        }
        val p = pts.distinct().sortedWith(compareBy({ it.first }, { it.second }))
        if (p.size < 3) return null
        fun cross(o: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>) =
            (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)
        val lower = ArrayList<Pair<Float, Float>>()
        for (q in p) { while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), q) <= 0) lower.removeAt(lower.size - 1); lower += q }
        val upper = ArrayList<Pair<Float, Float>>()
        for (q in p.asReversed()) { while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), q) <= 0) upper.removeAt(upper.size - 1); upper += q }
        val hull = lower.dropLast(1) + upper.dropLast(1)
        if (hull.size < 3) return null
        return FloatArray(hull.size * 2) { if (it % 2 == 0) hull[it / 2].first else hull[it / 2].second }
    }

    private companion object {
        val ALONG = floatArrayOf(1f, 0f, 0f)
        val UP = floatArrayOf(0f, 1f, 0f)
        /** The full measuring pass runs at most this often; outlines follow every frame. */
        const val PROCESS_INTERVAL_NS = 150_000_000L
        /** A depth-found object overlapping a detector box this much is the same object. */
        const val DEPTH_BOX_MATCH = 0.3f
        /** A YOLOX box overlapping an object this much names it. */
        const val NAME_MATCH = 0.3f
        /** Depth is sampled every this many grid pixels each way. */
        const val PIXEL_STEP = 2
        /** Outlines are traced in the picture on a frame this many pixels wide. */
        const val TRACE_WIDTH = 240
        const val MIN_DEPTH_MM = 120
        /** Items are scanned at arm's length; past this it is the room behind the table. */
        const val MAX_DEPTH_MM = 1_500
        /** Nearest a point may be to the camera, in metres, and still be drawn. */
        const val MIN_DRAW_DISTANCE_M = 0.1
        /** A detector box this close to the picture's edge may have more of its object outside it. */
        const val EDGE_OF_PICTURE = 0.01f
        /** A detector box bigger than this share of the picture is the scene, not an object. */
        const val MAX_BOX_AREA = 0.5f
        /** Share of a detector box trimmed off each side before sampling depth. */
        const val SHRINK = 0.01f
        /** Space between an object's outline and the bottom of its tag. */
        const val TAG_GAP_DP = 6f
        /** One full march of the scanning dashes (three dash periods) takes this long. */
        const val MARCH_PERIOD_MS = 1_200L

        fun length(p: FloatArray): Float {
            var l = 0f
            for (i in 0 until p.size / 2 - 1) l += kotlin.math.hypot(p[2 * i + 2] - p[2 * i], p[2 * i + 3] - p[2 * i + 1])
            return l
        }

        fun easeOut(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)

        fun fadeAlpha(argb: Long, f: Float): Long {
            val a = (((argb shr 24) and 0xFF) * f.coerceIn(0f, 1f)).toLong()
            return (a shl 24) or (argb and 0xFFFFFF)
        }

        /** The object's own pixels from the upright picture, with a little margin. */
        fun cropUpright(frame: Bitmap, box: FloatArray): Bitmap? {
            val m = 0.01f
            val x = ((box[0] - m) * frame.width).toInt().coerceIn(0, frame.width - 1)
            val y = ((box[1] - m) * frame.height).toInt().coerceIn(0, frame.height - 1)
            val w = ((box[2] - box[0] + 2 * m) * frame.width).toInt().coerceAtMost(frame.width - x)
            val h = ((box[3] - box[1] + 2 * m) * frame.height).toInt().coerceAtMost(frame.height - y)
            if (w < 32 || h < 32) return null
            return runCatching { Bitmap.createBitmap(frame, x, y, w, h) }.getOrNull()
        }
    }
}
