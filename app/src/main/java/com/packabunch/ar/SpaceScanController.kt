package com.packabunch.ar

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.UnavailableException
import com.packabunch.packing.DetectionPoints
import com.packabunch.packing.ObjectCloud
import com.packabunch.packing.PlaneFrame
import com.packabunch.packing.PlanePoint
import com.packabunch.packing.ScannedSpace
import com.packabunch.packing.SpaceBox
import com.packabunch.packing.SpaceFace
import com.packabunch.packing.SpaceFitter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

data class SpaceScanUi(
    val status: TrackingStatus = TrackingStatus.INITIALISING,
    val failureReason: TrackingFailureReason? = null,
    /** The floor of the space has been found and locked. */
    val floorFound: Boolean = false,
    /** The current fit, or null until enough of the walls has been seen. */
    val box: SpaceBox? = null,
    val torchOn: Boolean = false,
    val fatalError: String? = null,
) {
    val mappedPercent: Int get() = ((box?.mapped ?: 0f) * 100).toInt().coerceIn(0, 100)

    /** The same bar the space scan has always had: below it, a plan would be mostly guesswork. */
    val canFinish: Boolean get() = (box?.mapped ?: 0f) >= SCAN_DONE_THRESHOLD
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
 * The floor is the surface under the middle of the screen when the scan starts — the boot
 * floor, the bottom of the cupboard — and everything is measured from it. Depth samples are
 * kept when they stand over that floor's detected outline (grown a little, so the walls at
 * its edge count) and belong to the surfaces around the middle of the view; the car's
 * bumper below the sill and the garage wall behind it are not part of the boot.
 *
 * The fit ([SpaceFitter]) places each wall at the median of the points on it and finds the
 * opening and sill. Its result becomes the solver's grid ([SpaceFitter.toScannedSpace]):
 * free inside, solid where something stands in it, unknown behind a wall nobody saw.
 *
 * This is new and separate from [ArScanController], which is left as it was.
 */
