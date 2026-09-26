package com.packabunch.ar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Joining what the detector saw to what the depth grid measured.
 *
 * This is the seam where a name earns its way onto an item, so the interesting cases are all
 * about refusing to guess: overlapping neighbours, a big box sitting behind a small one, and
 * anything the labeller has not got to yet.
 */
class NameMatchingTest {

    /** A screen-space box as eight projected corners, base first then top. */
    private fun overlay(id: String, left: Float, top: Float, right: Float, bottom: Float) =
        ObjectOverlay(
            id = id,
            settled = true,
            corners = listOf(
                left to bottom, right to bottom, right to top, left to top,
                left to bottom, right to bottom, right to top, left to top,
            ),
        )

    private fun found(trackingId: Int, left: Float, top: Float, right: Float, bottom: Float) =
        FoundObject(trackingId, left, top, right, bottom, label = null)

    private fun namerWith(vararg names: Pair<Int, String>): ObjectNamer {
        val namer = ObjectNamer(com.packabunch.data.catalogue.ItemRecogniser())
        names.forEach { (id, name) -> namer.remember(id, name) }
        return namer
    }

    @Test
    fun `a measurement takes the name of the box it overlaps`() {
        val names = matchNames(
            projected = listOf(overlay("swept-1", 0.1f, 0.1f, 0.4f, 0.5f)),
            found = listOf(found(7, 0.12f, 0.12f, 0.38f, 0.48f)),
            namer = namerWith(7 to "Coffee cup"),
        )

        assertEquals(mapOf("swept-1" to "Coffee cup"), names)
    }

    @Test
    fun `a measurement with no overlapping detection stays unnamed`() {
        val names = matchNames(
            projected = listOf(overlay("swept-1", 0.1f, 0.1f, 0.3f, 0.3f)),
            found = listOf(found(7, 0.6f, 0.6f, 0.9f, 0.9f)),
            namer = namerWith(7 to "Coffee cup"),
        )

        assertTrue(names.isEmpty(), "borrowing a distant object's name is worse than no name")
    }

    @Test
    fun `a large box behind a small one does not swallow it`() {
        // The carton fills most of the frame; the cup sits inside its bounds on screen.
        val names = matchNames(
            projected = listOf(overlay("swept-cup", 0.20f, 0.30f, 0.30f, 0.60f)),
            found = listOf(
                found(1, 0.05f, 0.05f, 0.95f, 0.95f),   // the carton, much larger
                found(2, 0.19f, 0.29f, 0.31f, 0.61f),   // the cup itself
            ),
            namer = namerWith(1 to "Box", 2 to "Coffee cup"),
        )

        assertEquals(
            "Coffee cup",
            names["swept-cup"],
            "overlap is measured against the smaller box so the nearer fit wins",
        )
    }

    @Test
    fun `a barely touching detection is not a match`() {
        val names = matchNames(
            projected = listOf(overlay("swept-1", 0.10f, 0.10f, 0.50f, 0.50f)),
            // Clips one corner only — well under the threshold.
            found = listOf(found(7, 0.45f, 0.45f, 0.90f, 0.90f)),
            namer = namerWith(7 to "Coffee cup"),
        )

        assertNull(names["swept-1"])
    }

    @Test
    fun `an unlabelled detection contributes no name`() {
        val names = matchNames(
            projected = listOf(overlay("swept-1", 0.1f, 0.1f, 0.4f, 0.5f)),
            found = listOf(found(7, 0.12f, 0.12f, 0.38f, 0.48f)),
            namer = namerWith(),
        )

        assertTrue(names.isEmpty(), "labelling lags detection and must not block the measurement")
    }
}
