package com.packabunch.packing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScanLabelConsensusTest {
    @Test fun `one wrong label is not shown and repeated new evidence can correct the name`() {
        val names = ScanLabelConsensus()
        assertNull(names.observe("Wheel"))
        assertNull(names.observe("Cup"))
        assertNull(names.observe("Cup"))
        assertEquals("Cup", names.observe("Cup"))
        repeat(5) { names.observe(null) }
        assertNull(names.observe(null))
        repeat(3) { names.observe("Container") }
        assertEquals("Container", names.observe("Container"))
    }
}
