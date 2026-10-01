package com.packabunch.scan

import android.content.Context
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.packabunch.packing.DetectionPoints
import com.packabunch.packing.FloorOutline
import com.packabunch.packing.FloorPatch
import com.packabunch.packing.ObjectCloud
import com.packabunch.packing.PlaneFrame
import com.packabunch.packing.PlanarPose
import com.packabunch.packing.PlanePoint
import com.packabunch.packing.ScannedSpace
import com.packabunch.packing.SpaceBox
import com.packabunch.packing.SpaceFace
import com.packabunch.packing.SpaceFitter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Mapped share at which a space may be used: four fifths of its needed sides seen well. */
const val SCAN_DONE_THRESHOLD = 0.80f

data class SpaceScanUi(
    val status: TrackingStatus = TrackingStatus.INITIALISING,
    /** What is stopping measuring this frame, if anything. */
    val problem: ScanProblem? = null,
    val reference: Reference? = null,
    /** The floor of the space — where the card lies — has been found. */
    val floorFound: Boolean = false,
    /** The current fit, or null until enough of the walls has been seen. */
    val box: SpaceBox? = null,
    val torchOn: Boolean = false,
    val fatalError: String? = null,
    val scanHint: String? = null,
) {
    val mappedPercent: Int get() = ((box?.mapped ?: 0f) * 100).toInt().coerceIn(0, 100)

    /**
     * The space is in clear view: mapped past the bar, and every side it needs (the ceiling too,
     * in a room) seen well. Short of that the guidance names the side to sweep to instead.
     */
    val canFinish: Boolean get() = box?.let { it.mapped >= SCAN_DONE_THRESHOLD && it.weakestFace == null } ?: false
}

/** Where the space's labels go, in 0..1 of the view; null when that point is off screen. */
data class SpaceAnchors(
    val width: Pair<Float, Float>? = null,
    val depth: Pair<Float, Float>? = null,
    val height: Pair<Float, Float>? = null,
    val opening: Pair<Float, Float>? = null,
)

/**
 * Scanning a space — a carton, a cupboard, a car boot, a room — as the Figma frame
 * `SpaceScan · New` shows it: the space outlined as a box, its three lengths on its edges,
 * the opening marked, and the side still unseen drawn in amber.
 *
 * ### How the space is found
 * The floor is the surface the card or sheet of A4 lies on — the boot floor, the bottom of the
 * cupboard, the room's floor — and everything is measured from it ([FloorPatch] grows it out from
 * the card). Depth samples are kept when they stand over that floor (grown a little, so the walls
 * at its edge count) and belong to the surfaces around the middle of the view; the car's bumper
 * below the sill and the garage wall behind it are not part of the boot.
 *
 * The fit ([SpaceFitter]) places each wall at the median of the points on it and finds the
 * opening and sill. Its result becomes the solver's grid ([SpaceFitter.toScannedSpace]): free
 * inside, solid where something stands in it, unknown behind a wall nobody saw.
 */
class SpaceScanController(context: Context, private val density: Float) {

    /** The measuring engine: Python (packscan.py), with the Kotlin one as its fallback. */
    private val math = PythonEngine.lazy(context)

    private val _ui = MutableStateFlow(SpaceScanUi())
    val ui: StateFlow<SpaceScanUi> = _ui.asStateFlow()

    private val _anchors = MutableStateFlow(SpaceAnchors())
    val anchors: StateFlow<SpaceAnchors> = _anchors.asStateFlow()

    private val _outlines = MutableStateFlow(OutlineScene())
    val outlines: StateFlow<OutlineScene> = _outlines.asStateFlow()

    private val feed = CameraFeed(context)
    private val gravity = Gravity(context)
    private val engine = SceneEngine(context, gravity)
    private val diagnostics = ScanDiagnostics("space")
    private val lock = Any()

    private var cloud = newCloud()
    private val cameras = ArrayList<PlanePoint>()
    private val floor = FloorPatch()
    @Volatile private var drawn: SpaceBox? = null
    @Volatile private var viewW = 1
    @Volatile private var viewH = 1
    private var lastProcessedNs = 0L
    private var lastFitMs = 0L
    private var everSaw = false

    fun start(owner: LifecycleOwner, preview: PreviewView) {
        gravity.start()
        feed.start(owner, preview, ::onFrame) { message -> _ui.value = _ui.value.copy(fatalError = message) }
    }

