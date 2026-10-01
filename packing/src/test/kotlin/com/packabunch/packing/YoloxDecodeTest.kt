package com.packabunch.packing

import kotlin.math.abs
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YoloxDecodeTest {

    private val anchors = (52 * 52) + (26 * 26) + (13 * 13)

    /** One confident anchor on the stride-8 grid at (gx, gy), box w×h input pixels. */
    private fun output(label: String, gx: Int, gy: Int, w: Float, h: Float, score: Float = 0.9f): FloatArray {
        val out = FloatArray(anchors * 85)
        val o = (gy * 52 + gx) * 85
        out[o] = 0.5f; out[o + 1] = 0.5f
        out[o + 2] = ln(w / 8f); out[o + 3] = ln(h / 8f)
        out[o + 4] = 1f
        out[o + 5 + YoloxDecode.COCO.indexOf(label)] = score
        return out
    }

    @Test
    fun `a cup comes back where it was, in the original picture's coordinates`() {
        // 832×832 picture: ratio 0.5, so the input is the picture at half size.
        val found = YoloxDecode.decode(output("cup", gx = 26, gy = 26, w = 40f, h = 80f), 832, 832)

        assertEquals(1, found.size)
        val d = found.single()
        assertEquals("cup", d.label)
        val cx = (d.box[0] + d.box[2]) / 2; val cy = (d.box[1] + d.box[3]) / 2
        assertTrue(abs(cx - 26.5f * 8 / 416) < 0.01f, "centre x $cx")
        assertTrue(abs((d.box[2] - d.box[0]) - 40f / 416) < 0.01f, "width ${d.box[2] - d.box[0]}")
        assertTrue(abs(cy - 26.5f * 8 / 416) < 0.01f, "centre y $cy")
    }

    @Test
    fun `the table and people are never items`() {
        assertTrue(YoloxDecode.decode(output("dining table", 20, 20, 200f, 100f), 416, 416).isEmpty())
        assertTrue(YoloxDecode.decode(output("person", 20, 20, 60f, 200f), 416, 416).isEmpty())
    }

    @Test
    fun `there is no wheel in the vocabulary`() {
        assertTrue("wheel" !in YoloxDecode.COCO)
    }

    @Test
    fun `two boxes on the same cup become one`() {
        val out = output("cup", 26, 26, 40f, 80f)
        val o = (26 * 52 + 27) * 85 // the neighbouring cell, same cup
        out[o] = -0.5f; out[o + 1] = 0.5f; out[o + 2] = ln(40f / 8f); out[o + 3] = ln(80f / 8f)
        out[o + 4] = 1f; out[o + 5 + YoloxDecode.COCO.indexOf("cup")] = 0.8f
        assertEquals(1, YoloxDecode.decode(out, 416, 416).size)
    }

    @Test
    fun `a weak guess is dropped`() {
        assertTrue(YoloxDecode.decode(output("cup", 26, 26, 40f, 80f, score = 0.2f), 416, 416).isEmpty())
    }
}
