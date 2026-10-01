package com.packabunch.packing

import kotlin.test.Test
import kotlin.test.assertEquals

class LookupMergeTest {

    private fun d(w: Int, d: Int, h: Int) = Dimensions(w, d, h)

    @Test
    fun `one recognised product fixes the scale for everything else`() {
        // The photo had no reference and measured everything 2x too big.
        val measured = listOf(d(240, 140, 80), d(200, 100, 300))
        val answers = listOf(LookupMerge.Answer("Wireless mouse", exact = true, confidence = 0.9f, size = d(70, 115, 40)), null)
        val out = LookupMerge.merge(measured, listOf("mouse", null), answers, ScaleSource.TYPICAL)
        // The mouse takes its real size, laid on the photo's axes (its longest side was the width).
        assertEquals(d(115, 70, 40), out[0].dimensions)
        assertEquals(SizeSource.LOOKED_UP, out[0].source)
        // The other thing is scaled by 115 / 240.
        assertEquals(d(95, 47, 143), out[1].dimensions)
        assertEquals(SizeSource.PHOTO_SCALED_BY_LOOKUP, out[1].source)
    }

    @Test
    fun `with a card in the photo only exact products change`() {
        val measured = listOf(d(118, 70, 41), d(200, 100, 300))
        val answers = listOf(LookupMerge.Answer("Wireless mouse", true, 0.9f, d(70, 115, 40)), LookupMerge.Answer("Box", false, 0.8f, d(300, 300, 300)))
        val out = LookupMerge.merge(measured, listOf("mouse", null), answers, ScaleSource.CARD)
        assertEquals(d(115, 70, 40), out[0].dimensions)
        assertEquals(d(200, 100, 300), out[1].dimensions)
        assertEquals("Box", out[1].name) // unnamed things still take the lookup's name
    }

    @Test
    fun `an unsure answer changes nothing`() {
        val measured = listOf(d(240, 140, 80))
        val out = LookupMerge.merge(measured, listOf("cup"), listOf(LookupMerge.Answer("Mug", true, 0.3f, d(90, 90, 100))), ScaleSource.TYPICAL)
        assertEquals(measured[0], out[0].dimensions)
        assertEquals(SizeSource.PHOTO, out[0].source)
        assertEquals("cup", out[0].name)
    }

    @Test
    fun `the lookup's usual size beats a detector's wrong guess`() {
        // A tissue pack taken for a 19 cm remote: the photo's scale came from that wrong name.
        val measured = listOf(d(73, 51, 105), d(194, 136, 73))
        val answers = listOf(
            LookupMerge.Answer("Pocket tissues", exact = false, confidence = 0.7f, size = d(110, 55, 30)),
            LookupMerge.Answer("Wallet", exact = false, confidence = 0.6f, size = d(115, 95, 20)),
        )
        val out = LookupMerge.merge(measured, listOf("remote", "suitcase"), answers, ScaleSource.KNOWN_OBJECT)
        assertEquals("Pocket tissues", out[0].name)
        assertEquals(d(55, 30, 110), out[0].dimensions)
        assertEquals("Wallet", out[1].name)
        assertEquals(SizeSource.LOOKED_UP, out[1].source)
    }
}
