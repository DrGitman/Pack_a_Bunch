package com.packabunch.packing

/**
 * Solver axes, fixed for the whole engine:
 *
 *   X — left to right across the front of the space
 *   Y — front to back
 *   Z — upward, with the floor of the space at z = 0
 *
 * Every length is an integer count of millimetres. That is a calculation convention so
 * the geometry is exact and reproducible; it is not a claim that anybody measured to the
 * millimetre. Renderers and ARCore use their own conventions (often Y-up, in metres), so
 * the conversion has to be written out explicitly at that boundary rather than assumed.
 */

/** Smallest length the engine will accept, in mm. Below this the input is a typo. */
const val MIN_LENGTH_MM: Int = 1

/** Largest length the engine will accept, in mm — 50 m, well past any supported space. */
const val MAX_LENGTH_MM: Int = 50_000

/** An item's own three lengths, before any rotation is applied. */
data class Dimensions(
    val widthMm: Int,
    val depthMm: Int,
    val heightMm: Int,
) {
    /** mm³. `Long` throughout: 3 m cubed already overflows a 32-bit Int. */
    val volumeMm3: Long
        get() = widthMm.toLong() * depthMm.toLong() * heightMm.toLong()

    val longestEdgeMm: Int get() = maxOf(widthMm, depthMm, heightMm)
    val shortestEdgeMm: Int get() = minOf(widthMm, depthMm, heightMm)

    /** The three lengths ascending — used to test "could this ever fit" independent of pose. */
    fun sortedEdgesMm(): IntArray = intArrayOf(widthMm, depthMm, heightMm).sortedArray()

    fun isValid(): Boolean = listOf(widthMm, depthMm, heightMm)
        .all { it in MIN_LENGTH_MM..MAX_LENGTH_MM }
}

/** Which of an item's own three lengths is being pointed along a solver axis. */
enum class Axis { WIDTH, DEPTH, HEIGHT }

/**
 * The six axis-aligned poses of a cuboid: every permutation of (width, depth, height)
 * onto the (X, Y, Z) solver axes. Nothing off-axis and nothing at an angle — the engine
 * models a rigid cuboid envelope, so a "nearly fits if you tilt it" arrangement is
 * outside what it can honestly claim.
 */
enum class Orientation(
    val alongX: Axis,
    val alongY: Axis,
    val alongZ: Axis,
) {
    WIDTH_DEPTH_HEIGHT(Axis.WIDTH, Axis.DEPTH, Axis.HEIGHT),
    DEPTH_WIDTH_HEIGHT(Axis.DEPTH, Axis.WIDTH, Axis.HEIGHT),
    WIDTH_HEIGHT_DEPTH(Axis.WIDTH, Axis.HEIGHT, Axis.DEPTH),
    HEIGHT_WIDTH_DEPTH(Axis.HEIGHT, Axis.WIDTH, Axis.DEPTH),
    DEPTH_HEIGHT_WIDTH(Axis.DEPTH, Axis.HEIGHT, Axis.WIDTH),
    HEIGHT_DEPTH_WIDTH(Axis.HEIGHT, Axis.DEPTH, Axis.WIDTH);

    /**
     * True when the item's own height still points up. These are the only two poses left
     * when the user ticks "Keep it upright" on an item.
     */
    val isUpright: Boolean get() = alongZ == Axis.HEIGHT

    fun apply(dimensions: Dimensions): Dimensions = Dimensions(
        widthMm = dimensions.along(alongX),
        depthMm = dimensions.along(alongY),
        heightMm = dimensions.along(alongZ),
    )

    companion object {
        val UPRIGHT_ONLY: List<Orientation> = entries.filter { it.isUpright }

        /** Poses this item is allowed to take, in a fixed order so solving is deterministic. */
        fun allowedFor(keepUpright: Boolean): List<Orientation> =
            if (keepUpright) UPRIGHT_ONLY else entries
    }
}

private fun Dimensions.along(axis: Axis): Int = when (axis) {
    Axis.WIDTH -> widthMm
    Axis.DEPTH -> depthMm
    Axis.HEIGHT -> heightMm
}

/**
 * A half-open axis-aligned box in space coordinates: `[minX, maxX)` and so on. Half-open
 * is what makes two boxes that share a face count as touching rather than overlapping.
 */
data class Box(
    val minXMm: Int,
    val minYMm: Int,
    val minZMm: Int,
    val widthMm: Int,
    val depthMm: Int,
    val heightMm: Int,
) {
    val maxXMm: Int get() = minXMm + widthMm
    val maxYMm: Int get() = minYMm + depthMm
    val maxZMm: Int get() = minZMm + heightMm

    val volumeMm3: Long get() = widthMm.toLong() * depthMm.toLong() * heightMm.toLong()

    /** Shared faces and shared edges are not overlaps. Only shared interior volume is. */
    fun overlaps(other: Box): Boolean =
        minXMm < other.maxXMm && other.minXMm < maxXMm &&
            minYMm < other.maxYMm && other.minYMm < maxYMm &&
            minZMm < other.maxZMm && other.minZMm < maxZMm

    fun containsInAllAxes(other: Box): Boolean =
        other.minXMm >= minXMm && other.maxXMm <= maxXMm &&
            other.minYMm >= minYMm && other.maxYMm <= maxYMm &&
            other.minZMm >= minZMm && other.maxZMm <= maxZMm

    /** True when [other]'s footprint sits entirely within this box's footprint. */
    fun coversFootprintOf(other: Box): Boolean =
        other.minXMm >= minXMm && other.maxXMm <= maxXMm &&
            other.minYMm >= minYMm && other.maxYMm <= maxYMm
}
