package com.packabunch.packing

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * One photo, placed in the room.
 *
 * The phone's gravity sensor says which way is up in the picture. The depth model (MiDaS) says
 * which parts are nearer than which, but in no units. The surface the things stand on — a table,
 * a boot floor — is flat and level, so every ray that meets it meets it at a distance fixed by
 * one number: how high the camera is above it. Fitting the depth model to those rays puts the
 * whole picture into the right shape; that one height is then the only thing left to know, and
 * every length in the photo scales with it.
 *
 * [heightM] comes from whatever the photo offers ([PhotoScale]): a card or sheet of A4 if one is
 * there, the usual size of things it recognises, or a typical height for that kind of photo.
 *
 * Camera axes: x right, y down, z forward, in the upright picture's pixels. Plane frame: origin on
 * the surface straight below the camera, x across, y away from the camera, h up; millimetres.
 */
class PhotoGeometry private constructor(
    val k: PlanarPose.Intrinsics,
    /** Up, unit, in camera axes. */
    val up: DoubleArray,
    private val scale: MonoDepthScale,
    private val rel: FloatArray,
    val grid: Int,
    val picW: Int,
    val picH: Int,
) {
    private val e1: DoubleArray
    private val e2: DoubleArray

    init {
        val x = doubleArrayOf(1.0, 0.0, 0.0)
        val d = dot(x, up)
        e1 = norm(doubleArrayOf(x[0] - d * up[0], x[1] - d * up[1], x[2] - d * up[2]))
        e2 = cross(up, e1)
    }

    /** The point seen at depth-grid cell (gx, gy), for a camera [heightM] above the surface; null where unknown. */
    fun pointAt(gx: Int, gy: Int, heightM: Double): PlanePoint? {
        val zMm = scale.mm(rel[gy * grid + gx])
        if (zMm <= 0) return null
        val px = (gx + 0.5) * picW / grid; val py = (gy + 0.5) * picH / grid
        val z = zMm / 1000.0 * heightM
        val p = doubleArrayOf((px - k.cx) / k.fx * z, (py - k.cy) / k.fy * z, z)
        return PlanePoint((dot(p, e1) * 1000).toFloat(), (dot(p, e2) * 1000).toFloat(), ((dot(p, up) + heightM) * 1000).toFloat())
    }

    /** Where a plane point appears in the picture, in pixels; null if behind the camera. */
    fun pixelOf(p: PlanePoint, heightM: Double): DoubleArray? {
        val x = p.xMm / 1000.0; val y = p.yMm / 1000.0; val h = p.hMm / 1000.0 - heightM
        val c = DoubleArray(3) { x * e1[it] + y * e2[it] + h * up[it] }
        if (c[2] <= 0.02) return null
        return doubleArrayOf(k.fx * c[0] / c[2] + k.cx, k.fy * c[1] / c[2] + k.cy)
    }

    /** The camera itself, as a plane point. */
    fun camera(heightM: Double) = PlanePoint(0f, 0f, (heightM * 1000).toFloat())

    companion object {
        /** Rays this close to level never meet the surface within the picture. */
        private const val MIN_DOWN = 0.04

        /**
         * Fits the depth model to the surface.
         *
         * @param rel MiDaS output on a [grid] × [grid] square stretched over the picture.
         * @param isSurface which grid cells may be the surface (not an object, not above the horizon).
         * @return null when too little surface is in view, or it is not level.
         */
        fun fit(
            k: PlanarPose.Intrinsics, up: DoubleArray, rel: FloatArray, grid: Int, picW: Int, picH: Int,
            isSurface: (Int, Int) -> Boolean, step: Int = 4,
        ): PhotoGeometry? {
            val u = norm(up)
            val rs = ArrayList<Float>(); val zs = ArrayList<Float>()
            for (gy in step / 2 until grid step step) for (gx in step / 2 until grid step step) {
                if (!isSurface(gx, gy)) continue
                val px = (gx + 0.5) * picW / grid; val py = (gy + 0.5) * picH / grid
                val d = doubleArrayOf((px - k.cx) / k.fx, (py - k.cy) / k.fy, 1.0)
                val down = dot(d, u)
                if (down > -MIN_DOWN) continue
                // Where this ray meets a surface 1 m below the camera, along the optical axis.
                rs += rel[gy * grid + gx]; zs += (-1000.0 / down).toFloat()
            }
            val scale = MonoDepthScale.fit(rs.toFloatArray(), zs.toFloatArray()) ?: return null
            return PhotoGeometry(k, u, scale, rel, grid, picW, picH)
        }

        /** Up, in camera axes, for a camera tilted down by [pitchDeg] — when there is no gravity reading. */
        fun upForPitch(pitchDeg: Double): DoubleArray {
            val t = Math.toRadians(pitchDeg)
            return doubleArrayOf(0.0, -kotlin.math.cos(t), -kotlin.math.sin(t))
        }

        private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
        private fun norm(a: DoubleArray): DoubleArray { val l = sqrt(dot(a, a)); return DoubleArray(3) { a[it] / l } }
        private fun cross(a: DoubleArray, b: DoubleArray) =
            doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
        @Suppress("unused") private fun close(a: Double, b: Double) = abs(a - b) < 1e-9
    }
}

