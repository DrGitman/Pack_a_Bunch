package com.packabunch.packing

import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The card found in a photo-like picture, then the camera found from it.
 *
 * Rendered, not drawn: a pinhole camera looking down at a wood-grain table, a bank card with
 * rounded corners lying on it, a dark book beside it, and sensor noise — the kind of picture a
 * phone takes of items on a table.
 */
class CardFinderTest {

    private val w = 640
    private val h = 480
    private val k = PlanarPose.Intrinsics(560.0, 560.0, 320.0, 240.0)

    private fun lookingAt(eye: DoubleArray, target: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0)): PlanarPose.Pose {
        val f = norm(DoubleArray(3) { target[it] - eye[it] })
        val right = norm(cross(f, doubleArrayOf(0.0, 1.0, 0.0)))
        val down = cross(f, right)
        val r = doubleArrayOf(right[0], right[1], right[2], down[0], down[1], down[2], f[0], f[1], f[2])
        val t = DoubleArray(3) { i -> -(r[3 * i] * eye[0] + r[3 * i + 1] * eye[1] + r[3 * i + 2] * eye[2]) }
        return PlanarPose.Pose(r, t)
    }

    /** Renders the table scene: card centred at the origin, turned by [yaw] radians. */
    private fun render(pose: PlanarPose.Pose, yaw: Double, cardGray: Int = 235, seed: Int = 3): IntArray {
        val rnd = Random(seed)
        val c = kotlin.math.cos(yaw); val s = kotlin.math.sin(yaw)
        val a = PlanarPose.CARD_LONG_M / 2; val b = PlanarPose.CARD_SHORT_M / 2; val corner = 0.003
        // 3 x 3 samples per pixel: a lens blurs an edge across a pixel; a point sample would not.
        fun shade(px: Double, py: Double): Double {
            val hit = pose.rayToTable(k, px, py)
            return if (hit == null) 200.0 else {
                val x = hit[0]; val z = hit[2]
                // Card frame.
                val u = x * c + z * s; val v = -x * s + z * c
                val du = (abs(u) - (a - corner)).coerceAtLeast(0.0); val dv = (abs(v) - (b - corner)).coerceAtLeast(0.0)
                val inCard = abs(u) <= a && abs(v) <= b && du * du + dv * dv <= corner * corner
                val inBook = x in 0.09..0.24 && z in -0.12..0.05
                when {
                    inCard -> cardGray.toDouble()
                    inBook -> 40.0
                    else -> 70.0 + 18.0 * sin(z * 180 + sin(x * 40) * 3)  // wood grain
                }
            }
        }
        return IntArray(w * h) { i ->
            var sum = 0.0
            for (sy in 0 until 3) for (sx in 0 until 3) sum += shade(i % w + (sx + 0.5) / 3 - 0.5, i / w + (sy + 0.5) / 3 - 0.5)
            (sum / 9 + rnd.nextDouble(-6.0, 6.0)).toInt().coerceIn(0, 255)
        }
    }

    private fun cameraFrom(gray: IntArray): DoubleArray? {
        for (q in CardFinder.find(gray, w, h)) {
            val fit = PlanarPose.fromQuad(q.corners, PlanarPose.CARD_LONG_M, PlanarPose.CARD_SHORT_M, k, sides = q.sides) ?: continue
            return fit.pose.cameraPosition
        }
        return null
    }

    @Test
    fun `the card is found and the camera placed within a few millimetres`() {
        for ((eye, yaw) in listOf(
            doubleArrayOf(0.05, 0.40, 0.30) to 0.3,
            doubleArrayOf(-0.15, 0.35, 0.25) to 1.1,
            doubleArrayOf(0.0, 0.45, 0.12) to -0.6,
        )) {
            val pos = assertNotNull(cameraFrom(render(lookingAt(eye), yaw)), "card not found for eye ${eye.toList()}")
            val height = pos[1]; val dist = sqrt(pos[0] * pos[0] + pos[1] * pos[1] + pos[2] * pos[2])
            val trueDist = sqrt(eye[0] * eye[0] + eye[1] * eye[1] + eye[2] * eye[2])
            // Scale is what every measurement inherits: hold it to 2%.
            assertTrue(abs(dist - trueDist) / trueDist < 0.02, "distance $dist vs $trueDist")
            assertTrue(abs(height - eye[1]) / eye[1] < 0.03, "height $height vs ${eye[1]}")
        }
    }

    @Test
    fun `no card in view is no card, not a guess`() {
        val gray = render(lookingAt(doubleArrayOf(0.05, 0.40, 0.30)), 0.3, cardGray = 70) // card the colour of the table
        assertTrue(cameraFrom(gray) == null)
    }

    private fun norm(a: DoubleArray): DoubleArray { val l = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); return DoubleArray(3) { a[it] / l } }
    private fun cross(a: DoubleArray, b: DoubleArray) =
        doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
}
