package com.packabunch.packing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Measuring several things from one sweep.
 *
 * The fixtures build grids by hand so the right answer is known exactly, which is the only
 * way to tell a segmentation bug from a scan being noisy.
 */
class ObjectSegmentationTest {

    private val res = 20

    /**
     * A table at k=0..1 with objects standing on it. Cells are 20 mm, so a block spanning
     * five cells is 100 mm.
     */
    private fun sceneWith(
        objects: List<IntArray>, // i0, i1, j0, j1, heightCells (exclusive upper bounds)
        widthMm: Int = 1_000,
        depthMm: Int = 1_000,
        heightMm: Int = 600,
    ): VoxelGrid = VoxelGrid.build(widthMm, depthMm, heightMm, resolutionMm = res) { i, j, k ->
        when {
            k <= 1 -> Cell.SOLID // the surface everything stands on
            objects.any { (i0, i1, j0, j1, h) ->
                i in i0 until i1 && j in j0 until j1 && k in 2 until (2 + h)
            } -> Cell.SOLID
            else -> null
        }
    }

    private operator fun IntArray.component1() = this[0]
    private operator fun IntArray.component2() = this[1]
    private operator fun IntArray.component3() = this[2]
    private operator fun IntArray.component4() = this[3]
    private operator fun IntArray.component5() = this[4]

    @Test
    fun `the support surface is found under the objects, not the objects themselves`() {
        val grid = sceneWith(listOf(intArrayOf(5, 10, 5, 10, 6)))

        assertEquals(1, ObjectSegmentation.findSupportPlane(grid))
    }

    @Test
    fun `three separated things come back as three items`() {
        val grid = sceneWith(
            listOf(
                intArrayOf(2, 7, 2, 7, 5),
                intArrayOf(15, 20, 2, 7, 8),
                intArrayOf(2, 7, 15, 20, 3),
            ),
        )

        val found = ObjectSegmentation.detect(grid)

        assertEquals(3, found.size)
        assertTrue(found.all { it.restsOnSupport })
    }

    @Test
    fun `a measured size matches the block that was put there`() {
        // Five cells across, five deep, six tall, at 20 mm a cell.
        val grid = sceneWith(listOf(intArrayOf(5, 10, 5, 10, 6)))

        val item = ObjectSegmentation.detect(grid).single()

        assertEquals(100, item.dimensions.widthMm)
        assertEquals(100, item.dimensions.depthMm)
        assertEquals(120, item.dimensions.heightMm)
        // Nothing is known finer than one cell, and the screen has to be able to say so.
        assertEquals(res, item.toleranceMm)
    }

    @Test
    fun `scan speckle is not reported as an item`() {
        val grid = VoxelGrid.build(1_000, 1_000, 600, resolutionMm = res) { i, j, k ->
            when {
                k <= 1 -> Cell.SOLID
                // A real object, plus two stray cells of noise floating nearby.
                i in 5 until 10 && j in 5 until 10 && k in 2 until 8 -> Cell.SOLID
                i == 20 && j == 20 && k == 4 -> Cell.SOLID
                i == 30 && j == 30 && k == 6 -> Cell.SOLID
                else -> null
            }
        }

        assertEquals(1, ObjectSegmentation.detect(grid).size)
    }

    @Test
    fun `an item at an angle is not reported as bigger than it is`() {
        // A long thin block laid diagonally. Its axis-aligned bounding box is far larger
        // than the block, so a naive fit would overstate it badly.
        val grid = VoxelGrid.build(1_400, 1_400, 400, resolutionMm = res) { i, j, k ->
            when {
                k <= 1 -> Cell.SOLID
                k in 2 until 5 && i in 5..30 && (j - i) in 0..3 -> Cell.SOLID
                else -> null
            }
        }

        val item = ObjectSegmentation.detect(grid).single()
        val edges = item.dimensions.sortedEdgesMm()

        // The diagonal block is about 26 cells long and 4 wide. Rotating the fit has to
        // recover something much closer to that than to the 26 x 26 axis-aligned box.
        assertTrue(
            edges[1] < 26 * res,
            "shortest horizontal edge was ${edges[1]} mm — the fit did not rotate",
        )
        assertTrue(item.yawDegrees != 0, "a diagonal item should not fit best at zero yaw")
    }

    @Test
    fun `two touching things merge, and the result says it might have`() {
        // Deliberately abutting: this is the known limitation, and the flow depends on it
        // being detectable rather than silent.
        val grid = sceneWith(listOf(intArrayOf(2, 30, 5, 9, 3)))

        val item = ObjectSegmentation.detect(grid).single()

        assertTrue(
            item.looksMerged,
            "a long flat blob should be flagged as possibly two things pushed together",
        )
    }

    @Test
    fun `a compact single item is not flagged as merged`() {
        val grid = sceneWith(listOf(intArrayOf(5, 12, 5, 12, 6)))

        assertFalse(ObjectSegmentation.detect(grid).single().looksMerged)
    }

    @Test
    fun `a detected item becomes a suggestion that carries its own uncertainty`() {
        val grid = sceneWith(listOf(intArrayOf(5, 10, 5, 10, 6)))

        val suggestion = ObjectSegmentation.detect(grid).single().asSuggestion()

        assertEquals(res, suggestion.toleranceMm)
        // Loose against a tight gap, fine against a roomy one — the same rule the catalogue
        // suggestions use, so scanned and looked-up figures behave identically.
        assertTrue(suggestion.needsConfirming(clearanceMm = 10))
        assertFalse(suggestion.needsConfirming(clearanceMm = 60))
    }

    @Test
    fun `nothing on the table means nothing reported`() {
        val grid = sceneWith(emptyList())

        assertTrue(ObjectSegmentation.detect(grid).isEmpty())
    }

    @Test
    fun `a floor covered in things is measured in one sweep, and it stays quick`() {
        // A 3 m x 3 m floor tiled with separated blocks — 25 across each axis, so 625 of
        // them. Far more than anybody packs at once, which is the point: fifty has to be
        // comfortable rather than borderline.
        val grid = VoxelGrid.build(3_000, 3_000, 400, resolutionMm = res) { i, j, k ->
            when {
                k <= 1 -> Cell.SOLID
                k in 2 until 6 && (i % 6) < 3 && (j % 6) < 3 -> Cell.SOLID
                else -> null
            }
        }

        val started = System.nanoTime()
        val found = ObjectSegmentation.detect(grid)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertEquals(625, found.size, "every separated block should be found")
        assertTrue(elapsedMs < 4_000, "segmentation took $elapsedMs ms")
        println("${found.size} objects segmented in $elapsedMs ms on this JVM")
    }
}
