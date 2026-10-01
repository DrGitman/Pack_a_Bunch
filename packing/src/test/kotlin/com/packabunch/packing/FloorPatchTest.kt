package com.packabunch.packing

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FloorPatchTest {

    /** Floor points on a grid over [x0..x1] × [z0..z1] at height [y]. */
    private fun floor(x0: Float, x1: Float, z0: Float, z1: Float, y: Float = 0f): FloatArray {
        val out = ArrayList<Float>()
        var x = x0
        while (x <= x1) { var z = z0; while (z <= z1) { out += x; out += y; out += z; z += 0.02f }; x += 0.02f }
        return out.toFloatArray()
    }

    @Test
    fun `the boot floor joins up and the road below it does not`() {
        val patch = FloorPatch()
        // Boot floor: 1.0 × 0.86 m around the card. Road: 0.6 m lower, in front of it.
        patch.add(floor(-0.5f, 0.5f, -0.86f, 0f) + floor(-1.5f, 1.5f, 0.1f, 2.0f, y = -0.6f))
        val poly = assertNotNull(patch.polygon())
        val xs = poly.filterIndexed { i, _ -> i % 2 == 0 }; val zs = poly.filterIndexed { i, _ -> i % 2 == 1 }
        assertTrue(xs.min() >= -0.56f && xs.max() <= 0.56f, "x ${xs.min()}..${xs.max()}")
        assertTrue(zs.max() <= 0.06f, "the road must not join: z up to ${zs.max()}")
    }

    @Test
    fun `floor seen in later frames joins on`() {
        val patch = FloorPatch()
        patch.add(floor(-0.2f, 0.2f, -0.2f, 0.2f))
        patch.add(floor(0.2f, 0.8f, -0.2f, 0.2f)) // sweeping right
        val xs = assertNotNull(patch.polygon()).filterIndexed { i, _ -> i % 2 == 0 }
        assertTrue(xs.max() >= 0.75f, "grew to ${xs.max()}")
    }

    @Test
    fun `a shelf at another height across a gap stays out`() {
        val patch = FloorPatch()
        patch.add(floor(-0.2f, 0.2f, -0.2f, 0.2f) + floor(0.6f, 1.0f, -0.2f, 0.2f, y = 0.3f))
        val xs = assertNotNull(patch.polygon()).filterIndexed { i, _ -> i % 2 == 0 }
        assertTrue(xs.max() <= 0.26f)
    }
}
