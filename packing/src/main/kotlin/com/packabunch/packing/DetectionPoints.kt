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

    /**
     * One depth sample in the support surface's frame; [central] if it came from the middle of
     * the box, [edge] if it sits on a jump in depth (see [isEdge]).
     */
    class Sample(val point: PlanePoint, val central: Boolean, val edge: Boolean = false)

    const val CELL_MM = 15f

    /** See [select]: the grid for a space scanned from a metre or more away. */
    const val SPACE_CELL_MM = 50f

    /** Samples higher than this above the surface are not something anyone packs on a tabletop. */
    const val MAX_HEIGHT_MM = 1500f

    /**
     * Returns the indices of [samples] that belong to the object. Empty if no object is found.
     *
     * [minCentralShare] and [minCentralSamples] are how much of the middle of the view the kept
     * piece must own. An object must fill the middle of its box. A space need not: looking into
     * a boot or across a room the middle is mostly floor, and the walls round it are the space,
     * so the space scan passes zero for both.
     *
     * [cellMm] is the grid pieces are joined on. It must be wider than the gap between
     * neighbouring samples or a surface falls apart into specks: [CELL_MM] suits an object at
     * arm's length, where samples land a few millimetres apart; across a room they land five
     * centimetres apart, and the space scan passes [SPACE_CELL_MM].
     */
    fun select(
        samples: List<Sample>,
        minCentralShare: Float = MIN_CENTRAL_SHARE,
        minCentralSamples: Int = MIN_CENTRAL_SAMPLES,
        cellMm: Float = CELL_MM,
        /** Samples higher than this are ignored: [MAX_HEIGHT_MM] for things, a room's height for spaces. */
        maxHeightMm: Float = MAX_HEIGHT_MM,
    ): List<Int> {
        fun key(p: PlanePoint) = pack(floor(p.xMm / cellMm).toInt(), floor(p.yMm / cellMm).toInt(), floor(p.hMm / cellMm).toInt())
        val cells = HashMap<Long, MutableList<Int>>()
        val edges = ArrayList<Int>()
        var centralTotal = 0
        for ((i, s) in samples.withIndex()) {
            val p = s.point
            if (s.central) centralTotal++
            if (p.hMm <= ObjectCloud.MIN_HEIGHT_MM || p.hMm > maxHeightMm) continue
            // Edge pixels may be the object's own rim or may float in the gap behind it; they
            // are kept, but never allowed to join one piece to another.
            if (s.edge) edges += i else cells.getOrPut(key(p)) { ArrayList() } += i
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
                    // Cells in the lowest layer only join upwards, never sideways to each
                    // other. Depth noise lifts patches of the table itself just clear of the
                    // surface cut; joined sideways, those patches bridged every object on the
                    // desk to the next one and to the wall behind.
                    if (h == 0 && dh == 0) continue
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
        // The kept piece has to be most of what the middle of the box is looking at, counting the
        // middle's samples that landed on the surface too. When the detector boxes something
        // too flat for depth — a phone, a sheet of paper — the middle is mostly table, and the
        // biggest raised piece left in the box is the wall or sofa behind it. Taking that is how
        // a phone came out 60 cm tall. Nothing this frame is the honest answer.
        if (centralVotes[winner] < minCentralSamples || centralVotes[winner] < centralTotal * minCentralShare) return emptyList()
        val kept = cells.filterKeys { component[it] == winner }
        val out = kept.values.flatten().toMutableList()
        // An edge pixel belongs to the object if it lands in or right beside the object's cells.
        for (i in edges) {
            val (a, b, c) = unpack(key(samples[i].point))
            var near = false
            for (di in -1..1) for (dj in -1..1) for (dh in -1..1) if (pack(a + di, b + dj, c + dh) in kept) near = true
            if (near) out += i
        }
        return out.sorted()
    }

    /**
     * Whether a depth pixel sits on a jump in depth: an edge pixel reads somewhere between the
     * object and whatever is behind it, a point floating in the gap that joins the two. [d] is
     * the pixel's depth and [neighbours] its four neighbours' (0 for none), all in mm.
     */
    fun isEdge(d: Int, vararg neighbours: Int): Boolean {
        val jump = maxOf(EDGE_JUMP_MM, (d * EDGE_JUMP_SHARE).toInt())
        for (n in neighbours) if (n > 0 && kotlin.math.abs(n - d) > jump) return true
        return false
    }

    const val EDGE_JUMP_MM = 25
    const val EDGE_JUMP_SHARE = 0.04f

    /** Fewer samples than this is not an object this frame — maybe next frame. */
    const val MIN_SAMPLES = 12

    /** The kept piece needs at least this many samples from the middle of the box. */
    const val MIN_CENTRAL_SAMPLES = 6

    /** Share of the box's middle, surface included, the kept piece must own. */
    const val MIN_CENTRAL_SHARE = 0.5f

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

    /** The reverse: a 0..1 sensor box to 0..1 upright, for boxes found from depth rather than ML Kit. */
    fun sensorToUpright(box: FloatArray, rotationDegrees: Int): FloatArray {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (sx in floatArrayOf(box[0], box[2])) for (sy in floatArrayOf(box[1], box[3])) {
            val (xu, yu) = when (rotationDegrees) {
                90 -> 1f - sy to sx
                180 -> 1f - sx to 1f - sy
                270 -> sy to 1f - sx
                else -> sx to sy
            }
            l = minOf(l, xu); r = maxOf(r, xu); t = minOf(t, yu); b = maxOf(b, yu)
        }
        return floatArrayOf(l.coerceIn(0f, 1f), t.coerceIn(0f, 1f), r.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
    }
}
