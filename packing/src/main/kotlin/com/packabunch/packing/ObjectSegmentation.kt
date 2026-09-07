package com.packabunch.packing

import kotlin.math.roundToInt

/**
 * Finding the separate things in a scan.
 *
 * ### Why this exists
 *
 * Measuring one item at a time does not scale. Somebody packing a house has fifty things,
 * and any flow that costs thirty seconds each — typed or measured — costs them half an hour
 * before they see a single arrangement. That is not a packing tool, it is data entry.
 *
 * So the primary route is: put things out on the floor or a table, sweep the camera once,
 * and get every object's size in one pass. This is the geometry behind that. It reuses the
 * same occupancy grid the space scanner builds, pointed at objects instead of a container.
 *
 * ### How it works
 *
 * 1. Find the support surface — the height with far more solid cells than any other. That
 *    is the table or the floor everything is standing on.
 * 2. Take every solid cell above it. That is object material.
 * 3. Label connected components. Each blob of touching cells is one thing.
 * 4. Throw away blobs too small to be real, which is mostly scan speckle.
 * 5. Fit a box to each, trying rotations about the vertical axis so a thing lying at an
 *    angle to the phone does not get an inflated bounding box.
 *
 * ### What it cannot do, stated plainly
 *
 * **Touching objects merge.** Two boxes pushed against each other are one connected blob and
 * come back as one item. There is no depth-only fix for that, so the flow has to ask people
 * to leave gaps, and the review screen has to make splitting easy.
 *
 * **Sizes are quantised** to the grid and rounded outwards, so a detected item is up to one
 * cell larger than the real thing on each axis. That error is deliberate: overstating an
 * item means the plan says it does not fit when it would, which is an annoyance. Understating
 * means promising a fit that fails at the crate.
 */
object ObjectSegmentation {

    /** Blobs smaller than this are scan noise, not objects. */
    private const val MIN_CELLS = 12

    /** Rotations tried when fitting a box, in degrees. Finer buys very little. */
    private val YAW_STEPS = listOf(0, 15, 30, 45, 60, 75)

    /**
     * Everything standing on the support surface, measured.
     *
     * [supportPlaneCellK] can be supplied when the caller already knows the surface —
     * otherwise it is inferred.
     */
    fun detect(
        grid: VoxelGrid,
        supportPlaneCellK: Int = findSupportPlane(grid),
        minCells: Int = MIN_CELLS,
    ): List<DetectedObject> {
        val components = labelComponents(grid, aboveCellK = supportPlaneCellK, minCells = minCells)
        return components.map { component -> measure(component, grid, supportPlaneCellK) }
            .sortedByDescending { it.dimensions.volumeMm3 }
    }

    /**
     * The **top surface** of the thing everything is standing on.
     *
     * A table or a floor is a broad flat sheet, so it dominates its layers in a way an
     * object never does. But a slab is several cells thick and every one of those layers is
     * equally full, so the tie has to break *upwards*: what matters is the surface objects
     * rest on, not the underside of the table.
     *
     * Breaking it downwards instead leaves the top layer of the table counted as object
     * material, and since it spans the whole scan every object then joins to it and to each
     * other — the entire scene comes back as one enormous item.
     *
     * Searching the lower half only stops a wide flat thing lying on top — a board, a
     * mattress — being mistaken for the ground.
     */
    fun findSupportPlane(grid: VoxelGrid): Int {
        var bestK = 0
        var bestCount = -1
        val searchTo = (grid.countZ / 2).coerceAtLeast(1)

        for (k in 0 until searchTo) {
            var count = 0
            for (i in 0 until grid.countX) {
                for (j in 0 until grid.countY) {
                    if (grid.cellAt(i, j, k) == Cell.SOLID) count++
                }
            }
            // `>=`, so the highest of equally-full layers wins: the top of the slab.
            if (count >= bestCount) {
                bestCount = count
                bestK = k
            }
        }
        return bestK
    }

    /** 26-connected labelling, so objects touching only at a corner still join up. */
    private fun labelComponents(
        grid: VoxelGrid,
        aboveCellK: Int,
        minCells: Int,
    ): List<List<IntArray>> {
        val startK = aboveCellK + 1
        if (startK >= grid.countZ) return emptyList()

        val visited = HashSet<Int>()
        val components = mutableListOf<List<IntArray>>()

        fun flatten(i: Int, j: Int, k: Int) = (i * grid.countY + j) * grid.countZ + k

        for (i in 0 until grid.countX) {
            for (j in 0 until grid.countY) {
                for (k in startK until grid.countZ) {
                    if (grid.cellAt(i, j, k) != Cell.SOLID) continue
                    val start = flatten(i, j, k)
                    if (!visited.add(start)) continue

                    val cells = mutableListOf<IntArray>()
                    val stack = ArrayDeque<IntArray>()
                    stack.addLast(intArrayOf(i, j, k))

                    while (stack.isNotEmpty()) {
                        val at = stack.removeLast()
                        cells += at

                        for (di in -1..1) {
                            for (dj in -1..1) {
                                for (dk in -1..1) {
                                    if (di == 0 && dj == 0 && dk == 0) continue
                                    val ni = at[0] + di
                                    val nj = at[1] + dj
                                    val nk = at[2] + dk
                                    if (ni !in 0 until grid.countX) continue
                                    if (nj !in 0 until grid.countY) continue
                                    if (nk !in startK until grid.countZ) continue
                                    if (grid.cellAt(ni, nj, nk) != Cell.SOLID) continue
                                    if (!visited.add(flatten(ni, nj, nk))) continue
                                    stack.addLast(intArrayOf(ni, nj, nk))
                                }
                            }
                        }
                    }

                    if (cells.size >= minCells) components += cells
                }
            }
        }

        return components
    }

