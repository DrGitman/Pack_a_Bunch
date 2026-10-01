package com.packabunch.scan

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.packabunch.packing.CardFinder
import com.packabunch.packing.MonoDepthScale
import com.packabunch.packing.PlanarPose
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/** What the scan can see this frame that stops it measuring, in the person's terms. */
enum class ScanProblem {
    /** Too little light for the picture to show edges. */
    TOO_DARK,
    /** The picture is smeared: the phone is moving too fast. */
    TOO_FAST,
    /** No bank card or sheet of A4 in view. */
    NO_REFERENCE,
    /** The card is in view but not lying flat on the surface. */
    REFERENCE_NOT_FLAT,
}

/** The reference the scale comes from. */
enum class Reference(val longM: Double, val shortM: Double) {
    CARD(PlanarPose.CARD_LONG_M, PlanarPose.CARD_SHORT_M),
    A4(PlanarPose.A4_LONG_M, PlanarPose.A4_SHORT_M),
}

/** Depth in millimetres on a grid stretched over the whole picture, with its pinhole numbers. */
class MetricDepth(val mm: IntArray, val size: Int, val k: PlanarPose.Intrinsics)

/**
 * Everything the scan knows about one camera frame.
 *
 * World frame is the reference's: origin at the card's centre, X and Z along the surface it lies
 * on, Y up out of it, metres. So the surface is y = 0 and every height is simply y.
 */
class SceneFrame(
    val camera: CameraFrame,
    val pose: PlanarPose.Pose?,
    val reference: Reference?,
    /** The reference's corners in [camera]'s picture, for drawing it. */
    val referenceCorners: DoubleArray?,
    val depth: MetricDepth?,
    val problem: ScanProblem?,
)

/**
 * The measuring engine: one camera picture in, a placed and scaled scene out.
 *
 *  1. A bank card or A4 sheet lying on the surface is found ([CardFinder]) and the camera placed
 *     relative to it ([PlanarPose]): position, angle and real scale, every frame.
 *  2. MiDaS gives relative depth for every pixel ([MidasDepth]) — the depth model of the reference
 *     project, its mobile version.
 *  3. That depth is put into millimetres with invisible references: points on the surface whose
 *     true distance the card's pose gives exactly. Scale and offset are both fitted, which is what
 *     keeps a far wall from reading as four metres away.
 *
 * Runs on the camera's analysis thread; a slow frame simply makes the camera drop the next one.
 */
class SceneEngine(private val context: Context, private val gravity: Gravity) {

    @Volatile private var midas: MidasDepth? = null
    private var previousPose: PlanarPose.Pose? = null
    private var sharpnessBaseline = 0.0

    fun process(frame: CameraFrame, wantDepth: Boolean = true): SceneFrame {
        val picture = frame.picture
        // Card finding on a picture about 960 wide: enough pixels on the card's edges, quick enough.
        val s = (FIND_WIDTH.toDouble() / picture.width).coerceAtMost(1.0)
        val w = (picture.width * s).toInt(); val h = (picture.height * s).toInt()
        val small = if (s < 1.0) Bitmap.createScaledBitmap(picture, w, h, true) else picture
        val argb = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
        if (small !== picture) small.recycle()
        val gray = IntArray(w * h) { i -> val p = argb[i]; (((p shr 16) and 0xFF) * 77 + ((p shr 8) and 0xFF) * 150 + (p and 0xFF) * 29) shr 8 }

        val brightness = gray.average()
        val sharp = sharpness(gray, w, h)
        sharpnessBaseline = if (sharpnessBaseline == 0.0) sharp else sharpnessBaseline * 0.95 + sharp * 0.05
        val problemFromPicture = when {
            brightness < DARK_LEVEL -> ScanProblem.TOO_DARK
            sharp < sharpnessBaseline * BLUR_RATIO -> ScanProblem.TOO_FAST
            else -> null
        }

        val kSmall = PlanarPose.Intrinsics(frame.intrinsics.fx * s, frame.intrinsics.fy * s, frame.intrinsics.cx * s, frame.intrinsics.cy * s)
        var pose: PlanarPose.Pose? = null; var reference: Reference? = null; var corners: DoubleArray? = null
        var notFlat = false
        val up = gravity.upInCamera()
        search@ for (q in CardFinder.find(gray, w, h)) for (ref in Reference.entries) {
            val fit = PlanarPose.fromQuad(q.corners, ref.longM, ref.shortM, kSmall, previousPose, q.sides) ?: continue
            if (up != null) {
                // The reference's up (world Y) seen from the camera is the rotation's middle column.
                val yAxis = doubleArrayOf(fit.pose.r[1], fit.pose.r[4], fit.pose.r[7])
                if (yAxis[0] * up[0] + yAxis[1] * up[1] + yAxis[2] * up[2] < cos(Math.toRadians(MAX_TILT_DEG))) { notFlat = true; continue }
            }
            // Back to full-picture pixels: the pose is the same; only the corners scale.
            pose = fit.pose; reference = ref
            corners = DoubleArray(8) { q.corners[it] / s }
            break@search
        }
        if (pose != null) previousPose = pose

        val problem = problemFromPicture ?: when {
            pose != null -> null
            notFlat -> ScanProblem.REFERENCE_NOT_FLAT
            else -> ScanProblem.NO_REFERENCE
        }
        val depth = if (pose != null && wantDepth && problemFromPicture == null) metricDepth(picture, pose, frame.intrinsics) else null
        return SceneFrame(frame, pose, reference, corners, depth, problem)
    }

