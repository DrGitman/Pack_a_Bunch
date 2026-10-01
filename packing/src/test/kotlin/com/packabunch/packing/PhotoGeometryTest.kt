package com.packabunch.packing

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A rendered photo: a camera 0.5 m above a table, tilted down 40°, a 100 × 60 × 80 mm box on it.
 * The depth "model" is the true inverse depth with its own unknown scale and offset, as MiDaS's is.
 */
class PhotoGeometryTest {

    private val w = 640; private val h = 480; private val g = 128
    private val k = PlanarPose.Intrinsics(520.0, 520.0, 320.0, 240.0)
    private val camH = 0.5
    private val up = PhotoGeometry.upForPitch(40.0)

    // Plane frame axes, as PhotoGeometry builds them.
    private val e1 = doubleArrayOf(1.0, 0.0, 0.0)
    private val e2 = doubleArrayOf(up[1] * e1[2] - up[2] * e1[1], up[2] * e1[0] - up[0] * e1[2], up[0] * e1[1] - up[1] * e1[0])

    /** Box centred 450 mm ahead on the floor of the plane frame. */
    private fun inBox(x: Double, y: Double, hh: Double) = abs(x) <= 0.05 && abs(y - 0.45) <= 0.03 && hh in 0.0..0.08

    /** True z-depth and whether the ray hit the box, by marching. */
    private fun cast(px: Double, py: Double): Pair<Double, Boolean>? {
        val d = doubleArrayOf((px - k.cx) / k.fx, (py - k.cy) / k.fy, 1.0)
        var t = 0.05
        while (t < 6.0) {
            val p = DoubleArray(3) { d[it] * t }
            val x = p[0] * e1[0] + p[1] * e1[1] + p[2] * e1[2]
            val y = p[0] * e2[0] + p[1] * e2[1] + p[2] * e2[2]
            val hh = p[0] * up[0] + p[1] * up[1] + p[2] * up[2] + camH
            if (inBox(x, y, hh)) return t to true
            if (hh <= 0.0) return t to false
            t += 0.0015
        }
        return null
    }

    private val scene: Pair<FloatArray, BooleanArray> by lazy {
        val rel = FloatArray(g * g); val box = BooleanArray(g * g)
        for (gy in 0 until g) for (gx in 0 until g) {
            val hit = cast((gx + 0.5) * w / g, (gy + 0.5) * h / g)
            if (hit == null) { rel[gy * g + gx] = 0f; continue }
            rel[gy * g + gx] = (300.0 / hit.first - 40.0).toFloat() // MiDaS-like: scaled, offset inverse depth
            box[gy * g + gx] = hit.second
        }
        rel to box
    }

    private fun geometry() = assertNotNull(PhotoGeometry.fit(k, up, scene.first, g, w, h, { gx, gy -> !scene.second[gy * g + gx] }))

    @Test
    fun `with the right camera height, the box comes out its true size`() {
        val geo = geometry()
        val pts = (0 until g * g).filter { scene.second[it] }.mapNotNull { geo.pointAt(it % g, it / g, camH) }
        val hs = pts.map { it.hMm }.sorted(); val xs = pts.map { it.xMm }.sorted()
        val height = hs[(hs.size * 0.97).toInt()]
        val width = xs[(xs.size * 0.98).toInt()] - xs[(xs.size * 0.02).toInt()]
        assertTrue(abs(height - 80f) < 8f, "height $height")
        assertTrue(abs(width - 100f) < 10f, "width $width")
    }

    @Test
    fun `a recognised thing of known size gives back the camera height`() {
        val geo = geometry()
        // Measure the box as if the camera were 1 m up, then call it a "cup" 80 mm tall.
        val hs = (0 until g * g).filter { scene.second[it] }.mapNotNull { geo.pointAt(it % g, it / g, 1.0)?.hMm }.sorted()
        val h1 = hs[(hs.size * 0.97).toInt()]
        val est = 80.0 / h1
        assertTrue(abs(est - camH) < 0.05, "camera height $est")
    }

    @Test
    fun `the plane round-trips to the picture`() {
        val geo = geometry()
        val p = PlanePoint(30f, 420f, 0f)
        val px = assertNotNull(geo.pixelOf(p, camH))
        val gx = (px[0] * g / w).toInt(); val gy = (px[1] * g / h).toInt()
        val back = assertNotNull(geo.pointAt(gx, gy, camH))
        assertTrue(sqrt(((back.xMm - p.xMm) * (back.xMm - p.xMm) + (back.yMm - p.yMm) * (back.yMm - p.yMm)).toDouble()) < 15, "$back")
    }

    @Test
    fun `a gallery photo's tilt comes back from where the table's depth runs out`() {
        val rel = FloatArray(g * g) { i -> val hit = cast((i % g + 0.5) * w / g, (i / g + 0.5) * h / g); if (hit == null) 0f else (300.0 / hit.first).toFloat() }
        val pitch = assertNotNull(PhotoGeometry.estimatePitch(k, rel, g, h) { gx, gy -> !scene.second[gy * g + gx] })
        assertTrue(abs(pitch - 40.0) < 4.0, "pitch $pitch")
    }

    @Test
    fun `the median of several references ignores one odd one`() {
        val h = PhotoScale.fromPriors(listOf(
            "cup" to 200f,         // says 0.5 m
            "mouse" to 224f,       // says 0.5 m
            "cell phone" to 100f,  // says 1.5 m — an odd one
        ))
        assertTrue(abs(h!! - 0.5) < 0.01, "$h")
    }

    @Test
    fun `a bottle is never a reference, and a short one is a can`() {
        // The scan that came out four times too big: a can lying down, taken for a bottle.
        assertTrue(PhotoScale.fromPriors(listOf("bottle" to 66f)) == null)
        assertTrue(PhotoScale.nameFor("bottle", 150) == "Can")
        assertTrue(PhotoScale.nameFor("bottle", 300) == "Bottle")
    }
}
