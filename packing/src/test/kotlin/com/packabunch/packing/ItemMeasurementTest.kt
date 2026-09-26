package com.packabunch.packing

import com.packabunch.packing.SyntheticScans.arc
import com.packabunch.packing.SyntheticScans.box
import com.packabunch.packing.SyntheticScans.frustum
import com.packabunch.packing.SyntheticScans.lShape
import com.packabunch.packing.SyntheticScans.measure
import com.packabunch.packing.SyntheticScans.sphere
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ItemMeasurementTest {

    private fun report(name: String, m: ItemMeasurement, w: Float, d: Float, h: Float) {
        val fit = (m.state as? ItemScanState.Measured)?.fit ?: m.lastFit
        println(
            "  %-26s %-14s %-9s ±%-3s W %6.1f (%+5.1f)  D %6.1f (%+5.1f)  H %6.1f (%+5.1f)".format(
                name, m.state::class.simpleName, fit?.shape, (m.state as? ItemScanState.Measured)?.dimensions?.toleranceMm ?: "-",
                fit?.widthMm ?: 0f, (fit?.widthMm ?: 0f) - w,
                fit?.depthMm ?: 0f, (fit?.depthMm ?: 0f) - d,
                fit?.heightMm ?: 0f, (fit?.heightMm ?: 0f) - h,
            ),
        )
    }

    private fun assertMeasured(m: ItemMeasurement, w: Float, d: Float, h: Float, tolMm: Float): FittedObject {
        val s = m.state
        assertIs<ItemScanState.Measured>(s, "expected Measured, was $s")
        val f = s.fit
        assertTrue(abs(f.widthMm - w) <= tolMm, "width ${f.widthMm} vs $w")
        assertTrue(abs(f.depthMm - d) <= tolMm, "depth ${f.depthMm} vs $d")
        assertTrue(abs(f.heightMm - h) <= tolMm, "height ${f.heightMm} vs $h")
        assertTrue(s.dimensions.fullyMeasured)
        return f
    }

    @Test fun `rotated carton walked around is measured to within 8 mm`() {
        val m = measure(box(300f, 200f, 150f, yawDeg = 30f), arc(0f, 360f, 60))
        report("carton 300x200x150 @30°", m, 300f, 200f, 150f)
        val f = assertMeasured(m, 300f, 200f, 150f, 8f)
        assertEquals(ShapeFamily.BOX, f.shape)
        assertTrue(abs(f.yawDegrees - 30f) < 3f, "yaw ${f.yawDegrees}")
    }

    @Test fun `carton seen only from the front at table height asks for another angle`() {
        val m = measure(box(300f, 200f, 150f), arc(-10f, 10f, 40, distance = 500f, height = 90f))
        report("carton, front only, low", m, 300f, 200f, 150f)
        val s = m.state
        assertIs<ItemScanState.NeedsAngle>(s, "was $s")
        assertTrue(s.unseen.isNotEmpty())
        assertEquals(AngleHint.TILT_DOWN, s.hint)
    }

    @Test fun `carton seen from the front but from above is fully measured`() {
        // The top face shows both footprint lengths at once; no walk-around needed.
        val m = measure(box(300f, 200f, 150f), arc(-20f, 20f, 40, distance = 450f, height = 550f))
        report("carton, front from above", m, 300f, 200f, 150f)
        assertMeasured(m, 300f, 200f, 150f, 10f)
    }

    @Test fun `a suitcase-sized box coarsens its cloud and still measures within 2 percent`() {
        val m = measure(box(700f, 450f, 260f, yawDeg = 75f), arc(0f, 360f, 60, distance = 1300f, height = 1100f), count = 1500)
        report("case 700x450x260 @75°", m, 700f, 450f, 260f)
        assertMeasured(m, 700f, 450f, 260f, 14f)
        assertTrue(m.cloud.voxelMm > ObjectCloud.DEFAULT_VOXEL_MM, "expected coarsening, voxel ${m.cloud.voxelMm}")
    }

    @Test fun `a can seen over a quarter turn is a cylinder of the right diameter`() {
        val m = measure(frustum(33f, 33f, 115f), arc(0f, 100f, 40, distance = 400f, height = 300f))
        report("can Ø66x115", m, 66f, 66f, 115f)
        val f = assertMeasured(m, 66f, 66f, 115f, 6f)
        assertEquals(ShapeFamily.CYLINDER, f.shape)
    }

    @Test fun `a cup is tapered and measured at its rim`() {
        val m = measure(frustum(30f, 42f, 95f), arc(-40f, 80f, 40, distance = 400f, height = 300f))
        report("cup Ø84 rim, Ø60 base, 95", m, 84f, 84f, 95f)
        val f = assertMeasured(m, 84f, 84f, 95f, 6f)
        assertEquals(ShapeFamily.TAPERED, f.shape)
        assertTrue(f.topRadiusMm!! > f.bottomRadiusMm!!)
    }

    @Test fun `a ball is a sphere`() {
        val m = measure(sphere(110f), arc(0f, 200f, 50, distance = 700f, height = 500f))
        report("ball Ø220", m, 220f, 220f, 220f)
        val f = assertMeasured(m, 220f, 220f, 220f, 8f)
        assertEquals(ShapeFamily.SPHERE, f.shape)
    }

    @Test fun `an L-shaped object gets a hugging hull and a conservative bounding box`() {
        val m = measure(lShape(), arc(0f, 360f, 60, distance = 700f, height = 500f))
        report("L 300x300x120", m, 300f, 300f, 120f)
        val f = assertMeasured(m, 300f, 300f, 120f, 8f)
        assertEquals(ShapeFamily.IRREGULAR, f.shape)
        assertTrue(f.footprintHull.size >= 4)
    }

    @Test fun `heavy depth noise still settles, with a wider tolerance`() {
        val m = measure(box(300f, 200f, 150f, yawDeg = 10f), arc(0f, 360f, 80), sigmaMm = 8f)
        report("carton, noise σ8", m, 300f, 200f, 150f)
        val s = m.state
        assertIs<ItemScanState.Measured>(s, "was $s")
        assertMeasured(m, 300f, 200f, 150f, 15f)
    }

    @Test fun `something too thin to lift off the surface offers its footprint to type over`() {
        val m = measure(box(240f, 170f, 10f), arc(0f, 360f, 50, height = 500f))
        report("envelope 240x170x10", m, 240f, 170f, 10f)
        val s = m.state
        assertIs<ItemScanState.CannotMeasure>(s, "was $s")
        assertEquals(CannotMeasureReason.TOO_FLAT, s.reason)
        assertNotNull(s.prefill)
    }

    @Test fun `no depth at all says so instead of spinning forever`() {
        val m = ItemMeasurement()
        repeat(30) { m.addFrame(emptyList(), PlanePoint(500f, 0f, 400f)) }
        val s = m.state
        assertIs<ItemScanState.CannotMeasure>(s)
        assertEquals(CannotMeasureReason.NO_DEPTH, s.reason)
    }

    @Test fun `measured is sticky`() {
        val m = measure(box(300f, 200f, 150f), arc(0f, 360f, 60))
        assertIs<ItemScanState.Measured>(m.state)
        // Point the camera at nothing for a long time, then at something else entirely.
        repeat(40) { m.addFrame(emptyList(), PlanePoint(600f, 0f, 400f)) }
        m.addFrame(SyntheticScans.frame(box(500f, 500f, 500f), PlanePoint(600f, 0f, 400f), kotlin.random.Random(1)), PlanePoint(600f, 0f, 400f))
        val after = m.state
        assertIs<ItemScanState.Measured>(after)
        assertEquals(300f, after.fit.widthMm, 8f)
    }

    @Test fun `a partial scan uses the fallback only for the side never seen`() {
        val m = measure(box(300f, 200f, 150f), arc(-10f, 10f, 40, distance = 500f, height = 90f))
        val fallback = SuggestedDimensions(Dimensions(310, 210, 160), 20, "Shoe box — typical size", SuggestionConfidence.entries.first())
        val dims = assertNotNull(m.partial(fallback))
        assertEquals(MeasuredDimensions.Provenance.PARTLY_MEASURED, dims.summary)
    }
}
