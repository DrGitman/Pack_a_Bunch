package com.packabunch.ar

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Surface
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Which of the three edges is being measured. They are taken one at a time, on purpose. */
enum class EdgeStage(val label: String, val instruction: String) {
    WIDTH("Width", "Tap one end of the width, then the other."),
    DEPTH("Depth", "Now the depth — front to back."),
    HEIGHT("Height", "Now the height — floor to rim."),
}

/** What tracking is doing, in terms the UI can act on. */
enum class TrackingStatus {
    /** Still working out where it is. Capture is disabled. */
    INITIALISING,

    /** Good enough to measure. */
    TRACKING,

    /** Lost it — usually too fast, too dark, or a blank surface. Recoverable. */
    LOST,
}

data class ArMeasureState(
    val status: TrackingStatus = TrackingStatus.INITIALISING,
    val failureReason: TrackingFailureReason? = null,
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
) {
    val allEdgesMeasured: Boolean get() = EdgeStage.entries.all { measured.containsKey(it) }
}

/**
 * The real ARCore session behind the measure screen.
 *
 * How a length is actually obtained: two taps, each turned into a `HitResult` against a
 * tracked plane or feature point, each anchored so ARCore keeps correcting its position as
 * it learns more about the room. The distance is then the straight-line distance between
 * the two anchor poses, in metres, converted to millimetres.
 *
 * What it deliberately does **not** do:
 *
 *  - It never measures from 2D pixel distance. A tap that hits nothing produces no point,
 *    and the reticle says so before you tap.
 *  - It never reports a confidence percentage. ARCore's tracking state is not a calibrated
 *    accuracy figure, and dressing it up as one would be inventing a number.
 *  - It never keeps a measurement taken while tracking was lost.
 *
 * Every value it produces still goes to a review screen to be confirmed or corrected before
 * it is stored, and is stored as a camera estimate.
 */
class ArMeasureController(private val context: Context) : GLSurfaceView.Renderer {

    private val _state = MutableStateFlow(ArMeasureState())
    val state: StateFlow<ArMeasureState> = _state.asStateFlow()

    private var session: Session? = null
    private val background = CameraBackgroundRenderer()

    private val anchors = mutableListOf<Anchor>()
    private val pendingTap = AtomicBoolean(false)
    private var viewportWidth = 1
    private var viewportHeight = 1
    private var displayRotation = Surface.ROTATION_0
    private var geometryDirty = true

    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewProjection = FloatArray(16)

    // -- lifecycle ---------------------------------------------------------------------------

    /** Returns null on success, or a message describing why AR could not start. */
    fun resume(rotation: Int, width: Int, height: Int): String? {
        displayRotation = rotation
        viewportWidth = width
        viewportHeight = height
        geometryDirty = true

        try {
            if (session == null) {
                session = Session(context).apply {
                    configure(
                        Config(this).apply {
                            // Latest image, so a tap lines up with what is on screen.
                            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                            focusMode = Config.FocusMode.AUTO
                            lightEstimationMode = Config.LightEstimationMode.DISABLED

                            // Depth sharpens hit tests where it exists, and is simply absent
                            // on many ARCore phones — so it is requested, never required.
                            depthMode = if (isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                                Config.DepthMode.AUTOMATIC
                            } else {
                                Config.DepthMode.DISABLED
                            }
                        },
                    )
                }
            }
            session?.resume()
            return null
        } catch (e: CameraNotAvailableException) {
            return "The camera is being used by something else."
        } catch (e: UnavailableException) {
            return e.message ?: "AR is not available on this phone."
        } catch (t: Throwable) {
            Log.e(AR_TAG, "Could not start the AR session", t)
            return t.message ?: "AR could not start."
        }
    }

    fun pause() {
        session?.pause()
    }

    /** Anchors hold native resources; releasing the session without them leaks. */
    fun release() {
        anchors.forEach { it.detach() }
        anchors.clear()
        session?.close()
        session = null
    }

    fun onSurfaceSizeChanged(rotation: Int, width: Int, height: Int) {
        displayRotation = rotation
        viewportWidth = width
        viewportHeight = height
        geometryDirty = true
    }

    // -- user actions --------------------------------------------------------------------------

    /** Capture happens on the GL thread, on the next frame, so it uses that frame's pose. */
    fun capturePoint() {
        pendingTap.set(true)
    }

    fun undoLastPoint() {
        if (anchors.isEmpty()) return
        anchors.removeAt(anchors.lastIndex).detach()
        _state.value = _state.value.copy(liveLengthMm = null)
    }

    /** Accepts the current edge and moves on. Only ever called with two points captured. */
    fun confirmCurrentEdge() {
        val current = _state.value
        val length = current.liveLengthMm ?: measuredLength() ?: return

        val nextMeasured = current.measured + (current.stage to length)
        val nextStage = EdgeStage.entries.firstOrNull { it !in nextMeasured.keys }

        anchors.forEach { it.detach() }
        anchors.clear()

        _state.value = current.copy(
            measured = nextMeasured,
            stage = nextStage ?: current.stage,
            capturedScreen = emptyList(),
            liveLengthMm = null,
        )
    }

    fun restartCurrentEdge() {
        anchors.forEach { it.detach() }
        anchors.clear()
        _state.value = _state.value.copy(capturedScreen = emptyList(), liveLengthMm = null)
    }

    private fun measuredLength(): Int? {
        if (anchors.size < 2) return null
        return distanceMm(anchors[0], anchors[1])
    }

