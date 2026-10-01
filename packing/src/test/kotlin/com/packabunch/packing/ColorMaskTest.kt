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
    fun `the traced outline keeps an L's inside corner, which a hull would cut off`() {
        // An L: 60 × 60 square with its top-right 30 × 30 quarter missing, at (20, 20).
        val mw = 100; val mh = 100
        val mask = BooleanArray(mw * mh) { i -> val x = i % mw; val y = i / mw; x in 20 until 80 && y in 20 until 80 && !(x >= 50 && y < 50) }
        val c = assertNotNull(ColorMask.contour(mask, mw, mh))
        val pts = (0 until c.size / 2).map { c[2 * it] * mw to c[2 * it + 1] * mh }
        assertTrue(pts.size in 6..10, "an L has six corners: ${pts.size} points")
        assertTrue(pts.any { (x, y) -> kotlin.math.abs(x - 50) < 2 && kotlin.math.abs(y - 50) < 2 }, "inside corner kept: $pts")
        val (cx, cy) = assertNotNull(ColorMask.centroid(mask, mw, mh))
        assertTrue(cx * mw < 50 && cy * mh > 50, "centre of mass leans to the solid part: $cx, $cy")
    }

    @Test
    fun `a pale cup too close in colour to the table is found by its edge`() {
        val img = scene { _, _ -> noisy(212, 212, 210) }
        val mask = assertNotNull(ColorMask.segment(img, w, h, floatArrayOf(62f / w, 38f / h, 142f / w, 142f / h)))
        var both = 0; var either = 0
        for (y in 0 until h) for (x in 0 until w) { val m = mask[y * w + x]; val t = inMouse(x, y); if (m && t) both++; if (m || t) either++ }
        assertTrue(both.toDouble() / either > 0.8, "IoU ${both.toDouble() / either}")
    }

    @Test
    fun `a white cup on a white table cannot be told apart, so no mask`() {
        val img = scene { _, _ -> noisy(236, 236, 233) }
        assertNull(ColorMask.segment(img, w, h, floatArrayOf(62f / w, 38f / h, 142f / w, 142f / h)))
    }
}
