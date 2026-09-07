package com.packabunch.packing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The precedence rules, pinned down.
 *
 * These are the tests that stop somebody "improving" the resolver later by preferring the
 * precise-looking catalogue figure over the scan. That change would look like an upgrade
 * and would quietly make the app worse.
 */
class DimensionResolverTest {

    private fun scan(
        widthMm: Int = 330,
        depthMm: Int = 200,
        heightMm: Int = 120,
        toleranceMm: Int = 20,
        merged: Boolean = false,
        onSupport: Boolean = true,
    ) = DetectedObject(
        dimensions = Dimensions(widthMm, depthMm, heightMm),
        yawDegrees = 0,
        cellCount = 400,
        centroidXMm = 0,
        centroidYMm = 0,
        restsOnSupport = onSupport,
        toleranceMm = toleranceMm,
    )

    private fun suggestion(
        widthMm: Int = 330,
        depthMm: Int = 200,
        heightMm: Int = 120,
        toleranceMm: Int = 10,
        note: String = "IKEA SAMLA — published size",
    ) = SuggestedDimensions(
        dimensions = Dimensions(widthMm, depthMm, heightMm),
        toleranceMm = toleranceMm,
        sourceNote = note,
        confidence = SuggestionConfidence.EXACT_PRODUCT,
    )

    @Test
    fun `a clean scan is used on its own and nothing else is consulted`() {
        val resolved = DimensionResolver.resolve(scanned = scan(), suggestion = null)

        assertEquals(Dimensions(330, 200, 120), resolved.dimensions)
        assertEquals(MeasurementSource.CAMERA_ESTIMATE, resolved.source)
        assertFalse(resolved.needsConfirming)
    }

    @Test
    fun `what the person types beats everything`() {
        val resolved = DimensionResolver.resolve(
            scanned = scan(widthMm = 999),
            suggestion = suggestion(widthMm = 111),
            typed = Dimensions(330, 200, 120),
        )

        assertEquals(Dimensions(330, 200, 120), resolved.dimensions)
        assertEquals(MeasurementSource.TYPED_IN, resolved.source)
        assertFalse(resolved.needsConfirming)
    }

    @Test
    fun `when the scan and a catalogue disagree, the scan wins`() {
        // The catalogue describes a different instance off a production line. The scan
        // measured the one on the floor, lid and all.
        val resolved = DimensionResolver.resolve(
            scanned = scan(widthMm = 348),
            suggestion = suggestion(widthMm = 300),
        )

        assertEquals(348, resolved.dimensions?.widthMm)
        assertEquals(MeasurementSource.CAMERA_ESTIMATE, resolved.source)
        assertTrue(resolved.needsConfirming, "a disagreement is worth telling the user about")
        assertTrue(resolved.note.contains("catalogue"), "the note should explain the difference")
    }

    @Test
    fun `a catalogue only sharpens a figure when it agrees with the scan`() {
        val resolved = DimensionResolver.resolve(
            scanned = scan(toleranceMm = 20),
            suggestion = suggestion(toleranceMm = 5),
        )

        assertEquals(MeasurementSource.SUGGESTED, resolved.source)
        assertEquals(5, resolved.toleranceMm)
    }

    @Test
    fun `a merged blob falls back to the catalogue`() {
        // Two things touched, so the scan measured them as one. This is exactly the case
        // the lookup exists for.
        val resolved = DimensionResolver.resolve(
            scanned = scan(widthMm = 1_200, merged = true),
            suggestion = suggestion(),
        )

        assertEquals(MeasurementSource.SUGGESTED, resolved.source)
        assertEquals(Dimensions(330, 200, 120), resolved.dimensions)
        assertTrue(resolved.note.contains("couldn't tell this apart"))
    }

    @Test
    fun `a coarse scan falls back to the catalogue`() {
        val resolved = DimensionResolver.resolve(
            scanned = scan(toleranceMm = 60),
            suggestion = suggestion(),
        )

        assertEquals(MeasurementSource.SUGGESTED, resolved.source)
        assertTrue(resolved.note.contains("too coarse"))
    }

    @Test
    fun `nothing measured and nothing found means no invented number`() {
        val resolved = DimensionResolver.resolve(scanned = null, suggestion = null)

        assertEquals(null, resolved.dimensions)
        assertEquals(null, resolved.source)
        assertFalse(resolved.hasAnswer)
        assertTrue(resolved.needsConfirming)
    }

    @Test
    fun `an item lying the other way round is not called a disagreement`() {
        // Same box, different orientation. Comparing width-to-width would wrongly report a
        // mismatch and drag the user into confirming something that already agrees.
        val resolved = DimensionResolver.resolve(
            scanned = scan(widthMm = 200, depthMm = 330, heightMm = 120, toleranceMm = 20),
            suggestion = suggestion(widthMm = 330, depthMm = 200, heightMm = 120, toleranceMm = 5),
        )

        assertEquals(MeasurementSource.SUGGESTED, resolved.source)
        assertFalse(
            resolved.note.contains("measured differently"),
            "a rotated box is the same box",
        )
    }

    @Test
    fun `uncertainty only matters against the room actually available`() {
        val resolved = DimensionResolver.resolve(
            scanned = scan(toleranceMm = 20),
            suggestion = suggestion(toleranceMm = 20),
        )

        // Twenty millimetres of doubt is decisive in a snug crate and irrelevant in a boot.
        assertTrue(resolved.needsAttention(clearanceMm = 5))
        assertFalse(resolved.needsAttention(clearanceMm = 100))
    }

    @Test
    fun `a floating blob is not trusted even when it looks precise`() {
        // Not resting on the surface usually means the segmentation went wrong.
        val resolved = DimensionResolver.resolve(
            scanned = scan(toleranceMm = 5, onSupport = false),
            suggestion = suggestion(),
        )

        assertEquals(MeasurementSource.SUGGESTED, resolved.source)
        assertTrue(resolved.note.contains("wasn't sure"))
    }
}