    // -- the frame loop ----------------------------------------------------------------------------

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            background.createOnGlThread()
            session?.setCameraTextureName(background.textureId)
        } catch (t: Throwable) {
            Log.e(AR_TAG, "GL setup failed", t)
            _state.value = _state.value.copy(fatalError = "The AR view could not start.")
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        geometryDirty = true
        android.opengl.GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        android.opengl.GLES20.glClear(
            android.opengl.GLES20.GL_COLOR_BUFFER_BIT or android.opengl.GLES20.GL_DEPTH_BUFFER_BIT,
        )

        val active = session ?: return
        try {
            active.setCameraTextureName(background.textureId)
            if (geometryDirty) {
                active.setDisplayGeometry(displayRotation, viewportWidth, viewportHeight)
                geometryDirty = false
            }

            val frame = active.update()
            background.draw(frame)

            val camera = frame.camera
            when (camera.trackingState) {
                TrackingState.TRACKING -> onTracking(frame)
                TrackingState.PAUSED -> _state.value = _state.value.copy(
                    status = if (_state.value.status == TrackingStatus.INITIALISING) {
                        TrackingStatus.INITIALISING
                    } else {
                        TrackingStatus.LOST
                    },
                    failureReason = camera.trackingFailureReason,
                    reticleScreen = null,
                    reticleHasSurface = false,
                )
                TrackingState.STOPPED -> Unit
            }
        } catch (e: CameraNotAvailableException) {
            _state.value = _state.value.copy(fatalError = "The camera became unavailable.")
        } catch (t: Throwable) {
            Log.e(AR_TAG, "Frame failed", t)
        }
    }

    private fun onTracking(frame: Frame) {
        val camera = frame.camera
        camera.getViewMatrix(viewMatrix, 0)
        camera.getProjectionMatrix(projectionMatrix, 0, NEAR_M, FAR_M)
        android.opengl.Matrix.multiplyMM(viewProjection, 0, projectionMatrix, 0, viewMatrix, 0)

        // The reticle is a hit test through the centre of the screen, every frame. It is
        // what makes "there is nothing to measure here" visible before a tap, rather than
        // after one.
        val centreX = viewportWidth / 2f
        val centreY = viewportHeight / 2f
        val reticleHit = firstUsableHit(frame, centreX, centreY)

        if (pendingTap.compareAndSet(true, false)) {
            reticleHit?.let { hit ->
                if (anchors.size >= 2) {
                    anchors.forEach { it.detach() }
                    anchors.clear()
                }
                anchors += hit.createAnchor()
            }
        }

        val screenPoints = anchors
            .filter { it.trackingState == TrackingState.TRACKING }
            .mapNotNull { project(it.pose.tx(), it.pose.ty(), it.pose.tz()) }

        val live = when {
            anchors.size >= 2 -> distanceMm(anchors[0], anchors[1])
            anchors.size == 1 && reticleHit != null -> {
                val p = anchors[0].pose
                val h = reticleHit.hitPose
                distanceMm(p.tx(), p.ty(), p.tz(), h.tx(), h.ty(), h.tz())
            }
            else -> null
        }

        _state.value = _state.value.copy(
            status = TrackingStatus.TRACKING,
            failureReason = null,
            capturedScreen = screenPoints,
            reticleScreen = reticleHit?.let { project(it.hitPose.tx(), it.hitPose.ty(), it.hitPose.tz()) }
                ?: (centreX to centreY),
            reticleHasSurface = reticleHit != null,
            liveLengthMm = live,
        )
    }

    /**
     * The first hit that is a real surface.
     *
     * Planes are preferred, and only where the hit is actually inside the detected polygon
     * rather than on its infinite extension — otherwise a tap past the edge of a table
     * returns a point hanging in mid air. Feature points are accepted as a fallback only
     * when they face the camera, which filters out most of the noise.
     */
    private fun firstUsableHit(frame: Frame, x: Float, y: Float): HitResult? {
        val hits = try {
            frame.hitTest(x, y)
        } catch (t: Throwable) {
            return null
        }

        return hits.firstOrNull { hit ->
            when (val trackable = hit.trackable) {
                is Plane -> trackable.isPoseInPolygon(hit.hitPose) &&
                    trackable.trackingState == TrackingState.TRACKING
                is Point -> trackable.orientationMode ==
                    Point.OrientationMode.ESTIMATED_SURFACE_NORMAL
                else -> false
            }
        }
    }

    /** World point to screen pixels, for drawing the overlay in Compose. */
    private fun project(x: Float, y: Float, z: Float): Pair<Float, Float>? {
        val world = floatArrayOf(x, y, z, 1f)
        val clip = FloatArray(4)
        android.opengl.Matrix.multiplyMV(clip, 0, viewProjection, 0, world, 0)
        if (clip[3] <= 0f) return null // behind the camera

        val ndcX = clip[0] / clip[3]
        val ndcY = clip[1] / clip[3]
        return Pair(
            (ndcX + 1f) / 2f * viewportWidth,
            (1f - ndcY) / 2f * viewportHeight,
        )
    }

    private companion object {
        const val NEAR_M = 0.1f
        const val FAR_M = 100f

        fun distanceMm(a: Anchor, b: Anchor): Int {
            val pa = a.pose
            val pb = b.pose
            return distanceMm(pa.tx(), pa.ty(), pa.tz(), pb.tx(), pb.ty(), pb.tz())
        }

        fun distanceMm(
            ax: Float, ay: Float, az: Float,
            bx: Float, by: Float, bz: Float,
        ): Int {
            val dx = ax - bx
            val dy = ay - by
            val dz = az - bz
            // ARCore works in metres; everything downstream is integer millimetres.
            return (sqrt(dx * dx + dy * dy + dz * dz) * 1000f).roundToInt()
        }
    }
}
