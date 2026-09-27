package com.packabunch.packing

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import com.packabunch.packing.SceneSim.gaussian
import com.packabunch.packing.SceneSim.sq
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The whole item scan, end to end, on one real desk: a white smart display, a round speaker, a
 * black puck, a flat charger, a book and a big glass doughnut vase, on a wooden top against a
 * plaster wall. The layout, sizes and camera are lined up with a photograph of that desk.
 *
 * Each frame a depth image is ray-marched from the phone's pose — with ARCore's kind of error:
 * a percent or so of smooth, correlated depth error, pixels with no depth, and silhouettes
 * smeared between the object and what is behind it. Detector boxes are made the way ML Kit
 * makes them: loose rectangles around each object, at most five a frame, tracking ids that
 * change now and then, plus the boxes it wrongly puts on a patch of wall and around the whole
 * scene. Everything after that is the scan's own code, in the order the app runs it.
 *
 * The screenshot this replaces showed five objects for one heater, outlines the size of the
 * room, and nothing measured.
 */
class PhotoSceneScanTest {

    // -- the desk ------------------------------------------------------------------------------

    private sealed class Obj(val name: String, val x: Float, val z: Float) {
        abstract fun sdf(px: Float, py: Float, pz: Float): Float
    }

    private class Box(name: String, x: Float, z: Float, val w: Float, val d: Float, val h: Float, yawDeg: Float, val r: Float = 0.004f) : Obj(name, x, z) {
        private val c = cos(yawDeg * PI.toFloat() / 180f); private val s = sin(yawDeg * PI.toFloat() / 180f)
        override fun sdf(px: Float, py: Float, pz: Float): Float {
            val qx = px - x; val qz = pz - z
            val lx = qx * c + qz * s; val lz = -qx * s + qz * c
            val dx = abs(lx) - w / 2 + r; val dy = abs(py - h / 2) - h / 2 + r; val dz = abs(lz) - d / 2 + r
            return sqrt(sq(max(dx, 0f)) + sq(max(dy, 0f)) + sq(max(dz, 0f))) + min(max(dx, max(dy, dz)), 0f) - r
        }
    }

    private class Cyl(name: String, x: Float, z: Float, val r: Float, val h: Float) : Obj(name, x, z) {
        override fun sdf(px: Float, py: Float, pz: Float): Float {
            val dr = hypot(px - x, pz - z) - r; val dh = abs(py - h / 2) - h / 2
            return min(max(dr, dh), 0f) + hypot(max(dr, 0f), max(dh, 0f))
        }
    }

    private class Ball(name: String, x: Float, z: Float, val r: Float, val cy: Float) : Obj(name, x, z) {
        override fun sdf(px: Float, py: Float, pz: Float) = sqrt(sq(px - x) + sq(py - cy) + sq(pz - z)) - r
    }

    /** A doughnut standing on its edge, its hole facing along [yawDeg]. */
    private class Torus(name: String, x: Float, z: Float, val big: Float, val small: Float, yawDeg: Float) : Obj(name, x, z) {
        private val c = cos(yawDeg * PI.toFloat() / 180f); private val s = sin(yawDeg * PI.toFloat() / 180f)
        override fun sdf(px: Float, py: Float, pz: Float): Float {
            val qx = px - x; val qz = pz - z
            val lx = qx * c + qz * s; val lz = -qx * s + qz * c
            return hypot(hypot(lx, py - (big + small)) - big, lz) - small
        }
    }

    private val wallZ = -0.40f
    private val desk = listOf(
        Box("Display", -0.02f, -0.15f, 0.17f, 0.10f, 0.145f, -4f, r = 0.012f),
        Ball("Speaker", 0.175f, -0.27f, 0.085f, 0.08f),
        Cyl("Puck", 0.10f, -0.03f, 0.05f, 0.06f),
        Cyl("Charger", 0.265f, -0.11f, 0.085f, 0.014f),
        Box("Book", -0.12f, 0.09f, 0.17f, 0.24f, 0.03f, 10f),
        Torus("Vase", -0.32f, -0.25f, 0.075f, 0.048f, 15f),
    )

