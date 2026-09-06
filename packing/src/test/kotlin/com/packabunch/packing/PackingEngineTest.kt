package com.packabunch.packing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The fixtures the build plan asks for, before any visual work. Each one is a claim about
 * what the engine will and will not say.
 */
class PackingEngineTest {

    // -- fitting ---------------------------------------------------------------------------

    @Test
    fun `one item that fits is placed at the origin`() {
        val plan = solved(space(600, 400, 350), item("box", 300, 200, 150))

        assertEquals(1, plan.placements.size)
        val placed = plan.placementOf("box#1")
        assertEquals(0, placed.xMm)
        assertEquals(0, placed.yMm)
        assertEquals(0, placed.zMm)
        assertTrue(plan.unplaced.isEmpty())
    }

    @Test
    fun `an item exactly the size of the space fits`() {
        val plan = solved(space(600, 400, 350), item("snug", 600, 400, 350))

        assertEquals(1, plan.placements.size)
        assertEquals(100, plan.metrics.modelledFillPercent)
    }

    @Test
    fun `an edge gap turns an exact fit into an item larger than the space`() {
        val plan = solved(space(600, 400, 350, edgeGapMm = 1), item("snug", 600, 400, 350))

        assertTrue(plan.placements.isEmpty())
        assertEquals(UnplacedReason.LARGER_THAN_THE_SPACE, plan.reasonFor("snug#1"))
    }

    @Test
    fun `an item bigger than the space is reported as bigger than the space, not as no room`() {
        val plan = solved(space(600, 400, 350), item("wardrobe", 2000, 600, 500))

        assertEquals(UnplacedReason.LARGER_THAN_THE_SPACE, plan.reasonFor("wardrobe#1"))
    }

    // -- rotation ---------------------------------------------------------------------------

    @Test
    fun `an item that only fits turned is turned`() {
        // 250 long will not go across a 100 mm width, but it will go along a 300 mm depth.
        val plan = solved(space(100, 300, 100), item("plank", 250, 80, 50))

        val placed = plan.placementOf("plank#1")
        assertEquals(80, placed.orientedWidthMm)
        assertEquals(250, placed.orientedDepthMm)
    }

    @Test
    fun `keep upright refuses the pose that would otherwise make it fit`() {
        // The only pose that fits this narrow, tall space lays the item on its end.
        val laidDown = solved(space(100, 100, 300), item("lamp", 250, 80, 50, keepUpright = false))
        assertEquals(1, laidDown.placements.size)
        assertTrue(!laidDown.placementOf("lamp#1").orientation.isUpright)

        val upright = solved(space(100, 100, 300), item("lamp", 250, 80, 50, keepUpright = true))
        assertTrue(upright.placements.isEmpty())
        assertEquals(UnplacedReason.LARGER_THAN_THE_SPACE, upright.reasonFor("lamp#1"))
    }

    // -- quantities --------------------------------------------------------------------------

    @Test
    fun `quantity becomes distinct instances that place independently`() {
        val plan = solved(space(600, 200, 200), item("shoebox", 200, 200, 200, quantity = 3))

        assertEquals(3, plan.placements.size)
        assertEquals(
            setOf("shoebox#1", "shoebox#2", "shoebox#3"),
            plan.placements.map { it.instanceId }.toSet(),
        )
        assertEquals(3, plan.metrics.placedInstanceCount)
        assertEquals(3, plan.metrics.requestedInstanceCount)
    }

    // -- support ------------------------------------------------------------------------------

    @Test
    fun `items stack when the space is taller than it is wide`() {
        val plan = solved(space(200, 200, 400), item("crate", 200, 200, 200, quantity = 2))

        assertEquals(2, plan.placements.size)
        val zs = plan.placements.map { it.zMm }.sorted()
        assertEquals(listOf(0, 200), zs)
    }