/**
 * The camera's height above the surface — the one number a photo leaves open — from the best
 * thing in it.
 *
 * Typical sizes are of the commonest kind of each thing: a mug is about 9.5 cm tall, a computer
 * mouse about 11 cm long. Each recognised thing gives its own answer, and the median of them is
 * used, so one oddly sized cup cannot move everything. All of it is an estimate, and the app says
 * so; a card or sheet of A4 in the photo, when there is one, is used instead.
 */
object PhotoScale {

    /** A typical size: of the height, or of the longest side seen from above. */
    class Prior(val heightMm: Float? = null, val longestMm: Float? = null)

    /** Keyed by YOLOX's COCO label. */
    val PRIORS: Map<String, Prior> = mapOf(
        "cup" to Prior(heightMm = 95f),
        "bottle" to Prior(heightMm = 240f),
        "wine glass" to Prior(heightMm = 190f),
        "bowl" to Prior(longestMm = 160f),
        "mouse" to Prior(longestMm = 110f),
        "cell phone" to Prior(longestMm = 150f),
        "remote" to Prior(longestMm = 190f),
        "keyboard" to Prior(longestMm = 440f),
        "laptop" to Prior(longestMm = 330f),
        "book" to Prior(longestMm = 230f),
        "banana" to Prior(longestMm = 190f),
        "apple" to Prior(heightMm = 75f),
        "orange" to Prior(heightMm = 75f),
        "scissors" to Prior(longestMm = 190f),
        "toothbrush" to Prior(longestMm = 190f),
        "spoon" to Prior(longestMm = 170f),
        "fork" to Prior(longestMm = 190f),
        "knife" to Prior(longestMm = 220f),
        "clock" to Prior(longestMm = 280f),
        "vase" to Prior(heightMm = 250f),
        "sports ball" to Prior(heightMm = 220f),
        "backpack" to Prior(heightMm = 450f),
        "suitcase" to Prior(heightMm = 620f),
        "chair" to Prior(heightMm = 850f),
    )

    /** What a photo's height came from, for the log and the note on screen. */
    enum class Source { REFERENCE, RECOGNISED, TYPICAL }

    /**
     * Camera height, in metres, from recognised things measured as if the camera were 1 m up.
     * @param measured label to (height, longest) in millimetres at that 1 m.
     */
    fun fromPriors(measured: List<Triple<String, Float, Float>>): Double? {
        val answers = measured.mapNotNull { (label, height, longest) ->
            val prior = PRIORS[label] ?: return@mapNotNull null
            when {
                prior.heightMm != null && height > 5f -> prior.heightMm / height.toDouble()
                prior.longestMm != null && longest > 5f -> prior.longestMm / longest.toDouble()
                else -> null
            }
        }.filter { it in MIN_M..MAX_M }
        if (answers.isEmpty()) return null
        val sorted = answers.sorted()
        return sorted[sorted.size / 2]
    }

    const val MIN_M = 0.12
    const val MAX_M = 3.0

    /** Without any reference: arm's length above a table, or chest height into a space. */
    const val TYPICAL_ITEMS_M = 0.45

    /** Typical height above a space's floor, by what the space is called. */
    fun typicalForSpace(name: String?): Double {
        val n = name.orEmpty().lowercase()
        return when {
            "boot" in n || "trunk" in n || "car" in n || "van" in n -> 0.65
            "room" in n || "garage" in n || "shed" in n -> 1.35
            "box" in n || "crate" in n || "carton" in n || "drawer" in n -> 0.45
            else -> 0.8
        }
    }
}
