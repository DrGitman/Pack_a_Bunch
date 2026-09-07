package com.packabunch.packing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Packing real forms rather than the boxes around them.
 *
 * The point of these is that they *fail* if the solver ever goes back to colliding bounding
 * boxes. Shape-aware packing is not a refinement here — it decides whether the pack works.
 */
class ShapedPackingTest {

    private val res = 50

    /** Full along the bottom, and up the left side. Leaves a 3 × 3 notch. */
    private fun lShape(): ItemShape.VoxelMask {
        val cells = buildList {
            for (i in 0 until 4) add(intArrayOf(i, 0, 0))
            for (k in 1 until 4) add(intArrayOf(0, 0, k))
        }
        return ItemShape.VoxelMask.fromCells(cells, res)
    }

    private fun block(nx: Int, ny: Int, nz: Int): ItemShape.VoxelMask {
        val cells = buildList {
            for (i in 0 until nx) for (j in 0 until ny) for (k in 0 until nz) {
                add(intArrayOf(i, j, k))
            }
        }
        return ItemShape.VoxelMask.fromCells(cells, res)
    }

    @Test
    fun `a block goes into an L's notch, which boxes alone would refuse`() {
        // The space holds exactly one 200 x 50 x 200 bounding box and nothing more. If the
        // solver collided boxes, the second item could never be placed at all.
        val space = space(widthMm = 200, depthMm = 50, heightMm = 200)

        val lItem = ItemSpec(
            id = "l",
            name = "L bracket",
            dimensions = lShape().boundsMm,
            shape = lShape(),
        )
        val filler = ItemSpec(
            id = "filler",
            name = "Block",
            dimensions = block(3, 1, 3).boundsMm,
            shape = block(3, 1, 3),
        )

        val plan = solved(space, lItem, filler)

        assertEquals(
            2,
            plan.placements.size,
            "both should fit: the block belongs in the L's notch",
        )
        assertTrue(plan.unplaced.isEmpty())
    }

    @Test
    fun `the same two items as plain boxes do not both fit`() {
        // Identical sizes, no shapes. This is the control: it proves the test above is
        // passing because of the shape work and not because the space was roomy.
        val space = space(widthMm = 200, depthMm = 50, heightMm = 200)

        val asBox = ItemSpec(id = "l", name = "L bracket", dimensions = Dimensions(200, 50, 200))
        val filler = ItemSpec(id = "filler", name = "Block", dimensions = Dimensions(150, 50, 150))

        val plan = solved(space, asBox, filler)

        assertEquals(1, plan.placements.size, "without shapes only one can go in")
        assertEquals(
            UnplacedReason.NO_ROOM_IN_THIS_ARRANGEMENT,
            plan.reasonFor("filler#1"),
        )
    }

    @Test
    fun `modelled fill counts material, not the air inside a bounding box`() {
        val lItem = ItemSpec(
            id = "l",
            name = "L bracket",
            dimensions = lShape().boundsMm,
            shape = lShape(),
        )

        // Seven cells of solid out of a sixteen-cell bounding box.
        assertEquals(7L * res * res * res, lItem.solidVolumeMm3)
        assertTrue(
            lItem.solidVolumeMm3 < lItem.dimensions.volumeMm3,
            "a shaped item must not report its envelope as material",
        )
    }

    @Test
    fun `an item with no measured shape is still treated as a solid box`() {
        // Anything typed or looked up has three numbers and no form. Assuming it fills its
        // box is the conservative reading — inventing a shape would be the dangerous one.
        val typed = ItemSpec(id = "t", name = "Typed", dimensions = Dimensions(330, 200, 120))

        assertTrue(typed.effectiveShape is ItemShape.Cuboid)
        assertEquals(typed.dimensions.volumeMm3, typed.solidVolumeMm3)
    }

    @Test
    fun `shaped packing still produces a plan its own validator accepts`() {
        val space = space(widthMm = 400, depthMm = 200, heightMm = 200)
        val items = listOf(
            ItemSpec(id = "l", name = "L", dimensions = lShape().boundsMm, shape = lShape()),
            ItemSpec(id = "b1", name = "Block", dimensions = block(3, 1, 3).boundsMm, shape = block(3, 1, 3)),
            ItemSpec(id = "b2", name = "Cube", dimensions = Dimensions(100, 100, 100)),
        )
        val request = PackingRequest(space, items)

        val plan = (PackingEngine.solve(request, SolveBudget.unlimited()) as SolveResult.Solved).plan

        // The validator still checks bounds, support and sequence. Shapes must not become a
        // way to smuggle an invalid arrangement past it.
        assertTrue(PlanValidator.validate(request, plan).isValid)
    }
}
