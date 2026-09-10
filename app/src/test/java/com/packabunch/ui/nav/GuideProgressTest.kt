package com.packabunch.ui.nav

import com.packabunch.packing.Placement
import com.packabunch.packing.Orientation
import org.junit.Test
import org.junit.Assert.assertEquals

class GuideProgressTest {
    private fun piece(id: String, order: Int) = Placement(id, id, 0, 0, 0, 10, 10, 10, Orientation.entries.first(), order)

    @Test fun `resume uses support sequence rather than collection order or packed count`() {
        val pieces = listOf(piece("top", 2), piece("floor", 0), piece("middle", 1))
        assertEquals(0, nextPackingStep(pieces, setOf("middle")))
        assertEquals(1, nextPackingStep(pieces, setOf("floor", "deleted-item")))
        assertEquals(2, nextPackingStep(pieces, setOf("floor", "middle")))
    }

    @Test fun `complete and empty plans have a valid final index`() {
        assertEquals(0, nextPackingStep(emptyList(), emptySet()))
        assertEquals(1, nextPackingStep(listOf(piece("a", 0), piece("b", 1)), setOf("a", "b")))
    }
}
