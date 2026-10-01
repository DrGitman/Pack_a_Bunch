package com.packabunch.packing

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanarPoseTest {

    private val k = PlanarPose.Intrinsics(800.0, 800.0, 320.0, 240.0)

    /** A camera at [eye] looking at the origin on the table, as a phone held over it. */
    private fun lookingAt(eye: DoubleArray): PlanarPose.Pose {
        val f = norm(doubleArrayOf(-eye[0], -eye[1], -eye[2]))          // camera z: forward
        val right = norm(cross(f, doubleArrayOf(0.0, 1.0, 0.0)))        // camera x
        val down = cross(f, right)                                       // camera y (pointing down in the picture)
        val r = doubleArrayOf(right[0], right[1], right[2], down[0], down[1], down[2], f[0], f[1], f[2])
        val t = DoubleArray(3) { i -> -(r[3 * i] * eye[0] + r[3 * i + 1] * eye[1] + r[3 * i + 2] * eye[2]) }
        return PlanarPose.Pose(r, t)
    }

    private fun corners(pose: PlanarPose.Pose, long: Double, short: Double): DoubleArray {
        val a = long / 2; val b = short / 2
        val w = listOf(doubleArrayOf(-a, 0.0, -b), doubleArrayOf(a, 0.0, -b), doubleArrayOf(a, 0.0, b), doubleArrayOf(-a, 0.0, b))
        return w.flatMap { pose.project(k, it)!!.toList() }.toDoubleArray()
    }

    @Test
    fun `the camera is found where it is, to the millimetre`() {
        for (eye in listOf(doubleArrayOf(0.1, 0.4, 0.35), doubleArrayOf(-0.25, 0.3, 0.2), doubleArrayOf(0.05, 0.6, 0.02))) {
            val truth = lookingAt(eye)
            val fit = assertNotNull(PlanarPose.fromQuad(corners(truth, PlanarPose.CARD_LONG_M, PlanarPose.CARD_SHORT_M),
                PlanarPose.CARD_LONG_M, PlanarPose.CARD_SHORT_M, k))
            val got = fit.pose.cameraPosition
            // The card is symmetric, so the frame may be turned 180° about Y: compare height and distance.
            assertTrue(abs(got[1] - eye[1]) < 0.001, "height ${got[1]} vs ${eye[1]}")
            assertTrue(abs(dist(got) - dist(eye)) < 0.001, "distance ${dist(got)} vs ${dist(eye)}")
        }
    }

    @Test
    fun `a sheet of A4 is not taken for a card`() {
        val truth = lookingAt(doubleArrayOf(0.1, 0.5, 0.4))
        val quad = corners(truth, PlanarPose.A4_LONG_M, PlanarPose.A4_SHORT_M)
        assertNull(PlanarPose.fromQuad(quad, PlanarPose.CARD_LONG_M, PlanarPose.CARD_SHORT_M, k), "wrong shape for a card")
        assertNotNull(PlanarPose.fromQuad(quad, PlanarPose.A4_LONG_M, PlanarPose.A4_SHORT_M, k))
    }

    @Test
    fun `the frame does not flip between frames`() {
        val first = lookingAt(doubleArrayOf(0.1, 0.4, 0.35))
        val q = corners(first, PlanarPose.CARD_LONG_M, PlanarPose.CARD_SHORT_M)
        val a = assertNotNull(PlanarPose.fromQuad(q, PlanarPose.CARD_LONG_M, PlanarPose.CARD_SHORT_M, k)).pose
        // The same quad listed from a different corner, as a detector might.
        val shifted = DoubleArray(8) { q[(it + 4) % 8] }
        val b = assertNotNull(PlanarPose.fromQuad(shifted, PlanarPose.CARD_LONG_M, PlanarPose.CARD_SHORT_M, k, previous = a)).pose
        for (i in 0 until 3) assertTrue(abs(a.cameraPosition[i] - b.cameraPosition[i]) < 1e-6, "axis $i moved")
    }

    @Test
    fun `a point on the table maps back to where it is`() {
        val pose = lookingAt(doubleArrayOf(0.2, 0.45, 0.3))
        val fit = assertNotNull(PlanarPose.fromQuad(corners(pose, PlanarPose.CARD_LONG_M, PlanarPose.CARD_SHORT_M),
            PlanarPose.CARD_LONG_M, PlanarPose.CARD_SHORT_M, k, previous = pose)).pose
        val p = doubleArrayOf(0.12, 0.0, -0.08)
        val px = fit.project(k, p)!!
        val back = assertNotNull(fit.rayToTable(k, px[0], px[1]))
        assertTrue(abs(back[0] - p[0]) < 1e-6 && abs(back[2] - p[2]) < 1e-6)
    }

    private fun dist(a: DoubleArray) = kotlin.math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2])
    private fun norm(a: DoubleArray): DoubleArray { val l = dist(a); return DoubleArray(3) { a[it] / l } }
    private fun cross(a: DoubleArray, b: DoubleArray) =
        doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
}