    /** Distance to the nearest surface, and which: an object's index, [TABLE] or [WALL]. */
    private fun scene(px: Float, py: Float, pz: Float): Pair<Float, Int> {
        var best = py; var id = TABLE
        val wall = pz - wallZ
        if (wall < best) { best = wall; id = WALL }
        for ((i, o) in desk.withIndex()) {
            val d = o.sdf(px, py, pz)
            if (d < best) { best = d; id = i }
        }
        return best to id
    }

    // -- the phone -----------------------------------------------------------------------------

    private val lens = SceneSim.Lens(DW, DH, F)

    /** ML Kit's boxes: `[left, top, right, bottom]` in 0..1 of the depth image, with a tracking id. */
    private class Detection(val id: Int?, val box: FloatArray) {
        val area get() = (box[2] - box[0]) * (box[3] - box[1])
    }

    private fun detections(img: SceneSim.DepthImage, frame: Int, rnd: Random): List<Detection> {
        val out = ArrayList<Pair<Int, Detection>>()   // pixel count, box
        for (i in desk.indices) {
            var u0 = DW; var v0 = DH; var u1 = -1; var v1 = -1; var n = 0
            for (v in 0 until DH) for (u in 0 until DW) if (img.hit[v * DW + u] == i) { n++; u0 = min(u0, u); u1 = max(u1, u); v0 = min(v0, v); v1 = max(v1, v) }
            if (n < 30) continue
            // Loose, and a little different every frame.
            val padU = (u1 - u0) * (0.04f + 0.06f * rnd.nextFloat()); val padV = (v1 - v0) * (0.04f + 0.06f * rnd.nextFloat())
            val box = floatArrayOf(
                ((u0 - padU) / DW).coerceIn(0f, 1f), ((v0 - padV) / DH).coerceIn(0f, 1f),
                ((u1 + 1 + padU) / DW).coerceIn(0f, 1f), ((v1 + 1 + padV) / DH).coerceIn(0f, 1f),
            )
            // Tracking ids are handed out again every so often, as when things leave the frame.
            out += n to Detection(100 + i + 10 * (frame / 40), box)
        }
        // The wrong ones: a patch of plain wall above the display, and the whole scene at once.
        if (rnd.nextFloat() < 0.35f) out += 0 to Detection(900, floatArrayOf(0.30f, 0.00f, 0.62f, 0.22f))
        if (rnd.nextFloat() < 0.25f) out += 0 to Detection(901, floatArrayOf(0.02f, 0.05f, 0.95f, 0.95f))
        // ML Kit reports five at most, the most prominent.
        return out.sortedByDescending { it.first + (if (it.first == 0) 2000 else 0) * (if (rnd.nextBoolean()) 1 else 0) }.take(5).map { it.second }
    }

    // -- the scan: the same steps as ItemScanController, in the same order ------------------------

    private class Sampled(val det: Detection, val pixels: IntArray, val world: FloatArray, val central: BooleanArray, val edge: BooleanArray)

