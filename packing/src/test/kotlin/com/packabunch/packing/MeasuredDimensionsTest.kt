package com.packabunch.packing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The box-against-a-wall case, which is most of what people pack.
 */
class MeasuredDimensionsTest {

    private val catalogue = SuggestedDimensions(
        dimensions = Dimensions(330, 200, 120),
        toleranceMm = 15,
        sourceNote = "IKEA SAMLA — published size",
        confidence = SuggestionConfidence.MATCHED_MODEL,
    )

    @Test
    fun `two measured axes and one filled in is reported as exactly that`() {
        // Swept a box pushed against a wall: width and height seen, depth never was.
        val result = MeasuredDimensions.fromPartialScan(
            scanned = Dimensions(348, 0, 126),
            observedAxes = setOf(Axis.WIDTH, Axis.HEIGHT),
            toleranceMm = 20,
            fallback = catalogue,
        )!!

        assertEquals(348, result.width.millimetres)
        assertTrue(result.width.isMeasured)

        assertEquals(126, result.height.millimetres)
        assertTrue(result.height.isMeasured)

        // The one nobody saw comes from the catalogue and says so.
        assertEquals(200, result.depth.millimetres)
        assertTrue(result.depth.isGuess)
        assertTrue(result.depth.note!!.contains("never in view"))

        assertEquals(MeasuredDimensions.Provenance.PARTLY_MEASURED, result.summary)
        assertEquals(listOf("depth"), result.estimatedAxes())
    }

    @Test
    fun `a fully observed object keeps every measured figure`() {
        val result = MeasuredDimensions.fromPartialScan(
            scanned = Dimensions(348, 205, 126),
            observedAxes = setOf(Axis.WIDTH, Axis.DEPTH, Axis.HEIGHT),
            toleranceMm = 20,
            fallback = catalogue,
        )!!

        assertTrue(result.fullyMeasured)
        assertEquals(MeasuredDimensions.Provenance.MEASURED, result.summary)
        assertTrue(result.estimatedAxes().isEmpty())
        // The catalogue is ignored entirely — nothing measured gets overwritten.
        assertEquals(205, result.depth.millimetres)
    }

    @Test
    fun `an unobserved axis with no fallback produces nothing rather than a guess`() {
        val result = MeasuredDimensions.fromPartialScan(
            scanned = Dimensions(348, 0, 126),
            observedAxes = setOf(Axis.WIDTH, Axis.HEIGHT),
            toleranceMm = 20,
            fallback = null,
        )

        assertNull(result, "no fallback means no number, not an invented one")
    }

    @Test
    fun `a box is only as well known as its vaguest edge`() {
        val result = MeasuredDimensions.fromPartialScan(
            scanned = Dimensions(348, 0, 126),
            observedAxes = setOf(Axis.WIDTH, Axis.HEIGHT),
            toleranceMm = 5,
            fallback = catalogue,
        )!!

        // Two axes known to ±5, one to ±15. The object is a ±15 object.
        assertEquals(15, result.toleranceMm)
    }

    @Test
    fun `typed figures carry no uncertainty and read as typed`() {
        val result = MeasuredDimensions.allTyped(Dimensions(330, 200, 120))

        assertEquals(0, result.toleranceMm)
        assertEquals(MeasuredDimensions.Provenance.TYPED, result.summary)
    }

    @Test
    fun `nothing observed at all reads as an estimate, not a measurement`() {
        val result = MeasuredDimensions.fromPartialScan(
            scanned = Dimensions(0, 0, 0),
            observedAxes = emptySet(),
            toleranceMm = 20,
            fallback = catalogue,
        )!!

        assertEquals(MeasuredDimensions.Provenance.ESTIMATED, result.summary)
        assertFalse(result.fullyMeasured)
        assertEquals(3, result.estimatedAxes().size)
    }

    @Test
    fun `the solver only ever receives the numbers`() {
        val result = MeasuredDimensions.fromPartialScan(
            scanned = Dimensions(348, 0, 126),
            observedAxes = setOf(Axis.WIDTH, Axis.HEIGHT),
            toleranceMm = 20,
            fallback = catalogue,
        )!!

        // Provenance is for the person judging the plan, not for the geometry.
        assertEquals(Dimensions(348, 200, 126), result.asDimensions())
    }
}

/**
 * Objects settling independently during one continuous sweep.
 */
class ObservationProgressTest {

    @Test
    fun `an object against a wall still settles on two axes`() {
        // Waiting for all three would mean anything pushed against something never
        // finishes — and that is most of what people pack.
        val progress = ObservationProgress(
            viewpoints = 4,
            observedAxes = setOf(Axis.WIDTH, Axis.HEIGHT),
            stableUpdates = 5,
            lastChangeMm = 2,
        )

        assertTrue(progress.settled)
        assertEquals(setOf(Axis.DEPTH), progress.unobservedAxes)
    }

    @Test
    fun `one glance is not enough however still the box looks`() {
        val progress = ObservationProgress(
            viewpoints = 1,
            observedAxes = setOf(Axis.WIDTH, Axis.HEIGHT, Axis.DEPTH),
            stableUpdates = 9,
            lastChangeMm = 0,
        )

        assertFalse(progress.settled, "a box seen from one angle has an assumed far side")
    }

    @Test
    fun `an object that stops improving is reported as stalled, not left spinning`() {
        // Something is in the way. More sweeping will not help, and the UI should stop
        // implying it will.
        val progress = ObservationProgress(
            viewpoints = 2,
            observedAxes = setOf(Axis.WIDTH),
            stableUpdates = 14,
            lastChangeMm = 1,
        )

        assertFalse(progress.settled)
        assertTrue(progress.stalled)
    }

    @Test
    fun `an object still being revealed is neither settled nor stalled`() {
        val progress = ObservationProgress(
            viewpoints = 2,
            observedAxes = setOf(Axis.WIDTH),
            stableUpdates = 1,
            lastChangeMm = 40,
        )

        assertFalse(progress.settled)
        assertFalse(progress.stalled)
    }
}