    fun stop() { feed.stop(); gravity.stop() }

    fun release() { feed.release(); gravity.stop(); engine.release() }

    fun setViewSize(width: Int, height: Int) { viewW = width.coerceAtLeast(1); viewH = height.coerceAtLeast(1) }

    fun setTorch(on: Boolean) {
        val ok = feed.setTorch(on)
        _ui.value = _ui.value.copy(torchOn = on && ok)
    }

    /** Forget everything seen and start again. */
    fun restart() = synchronized(lock) {
        cloud = newCloud(); cameras.clear(); floor.clear(); drawn = null
        _ui.value = _ui.value.copy(floorFound = false, box = null)
    }

    /** The finished space for the planner, or null if nothing usable was fitted. Call off the main thread. */
    fun buildScannedSpace(): ScannedSpace? = synchronized(lock) {
        val snap = cloud.snapshot()
        val box = math.fitSpace(snap.points, cameras, snap.weights) ?: return@synchronized null
        if (!SpaceScanUi(box = box).canFinish) return@synchronized null
        SpaceFitter.toScannedSpace(box, snap.points, snap.weights)
    }

    // -- frames (camera analysis thread) -------------------------------------------------------

    private fun onFrame(frame: CameraFrame) {
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
        if (u.status != status || u.problem != scene.problem || u.reference != scene.reference) {
            _ui.value = u.copy(status = status, problem = scene.problem, reference = scene.reference)
        }
        if (process && pose != null && scene.depth != null) {
            lastProcessedNs = frame.timestampNs
            try { sample(pose, scene.depth) } catch (t: Throwable) { Log.e(SCAN_TAG, "space frame", t) }
        }
        if (pose != null) draw(frame, pose) else { _outlines.value = OutlineScene(); _anchors.value = SpaceAnchors() }
    }

    private fun sample(pose: PlanarPose.Pose, depth: MetricDepth) {
        val g = depth.size
        val pf = PlaneFrame(0f, 0f, 0f, ALONG, UP)
        fun at(x: Int, y: Int) = if (x in 0 until g && y in 0 until g) depth.mm[y * g + x] else 0
        val world = ArrayList<Float>()
        val candidates = ArrayList<Pair<PlanePoint, Boolean>>()
        val c0 = g * 0.3f; val c1 = g * 0.7f
        for (y in 0 until g step 2) for (x in 0 until g step 2) {
            val mm = at(x, y)
            // A space's surfaces are big; a pixel on a jump in depth is only ever a smear between
            // a flap's edge and the floor behind it, so it is dropped.
            if (mm !in MIN_DEPTH_MM..MAX_DEPTH_MM || DetectionPoints.isEdge(mm, at(x - 2, y), at(x + 2, y), at(x, y - 2), at(x, y + 2))) continue
            val w = pose.unproject(depth.k, x.toDouble(), y.toDouble(), mm / 1000.0)
            world += w[0].toFloat(); world += w[1].toFloat(); world += w[2].toFloat()
            val p = pf.toPlane(w[0].toFloat(), w[1].toFloat(), w[2].toFloat())
            if (p.hMm in -BELOW_FLOOR_MM..MAX_HEIGHT_MM) candidates += p to (x >= c0 && x <= c1 && y >= c0 && y <= c1)
        }
        floor.add(world.toFloatArray())
        val poly = floor.polygon()
        if (!_ui.value.floorFound && poly != null) _ui.value = _ui.value.copy(floorFound = true)
        if (poly == null) return
        val margin = FloorOutline.wallMargin(poly)
        val samples = candidates.filter { (p, _) -> FloorOutline.near(poly, p.xMm / 1000f, p.yMm / 1000f, margin) }
            .map { (p, central) -> DetectionPoints.Sample(p, central) }
        _ui.value = _ui.value.copy(scanHint = if (samples.isEmpty()) "Include the floor and the nearby edges" else null)
        diagnostics.record("depth_sampled", "samples=${samples.size} floorCells=${poly.size / 2}")
        if (samples.isEmpty()) return
        val camPos = pose.cameraPosition
        val cam = pf.toPlane(camPos[0].toFloat(), camPos[1].toFloat(), camPos[2].toFloat())
        synchronized(lock) { accumulate(samples, cam) }
    }