    @Test
    fun `nothing is stacked on an item marked nothing on top`() {
        val plan = solved(
            space(200, 200, 400),
            item("fragile", 200, 200, 200, maySupportItems = false),
            item("heavy", 200, 200, 200),
        )

        // Whichever goes down first, the fragile one cannot end up underneath the other.
        val fragile = plan.placements.firstOrNull { it.instanceId == "fragile#1" }
        val heavy = plan.placements.firstOrNull { it.instanceId == "heavy#1" }
        if (fragile != null && heavy != null) {
            assertNotEquals(
                fragile.box.maxZMm,
                heavy.zMm,
                "heavy was stacked on top of an item marked nothing-on-top",
            )
        }
        assertTrue(plan.placements.isNotEmpty())
    }

    @Test
    fun `the packing order never puts an item before the one it rests on`() {
        val plan = solved(space(200, 200, 600), item("crate", 200, 200, 200, quantity = 3))

        val bySequence = plan.placements.sortedBy { it.sequenceIndex }
        assertEquals(listOf(0, 200, 400), bySequence.map { it.zMm })
        assertEquals(listOf(0, 1, 2), bySequence.map { it.sequenceIndex })
    }

    // -- honest failure --------------------------------------------------------------------------

    @Test
    fun `enough total volume does not mean the shapes fit`() {
        // Two 60 mm cubes are 43% of a 100 mm cube by volume, and yet only one goes in.
        val plan = solved(space(100, 100, 100), item("cube", 60, 60, 60, quantity = 2))

        assertEquals(1, plan.placements.size)
        assertEquals(UnplacedReason.NO_ROOM_IN_THIS_ARRANGEMENT, plan.reasonFor("cube#2"))
        assertTrue(
            plan.metrics.placedVolumeMm3 * 2 < plan.metrics.usableVolumeMm3,
            "the fixture is only interesting while the volume genuinely is available",
        )
    }

    @Test
    fun `an ordering that beats the obvious one is the reason several are tried`() {
        // Largest-first puts the wide flat item on the floor and then has nowhere to stand
        // the two tall ones. Tallest-first stands them side by side and places two pieces.
        val plan = solved(
            space(100, 50, 100),
            item("flat", 100, 50, 60),
            item("tall", 50, 50, 95, quantity = 2),
        )

        assertEquals(2, plan.placements.size)
        assertEquals("tallest first", plan.strategy)
        assertEquals(UnplacedReason.NO_ROOM_IN_THIS_ARRANGEMENT, plan.reasonFor("flat#1"))
    }

    // -- metrics -----------------------------------------------------------------------------------

    @Test
    fun `modelled fill is placed envelope volume over usable volume`() {
        val plan = solved(space(200, 200, 200), item("half", 200, 200, 100))

        assertEquals(4_000_000L, plan.metrics.placedVolumeMm3)
        assertEquals(8_000_000L, plan.metrics.usableVolumeMm3)
        assertEquals(50, plan.metrics.modelledFillPercent)
        assertEquals(4_000_000L, plan.metrics.geometricEmptyVolumeMm3)
    }

    @Test
    fun `modelled fill is zero rather than a division by zero when nothing is placed`() {
        val plan = solved(space(100, 100, 100), item("huge", 5000, 5000, 5000))

        assertEquals(0, plan.metrics.modelledFillPercent)
        assertEquals(1, plan.metrics.unplacedInstanceCount)
    }

    // -- input ---------------------------------------------------------------------------------------

    @Test
    fun `zero and negative measurements are refused per field rather than solved around`() {
        val zero = problems(space(600, 400, 350), item("bad", 0, 100, 100))
        assertEquals(listOf("item:bad"), zero.map { it.field })

        val negative = problems(space(600, 400, 350), item("bad", -5, 100, 100))
        assertEquals(listOf("item:bad"), negative.map { it.field })

        val badSpace = problems(space(0, 400, 350), item("fine", 100, 100, 100))
        assertEquals(listOf("space"), badSpace.map { it.field })
    }

    @Test
    fun `an edge gap that swallows the space is refused`() {
        val found = problems(space(100, 100, 100, edgeGapMm = 60), item("fine", 10, 10, 10))

        assertEquals(listOf("space.edgeGap"), found.map { it.field })
    }

