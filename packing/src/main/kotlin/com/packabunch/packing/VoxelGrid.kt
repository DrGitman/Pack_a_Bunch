package com.packabunch.packing

import kotlin.math.ceil
import kotlin.math.floor

/**
 * A scanned space, as an occupancy grid.
 *
 * A boot is not a box. It has wheel arches, a sloping seat back, a parcel shelf and a
 * floor that is not flat, and none of that survives being described by three numbers. So a
 * scanned space is a grid of cells, and the solver asks the grid whether a box fits rather
 * than testing against six walls.
 *
 * The third cell state is the one that matters most. [Cell.UNKNOWN] is a part of the space
 * the scan never saw — behind the parcel shelf, the far corner the phone was never pointed
 * at. It is not free and it is not solid, and the app must not pretend either. The default
 * policy treats unknown as solid, which loses volume but never promises room that may not
 * be there. Both figures are reported so the user can be told exactly what that cost:
 * "you'd pack into 320 L instead of 384 L".
 */
enum class Cell {
    /** Observed, and empty. The only state the solver will place into. */
    FREE,

    /** Observed, and something is there — structure, or an obstruction left in. */
    SOLID,

    /** Never observed. Treated as solid by default; counted and reported separately. */
    UNKNOWN,
}

/**
 * Millimetre size of one cell. 20 mm is the working default: fine enough that a wheel arch
 * is the right shape, coarse enough that a car boot is about sixty thousand cells rather
 * than half a million.
 */
const val DEFAULT_VOXEL_MM: Int = 20

