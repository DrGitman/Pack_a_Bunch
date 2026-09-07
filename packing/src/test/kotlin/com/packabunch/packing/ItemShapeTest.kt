package com.packabunch.packing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Shapes that are not boxes, and the nesting that only works if you keep them.
 */
class ItemShapeTest {

    private val res = 50

    /** An L: full along the bottom, and only the left column standing up. */
    private fun lShape(): ItemShape.VoxelMask {
        val cells = buildList {
            for (i in 0 until 4) add(intArrayOf(i, 0, 0))
            for (k in 1 until 4) add(intArrayOf(0, 0, k))
        }
        return ItemShape.VoxelMask.fromCells(cells, res)
    }

    private fun solidBlock(nx: Int, ny: Int, nz: Int): ItemShape.VoxelMask {
        val cells = buildList {
            for (i in 0 until nx) for (j in 0 until ny) for (k in 0 until nz) {
                add(intArrayOf(i, j, k))
            }
        }
        return ItemShape.VoxelMask.fromCells(cells, res)
    }

    @Test
    fun `an L knows it is mostly air`() {
        val shape = lShape()

        assertEquals(Dimensions(200, 50, 200), shape.boundsMm)
        // Seven cells filled out of sixteen in the bounding box.
        assertEquals(7, shape.filledCells)
        assertTrue(shape.fillFraction < 0.5f)
        assertFalse(shape.isBoxLike, "a shape that is half air must not be treated as a box")
    }

    @Test
    fun `a nearly solid shape reports itself as box-like and skips the fine test`() {
        val shape = solidBlock(4, 4, 4)

        assertEquals(1f, shape.fillFraction)
        assertTrue(shape.isBoxLike)
    }

    @Test
    fun `a block sits in an L's notch, which bounding boxes would refuse`() {
        // The case boxes get wrong, and the reason shapes are worth keeping.
        //
        // The L is full along its bottom and up its left side, leaving a 3x3 notch. A block
        // that fits that notch shares most of the L's bounding box while touching none of
        // its material. Pack by boxes and this is rejected; pack by shape and it goes in.
        val l = lShape()
        val block = solidBlock(nx = 3, ny = 1, nz = 3)

        val lBox = Box(0, 0, 0, 200, 50, 200)
        val blockBox = Box(50, 0, 50, 150, 50, 150)

        assertTrue(lBox.overlaps(blockBox), "the fixture only means anything if boxes overlap")
        assertFalse(
            ItemShape.collide(l, lBox, block, blockBox),
            "a block in the notch touches no part of the L",
        )
    }

    @Test
    fun `the same block one cell over does collide`() {
        // Sanity in the other direction: nudge it into the L's upright and it must fail.
        val l = lShape()
        val block = solidBlock(nx = 3, ny = 1, nz = 3)

        assertTrue(
            ItemShape.collide(
                l,
                Box(0, 0, 0, 200, 50, 200),
                block,
                Box(0, 0, 50, 150, 50, 150),
            ),
            "shifted onto the upright, it is a real collision",
        )
    }

    @Test
    fun `overlapping solid shapes still collide`() {
        val a = solidBlock(4, 4, 4)
        val b = solidBlock(4, 4, 4)

        assertTrue(
            ItemShape.collide(a, Box(0, 0, 0, 200, 200, 200), b, Box(100, 0, 0, 200, 200, 200)),
        )
    }

    @Test
    fun `shapes whose boxes are apart never walk a single cell`() {
        val a = lShape()
        val b = lShape()

        assertFalse(
            ItemShape.collide(a, Box(0, 0, 0, 200, 50, 200), b, Box(1_000, 0, 0, 200, 50, 200)),
        )
    }

    @Test
    fun `a quarter turn moves the material without inventing or losing any`() {
        val shape = lShape()
        val turned = shape.rotatedQuarterTurns(1)

        assertEquals(shape.filledCells, turned.filledCells, "rotation must conserve material")
        // Width and depth swap; height is untouched.
        assertEquals(shape.boundsMm.heightMm, turned.boundsMm.heightMm)
        assertEquals(shape.boundsMm.widthMm, turned.boundsMm.depthMm)
    }

    @Test
    fun `four quarter turns come back to where it started`() {
        val shape = lShape()
        val round = shape.rotatedQuarterTurns(4)

        assertEquals(shape.filledCells, round.filledCells)
        assertEquals(shape.boundsMm, round.boundsMm)
    }

    @Test
    fun `a cuboid is always box-like and needs no shape test`() {
        val shape = ItemShape.Cuboid(Dimensions(330, 200, 120))

        assertTrue(shape.isBoxLike)
        assertEquals(330L * 200L * 120L, shape.solidVolumeMm3)
    }

    @Test
    fun `solid volume is the material, not the box around it`() {
        val shape = lShape()

        // Seven cells of 50mm, not the sixteen the bounding box would suggest.
        assertEquals(7L * 50 * 50 * 50, shape.solidVolumeMm3)
        assertTrue(shape.solidVolumeMm3 < shape.boundsMm.volumeMm3)
    }

    @Test
    fun `a shape built from segmentation cells sits at the origin`() {
        // Segmentation reports cells wherever they were found in the scan grid. The mask has
        // to be shifted home, or every item would carry its scan position around with it.
        val offset = listOf(
            intArrayOf(10, 20, 30),
            intArrayOf(11, 20, 30),
            intArrayOf(10, 21, 30),
        )
        val shape = ItemShape.VoxelMask.fromCells(offset, res)

        assertEquals(2, shape.countX)
        assertEquals(2, shape.countY)
        assertEquals(1, shape.countZ)
        assertTrue(shape.isOccupied(0, 0, 0))
        assertTrue(shape.isOccupied(1, 0, 0))
        assertFalse(shape.isOccupied(1, 1, 0))
    }
}
