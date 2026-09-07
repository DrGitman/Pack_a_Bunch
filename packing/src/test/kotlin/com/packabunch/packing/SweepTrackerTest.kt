package com.packabunch.packing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Many objects, one continuous sweep, each finishing on its own schedule.
 */
class SweepTrackerTest {

    private fun obj(
        id: Int,
        widthMm: Int = 330,
        depthMm: Int = 200,
        heightMm: Int = 120,
        xMm: Int = id * 500,
        yMm: Int = 0,
        merged: Boolean = false,
    ) = DetectedObject(
        dimensions = Dimensions(widthMm, depthMm, heightMm),
        yawDegrees = 0,
        cellCount = 500,
        centroidXMm = xMm,
        centroidYMm = yMm,
        restsOnSupport = true,
        toleranceMm = 20,
    ).let { base ->
        if (!merged) base else base.copy(dimensions = Dimensions(1_400, 200, 120))
    }

    @Test
    fun `the same object seen again is not counted twice`() {
        val tracker = SweepTracker()

        tracker.update(listOf(obj(1)), viewDirectionDegrees = 0)
        // Slight drift between frames, as real tracking produces.
        tracker.update(listOf(obj(1, xMm = 520)), viewDirectionDegrees = 45)
        tracker.update(listOf(obj(1, xMm = 495)), viewDirectionDegrees = 90)

        assertEquals(1, tracker.objects().size)
    }

    @Test
    fun `objects far apart stay separate`() {
        val tracker = SweepTracker()

        tracker.update(listOf(obj(1), obj(2), obj(3)), viewDirectionDegrees = 0)

        assertEquals(3, tracker.objects().size)
    }

    @Test
    fun `objects settle at different times in one pass`() {
        val tracker = SweepTracker()

        // Two objects. The first is seen from all around and holds still. The second keeps
        // growing as more of it is revealed, so it is still not finished.
        repeat(6) { step ->
            val settled = obj(1)
            val stillEmerging = obj(2, widthMm = 200 + step * 60)
            tracker.update(listOf(settled, stillEmerging), viewDirectionDegrees = step * 60)
        }

        val objects = tracker.objects()
        assertEquals(2, objects.size)

        val first = objects.first { it.detected.centroidXMm == 500 }
        val second = objects.first { it.detected.centroidXMm == 1_000 }

        assertTrue(first.settled, "a still object seen from all round should finish")
        assertFalse(second.settled, "an object still changing size has not finished")
        assertEquals("Measured", first.waitingFor)
    }

    @Test
    fun `one long stare is not the same as walking round`() {
        val tracker = SweepTracker()

        // Standing still, same direction, plenty of updates. It holds perfectly still —
        // and it still must not settle, because the far side has never been seen.
        repeat(10) { tracker.update(listOf(obj(1)), viewDirectionDegrees = 0) }

        val only = tracker.objects().single()
        assertFalse(only.settled)
        assertEquals("Walk round this one", only.waitingFor)
    }

    @Test
    fun `an object only ever seen from the front knows its depth is unmeasured`() {
        val tracker = SweepTracker()

        repeat(6) { tracker.update(listOf(obj(1)), viewDirectionDegrees = 0) }

        val only = tracker.objects().single()
        assertTrue(Axis.WIDTH in only.progress.observedAxes)
        assertTrue(Axis.HEIGHT in only.progress.observedAxes)
        assertEquals(setOf(Axis.DEPTH), only.progress.unobservedAxes)
    }

    @Test
    fun `a partly seen object fills only the axis nobody saw`() {
        val tracker = SweepTracker()
        repeat(6) { step -> tracker.update(listOf(obj(1)), viewDirectionDegrees = step * 5) }

        val catalogue = SuggestedDimensions(
            dimensions = Dimensions(999, 205, 999),
            toleranceMm = 15,
            sourceNote = "catalogue",
            confidence = SuggestionConfidence.MATCHED_MODEL,
        )
        val measured = tracker.objects().single().measured(catalogue)!!

        // Width and height were seen, so the catalogue's nonsense for those is ignored.
        assertEquals(330, measured.width.millimetres)
        assertEquals(120, measured.height.millimetres)
        // Depth never was, so it is filled and flagged.
        assertEquals(205, measured.depth.millimetres)
        assertTrue(measured.depth.isGuess)
    }

    @Test
    fun `a measurement never shrinks as more of the object is revealed`() {
        val tracker = SweepTracker()

        tracker.update(listOf(obj(1, widthMm = 400)), viewDirectionDegrees = 0)
        // A later frame catches less of it — occlusion, not the box getting smaller.
        tracker.update(listOf(obj(1, widthMm = 250)), viewDirectionDegrees = 90)

        assertEquals(400, tracker.objects().single().detected.dimensions.widthMm)
    }

    @Test
    fun `a stalled merge says what to do about it`() {
        val tracker = SweepTracker()

        repeat(15) { tracker.update(listOf(obj(1, merged = true)), viewDirectionDegrees = 0) }

        val only = tracker.objects().single()
        assertTrue(only.progress.stalled)
        assertTrue(only.waitingFor.contains("move them apart"))
    }

    @Test
    fun `settled objects are listed first because they are the actionable ones`() {
        val tracker = SweepTracker()

        repeat(6) { step ->
            tracker.update(
                listOf(obj(1, widthMm = 200 + step * 60), obj(2)),
                viewDirectionDegrees = step * 60,
            )
        }

        assertTrue(tracker.objects().first().settled)
    }

    @Test
    fun `clearing starts the sweep over`() {
        val tracker = SweepTracker()
        tracker.update(listOf(obj(1), obj(2)), viewDirectionDegrees = 0)

        tracker.clear()

        assertTrue(tracker.objects().isEmpty())
    }
}
