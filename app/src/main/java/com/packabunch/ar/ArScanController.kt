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
class ArScanController(
    private val context: Context,
    private val trackItems: Boolean = false,
    /** How many objects this pack can still take. Scanning stops noticing beyond it. */
    private val maxObjects: Int = Int.MAX_VALUE,
) : GLSurfaceView.Renderer {

    private val _state = MutableStateFlow(ArScanState())
    val state: StateFlow<ArScanState> = _state.asStateFlow()
    private val tracker = com.packabunch.packing.SweepTracker()
    private val _objects = MutableStateFlow<List<com.packabunch.packing.SweptObject>>(emptyList())
    val objects = _objects.asStateFlow()

    private val _overlays = MutableStateFlow<List<ObjectOverlay>>(emptyList())

    /**
     * Where each measured object sits on the screen, refreshed every frame.
     *
     * The projection maths lives here rather than in the UI because this is the only place
     * that holds the camera. What comes out is already in 0..1 screen space, so the overlay
     * is a dumb line drawing that cannot disagree with where the camera is actually pointing.
     */
    val overlays = _overlays.asStateFlow()
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
            // A space and a kettle need completely different grids.
            //
            // A car boot is metres across and its walls are centimetres thick, so 40 mm cells
            // over a 2.4 m box are right. An item is the opposite: a mug is 80 mm wide, which
            // at 40 mm is two cells — indistinguishable from noise — and a phone is thinner
            // than one cell, so it cannot be represented at all. That is why scanning small
            // things produced nothing usable, and no amount of sweeping would have helped.
            //
            // Item mode therefore trades reach for detail: a 1.2 m box at 10 mm. Cell counts
            // stay comparable (about 1.2M either way) so memory and segmentation cost do not
            // blow up.
            val reachM = if (trackItems) 0.6f else 1.2f
            val builder = accumulator ?: ScanAccumulator(
                // Origin is placed a little behind and below the camera's first tracked
                // position, so the space in front of the phone falls inside the grid.
                originXM = pose.tx() - reachM,
                originYM = requireNotNull(floor).centerPose.ty(),
                originZM = pose.tz() + reachM,
                resolutionMm = if (trackItems) 10 else 40,
                widthMm = if (trackItems) 1_200 else 2_400,
                depthMm = if (trackItems) 1_200 else 2_400,
                heightMm = if (trackItems) 800 else 1_600,
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
            if (trackItems) {
                val view = FloatArray(16)
                val projection = FloatArray(16)
                val viewProjection = FloatArray(16)
                camera.getViewMatrix(view, 0)
                camera.getProjectionMatrix(projection, 0, 0.05f, 20f)
                android.opengl.Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
                _overlays.value = _objects.value.mapNotNull { swept ->
                    projectObject(
                        detected = swept.detected,
                        originXM = builder.originXM,
                        originYM = builder.originYM,
                        originZM = builder.originZM,
                        viewProjection = viewProjection,
                    )?.let { ObjectOverlay(swept.id, swept.settled, it) }
                }
            }

            if (frame.timestamp - lastSegmentationTimestamp >= 500_000_000L) {
                val grid = builder.toVoxelGrid()
                finishedGrid = grid
                if (trackItems) {
                    val direction = Math.toDegrees(kotlin.math.atan2(
                        -pose.zAxis[0].toDouble(), -pose.zAxis[2].toDouble())).toInt()
                    tracker.update(com.packabunch.packing.ObjectSegmentation.detect(grid, supportPlaneCellK = 0), direction)
                    // The cap belongs here rather than at review time: tracking objects the
                    // pack could never hold costs frames and clutters the screen with boxes
                    // the user will only be told about later.
                    _objects.value = tracker.objects().take(maxObjects)
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

/**
 * One measured object, ready to draw over the camera.
 *
 * [corners] are the eight corners of its box in 0..1 screen space, ordered base first then
 * top, anticlockwise from the minimum corner. Anything behind the camera is dropped upstream,
 * so a box that appears here is genuinely in view.
 */
data class ObjectOverlay(
    val id: String,
    val settled: Boolean,
    val corners: List<Pair<Float, Float>>,
)

/**
 * Projects a detected object's box into screen space.
 *
 * The grid's axes are not the world's — grid +Y runs along world −Z and grid Z is up — so the
 * inverse of `ScanAccumulator.worldToCell` is applied here and nowhere else. Getting it wrong
 * draws a box that tracks the camera convincingly and sits in the wrong place.
 */
internal fun projectObject(
    detected: com.packabunch.packing.DetectedObject,
    originXM: Float,
    originYM: Float,
    originZM: Float,
    viewProjection: FloatArray,
): List<Pair<Float, Float>>? {
    val halfW = detected.dimensions.widthMm / 2_000f
    val halfD = detected.dimensions.depthMm / 2_000f
    val heightM = detected.dimensions.heightMm / 1_000f

    val centreX = originXM + detected.centroidXMm / 1_000f
    val centreZ = originZM - detected.centroidYMm / 1_000f

    // The tightest box was found at this yaw, so the drawn box has to share it.
    val yaw = Math.toRadians(detected.yawDegrees.toDouble())
    val cos = kotlin.math.cos(yaw).toFloat()
    val sin = kotlin.math.sin(yaw).toFloat()

    val footprint = listOf(
        -halfW to -halfD, halfW to -halfD, halfW to halfD, -halfW to halfD,
    )
    val out = ArrayList<Pair<Float, Float>>(8)
    val point = FloatArray(4)

    for (level in listOf(0f, heightM)) {
        for ((dx, dz) in footprint) {
            point[0] = centreX + dx * cos - dz * sin
            point[1] = originYM + level
            point[2] = centreZ + dx * sin + dz * cos
            point[3] = 1f

            val clip = FloatArray(4)
            android.opengl.Matrix.multiplyMV(clip, 0, viewProjection, 0, point, 0)
            // Behind the camera. One corner off-screen is fine; behind it is not drawable.
            if (clip[3] <= 0.0001f) return null
            out += ((clip[0] / clip[3]) * 0.5f + 0.5f) to (0.5f - (clip[1] / clip[3]) * 0.5f)
        }
    }
    return out
}
