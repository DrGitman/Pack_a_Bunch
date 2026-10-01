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

    /** Distance along the optical axis, metres, at cell (gx, gy) for a camera [heightM] up; 0 where unknown. */
    fun depthM(gx: Int, gy: Int, heightM: Double) = scale.mm(rel[gy * grid + gx]) / 1000.0 * heightM

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

        /**
         * How far the camera was tilted down, for a photo with no gravity reading (one from the
         * gallery). On a level surface the true inverse depth falls in a straight line up the
         * picture and reaches zero at the horizon; taking MiDaS's zero as that zero — HarshdeepJ's
         * own `D = S / r` — the row where the surface's line runs out is the horizon, and the
         * horizon's height in the picture is the tilt. Rows are fitted by their median, so a thing
         * on the table does not bend the line. Null when the surface does not slope that way.
         * ponytail: assumes no roll and MiDaS's offset near zero; a vanishing-point fit if gallery photos come out tilted.
         */
        fun estimatePitch(k: PlanarPose.Intrinsics, rel: FloatArray, grid: Int, picH: Int, isSurface: (Int, Int) -> Boolean): Double? {
            val ys = ArrayList<Double>(); val rs = ArrayList<Double>()
            for (gy in grid / 2 until grid) {
                val row = (0 until grid).filter { isSurface(it, gy) }.map { rel[gy * grid + it] }.sorted()
                if (row.size < grid / 8) continue
                ys += (gy + 0.5) * picH / grid; rs += row[row.size / 2].toDouble()
            }
            if (ys.size < 6) return null
            val n = ys.size; val my = ys.average(); val mr = rs.average()
            var sxy = 0.0; var sxx = 0.0
            for (i in 0 until n) { sxy += (ys[i] - my) * (rs[i] - mr); sxx += (ys[i] - my) * (ys[i] - my) }
            val slope = sxy / sxx
            if (!(slope > 0)) return null
            val horizon = my - mr / slope
            return Math.toDegrees(kotlin.math.atan2(k.cy - horizon, k.fy)).coerceIn(3.0, 80.0)
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
 * Only things whose size hardly varies are used, by their longest side, whichever way they lie:
 * a computer mouse is about 11 cm long standing or on its side. A "bottle" is not used — to the
 * detector that is anything from a 12 cm can to a 33 cm wine bottle, and a can lying down measured
 * as a bottle standing up is how a scan came out four times too big. Each recognised thing gives
 * its own answer and the median is used, so one oddly sized cup cannot move everything. It is
 * all an estimate, and the app says so; a card or sheet of A4 in the photo is used instead.
 */
object PhotoScale {

    /** Typical longest side, mm, keyed by YOLOX's COCO label. */
    val PRIORS: Map<String, Float> = mapOf(
        "mouse" to 112f,
        "cell phone" to 150f,
        "remote" to 190f,
        "keyboard" to 440f,
        "laptop" to 330f,
        "cup" to 100f,
        "wine glass" to 190f,
        "banana" to 190f,
        "apple" to 80f,
        "orange" to 78f,
        "scissors" to 190f,
        "toothbrush" to 190f,
        "spoon" to 170f,
        "fork" to 190f,
        "knife" to 220f,
    )

    /**
     * Camera height, in metres, from recognised things measured as if the camera were 1 m up.
     * @param measured label to its longest side, mm, at that 1 m.
     */
    fun fromPriors(measured: List<Pair<String, Float>>): Double? {
        val answers = measured.mapNotNull { (label, longest) ->
            val prior = PRIORS[label] ?: return@mapNotNull null
            if (longest > 5f) prior / longest.toDouble() else null
        }.filter { it in MIN_M..MAX_M }
        if (answers.isEmpty()) return null
        val sorted = answers.sorted()
        return sorted[sorted.size / 2]
    }

    const val MIN_M = 0.12
    const val MAX_M = 3.0

    /** Without any reference: arm's length above a table. */
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

    /**
     * The name to show. The detector knows no "can", so short "bottles" are cans.
     * @param longestMm the measured longest side.
     */
    fun nameFor(label: String, longestMm: Int): String = when {
        label == "bottle" && longestMm in 1..185 -> "Can"
        else -> YoloxDecode.itemName(label)
    }
}
