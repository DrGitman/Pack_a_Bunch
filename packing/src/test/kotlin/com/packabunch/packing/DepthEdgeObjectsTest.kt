package com.packabunch.packing

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * HarshdeepJ's depth-edge detection on a phone-style view: looking down at a table, so the table
 * recedes across the picture and an object's base touches it with no depth jump.
 */
class DepthEdgeObjectsTest {

    private val w = 160
    private val h = 120
    private val f = 120f

    /** Table depth by row: near at the bottom of the picture, far at the top. */
    private fun tableAt(y: Int) = 1300 - y * 8

    /** A flat-faced object standing on the table, its base on [baseRow]. */
    private class Item(val left: Int, val right: Int, val top: Int, val baseRow: Int)

    private fun scene(vararg items: Item): Pair<IntArray, BooleanArray> {
        val depth = IntArray(w * h) { tableAt(it / w) }
        for (item in items) {
            val face = tableAt(item.baseRow)
            for (y in item.top until item.baseRow) for (x in item.left until item.right) depth[y * w + x] = face
        }
        val onTable = BooleanArray(w * h) { abs(depth[it] - tableAt(it / w)) <= 10 }
        return depth to onTable
    }

    @Test
    fun `with the table as background the object is found and sized by w·d over f`() {
        val (depth, onTable) = scene(Item(60, 76, 40, 80))
        val found = DepthEdgeObjects.find(depth, w, h, f, f, onTable)

        assertEquals(1, found.size)
        val o = found.single()
        val d = tableAt(80)
        assertEquals(d, o.depthMm, "d is the median depth of the object")
        // 16 px wide, 40 px tall at d, give or take the edge dilation eating the rim.
        assertTrue(abs(o.widthMm - 16 * d / f) <= 4 * d / f, "W = w·d/f, got ${o.widthMm}")
        assertTrue(abs(o.heightMm - 40 * d / f) <= 4 * d / f, "H = h·d/f, got ${o.heightMm}")
    }

    @Test
    fun `two items standing apart are two objects`() {
        val (depth, onTable) = scene(Item(30, 46, 50, 90), Item(100, 124, 60, 95))
        assertEquals(2, DepthEdgeObjects.find(depth, w, h, f, f, onTable).size)
    }

    @Test
    fun `a bare table has no objects`() {
        val (depth, onTable) = scene()
        assertTrue(DepthEdgeObjects.find(depth, w, h, f, f, onTable).isEmpty())
    }
}