class VoxelGrid(
    /** Position of cell (0,0,0)'s minimum corner, in space coordinates. */
    val originXMm: Int,
    val originYMm: Int,
    val originZMm: Int,
    val resolutionMm: Int,
    val countX: Int,
    val countY: Int,
    val countZ: Int,
    cells: ByteArray,
) {
    init {
        require(resolutionMm > 0) { "voxel resolution must be positive" }
        require(countX > 0 && countY > 0 && countZ > 0) { "grid must have volume" }
        require(cells.size == countX * countY * countZ) {
            "expected ${countX * countY * countZ} cells, got ${cells.size}"
        }
    }

    private val data: ByteArray = cells.copyOf()

    /**
     * Running totals of solid and unknown cells, so asking "is this box clear?" is a
     * handful of array reads rather than a walk over every cell the box covers. Without
     * this, testing twenty items against every candidate position in a boot-sized grid is
     * far too slow to run on a phone inside the time budget.
     */
    private val solidPrefix = IntArray((countX + 1) * (countY + 1) * (countZ + 1))
    private val unknownPrefix = IntArray((countX + 1) * (countY + 1) * (countZ + 1))

    init {
        buildPrefix(solidPrefix) { it == Cell.SOLID }
        buildPrefix(unknownPrefix) { it == Cell.UNKNOWN }
    }

    private fun index(i: Int, j: Int, k: Int) = (i * countY + j) * countZ + k

    private fun prefixIndex(i: Int, j: Int, k: Int) =
        (i * (countY + 1) + j) * (countZ + 1) + k

    fun cellAt(i: Int, j: Int, k: Int): Cell = when (data[index(i, j, k)].toInt()) {
        0 -> Cell.FREE
        1 -> Cell.SOLID
        else -> Cell.UNKNOWN
    }

    private fun buildPrefix(target: IntArray, predicate: (Cell) -> Boolean) {
        for (i in 1..countX) {
            for (j in 1..countY) {
                for (k in 1..countZ) {
                    val here = if (predicate(cellAt(i - 1, j - 1, k - 1))) 1 else 0
                    target[prefixIndex(i, j, k)] = here +
                        target[prefixIndex(i - 1, j, k)] +
                        target[prefixIndex(i, j - 1, k)] +
                        target[prefixIndex(i, j, k - 1)] -
                        target[prefixIndex(i - 1, j - 1, k)] -
                        target[prefixIndex(i - 1, j, k - 1)] -
                        target[prefixIndex(i, j - 1, k - 1)] +
                        target[prefixIndex(i - 1, j - 1, k - 1)]
                }
            }
        }
    }

    /** Inclusion–exclusion over the running totals: eight lookups, whatever the box size. */
    private fun countIn(
        prefix: IntArray,
        i0: Int, j0: Int, k0: Int,
        i1: Int, j1: Int, k1: Int,
    ): Int = prefix[prefixIndex(i1, j1, k1)] -
        prefix[prefixIndex(i0, j1, k1)] -
        prefix[prefixIndex(i1, j0, k1)] -
        prefix[prefixIndex(i1, j1, k0)] +
        prefix[prefixIndex(i0, j0, k1)] +
        prefix[prefixIndex(i0, j1, k0)] +
        prefix[prefixIndex(i1, j0, k0)] -
        prefix[prefixIndex(i0, j0, k0)]

    /** Cell range a box touches. Any partially covered cell counts — this rounds outwards. */
    private fun cellRange(box: Box): IntArray? {
        val i0 = floor((box.minXMm - originXMm).toDouble() / resolutionMm).toInt()
        val j0 = floor((box.minYMm - originYMm).toDouble() / resolutionMm).toInt()
        val k0 = floor((box.minZMm - originZMm).toDouble() / resolutionMm).toInt()
        val i1 = ceil((box.maxXMm - originXMm).toDouble() / resolutionMm).toInt()
        val j1 = ceil((box.maxYMm - originYMm).toDouble() / resolutionMm).toInt()
        val k1 = ceil((box.maxZMm - originZMm).toDouble() / resolutionMm).toInt()

        // Anything reaching outside the scanned grid is outside the space.
        if (i0 < 0 || j0 < 0 || k0 < 0) return null
        if (i1 > countX || j1 > countY || k1 > countZ) return null
        if (i1 <= i0 || j1 <= j0 || k1 <= k0) return null
        return intArrayOf(i0, j0, k0, i1, j1, k1)
    }

    /**
     * True when every cell the box touches is free.
     *
     * [unknownIsSolid] is the honesty switch. Left true, a box will not be placed into
     * space the scan never saw.
     */
    fun isClear(box: Box, unknownIsSolid: Boolean = true): Boolean {
        val r = cellRange(box) ?: return false
        val solid = countIn(solidPrefix, r[0], r[1], r[2], r[3], r[4], r[5])
        if (solid > 0) return false
        if (!unknownIsSolid) return true
        return countIn(unknownPrefix, r[0], r[1], r[2], r[3], r[4], r[5]) == 0
    }

    /**
     * True when the whole footprint of [box] has solid material directly beneath it.
     *
     * This is how an item rests on a sloping boot floor or a wheel arch: there is no single
     * flat "floor plane" to compare against, so support is asked of the structure column by
     * column. A base that is solid under nine tenths of its area and hanging over fresh air
     * for the rest is refused.
     */
    fun restsOnStructure(box: Box, unknownIsSolid: Boolean = true): Boolean {
        val r = cellRange(box) ?: return false
        val baseK = r[2]
        if (baseK == 0) return true // sitting on the bottom of the scanned volume

        val footprint = (r[3] - r[0]) * (r[4] - r[1])
        val solidBelow = countIn(solidPrefix, r[0], r[1], baseK - 1, r[3], r[4], baseK)
        if (solidBelow == footprint) return true
        if (!unknownIsSolid) return false

        val unknownBelow = countIn(unknownPrefix, r[0], r[1], baseK - 1, r[3], r[4], baseK)
        return solidBelow + unknownBelow == footprint
    }

    private val cellVolumeMm3: Long
        get() = resolutionMm.toLong() * resolutionMm.toLong() * resolutionMm.toLong()

    val freeCellCount: Int by lazy { countAll(Cell.FREE) }
    val solidCellCount: Int by lazy { countAll(Cell.SOLID) }
    val unknownCellCount: Int by lazy { countAll(Cell.UNKNOWN) }

    private fun countAll(state: Cell): Int {
        var total = 0
        for (i in 0 until countX) {
            for (j in 0 until countY) {
                for (k in 0 until countZ) {
                    if (cellAt(i, j, k) == state) total++
                }
            }
        }
        return total
    }

    /** What the solver may actually use: observed, empty space. */
    val usableVolumeMm3: Long get() = freeCellCount * cellVolumeMm3

    /** What the space might hold if every unseen patch turned out to be empty. */
    val optimisticVolumeMm3: Long
        get() = (freeCellCount + unknownCellCount) * cellVolumeMm3

    val unknownVolumeMm3: Long get() = unknownCellCount * cellVolumeMm3

    /** Fraction of the scanned envelope that was actually observed, 0..1. */
    val observedFraction: Float
        get() {
            val considered = freeCellCount + unknownCellCount
            return if (considered == 0) 1f else freeCellCount.toFloat() / considered
        }

    /** Bounding box of the whole grid, in space coordinates. */
    val boundsMm: Box
        get() = Box(
            minXMm = originXMm,
            minYMm = originYMm,
            minZMm = originZMm,
            widthMm = countX * resolutionMm,
            depthMm = countY * resolutionMm,
            heightMm = countZ * resolutionMm,
        )

    /**
     * Every free cell that has solid material directly beneath it — the surfaces an item
     * could actually be set down on. These seed the search, because in an irregular space
     * the useful starting positions are not just the corners of a box.
     */
    fun restingSurfaces(strideCells: Int = 1): List<Triple<Int, Int, Int>> {
        val found = mutableListOf<Triple<Int, Int, Int>>()
        val stride = strideCells.coerceAtLeast(1)
        var i = 0
        while (i < countX) {
            var j = 0
            while (j < countY) {
                for (k in 0 until countZ) {
                    if (cellAt(i, j, k) != Cell.FREE) continue
                    val supported = k == 0 || cellAt(i, j, k - 1) != Cell.FREE
                    if (supported) {
                        found += Triple(
                            originXMm + i * resolutionMm,
                            originYMm + j * resolutionMm,
                            originZMm + k * resolutionMm,
                        )
                    }
                }
                j += stride
            }
            i += stride
        }
        return found
    }

    /**
     * Contiguous runs of unseen cells, so the app can say "under the parcel shelf, about
     * 46 L unknown" instead of one meaningless total. Flood fill over 6-connected
     * neighbours; regions smaller than [minCells] are scan noise and are folded away.
     */
    fun unknownRegions(minCells: Int = 8): List<UnknownRegion> {
        val visited = BooleanArray(countX * countY * countZ)
        val regions = mutableListOf<UnknownRegion>()
        val neighbours = arrayOf(
            intArrayOf(1, 0, 0), intArrayOf(-1, 0, 0),
            intArrayOf(0, 1, 0), intArrayOf(0, -1, 0),
            intArrayOf(0, 0, 1), intArrayOf(0, 0, -1),
        )

        for (si in 0 until countX) {
            for (sj in 0 until countY) {
                for (sk in 0 until countZ) {
                    val start = index(si, sj, sk)
                    if (visited[start] || cellAt(si, sj, sk) != Cell.UNKNOWN) continue

                    val stack = ArrayDeque<IntArray>()
                    stack.addLast(intArrayOf(si, sj, sk))
                    visited[start] = true

                    var count = 0
                    var sumI = 0L
                    var sumJ = 0L
                    var sumK = 0L

                    while (stack.isNotEmpty()) {
                        val (i, j, k) = stack.removeLast().let { Triple(it[0], it[1], it[2]) }
                        count++
                        sumI += i; sumJ += j; sumK += k

                        for (n in neighbours) {
                            val ni = i + n[0]
                            val nj = j + n[1]
                            val nk = k + n[2]
                            if (ni !in 0 until countX || nj !in 0 until countY || nk !in 0 until countZ) continue
                            val ix = index(ni, nj, nk)
                            if (visited[ix] || cellAt(ni, nj, nk) != Cell.UNKNOWN) continue
                            visited[ix] = true
                            stack.addLast(intArrayOf(ni, nj, nk))
                        }
                    }

                    if (count >= minCells) {
                        regions += UnknownRegion(
                            volumeMm3 = count * cellVolumeMm3,
                            centroidXMm = originXMm + ((sumI / count).toInt() * resolutionMm),
                            centroidYMm = originYMm + ((sumJ / count).toInt() * resolutionMm),
                            centroidZMm = originZMm + ((sumK / count).toInt() * resolutionMm),
                            cellCount = count,
                        )
                    }
                }
            }
        }

        return regions.sortedByDescending { it.volumeMm3 }
    }

    companion object {
        /**
         * A plain rectangular space expressed as a grid. Used to prove the two paths agree,
         * and as the starting point a scan refines.
         */
        fun forRectangle(
            widthMm: Int,
            depthMm: Int,
            heightMm: Int,
            resolutionMm: Int = DEFAULT_VOXEL_MM,
        ): VoxelGrid {
            val nx = ceil(widthMm.toDouble() / resolutionMm).toInt().coerceAtLeast(1)
            val ny = ceil(depthMm.toDouble() / resolutionMm).toInt().coerceAtLeast(1)
            val nz = ceil(heightMm.toDouble() / resolutionMm).toInt().coerceAtLeast(1)
            return VoxelGrid(
                originXMm = 0, originYMm = 0, originZMm = 0,
                resolutionMm = resolutionMm,
                countX = nx, countY = ny, countZ = nz,
                cells = ByteArray(nx * ny * nz), // all FREE
            )
        }

        /** Builder for tests and for the scanner to fill in. */
        fun build(
            widthMm: Int,
            depthMm: Int,
            heightMm: Int,
            resolutionMm: Int = DEFAULT_VOXEL_MM,
            initial: Cell = Cell.FREE,
            fill: (i: Int, j: Int, k: Int) -> Cell? = { _, _, _ -> null },
        ): VoxelGrid {
            val nx = ceil(widthMm.toDouble() / resolutionMm).toInt().coerceAtLeast(1)
            val ny = ceil(depthMm.toDouble() / resolutionMm).toInt().coerceAtLeast(1)
            val nz = ceil(heightMm.toDouble() / resolutionMm).toInt().coerceAtLeast(1)
            val cells = ByteArray(nx * ny * nz) { initial.code }
            for (i in 0 until nx) {
                for (j in 0 until ny) {
                    for (k in 0 until nz) {
                        val state = fill(i, j, k) ?: continue
                        cells[(i * ny + j) * nz + k] = state.code
                    }
                }
            }
            return VoxelGrid(0, 0, 0, resolutionMm, nx, ny, nz, cells)
        }
    }
}

private val Cell.code: Byte
    get() = when (this) {
        Cell.FREE -> 0
        Cell.SOLID -> 1
        Cell.UNKNOWN -> 2
    }

/**
 * A patch the scan never saw. Carries a volume so the app can say how much is at stake and
 * a centroid so it can point at where to sweep.
 *
 * It deliberately has no name. "Under the parcel shelf" is a label a person or a later
 * classifier supplies — the geometry cannot know it, and inventing one would be a guess
 * dressed up as an observation.
 */
data class UnknownRegion(
    val volumeMm3: Long,
    val centroidXMm: Int,
    val centroidYMm: Int,
    val centroidZMm: Int,
    val cellCount: Int,
    val label: String? = null,
) {
    val litres: Double get() = volumeMm3 / 1_000_000.0
}