class SpaceScanController(
    private val context: Context,
    private val density: Float,
) : GLSurfaceView.Renderer {

    private val _ui = MutableStateFlow(SpaceScanUi())
    val ui: StateFlow<SpaceScanUi> = _ui.asStateFlow()

    private val _anchors = MutableStateFlow(SpaceAnchors())
    val anchors: StateFlow<SpaceAnchors> = _anchors.asStateFlow()

    private var session: Session? = null
    private var config: Config? = null
    private val background = CameraBackgroundRenderer()
    private val outlines = GlowOutlineRenderer()

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "space-scan").apply { priority = Thread.NORM_PRIORITY - 1 } }
    private val workerBusy = AtomicBoolean(false)

    // Worker-owned.
    private var cloud = newCloud()
    private val cameras = ArrayList<PlanePoint>()

    @Volatile private var floorY: Float? = null
    @Volatile private var floorPolygon: FloatArray? = null
    @Volatile private var drawn: SpaceBox? = null
    @Volatile private var pendingTorch: Boolean? = null
    @Volatile private var pendingRestart = false

    private var viewportW = 1
    private var viewportH = 1
    private var displayRotation = 0
    private var geometryDirty = true
    private var lastProcessedNs = 0L
    private var lastDepthNs = 0L
    private var lastFitMs = 0L

    private val view = FloatArray(16)
    private val proj = FloatArray(16)
    private val viewProj = FloatArray(16)

    fun resume(rotation: Int, width: Int, height: Int): String? {
        displayRotation = rotation; viewportW = width; viewportH = height; geometryDirty = true
        return try {
            if (session == null) {
                val s = Session(context)
                if (!s.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    s.close()
                    return "This phone can't measure depth. You can still type the size in."
                }
                val c = Config(s).apply {
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                    focusMode = Config.FocusMode.AUTO
                    depthMode = Config.DepthMode.AUTOMATIC
                    lightEstimationMode = Config.LightEstimationMode.DISABLED
                }
                s.configure(c)
                session = s; config = c
            }
            session?.resume()
            null
        } catch (e: CameraNotAvailableException) {
            "The camera is being used by another app."
        } catch (e: UnavailableException) {
            e.message ?: "AR isn't available on this phone."
        } catch (t: Throwable) {
            Log.e(AR_TAG, "space scan could not start", t)
            t.message ?: "The scan couldn't start."
        }
    }

    fun pause() { session?.pause() }

    fun release() {
        worker.shutdownNow()
        session?.close()
        session = null
    }

    fun setTorch(on: Boolean) { pendingTorch = on }

    /** Forget the floor and everything seen, and start again from what is in the middle of the view. */
    fun restart() { pendingRestart = true }

    /** The finished space for the planner, or null if nothing usable was fitted. Call off the main thread. */
    fun buildScannedSpace(): ScannedSpace? = worker.submit<ScannedSpace?> {
        val snap = cloud.snapshot()
        val box = SpaceFitter.fit(snap.points, cameras, snap.weights) ?: return@submit null
        SpaceFitter.toScannedSpace(box, snap.points, snap.weights)
    }.get()

    // -- GL --------------------------------------------------------------------------------------

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            background.createOnGlThread()
            outlines.createOnGlThread()
            session?.setCameraTextureName(background.textureId)
        } catch (t: Throwable) {
            Log.e(AR_TAG, "space scan GL setup failed", t)
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
            if (pendingRestart) {
                pendingRestart = false
                floorY = null; floorPolygon = null; drawn = null
                worker.execute { cloud = newCloud(); cameras.clear() }
                _ui.value = _ui.value.copy(floorFound = false, box = null)
            }

            val frame = s.update()
            background.draw(frame)
            val camera = frame.camera
            if (camera.trackingState != TrackingState.TRACKING) {
                _ui.value = _ui.value.copy(
                    status = if (_ui.value.box == null) TrackingStatus.INITIALISING else TrackingStatus.LOST,
                    failureReason = camera.trackingFailureReason,
                )
                return
            }
            camera.getViewMatrix(view, 0)
            camera.getProjectionMatrix(proj, 0, 0.05f, 30f)
            Matrix.multiplyMM(viewProj, 0, proj, 0, view, 0)
            if (_ui.value.status != TrackingStatus.TRACKING) _ui.value = _ui.value.copy(status = TrackingStatus.TRACKING, failureReason = null)

            lockFloor(frame, s)
            if (floorY != null && frame.timestamp - lastProcessedNs >= PROCESS_INTERVAL_NS) {
                lastProcessedNs = frame.timestamp
                sample(frame)
            }
            drawSpace(frame)
        } catch (e: CameraNotAvailableException) {
            _ui.value = _ui.value.copy(fatalError = "The camera stopped responding.")
        } catch (t: Throwable) {
            Log.e(AR_TAG, "space scan frame failed", t)
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

    /**
     * The floor is whatever horizontal surface is under the middle of the screen. Once found it
     * is kept, and its outline is refreshed as ARCore grows it — the outline is what tells the
     * boot floor from the road.
     */
    private fun lockFloor(frame: Frame, s: Session) {
        val locked = floorY
        if (locked == null) {
            val hit = runCatching { frame.hitTest(viewportW / 2f, viewportH / 2f) }.getOrNull()
                ?.firstOrNull { h ->
                    val p = h.trackable as? Plane
                    p != null && p.type == Plane.Type.HORIZONTAL_UPWARD_FACING && p.trackingState == TrackingState.TRACKING &&
                        p.isPoseInPolygon(h.hitPose)
                } ?: return
            val plane = hit.trackable as Plane
            floorY = plane.centerPose.ty()
            floorPolygon = worldPolygon(plane)
            _ui.value = _ui.value.copy(floorFound = true)
            return
        }
        // Keep the widest outline of any horizontal plane at the locked height.
        val best = s.getAllTrackables(Plane::class.java)
            .filter { it.trackingState == TrackingState.TRACKING && it.subsumedBy == null && it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }
            .filter { kotlin.math.abs(it.centerPose.ty() - locked) < FLOOR_MATCH_M }
            .maxByOrNull { it.extentX * it.extentZ }
        if (best != null) floorPolygon = worldPolygon(best)
    }

    private fun worldPolygon(plane: Plane): FloatArray {
        val c = plane.centerPose
        val buf = plane.polygon
        val poly = FloatArray(buf.limit()).also { buf.rewind(); buf.get(it) }
        val out = FloatArray(poly.size)
        val local = FloatArray(3); val world = FloatArray(3)
        for (i in 0 until poly.size / 2) {
            local[0] = poly[2 * i]; local[1] = 0f; local[2] = poly[2 * i + 1]
            c.transformPoint(local, 0, world, 0)
            out[2 * i] = world[0]; out[2 * i + 1] = world[2]
        }
        return out
    }

    // -- sampling (GL thread) → fitting (worker) --------------------------------------------------

    private fun sample(frame: Frame) {
        if (workerBusy.get()) return
        val y0 = floorY ?: return
        val poly = floorPolygon ?: return
        val camera = frame.camera
        val pose = camera.pose
        val pf = PlaneFrame(0f, y0, 0f, ALONG, UP)
        val samples = ArrayList<DetectionPoints.Sample>(6000)
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
                val cu0 = depth.width * 0.3f; val cu1 = depth.width * 0.7f
                val cv0 = depth.height * 0.3f; val cv1 = depth.height * 0.7f
                var v = 0
                while (v < depth.height) {
                    var u = 0
                    while (u < depth.width) {
                        val mm = buf.getShort(v * plane.rowStride + u * plane.pixelStride).toInt() and 0xffff
                        if (mm in MIN_DEPTH_MM..MAX_DEPTH_MM) {
                            val p = projection.point(u, v, mm)
                            local[0] = p[0]; local[1] = p[1]; local[2] = p[2]
                            pose.transformPoint(local, 0, world, 0)
                            val pp2 = pf.toPlane(world[0], world[1], world[2])
                            if (pp2.hMm in -BELOW_FLOOR_MM..MAX_HEIGHT_MM && nearPolygon(poly, world[0], world[2], WALL_MARGIN_M)) {
                                samples += DetectionPoints.Sample(pp2, u >= cu0 && u <= cu1 && v >= cv0 && v <= cv1)
                            }
                        }
                        u += 2
                    }
                    v += 2
                }
            }
        } catch (_: NotYetAvailableException) {
            return
        }
        if (samples.isEmpty()) return
        val cam = pf.toPlane(pose.tx(), pose.ty(), pose.tz())
        workerBusy.set(true)
        worker.execute {
            try { accumulate(samples, cam) } catch (t: Throwable) { Log.e(AR_TAG, "space fit failed", t) } finally { workerBusy.set(false) }
        }
    }

    private fun accumulate(samples: List<DetectionPoints.Sample>, cam: PlanePoint) {
        // The walls are whatever stands connected around the middle of the view; the floor is
        // kept wherever it lies inside the outline.
        val keep = HashSet(DetectionPoints.select(samples))
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
        val box = SpaceFitter.fit(snap.points, cameras, snap.weights)
        drawn = box
        _ui.value = _ui.value.copy(box = box)
    }

    // -- drawing (GL thread) -----------------------------------------------------------------------

    private fun drawSpace(frame: Frame) {
        val box = drawn ?: run { _anchors.value = SpaceAnchors(); return }
        val y0 = floorY ?: return
        val pf = PlaneFrame(0f, y0, 0f, ALONG, UP)
        val hw = box.widthMm / 2; val hd = box.depthMm / 2; val h = box.heightMm
        fun at(u: Float, v: Float, z: Float): PlanePoint { val (x, y) = box.toPlan(u, v); return PlanePoint(x, y, z) }
        // Corners: front-left, front-right, back-right, back-left; bottom then top.
        val fl0 = at(-hw, -hd, 0f); val fr0 = at(hw, -hd, 0f); val br0 = at(hw, hd, 0f); val bl0 = at(-hw, hd, 0f)
        val fl1 = at(-hw, -hd, h); val fr1 = at(hw, -hd, h); val br1 = at(hw, hd, h); val bl1 = at(-hw, hd, h)
        fun seen(face: SpaceFace) = (box.coverage[face] ?: 0f) >= SpaceBox.WELL_SEEN || (face == SpaceFace.TOP) || (face == SpaceFace.FRONT && !box.cameraInside)
        data class Edge(val a: PlanePoint, val b: PlanePoint, val faces: List<SpaceFace>, val front: Boolean)
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
        val opening = ArrayList<FloatArray>(); val measured = ArrayList<FloatArray>(); val unseen = ArrayList<FloatArray>()
        for (e in edges) {
            val line = clipSegment(pf, e.a, e.b) ?: continue
            when {
                e.front && !box.cameraInside -> opening += line
                e.faces.all { seen(it) } -> measured += line
                else -> unseen += line
            }
        }
        // Floor first, faintly, so the lines sit on top of it.
        val floor = listOf(fl0, fr0, br0, bl0).map { projectPoint(pf, it) }
        if (floor.all { it != null }) {
            outlines.drawFill(floor.flatMap { listOf(it!!.first, it.second) }.toFloatArray(), FLOOR_FILL_ARGB, viewportW, viewportH)
        }
        outlines.drawPolylines(opening, OPENING_STYLE, density, viewportW, viewportH)
        outlines.drawPolylines(measured, MEASURED_STYLE, density, viewportW, viewportH)
        outlines.drawPolylines(unseen, UNSEEN_STYLE, density, viewportW, viewportH)

        fun mid(a: PlanePoint, b: PlanePoint) = PlanePoint((a.xMm + b.xMm) / 2, (a.yMm + b.yMm) / 2, (a.hMm + b.hMm) / 2)
        fun screen(p: PlanePoint) = projectPoint(pf, p)?.let { (x, y) ->
            if (x < 0 || y < 0 || x > viewportW || y > viewportH) null else x / viewportW to y / viewportH
        }
        _anchors.value = SpaceAnchors(
            width = screen(mid(fl0, fr0)),
            depth = screen(mid(bl0, fl0)),
            height = screen(at(0f, hd, h / 2)),
            opening = if (box.cameraInside) null else screen(mid(fl1, fr1)),
        )
    }

    private val world4 = FloatArray(4)
    private val clipA = FloatArray(4)
    private val clipB = FloatArray(4)
    private val world3 = FloatArray(3)

    private fun toClip(pf: PlaneFrame, p: PlanePoint, out: FloatArray) {
        pf.toWorld(p, world3)
        world4[0] = world3[0]; world4[1] = world3[1]; world4[2] = world3[2]; world4[3] = 1f
        Matrix.multiplyMV(out, 0, viewProj, 0, world4, 0)
    }

    private fun projectPoint(pf: PlaneFrame, p: PlanePoint): Pair<Float, Float>? {
        toClip(pf, p, clipA)
        if (clipA[3] <= NEAR_W) return null
        return ((clipA[0] / clipA[3]) * 0.5f + 0.5f) * viewportW to (0.5f - (clipA[1] / clipA[3]) * 0.5f) * viewportH
    }

    /**
     * A segment in pixels, cut where it passes behind the camera. Standing inside a room, most
     * of its box is behind or beside you; without the cut those edges would vanish entirely.
     */
    private fun clipSegment(pf: PlaneFrame, a: PlanePoint, b: PlanePoint): FloatArray? {
        toClip(pf, a, clipA); toClip(pf, b, clipB)
        val wa = clipA[3]; val wb = clipB[3]
        if (wa <= NEAR_W && wb <= NEAR_W) return null
        fun lerp(t: Float, i: Int) = clipA[i] + (clipB[i] - clipA[i]) * t
        val (s0, s1) = when {
            wa <= NEAR_W -> { val t = (NEAR_W - wa) / (wb - wa); floatArrayOf(lerp(t, 0), lerp(t, 1), NEAR_W) to floatArrayOf(clipB[0], clipB[1], wb) }
            wb <= NEAR_W -> { val t = (NEAR_W - wa) / (wb - wa); floatArrayOf(clipA[0], clipA[1], wa) to floatArrayOf(lerp(t, 0), lerp(t, 1), NEAR_W) }
            else -> floatArrayOf(clipA[0], clipA[1], wa) to floatArrayOf(clipB[0], clipB[1], wb)
        }
        fun px(c: FloatArray) = floatArrayOf(((c[0] / c[2]) * 0.5f + 0.5f) * viewportW, (0.5f - (c[1] / c[2]) * 0.5f) * viewportH)
        val p0 = px(s0); val p1 = px(s1)
        return floatArrayOf(p0[0], p0[1], p1[0], p1[1])
    }

    private companion object {
        val ALONG = floatArrayOf(1f, 0f, 0f)
        val UP = floatArrayOf(0f, 1f, 0f)
        const val PROCESS_INTERVAL_NS = 100_000_000L
        const val FIT_INTERVAL_MS = 300L
        const val MIN_DEPTH_MM = 150
        const val MAX_DEPTH_MM = 4_000
        const val BELOW_FLOOR_MM = 60f
        const val MAX_HEIGHT_MM = 3_000f
        /** How far past the floor's outline a wall may stand. */
        const val WALL_MARGIN_M = 0.2f
        const val FLOOR_MATCH_M = 0.04f
        const val NEAR_W = 0.06f
        const val FLOOR_FILL_ARGB = 0x1FFFFFFFL   // 12 % white

        // SpaceScan · New: "Measured edges", "Unseen side", "Opening".
        val MEASURED_STYLE = OutlineStyle(0xFFFFFFFF, 2.1f, glowDp = 3f, wideGlowDp = 10f)
        val UNSEEN_STYLE = OutlineStyle(0xFFF2A03D, 1.8f, dashOnDp = 2f, dashOffDp = 4f, glowDp = 5f, dotted = true)
        val OPENING_STYLE = OutlineStyle(0xCCFFFFFF, 1.3f, dashOnDp = 5f, dashOffDp = 4f, glowDp = 4f)

        fun newCloud() = ObjectCloud(voxelMm = 10f, maxVoxels = 40_000, minHeightMm = -BELOW_FLOOR_MM, relativeSightings = 0.03f)

        /** Inside the floor outline, or within [margin] metres of it — where the walls stand. */
        fun nearPolygon(poly: FloatArray, x: Float, z: Float, margin: Float): Boolean {
            val n = poly.size / 2
            if (n < 3) return false
            var inside = false
            var j = n - 1
            var best = Float.MAX_VALUE
            for (i in 0 until n) {
                val xi = poly[2 * i]; val zi = poly[2 * i + 1]; val xj = poly[2 * j]; val zj = poly[2 * j + 1]
                if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) inside = !inside
                // Distance to this edge.
                val ex = xi - xj; val ez = zi - zj
                val len2 = ex * ex + ez * ez
                val t = if (len2 > 0f) (((x - xj) * ex + (z - zj) * ez) / len2).coerceIn(0f, 1f) else 0f
                val dx = x - (xj + t * ex); val dz = z - (zj + t * ez)
                best = minOf(best, dx * dx + dz * dz)
                j = i
            }
            return inside || best <= margin * margin
        }
    }
}
