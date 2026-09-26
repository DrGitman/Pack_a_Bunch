package com.packabunch.packing

import com.packabunch.packing.SyntheticScans.box
import com.packabunch.packing.SyntheticScans.frame
import com.packabunch.packing.SyntheticScans.frustum
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ItemTrackerTest {

    private fun orbit(cx: Float, cy: Float, frames: Int, distance: Float = 750f, height: Float = 500f) =
        (0 until frames).map {
            val a = it * 2 * PI.toFloat() / frames
            PlanePoint(cx + distance * cos(a), cy + distance * sin(a), height)
        }

    @Test fun `a cup and a carton become two measured objects, even when tracking ids churn`() {
        val carton = box(300f, 200f, 150f, yawDeg = 15f)
        val cup = frustum(30f, 42f, 95f, cx = 330f, cy = 40f)
        val tracker = ItemTracker()
        val rnd = Random(3)
        var last: ItemTracker.Update? = null
        orbit(165f, 20f, 80).forEachIndexed { i, cam ->
            // ML Kit hands out new ids every 15 frames, as it does when things leave the frame.
            val epoch = i / 15
            last = tracker.update(
                listOf(
                    ItemTracker.Observation(100 + epoch * 2, frame(carton, cam, rnd, 1200)),
                    ItemTracker.Observation(101 + epoch * 2, frame(cup, cam, rnd, 500)),
                ),
                cam,
            )
        }
        val update = last!!
        assertEquals(2, update.tracks.size, "tracks: ${update.tracks.map { it.fit }}")
        assertEquals(2, update.measuredCount)
        val (a, b) = update.tracks
        val cartonFit = assertIs<ItemScanState.Measured>(a.state).fit
        val cupFit = assertIs<ItemScanState.Measured>(b.state).fit
        println("  tracker carton W %.1f D %.1f H %.1f | cup W %.1f H %.1f %s".format(
            cartonFit.widthMm, cartonFit.depthMm, cartonFit.heightMm, cupFit.widthMm, cupFit.heightMm, cupFit.shape))
        assertEquals(300f, cartonFit.widthMm, 8f)
        assertEquals(84f, cupFit.widthMm, 6f)
        assertEquals(ShapeFamily.TAPERED, cupFit.shape)
    }

    @Test fun `one carton reported as two detections from two sides ends as one object`() {
        val carton = box(400f, 300f, 200f)
        val tracker = ItemTracker()
        val rnd = Random(5)
        orbit(0f, 0f, 60).forEach { cam ->
            val pts = frame(carton, cam, rnd, 1500)
            // Two overlapping detector boxes over the same carton, as ML Kit sometimes returns.
            val left = pts.filter { it.xMm < 100 }
            val right = pts.filter { it.xMm >= -100 }
            tracker.update(listOf(ItemTracker.Observation(null, left), ItemTracker.Observation(null, right)), cam)
        }
        assertEquals(1, tracker.all.size)
    }

    @Test fun `the free plan stops starting new objects at twenty and says so`() {
        val tracker = ItemTracker(maxItems = 20)
        val rnd = Random(9)
        val cam = PlanePoint(0f, -1500f, 900f)
        val boxes = (0 until 24).map { box(80f, 80f, 80f, cx = (it % 6) * 200f - 500f, cy = (it / 6) * 200f - 300f) }
        var update: ItemTracker.Update? = null
        repeat(3) { update = tracker.update(boxes.mapIndexed { i, b -> ItemTracker.Observation(i, frame(b, cam, rnd, 200)) }, cam) }
        assertEquals(20, update!!.tracks.size)
        assertTrue(update!!.capReached)
    }

    @Test fun `a detection that never becomes a surface is forgotten`() {
        val tracker = ItemTracker()
        val cam = PlanePoint(0f, -600f, 400f)
        // Forty scattered points once: a reflection, a shadow, a hand.
        val rnd = Random(2)
        val junk = List(40) { PlanePoint(rnd.nextFloat() * 400, rnd.nextFloat() * 400, 10f + rnd.nextFloat() * 300) }
        tracker.update(listOf(ItemTracker.Observation(7, junk)), cam)
        assertEquals(1, tracker.all.size)
        repeat(ItemTracker.PHANTOM_FRAMES + 2) { tracker.update(emptyList(), cam) }
        assertEquals(0, tracker.all.size)
    }
}

class PlaneFrameTest {
    @Test fun `plane frame round-trips and measures height along a tilted normal`() {
        val up = floatArrayOf(0.05f, 1f, 0.02f)
        val frame = PlaneFrame(0.3f, -0.8f, -1.2f, floatArrayOf(1f, 0.1f, 0f), up)
        val p = frame.toPlane(0.5f, -0.6f, -1.0f)
        val out = FloatArray(3)
        frame.toWorld(p, out)
        assertEquals(0.5f, out[0], 1e-4f)
        assertEquals(-0.6f, out[1], 1e-4f)
        assertEquals(-1.0f, out[2], 1e-4f)
        // A point 100 mm along the normal from the origin is 100 mm high and at the origin in plan.
        val n = frame.up
        val q = frame.toPlane(0.3f + 0.1f * n[0], -0.8f + 0.1f * n[1], -1.2f + 0.1f * n[2])
        assertEquals(100f, q.hMm, 0.01f)
        assertEquals(0f, q.xMm, 0.01f)
        assertEquals(0f, q.yMm, 0.01f)
    }
}
