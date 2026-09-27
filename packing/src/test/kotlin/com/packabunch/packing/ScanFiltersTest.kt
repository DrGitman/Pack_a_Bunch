package com.packabunch.packing

import com.packabunch.packing.SyntheticScans.box
import com.packabunch.packing.SyntheticScans.frame
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanFiltersTest {

    @Test fun `the wall a metre behind a mug is outside the mug's depth range`() {
        // A mug 600 mm away fills the middle of the box; the box's edges see the wall at 1600 mm.
        val depths = IntArray(100) { if (it < 40) 600 + it % 5 else 1600 }
        val central = BooleanArray(100) { it < 30 }
        // 40 px across in a 200 px focal depth image at 600 mm: the box is 120 mm wide.
        val range = BoxDepth.keepRange(depths, central, boxSpanPx = 40f, focalPx = 200f)!!
        assertTrue(600 in range)
        assertTrue(1600 !in range)
    }

    @Test fun `a big carton keeps its own depth`() {
        // 400 mm carton seen from 800 mm: front at 800, back corner at 1150.
        val range = BoxDepth.keepRange(IntArray(50) { 800 + it * 7 }, BooleanArray(50) { it in 10..30 }, boxSpanPx = 100f, focalPx = 200f)!!
        assertTrue(800 in range && 1150 in range)
    }

    @Test fun `no depth in the middle of the box gives no range`() {
        assertNull(BoxDepth.keepRange(IntArray(20) { 700 }, BooleanArray(20) { it < 3 }, 40f, 200f))
    }

    @Test fun `a state that flips back within the hold never shows`() {
        val s = ScanStateSmoother(holdMs = 700)
        val scanning = ItemScanState.Scanning(null, 0.3f)
        val fit = FittedObject(0f, 0f, 0f, 100f, 100f, 100f, ShapeFamily.BOX, observedAxes = emptySet(), pointCount = 50)
        val angle = ItemScanState.NeedsAngle(fit, AngleHint.STEP_AROUND, setOf(Axis.DEPTH), 0.5f)
        assertIs<ItemScanState.Scanning>(s.smooth(1, scanning, 0))
        // Flicker every 100 ms: always shows scanning.
        for (t in 1..20) {
            val shown = s.smooth(1, if (t % 2 == 0) angle else scanning, t * 100L)
            assertIs<ItemScanState.Scanning>(shown, "at ${t * 100} ms")
        }
        // Held for 700 ms: now it shows.
        var shown: ItemScanState = scanning
        for (t in 0..7) shown = s.smooth(1, angle, 3000L + t * 100)
        assertIs<ItemScanState.NeedsAngle>(shown)
        // Measured shows at once.
        val measured = ItemScanState.Measured(fit, MeasuredDimensions(
            MeasuredLength.measured(100, 5, ""), MeasuredLength.measured(100, 5, ""), MeasuredLength.measured(100, 5, "")))
        assertIs<ItemScanState.Measured>(s.smooth(1, measured, 3900))
    }

    @Test fun `a detection seen once is never shown, and one seen steadily is`() {
        val tracker = ItemTracker()
        val rnd = Random(4)
        val cam = PlanePoint(0f, -700f, 450f)
        val carton = box(200f, 150f, 120f)
        val wallPatch = box(150f, 40f, 300f, cx = 600f, cy = 400f)
        var update = tracker.update(listOf(ItemTracker.Observation(1, frame(carton, cam, rnd, 400)), ItemTracker.Observation(2, frame(wallPatch, cam, rnd, 400))), cam)
        assertEquals(0, update.visible.size)
        repeat(ItemTracker.CONFIRM_HITS) { update = tracker.update(listOf(ItemTracker.Observation(1, frame(carton, cam, rnd, 400))), cam) }
        assertEquals(1, update.visible.size)
        repeat(ItemTracker.TENTATIVE_FRAMES + 1) { update = tracker.update(listOf(ItemTracker.Observation(1, frame(carton, cam, rnd, 400))), cam) }
        assertEquals(1, update.tracks.size, "the one-frame patch is gone")
    }

    @Test fun `a fit the size of the room is dropped`() {
        val tracker = ItemTracker()
        val rnd = Random(8)
        val cam = PlanePoint(0f, -2500f, 900f)
        val wall = box(2400f, 60f, 1200f)
        var update: ItemTracker.Update? = null
        repeat(12) { update = tracker.update(listOf(ItemTracker.Observation(3, frame(wall, cam, rnd, 1500))), cam) }
        assertEquals(0, update!!.tracks.size)
    }
}