    private fun sample(img: SceneSim.DepthImage, cam: SceneSim.Camera, dets: List<Detection>, walls: List<WallPlane>): List<Sampled> {
        val out = ArrayList<Sampled>()
        for (det in dets) {
            if (det.area > 0.5f) continue
            val b = det.box
            val w = b[2] - b[0]; val h = b[3] - b[1]
            val u0 = ((b[0] + w * SHRINK) * DW).toInt().coerceIn(0, DW - 1); val u1 = ((b[2] - w * SHRINK) * DW).toInt().coerceIn(0, DW - 1)
            val v0 = ((b[1] + h * SHRINK) * DH).toInt().coerceIn(0, DH - 1); val v1 = ((b[3] - h * SHRINK) * DH).toInt().coerceIn(0, DH - 1)
            if (u1 <= u0 || v1 <= v0) continue
            val step = if ((u1 - u0) * (v1 - v0) > 2400) 2 else 1
            val cu0 = u0 + (u1 - u0) * 0.3f; val cu1 = u1 - (u1 - u0) * 0.3f
            val cv0 = v0 + (v1 - v0) * 0.3f; val cv1 = v1 - (v1 - v0) * 0.3f
            val raw = ArrayList<Int>(); val rawMm = ArrayList<Int>(); val rawCentral = ArrayList<Boolean>()
            var v = v0
            while (v <= v1) {
                var u = u0
                while (u <= u1) {
                    val i = v * DW + u
                    val mm = img.mm[i]
                    if (mm in 150..1500) { raw += v * DW + u; rawMm += mm; rawCentral += u >= cu0 && u <= cu1 && v >= cv0 && v <= cv1 }
                    u += step
                }
                v += step
            }
            val depths = rawMm.toIntArray(); val centralArr = rawCentral.toBooleanArray()
            val keep = BoxDepth.keepRange(depths, centralArr, max(u1 - u0, v1 - v0).toFloat(), F) ?: continue
            val px = ArrayList<Int>(); val pts = ArrayList<Float>(); val central = ArrayList<Boolean>(); val edge = ArrayList<Boolean>()
            for (k in depths.indices) {
                if (depths[k] !in keep) continue
                val i = raw[k]; val uu = i % DW; val vv = i / DW
                val onEdge = DetectionPoints.isEdge(
                    depths[k], if (uu > 0) img.mm[i - 1] else 0, if (uu < DW - 1) img.mm[i + 1] else 0,
                    if (vv > 0) img.mm[i - DW] else 0, if (vv < DH - 1) img.mm[i + DW] else 0,
                )
                val p = cam.world((raw[k] % DW).toFloat(), (raw[k] / DW).toFloat(), depths[k] / 1000f)
                if (walls.any { it.contains(p[0], p[1], p[2]) }) continue
                px += raw[k]; pts += p[0]; pts += p[1]; pts += p[2]; central += centralArr[k]; edge += onEdge
            }
            if (px.isNotEmpty()) out += Sampled(det, px.toIntArray(), pts.toFloatArray(), central.toBooleanArray(), edge.toBooleanArray())
        }
        return out
    }