    /** MiDaS relative depth, put into millimetres against the surface the reference lies on. */
    private fun metricDepth(picture: Bitmap, pose: PlanarPose.Pose, k: PlanarPose.Intrinsics): MetricDepth? {
        val rel = try {
            (midas ?: MidasDepth(context).also { midas = it }).run(picture)
        } catch (t: Throwable) {
            Log.w(SCAN_TAG, "depth model", t); return null
        }
        val g = MidasDepth.SIZE
        val kg = PlanarPose.Intrinsics(k.fx * g / picture.width, k.fy * g / picture.height, k.cx * g / picture.width, k.cy * g / picture.height)
        // Invisible references: a lattice of rays to the surface. Where a ray meets it, the true
        // distance is known from the pose; MiDaS's value there pairs with it. A ray that hits an
        // object first gives a wrong pair — the fit drops the worst quarter.
        val rs = ArrayList<Float>(); val zs = ArrayList<Float>()
        for (gy in STEP / 2 until g step STEP) for (gx in STEP / 2 until g step STEP) {
            val hit = pose.rayToTable(kg, gx.toDouble(), gy.toDouble()) ?: continue
            val zMm = hit[3] * 1000
            if (zMm < MIN_MM || zMm > MAX_MM) continue
            rs += rel[gy * g + gx]; zs += zMm.toFloat()
        }
        val scale = MonoDepthScale.fit(rs.toFloatArray(), zs.toFloatArray()) ?: return null
        return MetricDepth(IntArray(g * g) { scale.mm(rel[it]) }, g, kg)
    }

    /** Mean absolute Laplacian: high on a crisp picture, low on a smeared one. */
    private fun sharpness(gray: IntArray, w: Int, h: Int): Double {
        var sum = 0.0; var n = 0
        for (y in 1 until h - 1 step 2) for (x in 1 until w - 1 step 2) {
            val i = y * w + x
            sum += abs(4 * gray[i] - gray[i - 1] - gray[i + 1] - gray[i - w] - gray[i + w]); n++
        }
        return if (n == 0) 0.0 else sum / n
    }

    fun release() { midas?.close(); midas = null }

    companion object {
        const val FIND_WIDTH = 960
        /** Mean brightness (0..255) below which edges are lost in noise. */
        const val DARK_LEVEL = 28.0
        /** A frame this much blurrier than recent ones is a smeared one. */
        const val BLUR_RATIO = 0.45
        /** The reference counts as lying flat within this many degrees of level. */
        const val MAX_TILT_DEG = 15.0
        const val STEP = 12
        const val MIN_MM = 120.0
        const val MAX_MM = 6000.0

        @Suppress("unused") private fun len(v: DoubleArray) = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
    }
}
