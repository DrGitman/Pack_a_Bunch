package com.packabunch.packing

import com.packabunch.packing.SyntheticScans.Shape
import com.packabunch.packing.SyntheticScans.Surface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SpaceFitterTest {

    /**
     * The inside of a box-shaped space: floor, walls and (optionally) a top, all facing in.
     * [front] adds a front wall of that height — a sill, or a full wall for a room.
     */
    private fun interior(w: Float, d: Float, h: Float, yawDeg: Float = 0f, top: Boolean = false, front: Float = 0f): Shape {
        val r = yawDeg * PI.toFloat() / 180f
        val c = cos(r); val s = sin(r)
        fun world(u: Float, v: Float, z: Float) = floatArrayOf(u * c - v * s, u * s + v * c, z)
        fun dir(u: Float, v: Float, z: Float) = floatArrayOf(u * c - v * s, u * s + v * c, z)
        fun rnd(rn: Random, a: Float) = (rn.nextFloat() - 0.5f) * a
        val parts = mutableListOf<Pair<Surface, Float>>(
            Surface { rn -> world(rnd(rn, w), rnd(rn, d), 0f) to dir(0f, 0f, 1f) } to w * d,
            Surface { rn -> world(-w / 2, rnd(rn, d), rn.nextFloat() * h) to dir(1f, 0f, 0f) } to d * h,
            Surface { rn -> world(w / 2, rnd(rn, d), rn.nextFloat() * h) to dir(-1f, 0f, 0f) } to d * h,
            Surface { rn -> world(rnd(rn, w), d / 2, rn.nextFloat() * h) to dir(0f, -1f, 0f) } to w * h,
        )
        if (top) parts += Surface { rn -> world(rnd(rn, w), rnd(rn, d), h) to dir(0f, 0f, -1f) } to w * d
        if (front > 0f) {
            parts += Surface { rn -> world(rnd(rn, w), -d / 2, rn.nextFloat() * front) to dir(0f, 1f, 0f) } to w * front
            // Its outside and its top edge, which is what you see of a sill from behind the car.
            if (front < h) parts += Surface { rn -> world(rnd(rn, w), -d / 2 - 1f, rn.nextFloat() * front) to dir(0f, -1f, 0f) } to w * front
        }
        return Shape(parts)
    }

    private fun scan(shape: Shape, cameras: List<PlanePoint>, count: Int = 2500, sigma: Float = 4f, seed: Int = 11): Pair<ObjectCloud, List<PlanePoint>> {
        val cloud = ObjectCloud(10f, 40_000, minHeightMm = -200f, relativeSightings = 0.03f)
        val rnd = Random(seed)
        for (cam in cameras) cloud.add(SyntheticScans.frame(shape, cam, rnd, count, sigma))
        return cloud to cameras
    }

    /** Standing in front of the opening and sweeping across it, as at a car boot. */
    private fun sweepFromFront(distance: Float, height: Float, span: Float, frames: Int) =
        (0 until frames).map { i ->
            val t = i / (frames - 1f) - 0.5f
            PlanePoint(t * span, -distance, height)
        }

    private fun fitOf(cloud: ObjectCloud, cams: List<PlanePoint>): SpaceBox {
        val snap = cloud.snapshot()
        return assertNotNull(SpaceFitter.fit(snap.points, cams, snap.weights))
    }

    @Test fun `a car boot seen from behind has width across, depth away and an opening above the sill`() {
        val boot = interior(1040f, 860f, 570f, top = true, front = 90f)
        val (cloud, cams) = scan(boot, sweepFromFront(900f, 1100f, 900f, 40))
        val box = fitOf(cloud, cams)
        println("  boot  W %.0f D %.0f H %.0f yaw %.1f mapped %.2f opening %s".format(box.widthMm, box.depthMm, box.heightMm, box.yawDegrees, box.mapped, box.opening))
        assertEquals(1040f, box.widthMm, 25f)
        assertEquals(860f, box.depthMm, 25f)
        assertEquals(570f, box.heightMm, 25f)
        val opening = assertNotNull(box.opening)
        assertEquals(1040f, opening.widthMm.toFloat(), 25f)
        assertEquals(480f, opening.heightMm.toFloat(), 30f)
        assertTrue(!box.cameraInside)
    }

    @Test fun `an open carton from above, rotated on the table`() {
        val carton = interior(400f, 300f, 280f, yawDeg = 20f)
        val cams = (0 until 30).map { i ->
            val a = (-60f + i * 4f) * PI.toFloat() / 180f
            PlanePoint(500f * sin(a), -500f * cos(a), 700f)
        }
        val (cloud, _) = scan(carton, cams, count = 2000, sigma = 3f)
        val box = fitOf(cloud, cams)
        println("  carton W %.0f D %.0f H %.0f yaw %.1f mapped %.2f".format(box.widthMm, box.depthMm, box.heightMm, box.yawDegrees, box.mapped))
        val dims = listOf(box.widthMm, box.depthMm).sorted()
        assertEquals(300f, dims[0], 15f)
        assertEquals(400f, dims[1], 15f)
        assertEquals(280f, box.heightMm, 15f)
    }

    @Test fun `a room scanned from its middle has no opening and needs all four walls`() {
        val room = interior(3600f, 3000f, 2500f, top = true, front = 2500f)
        val cams = (0 until 48).map { i ->
            val a = i * 7.5f * PI.toFloat() / 180f
            PlanePoint(300f * cos(a), 300f * sin(a), 1400f)
        }
        val (cloud, _) = scan(room, cams, count = 3000, sigma = 12f)
        val box = fitOf(cloud, cams)
        println("  room  W %.0f D %.0f H %.0f mapped %.2f inside=%s".format(box.widthMm, box.depthMm, box.heightMm, box.mapped, box.cameraInside))
        assertTrue(box.cameraInside)
        assertEquals(null, box.opening)
        val dims = listOf(box.widthMm, box.depthMm).sorted()
        assertEquals(3000f, dims[0], 60f)
        assertEquals(3600f, dims[1], 60f)
        assertTrue(SpaceFace.FRONT in box.requiredFaces)
    }

    @Test fun `a side never swept is named as the next thing to do`() {
        val boot = interior(1040f, 860f, 570f, top = true, front = 90f)
        // Only standing to the left: the right wall is barely seen.
        val cams = (0 until 20).map { i -> PlanePoint(-900f + i * 10f, -700f, 1000f) }
        val (cloud, _) = scan(boot, cams)
        val box = fitOf(cloud, cams)
        println("  partial coverage ${box.coverage.mapValues { "%.2f".format(it.value) }} weakest ${box.weakestFace}")
        assertTrue(box.mapped < 0.95f)
    }

    @Test fun `the grid is free inside, solid where something stands, unknown behind an unseen wall`() {
        val box = SpaceBox(0f, 0f, 0f, 1000f, 800f, 500f,
            coverage = mapOf(SpaceFace.FLOOR to 1f, SpaceFace.LEFT to 1f, SpaceFace.RIGHT to 0f, SpaceFace.BACK to 1f),
            opening = Opening(1000, 450), cameraInside = false, pointCount = 0)
        // A wheel arch: a block of points inside, near the left.
        val arch = (0 until 400).map { PlanePoint(-420f + (it % 20) * 5f, -100f + (it / 20) * 10f, 60f + (it % 7) * 20f) }
        val space = SpaceFitter.toScannedSpace(box, arch, FloatArray(arch.size) { 3f }, resolutionMm = 20)
        val g = space.baseGrid
        assertEquals(50, g.countX); assertEquals(40, g.countY); assertEquals(25, g.countZ)
        assertEquals(Cell.SOLID, g.cellAt(5, 20, 5))
        assertEquals(Cell.FREE, g.cellAt(25, 20, 10))
        assertEquals(Cell.UNKNOWN, g.cellAt(49, 20, 10))
        assertEquals(1000, space.opening!!.widthMm)
    }
}
