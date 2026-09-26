package com.packabunch.packing

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScanGeometryTest {

    @Test fun `upright boxes land where the sensor sees them for every rotation`() {
        // Sensor 640x480. A box covering sensor x 0.1..0.3, y 0.2..0.6.
        val sw = 640; val sh = 480
        fun check(rotation: Int, l: Float, t: Float, r: Float, b: Float) {
            val out = SensorBox.uprightToSensor(l, t, r, b, sw, sh, rotation)
            val expected = floatArrayOf(0.1f, 0.2f, 0.3f, 0.6f)
            for (i in 0 until 4) assertEquals(expected[i], out[i], 1e-4f, "rotation $rotation index $i")
        }
        check(0, 64f, 96f, 192f, 288f)
        // Rotated 90° clockwise: upright image is 480 wide, 640 tall; sensor (x, y) -> (H - y, x).
        check(90, 480 * 0.4f, 640 * 0.1f, 480 * 0.8f, 640 * 0.3f)
        check(180, 640 * 0.7f, 480 * 0.4f, 640 * 0.9f, 480 * 0.8f)
        check(270, 480 * 0.2f, 640 * 0.7f, 480 * 0.6f, 640 * 0.9f)
    }

    @Test fun `the object in the middle of the box is kept and the neighbour behind it is not`() {
        val rnd = Random(4)
        val samples = ArrayList<DetectionPoints.Sample>()
        // Mug: 80 mm cylinder at the origin, central in the box.
        repeat(300) {
            val a = rnd.nextFloat() * 6.28f
            samples += DetectionPoints.Sample(PlanePoint(40f * kotlin.math.cos(a), 40f * kotlin.math.sin(a), 10f + rnd.nextFloat() * 90f), central = abs(a - 4.7f) < 0.8f || rnd.nextBoolean())
        }
        // Carton face 60 mm behind the mug, filling the box edges.
        repeat(400) {
            samples += DetectionPoints.Sample(PlanePoint(-150f + rnd.nextFloat() * 300f, 100f, 10f + rnd.nextFloat() * 180f), central = rnd.nextFloat() < 0.15f)
        }
        // The table.
        repeat(200) { samples += DetectionPoints.Sample(PlanePoint(rnd.nextFloat() * 300f - 150f, rnd.nextFloat() * 200f - 100f, 1f), false) }
        val picked = DetectionPoints.select(samples)
        assertTrue(picked.isNotEmpty())
        assertTrue(picked.all { samples[it].point.yMm < 60f }, "carton or table leaked in")
        assertTrue(picked.size > 250)
    }

    @Test fun `a box outline has twelve edges, four per axis`() {
        val fit = FittedObject(0f, 0f, 30f, 300f, 200f, 150f, ShapeFamily.BOX, observedAxes = Axis.entries.toSet(), pointCount = 100)
        val edges = OutlineGeometry.of(fit, PlanePoint(600f, 0f, 400f))
        assertEquals(12, edges.size)
        for (axis in Axis.entries) assertEquals(4, edges.count { it.axis == axis })
        for (e in edges.filter { it.axis == Axis.WIDTH }) {
            val len = kotlin.math.hypot(e.b.xMm - e.a.xMm, e.b.yMm - e.a.yMm)
            assertEquals(300f, len, 0.01f)
        }
    }

    @Test fun `a can shows two rims and the two sides facing the camera`() {
        val fit = FittedObject(0f, 0f, 0f, 66f, 66f, 115f, ShapeFamily.CYLINDER, 33f, 33f, observedAxes = Axis.entries.toSet(), pointCount = 100)
        val edges = OutlineGeometry.of(fit, PlanePoint(500f, 0f, 300f), curveSegments = 32)
        assertEquals(66, edges.size)
        val sides = edges.filter { it.axis == Axis.HEIGHT }
        assertEquals(2, sides.size)
        // Seen from +x, the sides are at ±y.
        assertTrue(sides.all { abs(abs(it.a.yMm) - 33f) < 0.01f && abs(it.a.xMm) < 0.01f })
    }

    @Test fun `an L-shape hull outline puts uprights only at the silhouette`() {
        val hull = listOf(-150f to -150f, 150f to -150f, 150f to -50f, -50f to 150f, -150f to 150f)
        val fit = FittedObject(0f, 0f, 0f, 300f, 300f, 120f, ShapeFamily.IRREGULAR, footprintHull = hull, observedAxes = Axis.entries.toSet(), pointCount = 100)
        val edges = OutlineGeometry.of(fit, PlanePoint(900f, 900f, 500f))
        assertEquals(2, edges.count { it.axis == Axis.HEIGHT })
        assertEquals(10, edges.count { it.axis == Axis.WIDTH })
    }
}