    private fun accumulate(samples: List<DetectionPoints.Sample>, cam: PlanePoint) {
        // The walls are whatever stands connected around the middle of the view; the floor is
        // kept wherever it lies inside the outline.
        val keep = HashSet(math.selectSpace(samples, MAX_HEIGHT_MM))
        val points = samples.indices.filter { it in keep || samples[it].point.hMm <= SpaceFitter.FLOOR_BAND_MM }.map { samples[it].point }
        cloud.add(points)
        val last = cameras.lastOrNull()
        if (last == null || kotlin.math.hypot(kotlin.math.hypot(cam.xMm - last.xMm, cam.yMm - last.yMm), cam.hMm - last.hMm) > 40f) {
            cameras += cam
            if (cameras.size > 300) { val k = cameras.filterIndexed { i, _ -> i % 2 == 0 }; cameras.clear(); cameras += k }
        }
        val now = System.currentTimeMillis()
        if (now - lastFitMs < FIT_INTERVAL_MS) return
        lastFitMs = now
        val snap = cloud.snapshot()
        val box = math.fitSpace(snap.points, cameras, snap.weights)
        diagnostics.record("fitted", "points=${snap.points.size}, fitted=${box != null}" +
            (box?.let { " %.0fx%.0fx%.0fmm mapped=%.2f".format(it.widthMm, it.depthMm, it.heightMm, it.mapped) } ?: ""))
        drawn = box
        _ui.value = _ui.value.copy(box = box)
    }

    // -- drawing ---------------------------------------------------------------------------------

