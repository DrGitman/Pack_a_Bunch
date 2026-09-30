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
import com.packabunch.packing.StandingObjects
import com.packabunch.packing.SensorBox
import com.packabunch.packing.SupportPlane
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
 * The item scan: point the phone at things on a table and each one gets measured.
 *
 * ### Frame by frame
 * On the GL thread, at most ten times a second: depth, calibration, support planes and camera
 * pose are copied from the same frame whose camera image goes to ML Kit. Its asynchronous
 * result selects from that snapshot, never a newer depth image. Everything expensive — deciding which points are the object, fitting it, tracking it —
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
    private val diagnostics = ScanDiagnostics("items")

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
    private val nameEvidence = ConcurrentHashMap<Int, com.packabunch.packing.ScanLabelConsensus>()
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
        names.clear(); nameEvidence.clear(); crops.clear(); cropArea.clear(); measuredAt.clear(); planeHeights.clear()
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
                diagnostics.record("tracking", camera.trackingFailureReason.name)
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
            if (planes.isEmpty()) diagnostics.record("surface_missing")
            val walls = tracked.filter { it.type == Plane.Type.VERTICAL && WallPlane.isWallSized(it.extentX, it.extentZ) }
            if (_ui.value.status != TrackingStatus.TRACKING || _ui.value.surfaceFound != planes.isNotEmpty()) {
                _ui.value = _ui.value.copy(status = TrackingStatus.TRACKING, failureReason = null, surfaceFound = planes.isNotEmpty())
            }

            // Not gated on planes: ARCore only reports one where it can see visual features, and a
            // dark or glossy table gives it none. Depth works there regardless, so the surface is
            // worked out from the depth itself when ARCore has nothing (see SupportPlane).
            if (frame.timestamp - lastProcessedNs >= PROCESS_INTERVAL_NS) {
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

    /** Copies all geometry while this ARCore frame is current. No Frame/Image escapes to a worker. */
    private fun sampleAndSubmit(frame: Frame, planes: List<Plane>, wallPlanes: List<Plane>) {
        if (!workerBusy.compareAndSet(false, true)) return
        var image: android.media.Image? = null
        try {
            val camera = frame.camera
            val pose = camera.pose
            val cam = floatArrayOf(pose.tx(), pose.ty(), pose.tz())
            val walls = wallPlanes.map { p ->
                val c = p.centerPose
                WallPlane(c.translation, c.yAxis, c.xAxis, c.zAxis, p.extentX / 2, p.extentZ / 2)
            }
            val snaps = planes.map { p ->
                val c = p.centerPose
                val poly = p.polygon.let { buf -> FloatArray(buf.limit()).also { buf.rewind(); buf.get(it) } }
                val world = FloatArray(poly.size)
                for (i in 0 until poly.size / 2) {
                    val v = c.transformPoint(floatArrayOf(poly[2 * i], 0f, poly[2 * i + 1]))
                    world[2 * i] = v[0]; world[2 * i + 1] = v[2]
                }
                PlaneSnap(c.ty(), world)
            }
            val depth = MeasurementDepth.acquire(frame)
            val captured = depth.use {
                if (depth.timestamp == lastDepthNs) {
                    workerBusy.set(false)
                    return
                }
                lastDepthNs = depth.timestamp
                val intr = camera.textureIntrinsics
                val f = intr.focalLength; val pp = intr.principalPoint; val dim = intr.imageDimensions
                val projection = DepthProjection.scaled(f[0], f[1], pp[0], pp[1], dim[0], dim[1], depth.width, depth.height)
                val mapping = DepthImageMapping(frame)
                val pixels = ArrayList<DepthPixel>()
                fun at(u: Int, v: Int) = if (u in 0 until depth.width && v in 0 until depth.height) depth.at(u, v) else 0
                for (v in 0 until depth.height) for (u in 0 until depth.width) {
                    val mm = at(u, v)
                    if (mm !in MIN_DEPTH_MM..MAX_DEPTH_MM) continue
                    val uv = mapping.imagePoint(u.toFloat() / depth.width, v.toFloat() / depth.height)
                    if (uv[0] !in 0f..1f || uv[1] !in 0f..1f) continue
                    val world = pose.transformPoint(projection.point(u, v, mm))
                    if (walls.any { it.contains(world[0], world[1], world[2]) }) continue
                    pixels += DepthPixel(v * depth.width + u, uv[0], uv[1], mm, world,
                        DetectionPoints.isEdge(mm, at(u-1,v), at(u+1,v), at(u,v-1), at(u,v+1)))
                }
                pixels
            }

            // ARCore found no surface, so derive one from the depth we just gathered. Without this
            // the scan sits on "0 measured" forever on any table too plain or too glossy for
            // feature tracking, which is most dark furniture.
            val world = FloatArray(captured.size * 3)
            val uvs = FloatArray(captured.size * 2)
            for ((i, p) in captured.withIndex()) {
                world[3 * i] = p.world[0]; world[3 * i + 1] = p.world[1]; world[3 * i + 2] = p.world[2]
                uvs[2 * i] = p.u; uvs[2 * i + 1] = p.v
            }
            // The surface actually in view: the height most depth shares. ARCore may also report
            // the floor, which is the wrong reference for things on a table.
            val inView = SupportPlane.detect(world)
            val support = snaps.ifEmpty { inView?.let { listOf(PlaneSnap(it.y, it.polygonXZ)) }.orEmpty() }
            val standingY = inView?.y ?: snaps.firstOrNull()?.y
            // Objects from geometry, independent of the detector: whatever stands on that surface.
            val standing = standingY?.let { StandingObjects.find(world, uvs, it) }.orEmpty()
            if (support.isNotEmpty() && !_ui.value.surfaceFound) {
                _ui.value = _ui.value.copy(surfaceFound = true)
            }
            diagnostics.record("support", "arcore=${snaps.size} used=${support.size} depth=${captured.size} standing=${standing.size}")
            image = frame.acquireCameraImage()
            val focal = camera.imageIntrinsics.focalLength
            val imageWidth = image.width
            val imageHeight = image.height
            val timestamp = frame.timestamp
            val shouldName = timestamp - lastNamingNs >= NAMING_INTERVAL_NS
            if (shouldName) lastNamingNs = timestamp
            val ownedImage = image
            image = null // detector owns and closes it, including on failure
            detector.offer(ownedImage, sensorRotationDegrees, timestamp) { detected, detectedImage ->
                if (worker.isShutdown || detectedImage == null) {
                    detectedImage?.close()
                    workerBusy.set(false)
                } else {
                    try {
                        worker.execute {
                            var capturedPicture: Bitmap? = null
                            try {
                                // Conversion and segmentation run off the rendering and main threads.
                                val picture = yuvToUprightBitmap(detectedImage, 0) ?: error("Camera image conversion failed")
                                capturedPicture = picture
                                detectedImage.close()
                                val named = detected.filter { it.frameTimestampNs == timestamp && it.area <= MAX_BOX_AREA }
                                // The detector goes blind in dim light while depth does not, so it
                                // must never be the only way an object gets measured. Anything
                                // standing on the table that it missed is added unnamed.
                                val fromDepth = standing
                                    .filter { s -> named.none { StandingObjects.overlap(it.sensor, s.box) > DEPTH_BOX_MATCH } }
                                    .map { ScanBox(null, it.box, SensorBox.sensorToUpright(it.box, sensorRotationDegrees), timestamp) }
                                    .filter { it.area <= MAX_BOX_AREA }
                                val boxes = named + fromDepth
                                val samples = samplesForBoxes(captured, picture, boxes, imageWidth, imageHeight, focal)
                                track(samples, support, cam)
                                _ui.value = _ui.value.copy(scanHint = when {
                                    detector.failed -> "Object detection couldn't run — reopen the scanner"
                                    boxes.isEmpty() -> "Show the whole object and leave a little gap between items"
                                    samples.isEmpty() -> "No usable depth on the object — try another angle"
                                    else -> _ui.value.scanHint
                                })
                                diagnostics.record("matched_frame", "boxes=${boxes.size}, samples=${samples.sumOf { it.pixels.size }}")
                                if (shouldName && boxes.isNotEmpty()) {
                                    val upright = Bitmap.createBitmap(picture, 0, 0, picture.width, picture.height,
                                        android.graphics.Matrix().apply { postRotate(sensorRotationDegrees.toFloat()) }, true)
                                    // Copy if rotation is zero: this frame is recycled below.
                                    val namingPicture = if (upright === picture) upright.copy(Bitmap.Config.ARGB_8888, false) else upright
                                    val identities = tracker.all.map { it.id to it.trackingIds.toSet() }
                                    scope.launch { try { nameAndCrop(namingPicture, boxes, identities) } finally { namingPicture.recycle() } }
                                }
                            } catch (e: Exception) {
                                Log.e(AR_TAG, "matched frame processing failed", e)
                            } finally {
                                detectedImage.close()
                                capturedPicture?.recycle()
                                workerBusy.set(false)
                            }
                        }
                    } catch (_: java.util.concurrent.RejectedExecutionException) {
                        detectedImage.close()
                        workerBusy.set(false)
                    }
                }
            }
        } catch (_: NotYetAvailableException) {
            _ui.value = _ui.value.copy(scanHint = "Waiting for depth — move slowly sideways")
            diagnostics.record("depth_unavailable")
            workerBusy.set(false)
        } catch (e: Exception) {
            Log.w(AR_TAG, "capture scan frame", e)
            workerBusy.set(false)
        } finally {
            image?.close()
        }
    }

    private data class DepthPixel(val index: Int, val u: Float, val v: Float, val mm: Int, val world: FloatArray, val edge: Boolean)

    /** Detector boxes, colour masks and depth now refer to one exposure, even if detection is slow. */
    private fun samplesForBoxes(pixels: List<DepthPixel>, picture: Bitmap, boxes: List<ScanBox>,
                                imageWidth: Int, imageHeight: Int, focal: FloatArray): List<BoxSamples> {
        if (boxes.isEmpty()) return emptyList()
        val small = Bitmap.createScaledBitmap(picture, TRACE_WIDTH,
            (picture.height * TRACE_WIDTH / picture.width.toFloat()).toInt().coerceAtLeast(1), true)
        val labels = try {
            val argb = IntArray(small.width * small.height)
            small.getPixels(argb, 0, small.width, 0, 0, small.width, small.height)
            val rgb = ByteArray(argb.size * 3)
            for (i in argb.indices) {
                rgb[3*i] = (argb[i] shr 16).toByte(); rgb[3*i+1] = (argb[i] shr 8).toByte(); rgb[3*i+2] = argb[i].toByte()
            }
            PythonEngine.tracer()?.segmentFrame(rgb, small.width, small.height, boxes.map { it.sensor })
        } catch (e: Exception) {
            Log.w(AR_TAG, "trace object", e)
            null
        }
        val present = BooleanArray(256)
        labels?.forEach { present[it.toInt() and 0xff] = true }
        val result = boxes.mapIndexedNotNull { index, box ->
            val b = box.sensor
            val w = b[2] - b[0]; val h = b[3] - b[1]
            val kept = pixels.filter { it.u in (b[0]+w*SHRINK)..(b[2]-w*SHRINK) && it.v in (b[1]+h*SHRINK)..(b[3]-h*SHRINK) }
            if (kept.isEmpty()) return@mapIndexedNotNull null
            val widthPx = w * imageWidth; val heightPx = h * imageHeight
            val useWidth = widthPx / focal[0] >= heightPx / focal[1]
            BoxSamples(box, kept.map { it.index }.toIntArray(), kept.flatMap { it.world.toList() }.toFloatArray(),
                BooleanArray(kept.size) { val p = kept[it]; p.u in (b[0]+w*.3f)..(b[2]-w*.3f) && p.v in (b[1]+h*.3f)..(b[3]-h*.3f) },
                BooleanArray(kept.size) { kept[it].edge }, kept.map { it.mm }.toIntArray(),
                if (useWidth) widthPx else heightPx, if (useWidth) focal[0] else focal[1],
                if (labels != null && present[index+1]) BooleanArray(kept.size) {
                    val p = kept[it]
                    val x = (p.u * small.width).toInt().coerceIn(0, small.width-1)
                    val y = (p.v * small.height).toInt().coerceIn(0, small.height-1)
                    (labels[y * small.width+x].toInt() and 0xff) == index+1
                } else null)
        }
        if (small !== picture) small.recycle()
        return result
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
        _ui.value = _ui.value.copy(scanHint = null)
        if (observations.isEmpty() && samples.isNotEmpty()) {
            _ui.value = _ui.value.copy(scanHint = "Object depth isn't clear of its surface — try another angle")
        }
        diagnostics.record("selected", "sampleBoxes=${samples.size}, observations=${observations.size}, tracks=${update.visible.size}")
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
        return below.filter { inside(it.polygonXZ, mx, mz) }.maxByOrNull { it.y }?.y
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

    /** Labels each object's own crop on the phone, and keeps the best crop as its photo. */
    private suspend fun nameAndCrop(picture: Bitmap, boxes: List<ScanBox>, tracks: List<Pair<Int, Set<Int>>>) {
        for ((id, ids) in tracks) {
            val box = boxes.firstOrNull { b -> b.trackingId != null && b.trackingId in ids } ?: continue
            val area = box.area
            val crop = cropUpright(picture, box.upright) ?: continue
            if (area > (cropArea[id] ?: 0f)) {
                crops[id] = crop
                cropArea[id] = area
            }
            if (!naming.add(id)) continue
            try {
                val label = recogniser.scanCategory(crop)
                val agreed = nameEvidence.getOrPut(id) { com.packabunch.packing.ScanLabelConsensus() }.observe(label)
                if (agreed == null) names.remove(id) else names[id] = agreed
            } catch (e: Exception) {
                Log.w(AR_TAG, "naming", e)
            } finally {
                naming.remove(id)
            }
        }
        if (!worker.isShutdown) runCatching { worker.execute { publish(tracker.all.filter { it.isConfirmed }, _ui.value.capReached) } }
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
        /** A depth-found object overlapping a detector box this much is the same object. */
        const val DEPTH_BOX_MATCH = 0.3f
        const val NAMING_INTERVAL_NS = 1_000_000_000L
        /** Outlines are traced in the picture this often, on a frame this many pixels wide. */
        const val TRACE_WIDTH = 240
        /** An outline older than this is from a view the phone has moved on from. */
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
        const val SHRINK = 0.01f
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
            val m = 0.01f
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
