package com.packabunch.packing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TwoViewSizeTest {

    @Test
    fun `front gives width and height, depth waits for a side view`() {
        val s = TwoViewSize()
        s.add(10f, 200f, 150f)
        s.add(15f, 204f, 148f)

        assertEquals(204f, s.widthMm)
        assertNull(s.depthMm, "no side view yet, so no depth — not a guess")
        assertEquals(150f, s.heightMm)
    }

    @Test
    fun `a view about 90 degrees round gives depth`() {
        val s = TwoViewSize()
        s.add(0f, 200f, 150f)
        s.add(85f, 120f, 152f)
        s.add(95f, 118f, 149f)

        assertEquals(200f, s.widthMm)
        assertEquals(120f, s.depthMm)
    }

    @Test
    fun `a view from behind is still the front`() {
        val s = TwoViewSize()
        s.add(0f, 200f, 150f)
        s.add(178f, 198f, 150f)
        assertNull(s.depthMm)
    }

    @Test
    fun `one wild frame does not move the median`() {
        val s = TwoViewSize()
        repeat(5) { s.add(0f, 200f, 150f) }
        s.add(0f, 900f, 150f)
        assertEquals(200f, s.widthMm)
    }
}
