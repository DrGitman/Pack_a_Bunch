package com.packabunch.ar

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.Log
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import com.packabunch.packing.ScannedSpace
import com.packabunch.packing.VoxelGrid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

data class ArScanState(
    val status: TrackingStatus = TrackingStatus.INITIALISING,
    val failureReason: TrackingFailureReason? = null,
    val coverage: Float = 0f,
    val regionCoverage: Map<ScanRegion, Float> = emptyMap(),
    val elapsedSeconds: Int = 0,
    val pointsObserved: Long = 0,
    val fatalError: String? = null,
) {
    val coveragePercent: Int get() = coveragePercent(coverage)

    /** Below the threshold a plan built on this scan would be mostly guesswork. */
    val canFinish: Boolean get() = coverage >= SCAN_DONE_THRESHOLD

    /** Regions still worth sweeping, named so the instruction can be specific. */
    val thinRegions: List<ScanRegion>
        get() = regionCoverage.filterValues { it < SCAN_THIN_THRESHOLD }.keys.toList()
}

/**
 * Sweeping a space into an occupancy grid.
 *
 * Each frame, ARCore's point cloud is folded into a [ScanAccumulator] along with the
 * camera's position, so every point both marks something solid and carves out the empty
 * space the camera looked through to see it.
 *
 * The grid origin is fixed on the first tracked frame and never moves. Re-origining
 * mid-scan would silently smear earlier observations across the grid, which is the kind of
 * bug that produces a confident, wrong map.
 */
class ArScanController(private val context: Context) : GLSurfaceView.Renderer {

    private val _state = MutableStateFlow(ArScanState())
    val state: StateFlow<ArScanState> = _state.asStateFlow()

    private var session: Session? = null
    private val background = CameraBackgroundRenderer()
    private var accumulator: ScanAccumulator? = null
    private var startedAtMillis = 0L

    private var viewportWidth = 1
    private var viewportHeight = 1
    private var displayRotation = 0
    private var geometryDirty = true

    /** True when this phone can give depth; without it the scan leans on feature points. */
    var depthAvailable: Boolean = false
        private set

    fun resume(rotation: Int, width: Int, height: Int): String? {
        displayRotation = rotation
        viewportWidth = width
        viewportHeight = height
        geometryDirty = true

        return try {
            if (session == null) {
                session = Session(context).apply {
                    val supportsDepth = isDepthModeSupported(Config.DepthMode.AUTOMATIC)
                    depthAvailable = supportsDepth
                    configure(
                        Config(this).apply {
                            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                            focusMode = Config.FocusMode.AUTO
                            depthMode = if (supportsDepth) Config.DepthMode.AUTOMATIC
                            else Config.DepthMode.DISABLED
                        },
                    )
                }
            }
            session?.resume()
            startedAtMillis = System.currentTimeMillis()
            null
        } catch (e: CameraNotAvailableException) {
            "The camera is being used by something else."
        } catch (e: UnavailableException) {
            e.message ?: "AR is not available on this phone."
        } catch (t: Throwable) {
            Log.e(AR_TAG, "Could not start the scan session", t)
            t.message ?: "The scan could not start."
        }
    }

    fun pause() = session?.pause().let { }

    fun release() {
        session?.close()
        session = null
        accumulator = null
    }

    /** Throws the map away and starts again, keeping the session alive. */
    fun restart() {
        accumulator = null
        startedAtMillis = System.currentTimeMillis()
        _state.value = ArScanState(status = _state.value.status)
    }

    /** The finished map, or null if nothing was ever observed. */
    fun buildScannedSpace(): ScannedSpace? {
        val grid: VoxelGrid = accumulator?.toVoxelGrid() ?: return null
        return ScannedSpace(baseGrid = grid)
    }

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
            android.opengl.GLES20.GL_COLOR_BUFFER_BIT or
                android.opengl.GLES20.GL_DEPTH_BUFFER_BIT,
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
            if (camera.trackingState != TrackingState.TRACKING) {
                _state.value = _state.value.copy(
                    status = if (_state.value.coverage > 0f) TrackingStatus.LOST
                    else TrackingStatus.INITIALISING,
                    failureReason = camera.trackingFailureReason,
                )
                return
            }

            val pose = camera.pose
            val builder = accumulator ?: ScanAccumulator(
                // Origin is placed a little behind and below the camera's first tracked
                // position, so the space in front of the phone falls inside the grid.
                originXM = pose.tx() - 1.2f,
                originYM = pose.ty() - 1.2f,
                originZM = pose.tz() + 1.2f,
            ).also { accumulator = it }

            var added = 0L
            frame.acquirePointCloud().use { cloud ->
                val points = cloud.points
                // Packed as x, y, z, confidence — four floats per point.
                var offset = 0
                while (offset + 3 < points.limit()) {
                    val x = points.get(offset)
                    val y = points.get(offset + 1)
                    val z = points.get(offset + 2)
                    val confidence = points.get(offset + 3)
                    builder.observe(
                        cameraXM = pose.tx(), cameraYM = pose.ty(), cameraZM = pose.tz(),
                        pointXM = x, pointYM = y, pointZM = z,
                        confidence = confidence,
                    )
                    added++
                    offset += 4
                }
            }

            _state.value = _state.value.copy(
                status = TrackingStatus.TRACKING,
                failureReason = null,
                coverage = builder.coverage,
                regionCoverage = builder.regionCoverage(),
                elapsedSeconds = ((System.currentTimeMillis() - startedAtMillis) / 1000L).toInt(),
                pointsObserved = _state.value.pointsObserved + added,
            )
        } catch (e: CameraNotAvailableException) {
            _state.value = _state.value.copy(fatalError = "The camera became unavailable.")
        } catch (t: Throwable) {
            Log.e(AR_TAG, "Scan frame failed", t)
        }
    }
}
