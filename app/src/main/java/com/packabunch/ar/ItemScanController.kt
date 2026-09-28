package com.packabunch.ar

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.UnavailableException
import com.packabunch.packing.Axis
import com.packabunch.packing.BoxDepth
import com.packabunch.packing.DetectionPoints
import com.packabunch.packing.FittedObject
import com.packabunch.packing.ItemScanState
import com.packabunch.packing.ItemTracker
import com.packabunch.packing.MeasuredDimensions
import com.packabunch.packing.OutlineGeometry
import com.packabunch.packing.OutlineSegment
import com.packabunch.packing.PlaneFrame
import com.packabunch.packing.PlanePoint
import com.packabunch.packing.ScanStateSmoother
import com.packabunch.packing.ShapeFamily
import com.packabunch.packing.WallPlane
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

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
)

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
    val failureReason: TrackingFailureReason? = null,
    /** A table, floor or shelf has been found — items can only be measured standing on one. */
    val surfaceFound: Boolean = false,
    val items: List<ScanItem> = emptyList(),
    /** An object was in view but not started because the plan's limit was reached. */
    val capReached: Boolean = false,
    val maxItems: Int = ItemTracker.FREE_PLAN_MAX_ITEMS,
    val torchOn: Boolean = false,
    val torchAvailable: Boolean = false,
    val fatalError: String? = null,
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
 * The item scan: point the phone at things on a table and each one gets measured.
 *
 * ### Frame by frame
 * On the GL thread, at most ten times a second: the depth image is sampled inside each ML Kit
 * box and turned into world points, and the camera image is handed to ML Kit for the next
 * round. Everything expensive — deciding which points are the object, fitting it, tracking it —
 * runs on one worker thread and posts back a snapshot. If the worker is still busy, the frame
 * is skipped rather than queued. The GL thread only ever draws the latest snapshot, so the
 * camera never stutters because a fit took long.
 *
 * ### Coordinates
 * ARCore's world Y is gravity. Each object is measured in a frame whose origin is at the height
 * of the surface it stands on and whose axes are world X and −Z, so all objects share one plan
 * (the tracker can compare their positions) while each height is from its own surface.
 *
 * ### Keeping the room out
 * A detector box around a heater against a wall is mostly wall, and the old scan tracked the
 * wall as an object and drew an outline the size of the room. Three things stop that now:
 * boxes covering most of the picture are the scene, not a thing, and are skipped; inside a box
 * only depths near its middle's are kept ([BoxDepth]); and points on a *large* vertical plane —
 * a wall, a fridge door — are dropped. Only large ones: the old scan deleted every point on any
 * vertical plane, which is exactly where a carton's sides are.
 *
 * Nothing is uploaded.
 */
