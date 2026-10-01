package com.packabunch.packing

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MonoDepthScaleTest {

    /** A MiDaS-like reading for a true distance: inverse depth, with its own scale and offset. */
    private fun midas(zMm: Float) = 2.5e5f / zMm - 40f

    @Test
    fun `markers at several distances give true millimetres everywhere`() {
        val z = floatArrayOf(400f, 500f, 600f, 700f, 800f, 900f, 1000f, 1200f)
        val scale = assertNotNull(MonoDepthScale.fit(FloatArray(z.size) { midas(z[it]) }, z))

        for (truth in listOf(350f, 650f, 1500f)) {
            val got = scale.mm(midas(truth))
            assertTrue(abs(got - truth) < truth * 0.01f, "at $truth mm got $got")
        }
    }

    @Test
    fun `a marker that landed on an object instead of the table is ignored`() {
        val z = floatArrayOf(400f, 500f, 600f, 700f, 800f, 900f, 1000f, 1100f, 1200f, 1300f)
        val r = FloatArray(z.size) { midas(z[it]) }
        r[5] = midas(450f) // the ray hit a cup at 450 mm, but the table there is 900 mm
        val scale = assertNotNull(MonoDepthScale.fit(r, z))

        assertTrue(abs(scale.mm(midas(800f)) - 800f) < 12f, "got ${scale.mm(midas(800f))}")
    }

    @Test
    fun `too few references is no scale, not a guess`() {
        assertNull(MonoDepthScale.fit(floatArrayOf(1f, 2f, 3f), floatArrayOf(900f, 600f, 300f)))
    }

    @Test
    fun `references that say nearer is farther are rejected`() {
        val z = FloatArray(10) { 400f + it * 100f }
        assertNull(MonoDepthScale.fit(FloatArray(10) { -midas(z[it]) }, z))
    }
}
