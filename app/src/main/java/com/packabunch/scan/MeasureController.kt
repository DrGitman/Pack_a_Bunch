package com.packabunch.scan

import android.content.Context
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.packabunch.packing.PlanarPose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Which of the three edges is being measured. They are taken one at a time, on purpose. */
enum class EdgeStage(val label: String, val instruction: String) {
    WIDTH("Width", "Tap one end of the width, then the other."),
    DEPTH("Depth", "Now the depth, front to back."),
    HEIGHT("Height", "Now the height, floor to rim."),
}

data class MeasureState(
    val status: TrackingStatus = TrackingStatus.INITIALISING,
    val problem: ScanProblem? = null,
    val stage: EdgeStage = EdgeStage.WIDTH,
    /** Screen positions of the points captured for the current edge, for the overlay. */
    val capturedScreen: List<Pair<Float, Float>> = emptyList(),
    /** Where the reticle currently lands, and whether it landed on anything real. */
    val reticleScreen: Pair<Float, Float>? = null,
    val reticleHasSurface: Boolean = false,
    /** Live length between the first captured point and the reticle, in mm. */
    val liveLengthMm: Int? = null,
    /** Confirmed lengths, keyed by edge. */
    val measured: Map<EdgeStage, Int> = emptyMap(),
    val fatalError: String? = null,
    val torchOn: Boolean = false,
) {
    val allEdgesMeasured: Boolean get() = EdgeStage.entries.all { measured.containsKey(it) }
}

/**
 * The tape measure behind the measure screen.
 *
 * How a length is obtained: the reticle in the middle of the screen is a ray from the camera; it
 * lands on the surface under it — on the depth model's millimetre depth where there is any, on
 * the surface the card lies on otherwise. A tap keeps that point in the card's frame, which does
 * not move, so the point stays put while the phone does. The length is the straight line between
 * two kept points.
 *
 * What it deliberately does **not** do:
 *  - measure without the card in view: no card, no scale, no point;
 *  - report a confidence percentage it has no way of knowing;
 *  - keep a point taken while the picture was blurred or too dark.
 *
 * Every value still goes to the review screen before it is stored, as a camera estimate.
 */
class MeasureController(context: Context) {

    private val _state = MutableStateFlow(MeasureState())
    val state: StateFlow<MeasureState> = _state.asStateFlow()

    private val feed = CameraFeed(context)
    private val gravity = Gravity(context)
    private val engine = SceneEngine(context, gravity)
    private val points = ArrayList<DoubleArray>()
    private val pendingTap = AtomicBoolean(false)
    private val lock = Any()
    @Volatile private var viewW = 1
    @Volatile private var viewH = 1
    private var everSaw = false

    fun start(owner: LifecycleOwner, preview: PreviewView) {
        gravity.start()
        feed.start(owner, preview, ::onFrame) { message -> _state.value = _state.value.copy(fatalError = message) }
    }

    fun stop() { feed.stop(); gravity.stop() }

    fun release() { feed.release(); gravity.stop(); engine.release() }

    fun setViewSize(width: Int, height: Int) { viewW = width.coerceAtLeast(1); viewH = height.coerceAtLeast(1) }

    /** The phone's torch, for measuring inside a dark cupboard or boot. */
    fun setTorch(on: Boolean) {
        val ok = feed.setTorch(on)
        _state.value = _state.value.copy(torchOn = on && ok)
    }

    /** Taken on the next frame, so it uses that frame's placement. */
    fun capturePoint() = pendingTap.set(true)

    fun undoLastPoint() = synchronized(lock) {
        if (points.isNotEmpty()) points.removeAt(points.lastIndex)
        _state.value = _state.value.copy(liveLengthMm = null)
    }

    /** Accepts the current edge and moves on. Only ever called with two points captured. */
    fun confirmCurrentEdge() = synchronized(lock) {
        val current = _state.value
        val length = current.liveLengthMm ?: (if (points.size >= 2) distanceMm(points[0], points[1]) else null) ?: return@synchronized
        val nextMeasured = current.measured + (current.stage to length)
        val nextStage = EdgeStage.entries.firstOrNull { it !in nextMeasured.keys }
        points.clear()
        _state.value = current.copy(measured = nextMeasured, stage = nextStage ?: current.stage, capturedScreen = emptyList(), liveLengthMm = null)
    }

    fun restartCurrentEdge() = synchronized(lock) {
        points.clear()
        _state.value = _state.value.copy(capturedScreen = emptyList(), liveLengthMm = null)
    }

    private fun onFrame(frame: CameraFrame) {
        val scene = engine.process(frame, wantDepth = true)
        val pose = scene.pose
        if (pose == null || scene.problem != null) {
            _state.value = _state.value.copy(
                status = if (everSaw) TrackingStatus.LOST else TrackingStatus.INITIALISING,
                problem = scene.problem, reticleScreen = null, reticleHasSurface = false,
            )
            return
        }
        everSaw = true
        try { track(scene, pose) } catch (t: Throwable) { Log.e(SCAN_TAG, "measure frame", t) }
    }

    private fun track(scene: SceneFrame, pose: PlanarPose.Pose) {
        val frame = scene.camera
        val k = frame.intrinsics
        val mapping = ViewMapping(frame.rotationDegrees, frame.picture.width, frame.picture.height, viewW, viewH)
        val centre = viewW / 2f to viewH / 2f
        val (u, v) = mapping.toPicture(centre.first, centre.second)
        val hit = surfaceAt(scene, pose, u, v)

        synchronized(lock) {
            if (pendingTap.compareAndSet(true, false) && hit != null) {
                if (points.size >= 2) points.clear()
                points += hit
            }
            fun screen(w: DoubleArray) = pose.project(k, w)?.let { mapping.toView(it[0], it[1]) }
            val live = when {
                points.size >= 2 -> distanceMm(points[0], points[1])
                points.size == 1 && hit != null -> distanceMm(points[0], hit)
                else -> null
            }
            _state.value = _state.value.copy(
                status = TrackingStatus.TRACKING, problem = null,
                capturedScreen = points.mapNotNull { screen(it) },
                reticleScreen = hit?.let { screen(it) } ?: centre,
                reticleHasSurface = hit != null,
                liveLengthMm = live,
            )
        }
    }

    /**
     * The world point under picture pixel (u, v): on the measured depth where the depth model has
     * it, on the surface the card lies on otherwise, or null when the ray meets neither.
     */
    private fun surfaceAt(scene: SceneFrame, pose: PlanarPose.Pose, u: Double, v: Double): DoubleArray? {
        val pic = scene.camera.picture
        if (u !in 0.0..pic.width.toDouble() || v !in 0.0..pic.height.toDouble()) return null
        scene.depth?.let { d ->
            val gx = (u * d.size / pic.width).toInt().coerceIn(0, d.size - 1)
            val gy = (v * d.size / pic.height).toInt().coerceIn(0, d.size - 1)
            val mm = d.mm[gy * d.size + gx]
            if (mm in MIN_MM..MAX_MM) return pose.unproject(scene.camera.intrinsics, u, v, mm / 1000.0)
        }
        return pose.rayToTable(scene.camera.intrinsics, u, v)?.let { doubleArrayOf(it[0], 0.0, it[2]) }
    }

    private companion object {
        const val MIN_MM = 100
        const val MAX_MM = 6_000

        fun distanceMm(a: DoubleArray, b: DoubleArray): Int {
            val dx = a[0] - b[0]; val dy = a[1] - b[1]; val dz = a[2] - b[2]
            return (sqrt(dx * dx + dy * dy + dz * dz) * 1000).roundToInt()
        }
    }
}
