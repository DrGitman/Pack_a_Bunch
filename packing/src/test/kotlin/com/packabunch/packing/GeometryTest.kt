package com.packabunch.packing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeometryTest {

    @Test
    fun `two boxes sharing a face touch but do not overlap`() {
        val left = Box(0, 0, 0, 100, 100, 100)
        val right = Box(100, 0, 0, 100, 100, 100)

        assertFalse(left.overlaps(right))
        assertFalse(right.overlaps(left))
    }

    @Test
    fun `a one millimetre overlap is an overlap`() {
        val left = Box(0, 0, 0, 100, 100, 100)
        val right = Box(99, 0, 0, 100, 100, 100)

        assertTrue(left.overlaps(right))
        assertTrue(right.overlaps(left))
    }

    @Test
    fun `boxes stacked face to face do not overlap`() {
        val lower = Box(0, 0, 0, 100, 100, 40)
        val upper = Box(0, 0, 40, 100, 100, 40)

        assertFalse(lower.overlaps(upper))
        assertTrue(lower.coversFootprintOf(upper))
    }

    @Test
    fun `a footprint hanging over the edge is not covered`() {
        val lower = Box(0, 0, 0, 100, 100, 40)
        val overhanging = Box(50, 0, 40, 100, 100, 40)

        assertFalse(lower.coversFootprintOf(overhanging))
    }

    @Test
    fun `there are exactly six orientations and two of them keep the item upright`() {
        assertEquals(6, Orientation.entries.size)
        assertEquals(2, Orientation.entries.count { it.isUpright })
        assertEquals(2, Orientation.allowedFor(keepUpright = true).size)
        assertEquals(6, Orientation.allowedFor(keepUpright = false).size)
    }

    @Test
    fun `every orientation is a permutation of the same three lengths`() {
        val dimensions = Dimensions(330, 200, 120)
        val expected = dimensions.sortedEdgesMm().toList()

        Orientation.entries.forEach { orientation ->
            val oriented = orientation.apply(dimensions)
            assertEquals(
                expected,
                oriented.sortedEdgesMm().toList(),
                "$orientation changed the item's lengths",
            )
            assertEquals(
                dimensions.volumeMm3,
                oriented.volumeMm3,
                "$orientation changed the item's volume",
            )
        }
    }

    @Test
    fun `volume of the largest accepted box does not overflow`() {
        val huge = Dimensions(MAX_LENGTH_MM, MAX_LENGTH_MM, MAX_LENGTH_MM)

        // 50 m cubed is 1.25e14 mm3 — comfortably past Int.MAX_VALUE, which is the whole
        // reason lengths are Int but volumes are Long.
        assertEquals(125_000_000_000_000L, huge.volumeMm3)
        assertTrue(huge.volumeMm3 > Int.MAX_VALUE.toLong())
    }

    @Test
    fun `dimensions outside the accepted range are rejected`() {
        assertFalse(Dimensions(0, 100, 100).isValid())
        assertFalse(Dimensions(-1, 100, 100).isValid())
        assertFalse(Dimensions(100, 0, 100).isValid())
        assertFalse(Dimensions(100, 100, 0).isValid())
        assertFalse(Dimensions(MAX_LENGTH_MM + 1, 100, 100).isValid())
        assertTrue(Dimensions(1, 1, 1).isValid())
    }

    @Test
    fun `the edge gap insets four sides and the top but never the floor`() {
        val usable = space(1000, 800, 600, edgeGapMm = 10).usableBox

        assertEquals(10, usable.minXMm)
        assertEquals(10, usable.minYMm)
        assertEquals(0, usable.minZMm, "items rest on the floor, so it is not inset")
        assertEquals(980, usable.widthMm)
        assertEquals(780, usable.depthMm)
        assertEquals(590, usable.heightMm)
    }
}
