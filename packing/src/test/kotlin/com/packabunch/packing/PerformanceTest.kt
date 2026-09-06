package com.packabunch.packing

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A guard rail, not the benchmark. The number that matters is the one measured on the
 * actual mid-range test phone — a desktop JVM is several times faster and proves nothing
 * about the device. This exists to catch an accidental blow-up in the search, so its bound
 * is deliberately loose.
 */
class PerformanceTest {

    @Test
    fun `the supported twenty piece case solves well inside the time budget`() {
        val request = PackingRequest(
            space = space(584, 396, 350), // the crate from the artboards, in mm
            items = listOf(
                item("toolbox", 330, 200, 120),
                item("shoebox", 330, 200, 120, quantity = 4),
                item("game", 270, 270, 55, quantity = 3),
                item("kettle", 220, 160, 240, keepUpright = true),
                item("stove", 360, 300, 110),
                item("books", 240, 160, 200, quantity = 5),
                item("lamp", 140, 140, 300, keepUpright = true, maySupportItems = false),
                item("cables", 180, 120, 90, quantity = 4),
            ),
        )
        require(request.expandInstances().size == PackingEngine.BENCHMARK_INSTANCE_COUNT) {
            "this fixture is meant to be exactly ${PackingEngine.BENCHMARK_INSTANCE_COUNT} pieces"
        }

        val startNanos = System.nanoTime()
        val result = PackingEngine.solve(request, SolveBudget(timeBudgetMillis = 2_000L))
        val elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L

        val plan = (result as SolveResult.Solved).plan
        assertTrue(PlanValidator.validate(request, plan).isValid, "${plan.strategy} produced an invalid plan")
        assertTrue(
            !plan.stoppedOnTimeBudget,
            "the twenty piece case hit its time budget after $elapsedMillis ms",
        )
        assertTrue(
            elapsedMillis < 2_000L,
            "twenty pieces took $elapsedMillis ms on the JVM; check the device before shipping",
        )

        println(
            "20 pieces: ${plan.placements.size} placed, " +
                "${plan.metrics.modelledFillPercent}% modelled fill, " +
                "${plan.strategy}, ${elapsedMillis} ms on this JVM",
        )
    }
}
