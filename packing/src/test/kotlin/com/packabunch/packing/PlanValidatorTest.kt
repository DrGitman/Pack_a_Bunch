package com.packabunch.packing

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The validator is the thing standing between a heuristic bug and a user believing an
 * arrangement they cannot actually build. These tests break a good plan on purpose and
 * check it notices.
 */
class PlanValidatorTest {

    // Narrow and tall, so the engine is forced to stack — several of these tests are about
    // the support and ordering rules, which only exist once one item rests on another.
    private val theSpace = space(200, 200, 600)
    private val theItems = listOf(item("crate", 200, 200, 200, quantity = 2))
    private val request = PackingRequest(theSpace, theItems)

    private fun goodPlan(): PackingPlan =
        (PackingEngine.solve(request, SolveBudget.unlimited()) as SolveResult.Solved).plan

    private fun codesFor(plan: PackingPlan): Set<PlanValidator.Code> =
        PlanValidator.validate(request, plan).violations.mapTo(mutableSetOf()) { it.code }

    @Test
    fun `a plan straight from the engine passes`() {
        assertTrue(PlanValidator.validate(request, goodPlan()).isValid)
    }

    @Test
    fun `an overlap is caught`() {
        val plan = goodPlan()
        val moved = plan.placements[1].copy(
            xMm = plan.placements[0].xMm,
            yMm = plan.placements[0].yMm,
            zMm = plan.placements[0].zMm,
        )
        val broken = plan.copy(placements = listOf(plan.placements[0], moved))

        assertTrue(PlanValidator.Code.OVERLAP in codesFor(broken))
    }

    @Test
    fun `an item poking through a wall is caught`() {
        val plan = goodPlan()
        val broken = plan.copy(
            placements = plan.placements.mapIndexed { index, placement ->
                if (index == 0) placement.copy(xMm = 300) else placement
            },
        )

        assertTrue(PlanValidator.Code.OUT_OF_BOUNDS in codesFor(broken))
    }

    @Test
    fun `an item floating in mid air is caught`() {
        val plan = goodPlan()
        val broken = plan.copy(
            placements = plan.placements.mapIndexed { index, placement ->
                // 350 is inside the space but is not the floor and is not the top of anything.
                if (index == 0) placement.copy(zMm = 350) else placement
            },
        )

        assertTrue(PlanValidator.Code.UNSUPPORTED in codesFor(broken))
    }

    @Test
    fun `a stored size that is not the stated orientation is caught`() {
        val plan = goodPlan()
        val broken = plan.copy(
            placements = plan.placements.mapIndexed { index, placement ->
                if (index == 0) placement.copy(orientedWidthMm = 199) else placement
            },
        )

        assertTrue(PlanValidator.Code.WRONG_ORIENTED_SIZE in codesFor(broken))
    }

    @Test
    fun `a keep-upright item laid on its side is caught`() {
        val uprightRequest = PackingRequest(
            space(400, 400, 400),
            listOf(item("lamp", 200, 100, 300, keepUpright = true)),
        )
        val plan = (PackingEngine.solve(uprightRequest, SolveBudget.unlimited()) as SolveResult.Solved).plan
        val broken = plan.copy(
            placements = plan.placements.map {
                it.copy(
                    orientation = Orientation.WIDTH_HEIGHT_DEPTH,
                    orientedWidthMm = 200,
                    orientedDepthMm = 300,
                    orientedHeightMm = 100,
                )
            },
        )

        val codes = PlanValidator.validate(uprightRequest, broken).violations.map { it.code }
        assertTrue(PlanValidator.Code.UPRIGHT_RULE_BROKEN in codes)
    }

    @Test
    fun `stacking on a nothing-on-top item is caught`() {
        val fragileRequest = PackingRequest(
            space(200, 200, 400),
            listOf(
                item("fragile", 200, 200, 200, maySupportItems = false),
                item("heavy", 200, 200, 200),
            ),
        )
        // Hand-built, because the engine correctly refuses to produce this arrangement.
        val stacked = PackingPlan(
            spaceId = "space",
            inputRevision = fragileRequest.revision(),
            solverVersion = PackingEngine.SOLVER_VERSION,
            placements = listOf(
                Placement(
                    "fragile#1", "fragile", 0, 0, 0, 200, 200, 200,
                    Orientation.WIDTH_DEPTH_HEIGHT, 0,
                ),
                Placement(
                    "heavy#1", "heavy", 0, 0, 200, 200, 200, 200,
                    Orientation.WIDTH_DEPTH_HEIGHT, 1,
                ),
            ),
            unplaced = emptyList(),
            metrics = PackingMetrics(2, 2, 16_000_000L, 16_000_000L, 16_000_000L),
            strategy = "hand built",
            stoppedOnTimeBudget = false,
        )

        val codes = PlanValidator.validate(fragileRequest, stacked).violations.map { it.code }
        assertTrue(PlanValidator.Code.SUPPORTER_FORBIDS_STACKING in codes)
    }

    @Test
    fun `an item packed before the one it rests on is caught`() {
        val plan = goodPlan()
        // The engine returns these bottom-up; reversing the indices inverts that.
        val last = plan.placements.size - 1
        val broken = plan.copy(
            placements = plan.placements.map { it.copy(sequenceIndex = last - it.sequenceIndex) },
        )

        assertTrue(PlanValidator.Code.BAD_SEQUENCE in codesFor(broken))
    }

    @Test
    fun `an instance that is neither placed nor listed as unplaced is caught`() {
        val plan = goodPlan()
        val broken = plan.copy(placements = plan.placements.drop(1))

        assertTrue(PlanValidator.Code.INSTANCE_UNACCOUNTED_FOR in codesFor(broken))
    }

    @Test
    fun `metrics that do not match the placements are caught`() {
        val plan = goodPlan()
        val broken = plan.copy(
            metrics = plan.metrics.copy(placedVolumeMm3 = plan.metrics.usableVolumeMm3),
        )

        assertTrue(PlanValidator.Code.METRICS_MISMATCH in codesFor(broken))
    }
}
