package com.packabunch.packing

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The scene on the Huawei: a black mouse on a white desk, a teal bank card right beside it. */
class ColorMaskTest {

    private val w = 240
    private val h = 180
    private val rnd = Random(5)

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    private fun noisy(r: Int, g: Int, b: Int): Int { val n = rnd.nextInt(-6, 7); return rgb(r + n, g + n, b + n) }

    /** Mouse: an ellipse centred (100, 90), radii 30 × 45. */
    private fun inMouse(x: Int, y: Int) = ((x - 100) / 30.0).let { it * it } + ((y - 90) / 45.0).let { it * it } <= 1.0
    private fun inCard(x: Int, y: Int) = x in 135..200 && y in 70..112

    private fun scene(mouse: (Int, Int) -> Int) = IntArray(w * h) { i ->
        val x = i % w; val y = i / w
        when {
            inMouse(x, y) -> mouse(x, y)
            inCard(x, y) -> noisy(30, 160, 160)
            else -> noisy(235, 235, 232)
        }
    }

    @Test
    fun `the mouse is masked and the card beside it is not`() {
        val img = scene { _, _ -> noisy(25, 25, 28) }
        // A YOLO box a little loose round the mouse, overlapping the card's edge.
        val box = floatArrayOf(62f / w, 38f / h, 142f / w, 142f / h)
        val mask = assertNotNull(ColorMask.segment(img, w, h, box))

        var both = 0; var either = 0; var cardHit = 0
        for (y in 0 until h) for (x in 0 until w) {
            val m = mask[y * w + x]; val t = inMouse(x, y)
            if (m && t) both++; if (m || t) either++; if (m && inCard(x, y)) cardHit++
        }
        val iou = both.toDouble() / either
        assertTrue(iou > 0.85, "mask overlaps the mouse: IoU $iou")
        assertTrue(cardHit < 20, "card pixels in the mask: $cardHit")
    }

    @Test
    fun `a highlight on the mouse does not punch a hole in it`() {
        val img = scene { x, y -> if ((x - 95) * (x - 95) + (y - 80) * (y - 80) < 60) noisy(200, 200, 205) else noisy(25, 25, 28) }
        val mask = assertNotNull(ColorMask.segment(img, w, h, floatArrayOf(62f / w, 38f / h, 142f / w, 142f / h)))
        assertTrue(mask[80 * w + 95], "the shiny spot is still the mouse")
    }

    @Test
    fun `a white cup on a white table cannot be told apart, so no mask`() {
        val img = scene { _, _ -> noisy(236, 236, 233) }
        assertNull(ColorMask.segment(img, w, h, floatArrayOf(62f / w, 38f / h, 142f / w, 142f / h)))
    }
}
