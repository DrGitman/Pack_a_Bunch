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
class ArScanController(private val context: Context, private val trackItems: Boolean = false) : GLSurfaceView.Renderer {

    private val _state = MutableStateFlow(ArScanState())
    val state: StateFlow<ArScanState> = _state.asStateFlow()
    private val tracker = com.packabunch.packing.SweepTracker()
    private val _objects = MutableStateFlow<List<com.packabunch.packing.SweptObject>>(emptyList())
    val objects = _objects.asStateFlow()
    private var lastDepthTimestamp = 0L
    private var lastProcessedTimestamp = 0L
    private var lastSegmentationTimestamp = 0L
    @Volatile private var finishedGrid: VoxelGrid? = null

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
                    require(supportsDepth) { "Depth scanning is not supported on this phone. Use typed measurements instead." }
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
        tracker.clear()
        _objects.value = emptyList()
        finishedGrid = null
    }

    /** Throws the map away and starts again, keeping the session alive. */
    fun restart() {
        accumulator = null
        tracker.clear()
        _objects.value = emptyList()
        finishedGrid = null
        startedAtMillis = System.currentTimeMillis()
        _state.value = ArScanState(status = _state.value.status)
    }

    /** The finished map, or null if nothing was ever observed. */
    fun buildScannedSpace(): ScannedSpace? {
        val grid: VoxelGrid = finishedGrid ?: return null
        if (grid.countZ <= 1) return null
        // The detected support occupies layer zero. Start usable space above that layer;
        // leaving the solid floor at solver z=0 would prevent every floor placement.
        val nz = grid.countZ - 1
        val cells = ByteArray(grid.countX * grid.countY * nz) { n ->
            val x = n / (grid.countY * nz)
            val y = n / nz % grid.countY
            val z = n % nz + 1
            grid.cellAt(x, y, z).ordinal.toByte()
        }
        return ScannedSpace(baseGrid = VoxelGrid(0, 0, 0, grid.resolutionMm,
            grid.countX, grid.countY, nz, cells))
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
            // Work at at most 10 Hz; do not count repeated frames as new observations.
            if (frame.timestamp - lastProcessedTimestamp < 100_000_000L) return
            lastProcessedTimestamp = frame.timestamp
            val floor = active.getAllTrackables(com.google.ar.core.Plane::class.java)
                .filter { it.trackingState == TrackingState.TRACKING && it.subsumedBy == null &&
                    it.type == com.google.ar.core.Plane.Type.HORIZONTAL_UPWARD_FACING }
                .maxByOrNull { it.extentX * it.extentZ }
            if (accumulator == null && floor == null) {
                _state.value = _state.value.copy(status = TrackingStatus.INITIALISING)
                return
            }
            val builder = accumulator ?: ScanAccumulator(
                // Origin is placed a little behind and below the camera's first tracked
                // position, so the space in front of the phone falls inside the grid.
                originXM = pose.tx() - 1.2f,
                originYM = requireNotNull(floor).centerPose.ty(),
                originZM = pose.tz() + 1.2f,
            ).also { accumulator = it }

            var added = 0L
            try {
                frame.acquireDepthImage16Bits().use { depth ->
                    if (depth.timestamp == lastDepthTimestamp) return
                    lastDepthTimestamp = depth.timestamp
                    val intrinsics = camera.imageIntrinsics
                    val focal = intrinsics.focalLength
                    val principal = intrinsics.principalPoint
                    val imageSize = intrinsics.imageDimensions
                    val projection = DepthProjection.scaled(focal[0], focal[1], principal[0], principal[1],
                        imageSize[0], imageSize[1], depth.width, depth.height)
                    val plane = depth.planes[0]
                    val buffer = plane.buffer.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    val world = FloatArray(3)
                    for (v in 0 until depth.height step 3) for (u in 0 until depth.width step 3) {
                        val mm = buffer.getShort(v * plane.rowStride + u * plane.pixelStride).toInt() and 0xffff
                        if (mm !in 150..5000) continue
                        // Image +Y is down; ARCore camera +Y is up and forward is -Z.
                        val local = projection.point(u, v, mm)
                        pose.transformPoint(local, 0, world, 0)
                        builder.observe(pose.tx(), pose.ty(), pose.tz(), world[0], world[1], world[2], 1f)
                        added++
                    }
                }
            } catch (_: com.google.ar.core.exceptions.NotYetAvailableException) {
                return // No depth yet is not an empty scene or a completed measurement.
            }
            if (frame.timestamp - lastSegmentationTimestamp >= 500_000_000L) {
                val grid = builder.toVoxelGrid()
                finishedGrid = grid
                if (trackItems) {
                    val direction = Math.toDegrees(kotlin.math.atan2(
                        -pose.zAxis[0].toDouble(), -pose.zAxis[2].toDouble())).toInt()
                    tracker.update(com.packabunch.packing.ObjectSegmentation.detect(grid, supportPlaneCellK = 0), direction)
                    _objects.value = tracker.objects()
                }
                lastSegmentationTimestamp = frame.timestamp
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
