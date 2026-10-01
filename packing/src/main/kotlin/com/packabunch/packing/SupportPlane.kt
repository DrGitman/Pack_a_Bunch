package com.packabunch.packing

/**
 * The surface the items are standing on, worked out from depth alone.
 *
 * Finding a plane from tracked visual features fails where there are none, so a dark, glossy or
 * plain table gives it nothing and no plane is reported. Measuring was gated on
 * that list, so on those surfaces the scan sat at "0 measured" forever while depth was arriving
 * perfectly well the whole time.
 *
 * Depth does not need texture. A horizontal surface puts every one of its points at the same
 * height, so it piles up in a single height band; a wall spreads its points across every band it
 * spans. The support is therefore just the height the most points share.
 */
object SupportPlane {

    /** Height bands this tall, in metres. Coarse enough to survive depth noise. */
    private const val BIN_M = 0.01f

    /** Points within this of the chosen height are treated as lying on it. */
    private const val SLAB_M = 0.03f

    /** Below this many points the scene is too sparse to call anything a surface. */
    private const val MIN_POINTS = 60

    /** A band holding less than this share of all points is not a surface, just noise. */
    private const val MIN_SHARE = 0.06f

    /** The footprint is padded by this much so items at the edge still sit within it. */
    private const val PAD_M = 0.25f

    data class Support(val y: Float, val polygonXZ: FloatArray) {
        // Array fields: identity equals would be wrong, and these are compared in tests.
        override fun equals(other: Any?) =
            other is Support && other.y == y && other.polygonXZ.contentEquals(polygonXZ)
        override fun hashCode() = 31 * y.hashCode() + polygonXZ.contentHashCode()
    }

    /**
     * @param points world-space x,y,z triples, y up.
     * @return the support, or null when no height band stands out enough to be a surface.
     */
    fun detect(points: FloatArray): Support? {
        val count = points.size / 3
        if (count < MIN_POINTS) return null

        var lowest = Float.MAX_VALUE
        for (i in 0 until count) lowest = minOf(lowest, points[3 * i + 1])
        if (!lowest.isFinite()) return null

        // Tally heights. The fullest band is the flattest, largest thing in view.
        val tally = HashMap<Int, Int>()
        for (i in 0 until count) {
            val bin = ((points[3 * i + 1] - lowest) / BIN_M).toInt()
            tally[bin] = (tally[bin] ?: 0) + 1
        }
        val best = tally.maxByOrNull { it.value } ?: return null
        if (best.value < count * MIN_SHARE) return null

        // Average the band rather than taking its edge, so the height is not quantised to BIN_M.
        val level = lowest + (best.key + 0.5f) * BIN_M
        var sum = 0f
        var n = 0
        for (i in 0 until count) {
            val y = points[3 * i + 1]
            if (kotlin.math.abs(y - level) <= BIN_M) { sum += y; n++ }
        }
        val y = if (n > 0) sum / n else level

        // Footprint: the spread of everything resting at that height.
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        var onIt = 0
        for (i in 0 until count) {
            if (kotlin.math.abs(points[3 * i + 1] - y) > SLAB_M) continue
            val x = points[3 * i]; val z = points[3 * i + 2]
            minX = minOf(minX, x); maxX = maxOf(maxX, x)
            minZ = minOf(minZ, z); maxZ = maxOf(maxZ, z)
            onIt++
        }
        if (onIt < MIN_POINTS) return null

        return Support(
            y,
            floatArrayOf(
                minX - PAD_M, minZ - PAD_M,
                maxX + PAD_M, minZ - PAD_M,
                maxX + PAD_M, maxZ + PAD_M,
                minX - PAD_M, maxZ + PAD_M,
            ),
        )
    }
}
