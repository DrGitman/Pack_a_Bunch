package com.packabunch.packing

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Randomised, but seeded — a failure here is reproducible from the seed printed in the
 * message. The hand-written fixtures say what the engine should do on cases we thought of;
 * this says what must hold on cases we did not.
 */
class InvariantTest {

    @Test
    fun `every plan the engine returns passes its own validator`() {
        repeat(400) { iteration ->
            val seed = 20260906L + iteration
            val random = Random(seed)
            val request = randomRequest(random)

            when (val result = PackingEngine.solve(request, SolveBudget.unlimited())) {
                is SolveResult.InvalidInput ->
                    error("seed $seed produced valid-looking input the engine refused: $result")

                is SolveResult.Solved -> {
                    val validation = PlanValidator.validate(request, result.plan)
                    assertTrue(
                        validation.isValid,
                        "seed $seed: ${validation.violations}",
                    )
                }
            }
        }
    }

    @Test
    fun `every requested piece is either placed or explained, never dropped`() {
        repeat(200) { iteration ->
            val seed = 771_000L + iteration
            val request = randomRequest(Random(seed))
            val plan = (PackingEngine.solve(request, SolveBudget.unlimited()) as SolveResult.Solved).plan

            val requested = request.expandInstances().map { it.instanceId }.toSet()
            val accountedFor = plan.placements.map { it.instanceId }.toSet() +
                plan.unplaced.map { it.instanceId }.toSet()

            assertEquals(requested, accountedFor, "seed $seed")
            assertEquals(
                requested.size,
                plan.metrics.requestedInstanceCount,
                "seed $seed",
            )
        }
    }

    @Test
    fun `modelled fill never exceeds the space it is measured against`() {
        repeat(200) { iteration ->
            val seed = 990_000L + iteration
            val request = randomRequest(Random(seed))
            val plan = (PackingEngine.solve(request, SolveBudget.unlimited()) as SolveResult.Solved).plan

            assertTrue(
                plan.metrics.placedVolumeMm3 <= plan.metrics.usableVolumeMm3,
                "seed $seed placed ${plan.metrics.placedVolumeMm3} mm3 into " +
                    "${plan.metrics.usableVolumeMm3} mm3",
            )
            assertTrue(plan.metrics.modelledFillPercent in 0..100, "seed $seed")
            assertTrue(plan.metrics.geometricEmptyVolumeMm3 >= 0L, "seed $seed")
        }
    }

    @Test
    fun `solving is deterministic across repeated runs of the same random inputs`() {
        repeat(50) { iteration ->
            val seed = 31_000L + iteration
            val request = randomRequest(Random(seed))

            val first = PackingEngine.solve(request, SolveBudget.unlimited())
            val second = PackingEngine.solve(request, SolveBudget.unlimited())

            assertEquals(first, second, "seed $seed")
        }
    }

    private fun randomRequest(random: Random): PackingRequest {
        val space = Space(
            id = "space",
            name = "Random space",
            dimensions = Dimensions(
                widthMm = random.nextInt(200, 1200),
                depthMm = random.nextInt(200, 900),
                heightMm = random.nextInt(200, 900),
            ),
            edgeGapMm = if (random.nextInt(4) == 0) random.nextInt(1, 20) else 0,
        )

        val items = (1..random.nextInt(1, 9)).map { index ->
            ItemSpec(
                id = "i$index",
                name = "Item $index",
                dimensions = Dimensions(
                    widthMm = random.nextInt(20, 500),
                    depthMm = random.nextInt(20, 500),
                    heightMm = random.nextInt(20, 500),
                ),
                quantity = random.nextInt(1, 4),
                keepUpright = random.nextInt(3) == 0,
                maySupportItems = random.nextInt(4) != 0,
            )
        }

        return PackingRequest(space, items)
    }
}
