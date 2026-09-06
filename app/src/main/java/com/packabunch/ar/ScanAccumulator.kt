package com.packabunch.ar

import com.packabunch.packing.Cell
import com.packabunch.packing.VoxelGrid
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Turns a sweep of the phone into an occupancy grid.
 *
 * The method is ray carving, and the reason it is worth the effort is the third state.
 * ARCore gives us points it is confident about; a naive scanner marks those as solid and
 * treats everything else as empty, which quietly invents free space wherever the phone
 * never looked. Instead:
 *
 *  - the cell a point lands in is marked [Cell.SOLID] — something is there;
 *  - every cell along the ray from the camera to that point is marked [Cell.FREE] — we
 *    looked through it, so we know it is empty;
 *  - everything else stays [Cell.UNKNOWN] — we never saw it, and we say so.
 *
 * That last line is the entire point. "Under the parcel shelf, about 46 L unknown" is only
 * possible because unseen space is tracked rather than assumed.
 *
 * Coordinates: ARCore works in metres in its own world frame. The grid is integer
 * millimetres with its own origin, and [worldToCell] is the only place the two meet.
 */
class ScanAccumulator(
    /** World-space corner of the grid, in metres. */
    private val originXM: Float,
    private val originYM: Float,
    private val originZM: Float,
    private val resolutionMm: Int = 40,
    widthMm: Int = 2_400,
    depthMm: Int = 2_400,
    heightMm: Int = 1_600,
) {
    private val countX = ceil(widthMm.toDouble() / resolutionMm).toInt().coerceAtLeast(1)
    private val countY = ceil(depthMm.toDouble() / resolutionMm).toInt().coerceAtLeast(1)
    private val countZ = ceil(heightMm.toDouble() / resolutionMm).toInt().coerceAtLeast(1)

    /** 0 = unknown, 1 = seen-through (free), 2 = occupied. Occupied always wins. */
    private val cells = ByteArray(countX * countY * countZ)

    private val resolutionM = resolutionMm / 1000f

    val totalCells: Int get() = cells.size

    private fun index(i: Int, j: Int, k: Int) = (i * countY + j) * countZ + k

    private fun inBounds(i: Int, j: Int, k: Int) =
        i in 0 until countX && j in 0 until countY && k in 0 until countZ

    /**
     * ARCore's Y is up and the grid's Z is up, so the axes are swapped exactly here and
     * nowhere else. Getting this wrong produces a scan that looks plausible and packs
     * sideways, which is why it lives in one function.
     */
    private fun worldToCell(xM: Float, yM: Float, zM: Float): IntArray {
        val i = ((xM - originXM) / resolutionM).toInt()
        val j = ((-(zM - originZM)) / resolutionM).toInt() // world -Z is grid +Y (forward)
        val k = ((yM - originYM) / resolutionM).toInt()
        return intArrayOf(i, j, k)
    }

    /**
     * Folds one observation in: a point the camera saw, and where the camera was.
     *
     * [confidence] is ARCore's own 0..1 for the point. Low-confidence points are dropped
     * rather than averaged in — a scan built from guesses is worse than a smaller scan.
     */
    fun observe(
        cameraXM: Float, cameraYM: Float, cameraZM: Float,
        pointXM: Float, pointYM: Float, pointZM: Float,
        confidence: Float,
    ) {
        if (confidence < MIN_CONFIDENCE) return

        val end = worldToCell(pointXM, pointYM, pointZM)
        val start = worldToCell(cameraXM, cameraYM, cameraZM)

        carve(start, end)

        if (inBounds(end[0], end[1], end[2])) {
            cells[index(end[0], end[1], end[2])] = OCCUPIED
        }
    }

    /**
     * Marks the cells between camera and point as seen-through.
     *
     * A straight walk in cell space at half-cell steps. Not a perfect 3D line algorithm,
     * but it is stable, cheap enough to run on every frame's point cloud, and errs towards
     * carving fewer cells rather than more — under-carving leaves space unknown, which is
     * the safe direction. Over-carving would invent room.
     */
    private fun carve(start: IntArray, end: IntArray) {
        val dx = (end[0] - start[0]).toFloat()
        val dy = (end[1] - start[1]).toFloat()
        val dz = (end[2] - start[2]).toFloat()
        val length = sqrt(dx * dx + dy * dy + dz * dz)
        if (length < 1f) return

        val steps = (length * 2f).roundToInt().coerceAtMost(MAX_CARVE_STEPS)
        for (step in 0 until steps) {
            val t = step / steps.toFloat()
            val i = (start[0] + dx * t).roundToInt()
            val j = (start[1] + dy * t).roundToInt()
            val k = (start[2] + dz * t).roundToInt()
            if (!inBounds(i, j, k)) continue

            val at = index(i, j, k)
            // Occupied never downgrades to free: one solid observation outranks any number
            // of rays that happened to pass near it.
            if (cells[at] == UNKNOWN) cells[at] = FREE
        }
    }

    /** Fraction of the grid that has been observed at all, 0..1. */
    val coverage: Float
        get() {
            var seen = 0
            for (value in cells) if (value != UNKNOWN) seen++
            return seen.toFloat() / cells.size
        }

    /**
     * Coverage broken down by region, so the scan screen can say *where* to sweep rather
     * than just how far along you are. "Right side still thin" is actionable; "68%" is not.
     */
    fun regionCoverage(): Map<ScanRegion, Float> {
        val counts = mutableMapOf<ScanRegion, Pair<Int, Int>>()
        for (i in 0 until countX) {
            val region = when {
                i < countX / 3 -> ScanRegion.LEFT
                i > countX * 2 / 3 -> ScanRegion.RIGHT
                else -> ScanRegion.MIDDLE
            }
            for (j in 0 until countY) {
                for (k in 0 until countZ) {
                    val seen = if (cells[index(i, j, k)] != UNKNOWN) 1 else 0
                    val (s, t) = counts.getOrDefault(region, 0 to 0)
                    counts[region] = (s + seen) to (t + 1)
                }
            }
        }
        return counts.mapValues { (_, v) -> if (v.second == 0) 0f else v.first.toFloat() / v.second }
    }

    /**
     * The finished grid.
     *
     * Free stays free, occupied becomes solid, and anything never observed stays unknown so
     * the packing engine refuses to place into it and the review screen can report it.
     */
    fun toVoxelGrid(): VoxelGrid {
        val out = ByteArray(cells.size)
        for (n in cells.indices) {
            out[n] = when (cells[n]) {
                FREE -> 0 // Cell.FREE
                OCCUPIED -> 1 // Cell.SOLID
                else -> 2 // Cell.UNKNOWN
            }
        }
        return VoxelGrid(
            originXMm = 0,
            originYMm = 0,
            originZMm = 0,
            resolutionMm = resolutionMm,
            countX = countX,
            countY = countY,
            countZ = countZ,
            cells = out,
        )
    }

    /** For fixtures and previews without a phone. */
    fun fillForTesting(state: Cell, predicate: (Int, Int, Int) -> Boolean) {
        val code = when (state) {
            Cell.FREE -> FREE
            Cell.SOLID -> OCCUPIED
            Cell.UNKNOWN -> UNKNOWN
        }
        for (i in 0 until countX) {
            for (j in 0 until countY) {
                for (k in 0 until countZ) {
                    if (predicate(i, j, k)) cells[index(i, j, k)] = code
                }
            }
        }
    }

    private companion object {
        const val UNKNOWN: Byte = 0
        const val FREE: Byte = 1
        const val OCCUPIED: Byte = 2

        /** ARCore's own confidence for the point. Below this it is noise, not geometry. */
        const val MIN_CONFIDENCE = 0.3f

        /** Stops a stray far-away point from carving a corridor across the whole grid. */
        const val MAX_CARVE_STEPS = 200
    }
}

enum class ScanRegion(val label: String) {
    LEFT("Left side"),
    MIDDLE("Middle"),
    RIGHT("Right side"),
}

/** How much of the space has to be mapped before a plan built on it means anything. */
const val SCAN_DONE_THRESHOLD = 0.80f

/** Below this a region is called out by name as still needing a sweep. */
const val SCAN_THIN_THRESHOLD = 0.55f

/** Convenience for the progress readout. */
fun coveragePercent(coverage: Float): Int = (coverage * 100).roundToInt().coerceIn(0, 100)

internal fun approximatelyEqual(a: Float, b: Float, tolerance: Float = 0.001f) =
    abs(a - b) <= max(tolerance, tolerance * max(abs(a), abs(b)))
