package com.packabunch.packing

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Finding the table without ARCore's help.
 *
 * The scenes here are the ones that defeated plane detection on the phone: a dark table with a
 * wall behind it, and a couple of objects standing on top.
 */
class SupportPlaneTest {

    private val random = Random(7)

    /** A horizontal patch at height [y], noisy by a few millimetres like real depth. */
    private fun table(y: Float, spanX: Float = 1.0f, spanZ: Float = 0.8f, n: Int = 3000) =
        FloatArray(n * 3) { i ->
            when (i % 3) {
                0 -> (random.nextFloat() - 0.5f) * spanX
                1 -> y + (random.nextFloat() - 0.5f) * 0.004f
                else -> (random.nextFloat() - 0.5f) * spanZ
            }
        }

    /** A vertical wall: same point count, but spread over every height it spans. */
    private fun wall(fromY: Float, toY: Float, z: Float, n: Int = 3000) =
        FloatArray(n * 3) { i ->
            when (i % 3) {
                0 -> (random.nextFloat() - 0.5f) * 1.2f
                1 -> fromY + random.nextFloat() * (toY - fromY)
                else -> z + (random.nextFloat() - 0.5f) * 0.004f
            }
        }

    private fun box(y: Float, height: Float, n: Int = 400) =
        FloatArray(n * 3) { i ->
            when (i % 3) {
                0 -> 0.1f + (random.nextFloat() - 0.5f) * 0.1f
                1 -> y + random.nextFloat() * height
                else -> (random.nextFloat() - 0.5f) * 0.1f
            }
        }

    @Test
    fun `the table is found even with a wall behind it`() {
        val scene = table(-0.4f) + wall(-0.4f, 0.6f, -0.9f)

        val support = assertNotNull(SupportPlane.detect(scene), "a flat surface is present")
        assertTrue(
            abs(support.y - (-0.4f)) < 0.01f,
            "the wall spreads across every height band; only the table piles into one. Got ${support.y}",
        )
    }

    @Test
    fun `objects standing on the table do not move it`() {
        val scene = table(-0.35f) + box(-0.35f, 0.24f) + box(-0.35f, 0.1f)

        val support = assertNotNull(SupportPlane.detect(scene))
        assertTrue(abs(support.y - (-0.35f)) < 0.01f, "got ${support.y}")
    }

    @Test
    fun `the footprint covers the objects on it`() {
        val support = assertNotNull(SupportPlane.detect(table(-0.4f, spanX = 1.0f, spanZ = 0.8f)))
        val xs = (0 until 4).map { support.polygonXZ[it * 2] }
        val zs = (0 until 4).map { support.polygonXZ[it * 2 + 1] }

        assertTrue(xs.min() <= -0.5f && xs.max() >= 0.5f, "spans the table in x: $xs")
        assertTrue(zs.min() <= -0.4f && zs.max() >= 0.4f, "spans the table in z: $zs")
    }

    @Test
    fun `a wall on its own is not a surface to stand things on`() {
        assertNull(
            SupportPlane.detect(wall(-0.5f, 1.5f, -1.0f)),
            "nothing is flat here; inventing a height would put every item at the wrong place",
        )
    }

    @Test
    fun `too few points is not a surface`() {
        assertNull(SupportPlane.detect(table(-0.4f, n = 10)))
        assertEquals(null, SupportPlane.detect(FloatArray(0)))
    }
}