class ItemScanController(
    private val context: Context,
    private val density: Float,
    maxItems: Int = ItemTracker.FREE_PLAN_MAX_ITEMS,
) : GLSurfaceView.Renderer {

    private val _ui = MutableStateFlow(ItemScanUi(maxItems = maxItems))
    val ui: StateFlow<ItemScanUi> = _ui.asStateFlow()

    private val _anchors = MutableStateFlow<List<ScanAnchor>>(emptyList())

    /** Tag positions, per frame. Separate from [ui] so moving tags do not rebuild the screen. */
    val anchors: StateFlow<List<ScanAnchor>> = _anchors.asStateFlow()

    private var session: Session? = null
    private var config: Config? = null
    private val background = CameraBackgroundRenderer()
    private val outlines = GlowOutlineRenderer()
    private val detector = ItemScanDetector()

    /**
     * The latest outlines traced in the picture (see [PythonEngine.segmentFrame]): one byte per
     * pixel of a small sensor-oriented frame, with the tracking ids of the boxes in label order.
     */
    private class Traced(val labels: ByteArray, val width: Int, val height: Int, val ids: List<Int?>, val boxes: List<FloatArray>, val atNs: Long)
    @Volatile private var traced: Traced? = null
    private val tracing = java.util.concurrent.atomic.AtomicBoolean(false)
    private val tracer = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "outline-tracer").apply { isDaemon = true } }
    private var lastTraceNs = 0L

    /** The measuring engine: Python (packscan.py), with the Kotlin one as its fallback. */
    private val math = PythonEngine.lazy(context)
    private val tracker = ItemTracker(maxItems, math = math)
    private val smoother = ScanStateSmoother()
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "item-scan").apply { priority = Thread.NORM_PRIORITY - 1 } }
    private val workerBusy = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** What the GL thread draws. Replaced wholesale by the worker, never mutated. */
    @Volatile private var drawn: List<TrackSnapshot> = emptyList()

    private val planeHeights = ConcurrentHashMap<Int, Float>()
    private val names = ConcurrentHashMap<Int, String>()
    private val crops = ConcurrentHashMap<Int, Bitmap>()
    private val cropArea = ConcurrentHashMap<Int, Float>()
    private val measuredAt = ConcurrentHashMap<Int, Long>()
    private val naming = java.util.Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())
    private val recogniser by lazy { com.packabunch.data.catalogue.ItemRecogniser() }

    private var viewportW = 1
    private var viewportH = 1
    private var displayRotation = 0
    private var geometryDirty = true
    private var lastProcessedNs = 0L
    private var lastDepthNs = 0L
    private var lastNamingNs = 0L
    @Volatile private var pendingTorch: Boolean? = null

    private val view = FloatArray(16)
    private val proj = FloatArray(16)
    private val viewProj = FloatArray(16)

    private val sensorRotationDegrees: Int by lazy {
        runCatching {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val back = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) ==
                    android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
            } ?: manager.cameraIdList.first()
            manager.getCameraCharacteristics(back).get(android.hardware.camera2.CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        }.getOrDefault(90)
    }

    // -- lifecycle -------------------------------------------------------------------------------

    /** Null on success, otherwise why it could not start, in words for the person. */
    fun resume(rotation: Int, width: Int, height: Int): String? {
        displayRotation = rotation; viewportW = width; viewportH = height; geometryDirty = true
        return try {
            if (session == null) {
                val s = Session(context)
                if (!s.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    s.close()
                    return "This phone can't measure depth. You can still type sizes in."
                }
                val c = Config(s).apply {
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    focusMode = Config.FocusMode.AUTO
                    depthMode = Config.DepthMode.AUTOMATIC
                    lightEstimationMode = Config.LightEstimationMode.DISABLED
                }
                s.configure(c)
                session = s; config = c
                _ui.value = _ui.value.copy(torchAvailable = true)
            }
            session?.resume()
            null
        } catch (e: CameraNotAvailableException) {
            "The camera is being used by another app."
        } catch (e: UnavailableException) {
            e.message ?: "AR isn't available on this phone."
        } catch (t: Throwable) {
            Log.e(AR_TAG, "item scan could not start", t)
            t.message ?: "The scan couldn't start."
        }
    }

    fun pause() { session?.pause() }

    fun release() {
        scope.cancel()
        worker.shutdownNow()
        tracer.shutdownNow()
        detector.close()
        session?.close()
        session = null
    }

    fun onSurfaceSizeChanged(rotation: Int, width: Int, height: Int) {
        displayRotation = rotation; viewportW = width; viewportH = height; geometryDirty = true
    }

    fun setTorch(on: Boolean) { pendingTorch = on }

    /** Forgets one object (the ✕ on its tag). */
    fun remove(id: Int) = worker.execute {
        tracker.remove(id)
        publish(tracker.all.filter { it.isConfirmed }, capReached = false)
    }

    /** Throws everything away and starts again, keeping the camera running. */
    fun restart() = worker.execute {
        tracker.clear()
        smoother.clear()
        names.clear(); crops.clear(); cropArea.clear(); measuredAt.clear(); planeHeights.clear()
        publish(emptyList(), capReached = false)
    }

    /**
     * Everything measured, ready to become items. Blocks briefly for the worker; call it off
     * the main thread.
     */
    fun results(): List<ScannedItemResult> = worker.submit<List<ScannedItemResult>> {
        tracker.all.mapNotNull { t ->
            val m = t.state as? ItemScanState.Measured ?: return@mapNotNull null
            ScannedItemResult(t.id, names[t.id] ?: t.label, m.dimensions, m.fit.shape, crops[t.id], m.fit)
        }
    }.get()

    // -- GL ----------------------------------------------------------------------------------------

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            background.createOnGlThread()
            outlines.createOnGlThread()
            session?.setCameraTextureName(background.textureId)
        } catch (t: Throwable) {
            Log.e(AR_TAG, "item scan GL setup failed", t)
            _ui.value = _ui.value.copy(fatalError = "The camera view couldn't start.")
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportW = width; viewportH = height; geometryDirty = true
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val s = session ?: return
        try {
            s.setCameraTextureName(background.textureId)
            if (geometryDirty) { s.setDisplayGeometry(displayRotation, viewportW, viewportH); geometryDirty = false }
            pendingTorch?.let { on -> applyTorch(s, on); pendingTorch = null }

            val frame = s.update()
            background.draw(frame)
            val camera = frame.camera
            if (camera.trackingState != TrackingState.TRACKING) {
                val u = _ui.value
                _ui.value = u.copy(
                    status = if (u.items.isEmpty() && u.status == TrackingStatus.INITIALISING) TrackingStatus.INITIALISING else TrackingStatus.LOST,
                    failureReason = camera.trackingFailureReason,
                )
                return
            }
            camera.getViewMatrix(view, 0)
            camera.getProjectionMatrix(proj, 0, 0.05f, 20f)
            Matrix.multiplyMM(viewProj, 0, proj, 0, view, 0)

            val tracked = s.getAllTrackables(Plane::class.java).filter { it.trackingState == TrackingState.TRACKING && it.subsumedBy == null }
            val planes = tracked.filter { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }
            val walls = tracked.filter { it.type == Plane.Type.VERTICAL && WallPlane.isWallSized(it.extentX, it.extentZ) }
            if (_ui.value.status != TrackingStatus.TRACKING || _ui.value.surfaceFound != planes.isNotEmpty()) {
                _ui.value = _ui.value.copy(status = TrackingStatus.TRACKING, failureReason = null, surfaceFound = planes.isNotEmpty())
            }

            if (frame.timestamp - lastProcessedNs >= PROCESS_INTERVAL_NS && planes.isNotEmpty()) {
                lastProcessedNs = frame.timestamp
                sampleAndSubmit(frame, planes, walls)
            }
            drawOutlines(frame)
        } catch (e: CameraNotAvailableException) {
            _ui.value = _ui.value.copy(fatalError = "The camera stopped responding.")
        } catch (t: Throwable) {
            Log.e(AR_TAG, "item scan frame failed", t)
        }
    }

    private fun applyTorch(s: Session, on: Boolean) {
        val c = config ?: return
        runCatching {
            c.flashMode = if (on) Config.FlashMode.TORCH else Config.FlashMode.OFF
            s.configure(c)
            _ui.value = _ui.value.copy(torchOn = on)
        }.onFailure { Log.w(AR_TAG, "torch", it) }
    }

    // -- sampling (GL thread) ------------------------------------------------------------------

    private class PlaneSnap(val y: Float, val polygonXZ: FloatArray)

    private class BoxSamples(val box: ScanBox, val pixels: IntArray, val world: FloatArray, val central: BooleanArray, val edge: BooleanArray,
                             val depths: IntArray, val spanPx: Float, val focalPx: Float, val inMask: BooleanArray?)

    private fun sampleAndSubmit(frame: Frame, planes: List<Plane>, wallPlanes: List<Plane>) {
        val camera = frame.camera
        val pose = camera.pose

        // Camera image: every processed frame goes to the detector; about once a second one is
        // also turned into a picture first, for naming and the thumbnail.
        try {
            val image = frame.acquireCameraImage()
            if (frame.timestamp - lastNamingNs >= NAMING_INTERVAL_NS && needsNaming()) {
                lastNamingNs = frame.timestamp
                val picture = yuvToUprightBitmap(image, sensorRotationDegrees)
                val boxes = detector.latest
                if (picture != null) scope.launch { nameAndCrop(picture, boxes) }
            }
            if (frame.timestamp - lastTraceNs >= TRACE_INTERVAL_NS && !tracing.get()) {
                val engine = PythonEngine.tracer()
                val boxes = detector.latest.filter { frame.timestamp - it.frameTimestampNs < MAX_BOX_AGE_NS && it.area <= MAX_BOX_AREA }
                if (engine != null && boxes.isNotEmpty()) {
                    val small = yuvToUprightBitmap(image, 0)?.let { full ->
                        val w = TRACE_WIDTH; val h = (full.height * TRACE_WIDTH / full.width.toFloat()).toInt().coerceAtLeast(1)
                        Bitmap.createScaledBitmap(full, w, h, true).also { if (it !== full) full.recycle() }
                    }
                    if (small != null) {
                        lastTraceNs = frame.timestamp
                        tracing.set(true)
                        val at = frame.timestamp
                        tracer.execute {
                            try {
                                val px = IntArray(small.width * small.height)
                                small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
                                val rgb = ByteArray(px.size * 3)
                                for (i in px.indices) { val c = px[i]; rgb[3 * i] = (c shr 16).toByte(); rgb[3 * i + 1] = (c shr 8).toByte(); rgb[3 * i + 2] = c.toByte() }
                                engine.segmentFrame(rgb, small.width, small.height, boxes.map { it.sensor })?.let { labels ->
                                    traced = Traced(labels, small.width, small.height, boxes.map { it.trackingId }, boxes.map { it.sensor }, at)
                                }
                            } finally {
                                small.recycle()
                                tracing.set(false)
                            }
                        }
                    }
                }
            }
            detector.offer(image, sensorRotationDegrees, frame.timestamp)
        } catch (_: NotYetAvailableException) {
        } catch (e: Exception) {
            Log.w(AR_TAG, "camera image", e)
        }

        if (workerBusy.get()) return
        // A box over most of the picture is ML Kit boxing the scene — the wall, the floor — not a thing.
        val boxes = detector.latest.filter { frame.timestamp - it.frameTimestampNs < MAX_BOX_AGE_NS && it.area <= MAX_BOX_AREA }
        val walls = wallPlanes.map {
            // A plane's own Y axis is its normal; X and Z run along it.
            val c = it.centerPose
            WallPlane(c.translation, c.yAxis, c.xAxis, c.zAxis, it.extentX / 2, it.extentZ / 2)
        }
        val snaps = planes.map { p ->
            val c = p.centerPose
            val poly = p.polygon.let { buf -> FloatArray(buf.limit()).also { buf.rewind(); buf.get(it) } }
            // Polygon is in the plane's local X/Z; move it into world X/Z.
            val world = FloatArray(poly.size)
            val local = FloatArray(3); val out = FloatArray(3)
            for (i in 0 until poly.size / 2) {
                local[0] = poly[2 * i]; local[1] = 0f; local[2] = poly[2 * i + 1]
                c.transformPoint(local, 0, out, 0)
                world[2 * i] = out[0]; world[2 * i + 1] = out[2]
            }
            PlaneSnap(c.ty(), world)
        }
        val cam = floatArrayOf(pose.tx(), pose.ty(), pose.tz())

        val samples = ArrayList<BoxSamples>()
        try {
            frame.acquireDepthImage16Bits().use { depth ->
                if (depth.timestamp == lastDepthNs) return
                lastDepthNs = depth.timestamp
                val intr = camera.imageIntrinsics
                val f = intr.focalLength; val pp = intr.principalPoint; val dim = intr.imageDimensions
                val projection = DepthProjection.scaled(f[0], f[1], pp[0], pp[1], dim[0], dim[1], depth.width, depth.height)
                val plane = depth.planes[0]
                val buf = plane.buffer.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN)
                val local = FloatArray(3); val world = FloatArray(3)
                val focal = maxOf(projection.fx, projection.fy)
                fun depthAt(u: Int, v: Int): Int =
                    if (u < 0 || v < 0 || u >= depth.width || v >= depth.height) 0
                    else buf.getShort(v * plane.rowStride + u * plane.pixelStride).toInt() and 0xffff
                val outline = traced?.takeIf { frame.timestamp - it.atNs < MAX_TRACE_AGE_NS }
                for (box in boxes) {
                    // This box's traced object: same tracking id, or failing that the same place.
                    val label = outline?.let { t ->
                        val byId = box.trackingId?.let { id -> t.ids.indexOf(id) }?.takeIf { it >= 0 }
                        (byId ?: t.boxes.indexOfFirst { b -> (0..3).all { k -> kotlin.math.abs(b[k] - box.sensor[k]) < 0.04f } }.takeIf { it >= 0 })?.plus(1)
                    }
                    val masked = ArrayList<Boolean>()
                    val w = box.sensor[2] - box.sensor[0]; val h = box.sensor[3] - box.sensor[1]
                    val u0 = ((box.sensor[0] + w * SHRINK) * depth.width).toInt().coerceIn(0, depth.width - 1)
                    val u1 = ((box.sensor[2] - w * SHRINK) * depth.width).toInt().coerceIn(0, depth.width - 1)
                    val v0 = ((box.sensor[1] + h * SHRINK) * depth.height).toInt().coerceIn(0, depth.height - 1)
                    val v1 = ((box.sensor[3] - h * SHRINK) * depth.height).toInt().coerceIn(0, depth.height - 1)
                    if (u1 <= u0 || v1 <= v0) continue
                    val step = if ((u1 - u0) * (v1 - v0) > 2400) 2 else 1
                    val cu0 = u0 + (u1 - u0) * 0.3f; val cu1 = u1 - (u1 - u0) * 0.3f
                    val cv0 = v0 + (v1 - v0) * 0.3f; val cv1 = v1 - (v1 - v0) * 0.3f
                    // First pass: the raw depths, to find how far back the middle of the box is.
                    val raw = ArrayList<Int>(); val rawMm = ArrayList<Int>(); val rawCentral = ArrayList<Boolean>()
                    var v = v0
                    while (v <= v1) {
                        var u = u0
                        while (u <= u1) {
                            val mm = buf.getShort(v * plane.rowStride + u * plane.pixelStride).toInt() and 0xffff
                            if (mm in MIN_DEPTH_MM..MAX_DEPTH_MM) {
                                raw += v * depth.width + u
                                rawMm += mm
                                rawCentral += u >= cu0 && u <= cu1 && v >= cv0 && v <= cv1
                            }
                            u += step
                        }
                        v += step
                    }
                    val depths = rawMm.toIntArray(); val centralArr = rawCentral.toBooleanArray()
                    // Second pass: world points, nothing on a wall. Which depths are the object is the
                    // engine's call (ScanMath.select), made on the worker thread.
                    val px = ArrayList<Int>(); val pts = ArrayList<Float>(); val central = ArrayList<Boolean>(); val edge = ArrayList<Boolean>()
                    val kept = ArrayList<Int>()
                    for (k in depths.indices) {
                        val mm = depths[k]
                        val pix = raw[k]
                        val pu = pix % depth.width; val pv = pix / depth.width
                        val p = projection.point(pu, pv, mm)
                        local[0] = p[0]; local[1] = p[1]; local[2] = p[2]
                        pose.transformPoint(local, 0, world, 0)
                        if (walls.any { w -> w.contains(world[0], world[1], world[2]) }) continue
                        px += pix
                        pts += world[0]; pts += world[1]; pts += world[2]
                        central += centralArr[k]
                        edge += DetectionPoints.isEdge(mm, depthAt(pu - 1, pv), depthAt(pu + 1, pv), depthAt(pu, pv - 1), depthAt(pu, pv + 1))
                        kept += mm
                        if (label != null && outline != null) {
                            // Depth and camera image share the field of view; scale across.
                            val mx = (pu * outline.width / depth.width).coerceIn(0, outline.width - 1)
                            val my = (pv * outline.height / depth.height).coerceIn(0, outline.height - 1)
                            masked += (outline.labels[my * outline.width + mx].toInt() and 0xff) == label
                        }
                    }
                    if (px.isNotEmpty()) samples += BoxSamples(box, px.toIntArray(), pts.toFloatArray(), central.toBooleanArray(), edge.toBooleanArray(),
                        kept.toIntArray(), maxOf(u1 - u0, v1 - v0).toFloat(), focal, if (label != null) masked.toBooleanArray() else null)
                }
            }
        } catch (_: NotYetAvailableException) {
            return
        }
        if (samples.isEmpty() && tracker.all.isEmpty()) return
        workerBusy.set(true)
        worker.execute {
            try { track(samples, snaps, cam) } catch (t: Throwable) { Log.e(AR_TAG, "item tracking failed", t) } finally { workerBusy.set(false) }
        }
    }

    // -- tracking (worker thread) ----------------------------------------------------------------

    private fun track(samples: List<BoxSamples>, planes: List<PlaneSnap>, cam: FloatArray) {
        val claimed = HashSet<Int>()
        val observations = ArrayList<ItemTracker.Observation>()
        val obsPlaneY = ArrayList<Float>()
        // Smallest boxes first: a mug in front of a carton keeps its own pixels.
        for (bs in samples.sortedBy { it.box.area }) {
            val keep = bs.pixels.indices.filter { bs.pixels[it] !in claimed }
            if (keep.size < DetectionPoints.MIN_SAMPLES) continue
            val planeY = supportUnder(bs, keep, planes) ?: continue
            val frame = PlaneFrame(0f, planeY, 0f, ALONG, UP)
            val list = keep.map { i ->
                DetectionPoints.Sample(frame.toPlane(bs.world[3 * i], bs.world[3 * i + 1], bs.world[3 * i + 2]), bs.central[i], bs.edge[i])
            }
            val picked = math.select(list, IntArray(keep.size) { bs.depths[keep[it]] }, bs.spanPx, bs.focalPx,
                bs.inMask?.let { m -> BooleanArray(keep.size) { m[keep[it]] } })
            if (picked.isEmpty()) continue
            for (j in picked) claimed += bs.pixels[keep[j]]
            val b = bs.box.sensor
            val whole = b[0] > EDGE_OF_PICTURE && b[1] > EDGE_OF_PICTURE && b[2] < 1 - EDGE_OF_PICTURE && b[3] < 1 - EDGE_OF_PICTURE
            observations += ItemTracker.Observation(bs.box.trackingId, picked.map { list[it].point }, frame.toPlane(cam[0], cam[1], cam[2]), whole)
            obsPlaneY += planeY
        }
        val defaultY = obsPlaneY.firstOrNull() ?: planes.maxOfOrNull { if (it.y < cam[1]) it.y else Float.NEGATIVE_INFINITY } ?: return
        val update = tracker.update(observations, PlaneFrame(0f, defaultY, 0f, ALONG, UP).toPlane(cam[0], cam[1], cam[2]))
        for ((obsIndex, trackId) in update.assigned) {
            val y = obsPlaneY[obsIndex]
            planeHeights[trackId] = planeHeights[trackId]?.let { it * 0.8f + y * 0.2f } ?: y
        }
        publish(update.visible, update.capReached)
    }

    /** The surface an object stands on: the highest tracked plane below it that it is over. */
    private fun supportUnder(bs: BoxSamples, keep: List<Int>, planes: List<PlaneSnap>): Float? {
        val ys = keep.map { bs.world[3 * it + 1] }.sorted()
        val xs = keep.map { bs.world[3 * it] }.sorted()
        val zs = keep.map { bs.world[3 * it + 2] }.sorted()
        val my = ys[ys.size / 2]; val mx = xs[xs.size / 2]; val mz = zs[zs.size / 2]
        val below = planes.filter { it.y < my - 0.005f }
        return (below.filter { inside(it.polygonXZ, mx, mz) }.maxByOrNull { it.y } ?: below.maxByOrNull { it.y })?.y
    }

    /** Hands the confirmed [tracks] to the screen and the renderer, each in its steadied state. */
    private fun publish(tracks: List<ItemTracker.Track>, capReached: Boolean) {
        val now = System.currentTimeMillis()
        smoother.retain(tracks.map { it.id }.toSet())
        val shown = tracks.associate { t -> t.id to smoother.smooth(t.id, t.state, now) }
        for (t in tracks) if (t.isMeasured) measuredAt.putIfAbsent(t.id, now)
        drawn = tracks.mapNotNull { t ->
            val y = planeHeights[t.id] ?: return@mapNotNull null
            TrackSnapshot(t.id, shown.getValue(t.id), t.fit, y, measuredAt[t.id])
        }
        val items = tracks.map { t -> ScanItem(t.id, names[t.id], shown.getValue(t.id), t.fit?.shape, crops[t.id], t.fit) }
        val u = _ui.value
        _ui.value = u.copy(items = items, capReached = capReached || (u.capReached && tracks.size >= u.maxItems))
    }

    // -- naming ------------------------------------------------------------------------------------

    private fun needsNaming(): Boolean = drawn.any { !names.containsKey(it.id) || !crops.containsKey(it.id) }

    /** Labels each object's own crop on the phone, and keeps the best crop as its photo. */
    private suspend fun nameAndCrop(picture: Bitmap, boxes: List<ScanBox>) {
        // The tracker belongs to the worker thread; ask it rather than reading across threads.
        val tracks = runCatching {
            worker.submit<List<Pair<Int, Set<Int>>>> { tracker.all.map { it.id to it.trackingIds.toSet() } }.get()
        }.getOrElse { return }
        for ((id, ids) in tracks) {
            val box = boxes.firstOrNull { b -> b.trackingId != null && b.trackingId in ids } ?: continue
            val area = box.area
            val crop = cropUpright(picture, box.upright) ?: continue
            if (area > (cropArea[id] ?: 0f)) {
                crops[id] = crop
                cropArea[id] = area
            }
            if (names.containsKey(id) || !naming.add(id)) continue
            try {
                recogniser.recognise(crop).suggestedName()?.let { names[id] = it }
            } catch (e: Exception) {
                Log.w(AR_TAG, "naming", e)
            } finally {
                naming.remove(id)
            }
        }
        worker.execute { publish(tracker.all.filter { it.isConfirmed }, _ui.value.capReached) }
    }

    // -- drawing (GL thread) -----------------------------------------------------------------------

    private class TrackSnapshot(val id: Int, val state: ItemScanState, val fit: FittedObject?, val planeY: Float, val measuredAtMs: Long?)

    private fun drawOutlines(frame: Frame) {
        val pose = frame.camera.pose
        val anchors = ArrayList<ScanAnchor>()
        val now = System.currentTimeMillis()
        for (t in drawn) {
            val fit = t.fit ?: continue
            val pf = PlaneFrame(0f, t.planeY, 0f, ALONG, UP)
            val cam = pf.toPlane(pose.tx(), pose.ty(), pose.tz())
            val segments = OutlineGeometry.of(fit, cam)

            when (val st = t.state) {
                // No outline: a wrong outline would say "measured this". Only the tag, saying why.
                is ItemScanState.CannotMeasure -> Unit
                is ItemScanState.Measured -> {
                    val elapsed = now - (t.measuredAtMs ?: now)
                    val progress = (elapsed / OutlineStyle.DRAW_ON_MS.toFloat()).coerceIn(0f, 1f)
                    project(OutlineGeometry.footprint(fit), pf)?.let { poly ->
                        if (progress > 0.5f) outlines.drawFill(poly, fadeAlpha(OutlineStyle.MEASURED_FILL_ARGB, (progress - 0.5f) * 2f), viewportW, viewportH)
                    }
                    val lines = polylines(segments, pf)
                    if (progress >= 1f) {
                        outlines.drawPolylines(lines, OutlineStyle.MEASURED, density, viewportW, viewportH)
                    } else for (l in lines) {
                        outlines.drawPolylines(listOf(l), OutlineStyle.MEASURED, density, viewportW, viewportH, revealPx = length(l) * easeOut(progress))
                    }
                }
                is ItemScanState.NeedsAngle -> {
                    // Round things have one footprint length: an unseen depth is an unseen width.
                    val unseen = if (fit.shape == ShapeFamily.BOX || fit.shape == ShapeFamily.IRREGULAR) st.unseen
                    else st.unseen.map { if (it == Axis.DEPTH) Axis.WIDTH else it }.toSet()
                    outlines.drawPolylines(polylines(segments.filter { it.axis !in unseen }, pf), OutlineStyle.ANOTHER_ANGLE_SEEN, density, viewportW, viewportH)
                    outlines.drawPolylines(polylines(segments.filter { it.axis in unseen }, pf), OutlineStyle.ANOTHER_ANGLE_UNSEEN, density, viewportW, viewportH)
                }
                else -> outlines.drawPolylines(
                    polylines(segments, pf), OutlineStyle.SCANNING, density, viewportW, viewportH,
                    // Backwards along the line reads as the dashes travelling forwards.
                    dashPhasePx = -((now % MARCH_PERIOD_MS) / MARCH_PERIOD_MS.toFloat()) * (OutlineStyle.SCANNING.dashOnDp + OutlineStyle.SCANNING.dashOffDp) * density * 3f,
                )
            }

            // The tag sits on top of the object's outline as seen — its highest visible point —
            // centred over it; pills sit on the edges they measure. Anchoring to a point above
            // the fit's centre put a short object's tag on whatever stood behind it. An object
            // wholly out of view has no tag at all: pinning its tag to the edge of the screen
            // is what put labels under the status bar.
            tagPoint(segments, fit, pf)?.let { (x, y) -> anchors += labelAnchors(t.id, x, y, fit, cam, segments, pf) }
        }
        _anchors.value = anchors
    }

    /**
     * Pill positions. Width goes on the bottom edge nearest the camera, depth on the top edge
     * running away from it, height on the upright nearest the camera — the edges a person
     * would hold a tape against from where they are standing.
     */
    private fun labelAnchors(
        id: Int, tagX: Float, tagY: Float, fit: FittedObject, cam: PlanePoint,
        segments: List<OutlineSegment>, pf: PlaneFrame,
    ): ScanAnchor {
        fun dist(p: PlanePoint) = kotlin.math.hypot(p.xMm - cam.xMm, p.yMm - cam.yMm)
        fun mid(a: PlanePoint, b: PlanePoint) = PlanePoint((a.xMm + b.xMm) / 2, (a.yMm + b.yMm) / 2, (a.hMm + b.hMm) / 2)
        fun screen(p: PlanePoint) = projectPoint(pf, p)?.let { (x, y) ->
            if (x < 0 || y < 0 || x > viewportW || y > viewportH) null else x / viewportW to y / viewportH
        }
        val round = fit.shape == ShapeFamily.CYLINDER || fit.shape == ShapeFamily.TAPERED || fit.shape == ShapeFamily.SPHERE
        val width: PlanePoint?; val depth: PlanePoint?; val height: PlanePoint?
        if (round) {
            // Front of the base rim, and the near side's midpoint.
            val dx = cam.xMm - fit.centreXMm; val dy = cam.yMm - fit.centreYMm
            val l = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
            val r = fit.bottomRadiusMm ?: fit.widthMm / 2
            width = PlanePoint(fit.centreXMm + dx / l * r, fit.centreYMm + dy / l * r, 0f)
            depth = null
            val side = segments.filter { it.axis == Axis.HEIGHT }.minByOrNull { dist(mid(it.a, it.b)) }
            height = side?.let { mid(it.a, it.b) }
        } else {
            width = segments.filter { it.axis == Axis.WIDTH && it.a.hMm < 1f }.minByOrNull { dist(mid(it.a, it.b)) }?.let { mid(it.a, it.b) }
            depth = segments.filter { it.axis == Axis.DEPTH && it.a.hMm > 1f }.minByOrNull { dist(mid(it.a, it.b)) }?.let { mid(it.a, it.b) }
            height = segments.filter { it.axis == Axis.HEIGHT }.minByOrNull { dist(it.a) }?.let { mid(it.a, it.b) }
        }
        // How wide the object looks, from its footprint's projected extent.
        val xs = OutlineGeometry.footprint(fit, 16).mapNotNull { projectPoint(pf, it)?.first }
        val span = if (xs.size >= 2) (xs.max() - xs.min()) / viewportW else 0f
        return ScanAnchor(
            id, tagX / viewportW, tagY / viewportH,
            width = width?.let(::screen), depth = depth?.let(::screen), height = height?.let(::screen),
            span = span,
        )
    }

    /** Top middle of the object's on-screen outline, or null when none of it is on screen. */
    private fun tagPoint(segments: List<OutlineSegment>, fit: FittedObject, pf: PlaneFrame): Pair<Float, Float>? {
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var top = Float.MAX_VALUE
        for (s in segments) for (p in listOf(s.a, s.b)) {
            val q = projectPoint(pf, p) ?: continue
            if (!onScreen(q)) continue
            minX = minOf(minX, q.first); maxX = maxOf(maxX, q.first); top = minOf(top, q.second)
        }
        if (top == Float.MAX_VALUE) return null
        val centre = projectPoint(pf, PlanePoint(fit.centreXMm, fit.centreYMm, fit.heightMm))?.first ?: ((minX + maxX) / 2)
        return centre.coerceIn(minX, maxX) to (top - TAG_GAP_DP * density)
    }

    private fun onScreen(p: Pair<Float, Float>) =
        p.first in 0f..viewportW.toFloat() && p.second in 0f..viewportH.toFloat()

    private fun polylines(segments: List<OutlineSegment>, pf: PlaneFrame): List<FloatArray> {
        // Chain segments that meet end to start so dashes run on around corners and curves.
        val out = ArrayList<FloatArray>()
        var current = ArrayList<PlanePoint>()
        fun flush() {
            if (current.size >= 2) project(current, pf, closed = false)?.let { out += it }
            current = ArrayList()
        }
        for (s in segments) {
            if (current.isNotEmpty() && current.last() == s.a) current += s.b
            else { flush(); current += s.a; current += s.b }
        }
        flush()
        return out
    }

    /** Projects a run of plan points to pixels. Null if any of it is behind the camera. */
    private fun project(points: List<PlanePoint>, pf: PlaneFrame, closed: Boolean = true): FloatArray? {
        val out = FloatArray(points.size * 2)
        for ((i, p) in points.withIndex()) {
            val (x, y) = projectPoint(pf, p) ?: return null
            out[2 * i] = x; out[2 * i + 1] = y
        }
        return out
    }

    private val world4 = FloatArray(4)
    private val clip4 = FloatArray(4)
    private val world3 = FloatArray(3)

    private fun projectPoint(pf: PlaneFrame, p: PlanePoint): Pair<Float, Float>? {
        pf.toWorld(p, world3)
        world4[0] = world3[0]; world4[1] = world3[1]; world4[2] = world3[2]; world4[3] = 1f
        Matrix.multiplyMV(clip4, 0, viewProj, 0, world4, 0)
        // Anything nearer than this is inside the depth sensor's dead zone anyway, and a corner
        // 2 cm in front of the lens projects thousands of pixels off screen — the lines that
        // slashed across the whole view came from exactly that.
        if (clip4[3] <= MIN_DRAW_DISTANCE_M) return null
        return ((clip4[0] / clip4[3]) * 0.5f + 0.5f) * viewportW to (0.5f - (clip4[1] / clip4[3]) * 0.5f) * viewportH
    }

    private companion object {
        val ALONG = floatArrayOf(1f, 0f, 0f)
        val UP = floatArrayOf(0f, 1f, 0f)
        const val PROCESS_INTERVAL_NS = 100_000_000L
        const val NAMING_INTERVAL_NS = 1_000_000_000L
        const val MAX_BOX_AGE_NS = 400_000_000L
        /** Outlines are traced in the picture this often, on a frame this many pixels wide. */
        const val TRACE_INTERVAL_NS = 700_000_000L
        const val TRACE_WIDTH = 240
        /** An outline older than this is from a view the phone has moved on from. */
        const val MAX_TRACE_AGE_NS = 1_500_000_000L
        const val MIN_DEPTH_MM = 150
        /**
         * Items are scanned at arm's length. Past this the depth is mostly the room behind the
         * table, which only ever adds background to a box and never adds the object.
         */
        const val MAX_DEPTH_MM = 1_500
        /** Nearest a point may be to the camera, in metres, and still be drawn. */
        const val MIN_DRAW_DISTANCE_M = 0.1f
        /** A detector box this close to the picture's edge may have more of its object outside it. */
        const val EDGE_OF_PICTURE = 0.01f
        /** A detector box bigger than this share of the picture is the scene, not an object. */
        const val MAX_BOX_AREA = 0.5f
        /** Share of a detector box trimmed off each side before sampling depth. */
        const val SHRINK = 0.06f
        /** Space between an object's outline and the bottom of its tag. */
        const val TAG_GAP_DP = 6f
        /** One full march of the scanning dashes (three dash periods) takes this long. */
        const val MARCH_PERIOD_MS = 1_200L

        fun inside(poly: FloatArray, x: Float, z: Float): Boolean {
            var inside = false
            val n = poly.size / 2
            var j = n - 1
            for (i in 0 until n) {
                val xi = poly[2 * i]; val zi = poly[2 * i + 1]; val xj = poly[2 * j]; val zj = poly[2 * j + 1]
                if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) inside = !inside
                j = i
            }
            return inside
        }

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
            val m = 0.04f
            val x = ((box[0] - m) * frame.width).toInt().coerceIn(0, frame.width - 1)
            val y = ((box[1] - m) * frame.height).toInt().coerceIn(0, frame.height - 1)
            val w = ((box[2] - box[0] + 2 * m) * frame.width).toInt().coerceAtMost(frame.width - x)
            val h = ((box[3] - box[1] + 2 * m) * frame.height).toInt().coerceAtMost(frame.height - y)
            if (w < 32 || h < 32) return null
            return runCatching { Bitmap.createBitmap(frame, x, y, w, h) }.getOrNull()
        }

        /** YUV_420_888 camera image to an upright bitmap. Once a second, off the hot path. */
        fun yuvToUprightBitmap(image: android.media.Image, rotationDegrees: Int): Bitmap? = runCatching {
            val w = image.width; val h = image.height
            val nv21 = ByteArray(w * h * 3 / 2)
            val yPlane = image.planes[0]; val uPlane = image.planes[1]; val vPlane = image.planes[2]
            val yBuf = yPlane.buffer.duplicate(); val uBuf = uPlane.buffer.duplicate(); val vBuf = vPlane.buffer.duplicate()
            var o = 0
            for (row in 0 until h) {
                val base = row * yPlane.rowStride
                for (col in 0 until w) nv21[o++] = yBuf.get(base + col * yPlane.pixelStride)
            }
            for (row in 0 until h / 2) for (col in 0 until w / 2) {
                nv21[o++] = vBuf.get(row * vPlane.rowStride + col * vPlane.pixelStride)
                nv21[o++] = uBuf.get(row * uPlane.rowStride + col * uPlane.pixelStride)
            }
            val jpeg = java.io.ByteArrayOutputStream()
            android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, w, h, null)
                .compressToJpeg(android.graphics.Rect(0, 0, w, h), 85, jpeg)
            val flat = android.graphics.BitmapFactory.decodeByteArray(jpeg.toByteArray(), 0, jpeg.size()) ?: return@runCatching null
            if (rotationDegrees == 0) flat
            else Bitmap.createBitmap(flat, 0, 0, flat.width, flat.height, android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }, true)
        }.getOrNull()
    }
}
