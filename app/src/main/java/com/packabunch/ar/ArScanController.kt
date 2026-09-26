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
import kotlinx.coroutines.launch
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

    private val finder = if (trackItems) ObjectFinder() else null
    private val namer = if (trackItems) {
        ObjectNamer(com.packabunch.data.catalogue.ItemRecogniser())
    } else {
        null
    }
    private val namingScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default,
    )

    private val _names = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Recognised names by swept object id, filled in as labelling catches up. */
    val names = _names.asStateFlow()

    /**
     * How far the sensor image is rotated from the way the phone is being held.
     *
     * ARCore hands over the raw sensor image, which on essentially every Android phone is
     * landscape regardless of how the device is held. ML Kit needs telling, or every box
     * comes back transposed.
     */
    private val cameraRotationDegrees: Int
        get() = runCatching {
            val characteristics = (context.getSystemService(Context.CAMERA_SERVICE)
                as android.hardware.camera2.CameraManager)
                .let { manager -> manager.cameraIdList.firstOrNull()?.let(manager::getCameraCharacteristics) }
            characteristics?.get(android.hardware.camera2.CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        }.getOrDefault(90)

    private val _found = MutableStateFlow<List<FoundObject>>(emptyList())

    /** What the detector can currently see, in 0..1 image space. */
    val found = _found.asStateFlow()

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
    private var lastNamingTimestamp = 0L
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
        finder?.close()
        namer?.clear()
        _found.value = emptyList()
        _names.value = emptyMap()
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
            val reachM = if (trackItems) 0.8f else 1.2f
            val builder = accumulator ?: ScanAccumulator(
                // Origin is placed a little behind and below the camera's first tracked
                // position, so the space in front of the phone falls inside the grid.
                originXM = pose.tx() - reachM,
                originYM = requireNotNull(floor).centerPose.ty(),
                originZM = pose.tz() + reachM,
                resolutionMm = if (trackItems) 20 else 40,
                widthMm = if (trackItems) 1_600 else 2_400,
                depthMm = if (trackItems) 1_600 else 2_400,
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
                    // Every tracked surface, so the table can be subtracted from the scene.
                    val surfaces = active.getAllTrackables(com.google.ar.core.Plane::class.java)
                        .filter { it.trackingState == TrackingState.TRACKING && it.subsumedBy == null }

                    for (v in 0 until depth.height step 3) for (u in 0 until depth.width step 3) {
                        val mm = buffer.getShort(v * plane.rowStride + u * plane.pixelStride).toInt() and 0xffff
                        // Beyond about 1.5 m this sensor's depth is not worth believing, which
                        // is the range Google's own raw-depth sample works in. The old 5 m
                        // ceiling is where the far-away phantom geometry was coming from.
                        if (mm !in 150..MAX_DEPTH_MM) continue
                        // Image +Y is down; ARCore camera +Y is up and forward is -Z.
                        val local = projection.point(u, v, mm)
                        pose.transformPoint(local, 0, world, 0)
                        if (liesOnASurface(world, surfaces)) continue
                        builder.observe(pose.tx(), pose.ty(), pose.tz(), world[0], world[1], world[2], 1f)
                        added++
                    }
                }
            } catch (_: com.google.ar.core.exceptions.NotYetAvailableException) {
                return // No depth yet is not an empty scene or a completed measurement.
            }
            if (trackItems) {
                // One camera frame per pass to the detector. It drops frames rather than
                // queueing, so this never holds up rendering.
                // No `use`: ownership passes to the finder, which closes it once ML Kit is
                // done. Rotation is the sensor-to-display angle — a hardcoded 0 leaves every
                // box on its side, since the analysis image is landscape while the phone is
                // not.
                try {
                    val image = frame.acquireCameraImage()
                    // Labelling is far slower than detection and the answer does not change,
                    // so it runs about once a second on its own thread rather than per frame.
                    if (frame.timestamp - lastNamingTimestamp >= 1_000_000_000L) {
                        lastNamingTimestamp = frame.timestamp
                        val boxes = _found.value
                        val snapshot = image.toBitmap(cameraRotationDegrees)
                        if (snapshot != null && boxes.isNotEmpty()) {
                            namingScope.launch { namer?.name(snapshot, boxes) }
                        }
                    }
                    finder?.offer(image, cameraRotationDegrees) {
                        _found.value = finder?.found.orEmpty().map { it.toViewSpace(frame) }
                    } ?: image.close()
                } catch (_: com.google.ar.core.exceptions.NotYetAvailableException) {
                    // Nothing this frame. Normal, and not worth a log line at 30 Hz.
                } catch (e: Exception) {
                    android.util.Log.w("ArScan", "camera image unavailable", e)
                }
            }

            if (trackItems) {
                val view = FloatArray(16)
                val projection = FloatArray(16)
                val viewProjection = FloatArray(16)
                camera.getViewMatrix(view, 0)
                camera.getProjectionMatrix(projection, 0, 0.05f, 20f)
                android.opengl.Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
                val projected = _objects.value.mapNotNull { swept ->
                    projectObject(
                        detected = swept.detected,
                        originXM = builder.originXM,
                        originYM = builder.originYM,
                        originZM = builder.originZM,
                        viewProjection = viewProjection,
                    )?.let { ObjectOverlay(swept.id, swept.settled, it) }
                }
                _overlays.value = projected

                // Tie the two halves together.
                //
                // The detector knows what things are; the depth grid knows how big they are;
                // until now neither knew they were talking about the same object. Each
                // measured object is projected to its screen rectangle and matched to the
                // detector box it overlaps most, which is what lets a measurement inherit a
                // name. Overlap is the right test rather than centre distance: a mug and the
                // carton behind it can share a centre on screen and never share an outline.
                _names.value = matchNames(projected, _found.value, namer)
            }

            if (frame.timestamp - lastSegmentationTimestamp >= 500_000_000L) {
                val grid = builder.toVoxelGrid()
                finishedGrid = grid
                if (trackItems) {
                    val direction = Math.toDegrees(kotlin.math.atan2(
                        -pose.zAxis[0].toDouble(), -pose.zAxis[2].toDouble())).toInt()
                    // The noise floor has to be a real volume, not a cell count.
                    //
                    // ObjectSegmentation's own default is 12 cells, which at 40 mm is a
                    // proper blob and at 20 mm is a thimble. Left alone it reported eighteen
                    // "objects" on a table holding four. Anything under about 60 cm3 is not
                    // something anybody packs, so that is the line, converted into whatever
                    // cell count this grid needs to express it.
                    val cellVolumeMm3 = grid.resolutionMm.toLong() * grid.resolutionMm * grid.resolutionMm
                    val minCells = (MIN_OBJECT_VOLUME_MM3 / cellVolumeMm3).toInt().coerceAtLeast(12)
                    tracker.update(
                        com.packabunch.packing.ObjectSegmentation.detect(
                            grid, minCells = minCells,
                        ),
                        direction,
                    )
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

/**
 * The smallest thing worth calling an object, in cubic millimetres.
 *
 * About 60 cm3 — a small mug. Below this, on a phone without a depth sensor, what the grid
 * holds is far more likely to be a fragment of something larger or a patch of noise than a
 * separate item somebody intends to pack.
 */
private const val MIN_OBJECT_VOLUME_MM3 = 60_000L

/** Depth past this is too unreliable on a phone without a depth sensor to build on. */
private const val MAX_DEPTH_MM = 1_500

/** Points this close to a tracked plane are that plane, not something standing on it. */
private const val SURFACE_TOLERANCE_M = 0.03f

/**
 * Whether a world point belongs to a surface rather than to an object.
 *
 * Google's raw-depth sample does exactly this before clustering, and it is the step this
 * scan was missing: without it the table top is simply the largest solid mass in the grid,
 * so it gets segmented and measured as though it were something you could pack, and every
 * object standing on it merges into that same blob.
 *
 * The polygon test matters as much as the distance one. A plane is infinite in its own
 * mathematics but finite in reality, and skipping the bounds check would delete anything
 * that happened to sit at table height anywhere in the room.
 */
private fun liesOnASurface(
    world: FloatArray,
    surfaces: List<com.google.ar.core.Plane>,
): Boolean {
    for (surface in surfaces) {
        val centre = surface.centerPose
        val normal = floatArrayOf(centre.yAxis[0], centre.yAxis[1], centre.yAxis[2])
        val dx = world[0] - centre.tx()
        val dy = world[1] - centre.ty()
        val dz = world[2] - centre.tz()
        val distance = kotlin.math.abs(dx * normal[0] + dy * normal[1] + dz * normal[2])
        if (distance > SURFACE_TOLERANCE_M) continue

        val pose = com.google.ar.core.Pose.makeTranslation(world[0], world[1], world[2])
        if (surface.isPoseInPolygon(pose)) return true
    }
    return false
}

/**
 * Moves a detector box out of image space and into the space the preview is drawn in.
 *
 * These are not the same rectangle. ARCore renders the camera feed through a display
 * transform that crops the sensor image to the screen's aspect ratio, so normalised image
 * coordinates multiplied by the screen's width and height land somewhere else — the error
 * grows towards the edges and looks exactly like the detector being inaccurate. ARCore
 * exposes the conversion, so it is used rather than approximated.
 */
private fun FoundObject.toViewSpace(frame: com.google.ar.core.Frame): FoundObject {
    val source = floatArrayOf(left, top, right, bottom)
    val out = FloatArray(4)
    return runCatching {
        frame.transformCoordinates2d(
            com.google.ar.core.Coordinates2d.IMAGE_NORMALIZED,
            source,
            com.google.ar.core.Coordinates2d.VIEW_NORMALIZED,
            out,
        )
        // VIEW_NORMALIZED runs -1..1 with +Y up; the canvas wants 0..1 with +Y down.
        fun x(v: Float) = (v * 0.5f + 0.5f)
        fun y(v: Float) = (0.5f - v * 0.5f)
        copy(
            left = minOf(x(out[0]), x(out[2])),
            right = maxOf(x(out[0]), x(out[2])),
            top = minOf(y(out[1]), y(out[3])),
            bottom = maxOf(y(out[1]), y(out[3])),
        )
    }.getOrDefault(this)
}

/** Below this intersection-over-union, two boxes are not the same thing. */
private const val MIN_OVERLAP = 0.35f

/**
 * Which measured object each recognised name belongs to.
 *
 * Keyed by the swept object's own id, because that is what the item import understands. A
 * measurement with no confident detector match keeps its numbered fallback rather than
 * borrowing a neighbour's name, which would be worse than no name at all.
 */
internal fun matchNames(
    projected: List<ObjectOverlay>,
    found: List<FoundObject>,
    namer: ObjectNamer?,
): Map<String, String> {
    if (namer == null || projected.isEmpty() || found.isEmpty()) return emptyMap()
    val out = HashMap<String, String>()

    for (overlay in projected) {
        val xs = overlay.corners.map { it.first }
        val ys = overlay.corners.map { it.second }
        val left = xs.min()
        val right = xs.max()
        val top = ys.min()
        val bottom = ys.max()

        val best = found.maxByOrNull { overlapOf(left, top, right, bottom, it) } ?: continue
        if (overlapOf(left, top, right, bottom, best) < MIN_OVERLAP) continue
        namer.nameFor(best.trackingId)?.let { out[overlay.id] = it }
    }
    return out
}

/**
 * Intersection over union.
 *
 * Measuring the intersection as a share of the *smaller* box seems reasonable and is wrong:
 * a carton filling the frame completely contains a mug's rectangle, so that ratio is 1.0 for
 * both the carton and the mug's own box, and the mug inherits whichever happened to be
 * checked first. Dividing by the union instead charges the carton for all the area it covers
 * that the mug does not, so only a box of about the right size and place can win.
 */
private fun overlapOf(left: Float, top: Float, right: Float, bottom: Float, other: FoundObject): Float {
    val w = minOf(right, other.right) - maxOf(left, other.left)
    val h = minOf(bottom, other.bottom) - maxOf(top, other.top)
    if (w <= 0f || h <= 0f) return 0f
    val intersection = w * h
    val union = (right - left) * (bottom - top) +
        (other.right - other.left) * (other.bottom - other.top) - intersection
    return if (union <= 0f) 0f else intersection / union
}