    /**
     * The smallest upright box that holds a blob.
     *
     * Rotations about the vertical are tried because a box sitting at forty degrees to the
     * phone has an axis-aligned bounding box far larger than the box itself — which would
     * report a shoe box as half again its real size and make the pack look impossible.
     * Tipping is not tried: things standing on a surface are almost never tilted, and
     * searching that space costs far more than it returns.
     */
    private fun measure(
        cells: List<IntArray>,
        grid: VoxelGrid,
        supportPlaneCellK: Int,
    ): DetectedObject {
        val res = grid.resolutionMm

        var bestFootprint = Long.MAX_VALUE
        var bestWidth = 0
        var bestDepth = 0
        var bestYaw = 0

        for (yawDegrees in YAW_STEPS) {
            val radians = Math.toRadians(yawDegrees.toDouble())
            val cos = kotlin.math.cos(radians)
            val sin = kotlin.math.sin(radians)

            var minU = Double.MAX_VALUE
            var maxU = -Double.MAX_VALUE
            var minV = Double.MAX_VALUE
            var maxV = -Double.MAX_VALUE

            cells.forEach { (i, j, _) ->
                val x = i.toDouble()
                val y = j.toDouble()
                val u = x * cos - y * sin
                val v = x * sin + y * cos
                if (u < minU) minU = u
                if (u > maxU) maxU = u
                if (v < minV) minV = v
                if (v > maxV) maxV = v
            }

            // Plus one cell: a cell's extent is its whole width, not its index.
            val width = ((maxU - minU + 1) * res).roundToInt()
            val depth = ((maxV - minV + 1) * res).roundToInt()
            val footprint = width.toLong() * depth.toLong()

            if (footprint < bestFootprint) {
                bestFootprint = footprint
                bestWidth = width
                bestDepth = depth
                bestYaw = yawDegrees
            }
        }

        val minK = cells.minOf { it[2] }
        val maxK = cells.maxOf { it[2] }
        val height = ((maxK - minK + 1) * res)

        val centroidI = cells.map { it[0] }.average()
        val centroidJ = cells.map { it[1] }.average()

        return DetectedObject(
            dimensions = Dimensions(
                widthMm = bestWidth.coerceAtLeast(MIN_LENGTH_MM),
                depthMm = bestDepth.coerceAtLeast(MIN_LENGTH_MM),
                heightMm = height.coerceAtLeast(MIN_LENGTH_MM),
            ),
            yawDegrees = bestYaw,
            cellCount = cells.size,
            centroidXMm = grid.originXMm + (centroidI * res).roundToInt(),
            centroidYMm = grid.originYMm + (centroidJ * res).roundToInt(),
            restsOnSupport = minK <= supportPlaneCellK + 1,
            // A grid cell is the finest distinction the scan can draw, so nothing is known
            // better than that. Reported rather than hidden.
            toleranceMm = res,
        )
    }
}

/**
 * One thing found standing on the surface.
 *
 * [toleranceMm] is the honest limit of the scan: sizes are quantised to the grid, so the
 * real object is within about one cell of this. It flows straight into
 * [SuggestedDimensions.toleranceMm] so the review screen can decide whether the uncertainty
 * actually matters for this particular pack.
 */
data class DetectedObject(
    val dimensions: Dimensions,
    /** Rotation about the vertical that produced the tightest box, in degrees. */
    val yawDegrees: Int,
    val cellCount: Int,
    val centroidXMm: Int,
    val centroidYMm: Int,
    /** False when the blob floats — usually a sign two things merged, or scan noise. */
    val restsOnSupport: Boolean,
    val toleranceMm: Int,
) {
    /**
     * Whether this looks like one object rather than several that touched.
     *
     * A very wide, very flat blob is the classic merge: two boxes side by side read as one
     * long low thing. It is a hint for the review screen to offer a split, not a verdict.
     */
    val looksMerged: Boolean
        get() {
            val edges = dimensions.sortedEdgesMm()
            return edges[2] > edges[0] * 6 && restsOnSupport
        }

    fun asSuggestion(): SuggestedDimensions = SuggestedDimensions(
        dimensions = dimensions,
        toleranceMm = toleranceMm,
        sourceNote = "measured from your scan",
        confidence = SuggestionConfidence.EXACT_PRODUCT,
    )
}