    private fun draw(frame: CameraFrame, pose: PlanarPose.Pose) {
        val box = drawn ?: run { _outlines.value = OutlineScene(); _anchors.value = SpaceAnchors(); return }
        val mapping = ViewMapping(frame.rotationDegrees, frame.picture.width, frame.picture.height, viewW, viewH)
        val k = frame.intrinsics
        val pf = PlaneFrame(0f, 0f, 0f, ALONG, UP)
        val hw = box.widthMm / 2; val hd = box.depthMm / 2; val h = box.heightMm
        fun at(u: Float, v: Float, z: Float): PlanePoint { val (x, y) = box.toPlan(u, v); return PlanePoint(x, y, z) }
        // Corners: front-left, front-right, back-right, back-left; bottom then top.
        val fl0 = at(-hw, -hd, 0f); val fr0 = at(hw, -hd, 0f); val br0 = at(hw, hd, 0f); val bl0 = at(-hw, hd, 0f)
        val fl1 = at(-hw, -hd, h); val fr1 = at(hw, -hd, h); val br1 = at(hw, hd, h); val bl1 = at(-hw, hd, h)
        fun seen(face: SpaceFace) = (box.coverage[face] ?: 0f) >= SpaceBox.WELL_SEEN || (face == SpaceFace.TOP) || (face == SpaceFace.FRONT && !box.cameraInside)
        class Edge(val a: PlanePoint, val b: PlanePoint, val faces: List<SpaceFace>, val front: Boolean)
        val edges = listOf(
            Edge(fl0, fr0, listOf(SpaceFace.FLOOR, SpaceFace.FRONT), true),
            Edge(fr0, br0, listOf(SpaceFace.FLOOR, SpaceFace.RIGHT), false),
            Edge(br0, bl0, listOf(SpaceFace.FLOOR, SpaceFace.BACK), false),
            Edge(bl0, fl0, listOf(SpaceFace.FLOOR, SpaceFace.LEFT), false),
            Edge(fl1, fr1, listOf(SpaceFace.TOP, SpaceFace.FRONT), true),
            Edge(fr1, br1, listOf(SpaceFace.TOP, SpaceFace.RIGHT), false),
            Edge(br1, bl1, listOf(SpaceFace.TOP, SpaceFace.BACK), false),
            Edge(bl1, fl1, listOf(SpaceFace.TOP, SpaceFace.LEFT), false),
            Edge(fl0, fl1, listOf(SpaceFace.LEFT, SpaceFace.FRONT), true),
            Edge(fr0, fr1, listOf(SpaceFace.RIGHT, SpaceFace.FRONT), true),
            Edge(br0, br1, listOf(SpaceFace.RIGHT, SpaceFace.BACK), false),
            Edge(bl0, bl1, listOf(SpaceFace.LEFT, SpaceFace.BACK), false),
        )
        val world = FloatArray(3)
        fun cam(p: PlanePoint): DoubleArray { pf.toWorld(p, world); return pose.toCamera(doubleArrayOf(world[0].toDouble(), world[1].toDouble(), world[2].toDouble())) }
        fun px(c: DoubleArray) = mapping.toView(k.fx * c[0] / c[2] + k.cx, k.fy * c[1] / c[2] + k.cy)
        /** A segment in view pixels, cut where it passes behind the camera — inside a room, most of the box is. */
        fun clip(a: PlanePoint, b: PlanePoint): FloatArray? {
            var ca = cam(a); var cb = cam(b)
            if (ca[2] <= NEAR_M && cb[2] <= NEAR_M) return null
            if (ca[2] <= NEAR_M) { val t = (NEAR_M - ca[2]) / (cb[2] - ca[2]); ca = DoubleArray(3) { ca[it] + (cb[it] - ca[it]) * t } }
            if (cb[2] <= NEAR_M) { val t = (NEAR_M - cb[2]) / (ca[2] - cb[2]); cb = DoubleArray(3) { cb[it] + (ca[it] - cb[it]) * t } }
            val (x0, y0) = px(ca); val (x1, y1) = px(cb)
            return floatArrayOf(x0, y0, x1, y1)
        }
        fun screen(p: PlanePoint): Pair<Float, Float>? {
            val c = cam(p); if (c[2] <= NEAR_M) return null
            val (x, y) = px(c)
            return if (x < 0 || y < 0 || x > viewW || y > viewH) null else x / viewW to y / viewH
        }
        val strokes = ArrayList<OutlineStroke>()
        for (e in edges) {
            val line = clip(e.a, e.b) ?: continue
            strokes += OutlineStroke(line, when {
                e.front && !box.cameraInside -> OPENING_STYLE
                e.faces.all { seen(it) } -> MEASURED_STYLE
                else -> UNSEEN_STYLE
            })
        }
        // Floor first, faintly, so the lines sit on top of it.
        val fills = ArrayList<OutlineFill>()
        val floorCam = listOf(fl0, fr0, br0, bl0).map { cam(it) }
        if (floorCam.all { it[2] > NEAR_M }) fills += OutlineFill(floorCam.flatMap { px(it).toList() }.toFloatArray(), FLOOR_FILL_ARGB)
        _outlines.value = OutlineScene(strokes, fills)
        fun mid(a: PlanePoint, b: PlanePoint) = PlanePoint((a.xMm + b.xMm) / 2, (a.yMm + b.yMm) / 2, (a.hMm + b.hMm) / 2)
        _anchors.value = SpaceAnchors(
            width = screen(mid(fl0, fr0)),
            depth = screen(mid(bl0, fl0)),
            height = screen(at(0f, hd, h / 2)),
            opening = if (box.cameraInside) null else screen(mid(fl1, fr1)),
        )
    }

    private companion object {
        val ALONG = floatArrayOf(1f, 0f, 0f)
        val UP = floatArrayOf(0f, 1f, 0f)
        const val PROCESS_INTERVAL_NS = 150_000_000L
        const val FIT_INTERVAL_MS = 300L
        const val MIN_DEPTH_MM = 150
        const val MAX_DEPTH_MM = 4_000
        const val BELOW_FLOOR_MM = 60f
        const val MAX_HEIGHT_MM = 3_000f
        const val NEAR_M = 0.06
        const val FLOOR_FILL_ARGB = 0x1FFFFFFFL   // 12 % white

        // SpaceScan · New: "Measured edges", "Unseen side", "Opening".
        val MEASURED_STYLE = OutlineStyle(0xFFFFFFFF, 2.1f, glowDp = 3f, wideGlowDp = 10f)
        val UNSEEN_STYLE = OutlineStyle(0xFFF2A03D, 1.8f, dashOnDp = 2f, dashOffDp = 4f, glowDp = 5f, dotted = true)
        val OPENING_STYLE = OutlineStyle(0xCCFFFFFF, 1.3f, dashOnDp = 5f, dashOffDp = 4f, glowDp = 4f)

        fun newCloud() = ObjectCloud(voxelMm = 10f, maxVoxels = 40_000, minHeightMm = -BELOW_FLOOR_MM, relativeSightings = 0.03f)
    }
}
