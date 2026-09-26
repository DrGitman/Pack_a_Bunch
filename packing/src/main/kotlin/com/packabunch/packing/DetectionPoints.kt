package com.packabunch.packing

import kotlin.math.floor

/**
 * Picks out which depth samples inside a detector box are actually the detected object.
 *
 * A detector box is a rectangle on the screen; the object is whatever solid thing fills the
 * middle of it. Around that thing the same rectangle also holds the table, the wall behind,
 * and often a corner of the neighbouring object — a mug in front of a carton puts carton in
 * the mug's box and mug in the carton's.
 *
 * So the samples are grouped into connected pieces in 3D (on a [CELL_MM] grid), and the piece
 * most of the box's *central* samples belong to is kept. The centre of a detector box is
 * almost always the object; the edges almost never only are. Anything touching the support
 * surface is left for [ObjectCloud] to drop, but still counts for connectivity, so a thin
 * base does not split an object from its own upper half.
 */
object DetectionPoints {

    /** One depth sample in the support surface's frame; [central] if it came from the middle of the box. */
    class Sample(val point: PlanePoint, val central: Boolean)

    const val CELL_MM = 15f

    /** Samples higher than this above the surface are not something anyone packs on a tabletop. */
    const val MAX_HEIGHT_MM = 1500f

    /** Returns the indices of [samples] that belong to the object. Empty if no object is found. */
    fun select(samples: List<Sample>): List<Int> {
        val cells = HashMap<Long, MutableList<Int>>()
        for ((i, s) in samples.withIndex()) {
            val p = s.point
            if (p.hMm <= ObjectCloud.MIN_HEIGHT_MM || p.hMm > MAX_HEIGHT_MM) continue
            cells.getOrPut(key(p)) { ArrayList() } += i
        }
        if (cells.isEmpty()) return emptyList()

        // Connected components over occupied cells.
        val component = HashMap<Long, Int>()
        var next = 0
        val stack = ArrayDeque<Long>()
        for (start in cells.keys) {
            if (start in component) continue
            val id = next++
            component[start] = id
            stack.addLast(start)
            while (stack.isNotEmpty()) {
                val k = stack.removeLast()
                val (i, j, h) = unpack(k)
                for (di in -1..1) for (dj in -1..1) for (dh in -1..1) {
                    val n = pack(i + di, j + dj, h + dh)
                    if (n in cells && n !in component) { component[n] = id; stack.addLast(n) }
                }
            }
        }

        val centralVotes = IntArray(next)
        val size = IntArray(next)
        for ((k, idx) in cells) {
            val c = component.getValue(k)
            size[c] += idx.size
            for (i in idx) if (samples[i].central) centralVotes[c]++
        }
        val winner = (0 until next).maxWithOrNull(compareBy({ centralVotes[it] }, { size[it] })) ?: return emptyList()
        if (size[winner] < MIN_SAMPLES) return emptyList()
        return cells.filterKeys { component[it] == winner }.values.flatten().sorted()
    }

    /** Fewer samples than this is not an object this frame — maybe next frame. */
    const val MIN_SAMPLES = 12

    private fun key(p: PlanePoint) = pack(
        floor(p.xMm / CELL_MM).toInt(), floor(p.yMm / CELL_MM).toInt(), floor(p.hMm / CELL_MM).toInt(),
    )

    private const val BIAS = 1 shl 20
    private const val MASK = (1L shl 21) - 1
    private fun pack(i: Int, j: Int, h: Int): Long =
        ((i + BIAS).toLong() and MASK shl 42) or ((j + BIAS).toLong() and MASK shl 21) or ((h + BIAS).toLong() and MASK)
    private fun unpack(k: Long) = Triple(
        ((k ushr 42) and MASK).toInt() - BIAS, ((k ushr 21) and MASK).toInt() - BIAS, (k and MASK).toInt() - BIAS,
    )
}

/**
 * Detector boxes between the orientation ML Kit reports them in and the camera sensor's.
 *
 * ML Kit is given the sensor image plus a rotation and answers in *upright* pixels — the
 * rotated image's, whose width and height are swapped for 90° and 270°. ARCore's depth image
 * and `IMAGE_NORMALIZED` coordinates are in the *sensor's* orientation. The old code
 * normalised upright pixels by the sensor's width and height and skipped the rotation, which
 * put every box in the wrong place on a phone held upright.
 */
object SensorBox {

    /** Upright pixel box to 0..1 sensor coordinates as `[left, top, right, bottom]`. */
    fun uprightToSensor(
        left: Float, top: Float, right: Float, bottom: Float,
        sensorWidth: Int, sensorHeight: Int, rotationDegrees: Int,
    ): FloatArray {
        val swapped = rotationDegrees == 90 || rotationDegrees == 270
        val uw = (if (swapped) sensorHeight else sensorWidth).toFloat()
        val uh = (if (swapped) sensorWidth else sensorHeight).toFloat()
        val xs = floatArrayOf(left / uw, right / uw)
        val ys = floatArrayOf(top / uh, bottom / uh)
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (xu in xs) for (yu in ys) {
            val (sx, sy) = when (rotationDegrees) {
                90 -> yu to 1f - xu
                180 -> 1f - xu to 1f - yu
                270 -> 1f - yu to xu
                else -> xu to yu
            }
            l = minOf(l, sx); r = maxOf(r, sx); t = minOf(t, sy); b = maxOf(b, sy)
        }
        return floatArrayOf(l.coerceIn(0f, 1f), t.coerceIn(0f, 1f), r.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
    }
}