    private val table = PlaneFrame(0f, 0f, 0f, floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f))

    private fun observations(samples: List<Sampled>, cam: SceneSim.Camera): List<ItemTracker.Observation> {
        val claimed = HashSet<Int>()
        val out = ArrayList<ItemTracker.Observation>()
        for (bs in samples.sortedBy { it.det.area }) {
            val keep = bs.pixels.indices.filter { bs.pixels[it] !in claimed }
            if (keep.size < DetectionPoints.MIN_SAMPLES) continue
            val list = keep.map { i -> DetectionPoints.Sample(table.toPlane(bs.world[3 * i], bs.world[3 * i + 1], bs.world[3 * i + 2]), bs.central[i], bs.edge[i]) }
            val picked = DetectionPoints.select(list)
            if (picked.isEmpty()) continue
            for (j in picked) claimed += bs.pixels[keep[j]]
            val b = bs.det.box
            val whole = b[0] > EDGE && b[1] > EDGE && b[2] < 1 - EDGE && b[3] < 1 - EDGE
            out += ItemTracker.Observation(bs.det.id, picked.map { list[it].point }, table.toPlane(cam.pos[0], cam.pos[1], cam.pos[2]), whole)
        }
        return out
    }

    /** Moving the phone the way people do: across the desk and back, rising a little, ending on the photo's view. */
    private fun sweep(frames: Int, rnd: Random): List<SceneSim.Camera> {
        val target = floatArrayOf(0f, 0.06f, -0.19f)
        val radius = hypot(PHOTO_POS[0] - target[0], PHOTO_POS[2] - target[2])
        return (0 until frames).map { i ->
            val s = i / frames.toFloat()
            val az = 40f * sin(2 * PI.toFloat() * s) * PI.toFloat() / 180f
            val hgt = PHOTO_POS[1] + 0.13f * sq(sin(PI.toFloat() * s))
            val pos = floatArrayOf(
                target[0] + radius * sin(az) + 0.004f * gaussian(rnd),
                hgt + 0.004f * gaussian(rnd),
                target[2] + radius * cos(az) + 0.004f * gaussian(rnd),
            )
            SceneSim.Camera.lookingAt(pos, target, lens)
        } + SceneSim.Camera(PHOTO_POS, 0f, PHOTO_PITCH, lens)
    }

    private class Result(val update: ItemTracker.Update, val shown: Map<Int, ItemScanState>, val last: SceneSim.Camera)

    private fun scan(seed: Int, wallFound: Boolean, frames: Int = 150): Result {
        val rnd = Random(seed)
        val tracker = ItemTracker()
        val smoother = ScanStateSmoother()
        val walls = if (wallFound) listOf(WallPlane(floatArrayOf(0f, 0.4f, wallZ), floatArrayOf(0f, 0f, 1f), floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f), 0.7f, 0.4f)) else emptyList()
        var update: ItemTracker.Update? = null
        var shown = emptyMap<Int, ItemScanState>()
        val cams = sweep(frames, rnd)
        for ((i, cam) in cams.withIndex()) {
            val img = SceneSim.depthImage(::scene, cam, rnd)
            val obs = observations(sample(img, cam, detections(img, i, rnd), walls), cam)
            update = tracker.update(obs, table.toPlane(cam.pos[0], cam.pos[1], cam.pos[2]))
            smoother.retain(update.visible.map { it.id }.toSet())
            shown = update.visible.associate { it.id to smoother.smooth(it.id, it.state, i * 100L) }
        }
        return Result(update!!, shown, cams.last())
    }

    /** Which object on the desk a track is: the one whose middle is nearest its footprint's. */
    private fun truthOf(t: ItemTracker.Track): Obj? {
        val f = t.fit ?: return null
        return desk.minByOrNull { hypot(it.x * 1000 - f.centreXMm, -it.z * 1000 - f.centreYMm) }
            // The book runs out of the picture, so its fit is only the part seen.
            ?.takeIf { hypot(it.x * 1000 - f.centreXMm, -it.z * 1000 - f.centreYMm) < 130f }
    }

    private fun report(r: Result): String = r.update.visible.joinToString("\n") { t ->
        val f = t.fit
        val s = r.shown[t.id]
        "  %-8s %-14s %-9s W %4.0f D %4.0f H %4.0f".format(
            truthOf(t)?.name ?: "PHANTOM", s?.javaClass?.simpleName + ((s as? ItemScanState.CannotMeasure)?.reason?.let { " $it" } ?: ""),
            f?.shape, f?.widthMm ?: 0f, f?.depthMm ?: 0f, f?.heightMm ?: 0f,
        )
    }

    // -- what the photo should come out as ---------------------------------------------------------

    private fun check(r: Result) {
        val visible = r.update.visible
        println(report(r))
        // Nothing but the six things on the desk; never the wall, never the room.
        for (t in visible) assertTrue(truthOf(t) != null, "a track that is not an object:\n${report(r)}")
        assertEquals(visible.size, visible.map { truthOf(it) }.toSet().size, "one object shown twice:\n${report(r)}")
        for (t in visible) t.fit?.let { f -> assertTrue(max(f.widthMm, f.depthMm) < 400f && f.heightMm < 300f, "outline bigger than anything on the desk:\n${report(r)}") }

        fun track(name: String) = visible.firstOrNull { truthOf(it)?.name == name }
        fun measured(name: String): FittedObject = assertIs<ItemScanState.Measured>(track(name)?.state, "$name not measured:\n${report(r)}").fit

        val display = measured("Display")
        assertEquals(ShapeFamily.BOX, display.shape)
        assertEquals(170f, max(display.widthMm, display.depthMm), 15f)
        assertEquals(100f, min(display.widthMm, display.depthMm), 15f)
        assertEquals(145f, display.heightMm, 12f)

        val puck = measured("Puck")
        assertTrue(puck.shape == ShapeFamily.CYLINDER || puck.shape == ShapeFamily.TAPERED, "puck is ${puck.shape}")
        assertEquals(100f, puck.widthMm, 10f)
        assertEquals(60f, puck.heightMm, 8f)

        val speaker = measured("Speaker")
        assertTrue(speaker.shape != ShapeFamily.BOX, "speaker is a box")
        assertEquals(170f, speaker.widthMm, 15f)
        assertEquals(165f, speaker.heightMm, 15f)

        // The book runs off the bottom of the picture in every view: never measured, and asked
        // for a step back instead of given a short length.
        track("Book")?.let { t ->
            val s = assertIs<ItemScanState.NeedsAngle>(t.state, "book: ${t.state}")
            assertEquals(AngleHint.STEP_BACK, s.hint)
        }

        // The charger is 14 mm thick: too flat for depth to lift it off the desk. Either nothing,
        // or a tag saying so and offering to type it — never a made-up size.
        track("Charger")?.let { t ->
            val s = t.state
            assertTrue(s !is ItemScanState.Measured || s.fit.heightMm < 25f, "charger: $s")
        }
    }

    @Test fun `the desk in the photo, with the wall found`() = check(scan(seed = 11, wallFound = true))

    @Test fun `the desk in the photo, when ARCore never finds the wall`() = check(scan(seed = 12, wallFound = false))

    /** Writes the result for the overlay drawn on the photo, when asked (`-DscanSim=path`). */
    @Test fun `export for the photo overlay`() {
        val path = System.getProperty("scanSim") ?: System.getenv("SCAN_SIM_OUT") ?: return
        val r = scan(seed = 11, wallFound = true)
        val cam = table.toPlane(r.last.pos[0], r.last.pos[1], r.last.pos[2])
        val sb = StringBuilder("{\"tracks\":[")
        r.update.visible.forEachIndexed { n, t ->
            val f = t.fit ?: return@forEachIndexed
            val s = r.shown[t.id] ?: t.state
            if (n > 0 && sb.last() != '[') sb.append(',')
            sb.append("{\"id\":${t.id},\"truth\":\"${truthOf(t)?.name}\",\"state\":\"${s.javaClass.simpleName}\"")
            when (s) {
                is ItemScanState.NeedsAngle -> sb.append(",\"hint\":\"${s.hint}\",\"unseen\":[${s.unseen.joinToString(",") { "\"$it\"" }}],\"progress\":${s.progress}")
                is ItemScanState.Scanning -> sb.append(",\"progress\":${s.progress}")
                is ItemScanState.CannotMeasure -> sb.append(",\"reason\":\"${s.reason}\"")
                is ItemScanState.Measured -> Unit
            }
            sb.append(",\"shape\":\"${f.shape}\",\"cx\":${f.centreXMm},\"cy\":${f.centreYMm},\"w\":${f.widthMm},\"d\":${f.depthMm},\"h\":${f.heightMm}")
            sb.append(",\"segments\":[")
            sb.append(OutlineGeometry.of(f, cam).joinToString(",") { g -> "[${g.a.xMm},${g.a.yMm},${g.a.hMm},${g.b.xMm},${g.b.yMm},${g.b.hMm},\"${g.axis}\"]" })
            sb.append("],\"footprint\":[")
            sb.append(OutlineGeometry.footprint(f).joinToString(",") { p -> "[${p.xMm},${p.yMm},${p.hMm}]" })
            sb.append("]}")
        }
        sb.append("]}")
        java.io.File(path).writeText(sb.toString())
    }

    private companion object {
        const val TABLE = -1
        const val WALL = -2
        /** ARCore's depth image on this kind of phone, landscape like the photo. */
        const val DW = 160
        const val DH = 90
        /** The photo's focal length, 640 px at 739 wide, scaled to the depth image. */
        const val F = 640f * DW / 739f
        const val CX = DW / 2f
        const val CY = DH / 2f
        const val SHRINK = 0.06f
        const val EDGE = 0.01f
        val PHOTO_POS = floatArrayOf(0f, 0.17f, 0.34f)
        const val PHOTO_PITCH = -11f
    }
}