    @Test
    fun `quantity outside the accepted range is refused`() {
        assertEquals(
            listOf("item:x.quantity"),
            problems(space(600, 400, 350), item("x", 10, 10, 10, quantity = 0)).map { it.field },
        )
    }

    @Test
    fun `past the searchable ceiling is refused, not silently truncated`() {
        val found = problems(
            space(6000, 4000, 3500),
            item("many", 10, 10, 10, quantity = 199),
            item("more", 10, 10, 10, quantity = 199),
            item("yetmore", 10, 10, 10, quantity = 199),
        )

        assertEquals(listOf("items"), found.map { it.field })
    }

    @Test
    fun `the old twenty piece cap is gone from the engine`() {
        // Thirty pieces used to be refused outright. The engine's only limit now is what it
        // can search; anything narrower is a product decision and lives in TierLimits.
        val plan = solved(
            space(1200, 800, 800),
            item("crate", 180, 180, 180, quantity = 30),
        )

        assertEquals(30, plan.metrics.requestedInstanceCount)
        assertTrue(plan.placements.size > PackingEngine.BENCHMARK_INSTANCE_COUNT)
    }

    // -- determinism and budget -------------------------------------------------------------------------

    @Test
    fun `the same inputs give byte for byte the same plan`() {
        val request = PackingRequest(
            space = space(580, 396, 350),
            items = listOf(
                item("toolbox", 330, 200, 120),
                item("shoebox", 330, 200, 120, quantity = 3),
                item("game", 270, 270, 55, quantity = 2),
                item("kettle", 220, 160, 240, keepUpright = true),
            ),
        )

        val first = PackingEngine.solve(request, SolveBudget.unlimited())
        val second = PackingEngine.solve(request, SolveBudget.unlimited())

        assertEquals(first, second)
    }

    @Test
    fun `a cancelled solve returns an empty plan and says why, rather than a partial guess`() {
        val request = PackingRequest(space(600, 400, 350), listOf(item("box", 100, 100, 100)))

        val result = PackingEngine.solve(request, SolveBudget.exhausted())
        val plan = (result as SolveResult.Solved).plan

        assertTrue(plan.placements.isEmpty())
        assertTrue(plan.stoppedOnTimeBudget)
        assertEquals(UnplacedReason.TIME_BUDGET_REACHED, plan.reasonFor("box#1"))
        assertTrue(PlanValidator.validate(request, plan).isValid)
    }

    // -- staleness --------------------------------------------------------------------------------------

    @Test
    fun `editing a dimension changes the input revision so the old plan is detectably stale`() {
        val before = PackingRequest(space(600, 400, 350), listOf(item("box", 100, 100, 100)))
        val afterItemEdit = PackingRequest(space(600, 400, 350), listOf(item("box", 101, 100, 100)))
        val afterSpaceEdit = PackingRequest(space(601, 400, 350), listOf(item("box", 100, 100, 100)))
        val afterGapEdit = PackingRequest(
            space(600, 400, 350, edgeGapMm = 5),
            listOf(item("box", 100, 100, 100)),
        )
        val afterUprightEdit = PackingRequest(
            space(600, 400, 350),
            listOf(item("box", 100, 100, 100, keepUpright = true)),
        )

        val original = before.revision()
        assertEquals(original, before.revision(), "the fingerprint has to be stable")
        assertNotEquals(original, afterItemEdit.revision())
        assertNotEquals(original, afterSpaceEdit.revision())
        assertNotEquals(original, afterGapEdit.revision())
        assertNotEquals(original, afterUprightEdit.revision())
    }

    @Test
    fun `the revision ignores the order items were entered in`() {
        val a = item("a", 100, 100, 100)
        val b = item("b", 200, 200, 200)

        assertEquals(
            PackingRequest(space(600, 400, 350), listOf(a, b)).revision(),
            PackingRequest(space(600, 400, 350), listOf(b, a)).revision(),
        )
    }
}
