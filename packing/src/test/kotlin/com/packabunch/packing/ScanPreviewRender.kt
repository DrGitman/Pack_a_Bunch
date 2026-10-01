package com.packabunch.packing

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.test.Test

/**
 * Pictures of the measuring code at work, for a person to look at.
 *
 * Each scene is built to real sizes, rendered the way the phone's camera sees it (pinhole camera,
 * depth per pixel), and handed to the app's own code — DepthEdgeObjects, TwoViewSize, SpaceFitter.
 * The PNGs land in packing/build/scan-previews/ with the measured and true sizes side by side.
 *
 * These are simulations: perfect depth, no noise, no glare. They show the logic, not the phone.
 */
class ScanPreviewRender {

    private val out = File("build/scan-previews").apply { mkdirs() }

    // ---------------------------------------------------------------- tiny ray caster

    private class Hit(val t: Float, val n: FloatArray, val color: Color, val id: Int)

    private fun interface Prim { fun hit(o: FloatArray, d: FloatArray): Hit? }

    /** Axis-aligned rectangle on plane `axis = c`, bounded on the other two axes. */
    private fun rect(axis: Int, c: Float, a0: Float, a1: Float, b0: Float, b1: Float, color: Color, id: Int = -1) = Prim { o, d ->
        if (abs(d[axis]) < 1e-6f) return@Prim null
        val t = (c - o[axis]) / d[axis]
        if (t <= 1e-4f) return@Prim null
        val (ai, bi) = listOf(0, 1, 2).filter { it != axis }
        val pa = o[ai] + d[ai] * t; val pb = o[bi] + d[bi] * t
        if (pa !in a0..a1 || pb !in b0..b1) return@Prim null
        val n = FloatArray(3); n[axis] = if (d[axis] < 0) 1f else -1f
        Hit(t, n, color, id)
    }

    /** Box from (x0,0,z0) to (x1,h,z1), standing on the table. */
    private fun box(x0: Float, z0: Float, x1: Float, z1: Float, h: Float, color: Color, id: Int) = Prim { o, d ->
        var tmin = 1e-4f; var tmax = Float.MAX_VALUE; var axisHit = -1
        val lo = floatArrayOf(x0, 0f, z0); val hi = floatArrayOf(x1, h, z1)
        for (a in 0..2) {
            if (abs(d[a]) < 1e-9f) { if (o[a] < lo[a] || o[a] > hi[a]) return@Prim null; continue }
            var t1 = (lo[a] - o[a]) / d[a]; var t2 = (hi[a] - o[a]) / d[a]
            if (t1 > t2) { val s = t1; t1 = t2; t2 = s }
            if (t1 > tmin) { tmin = t1; axisHit = a }
            if (t2 < tmax) tmax = t2
            if (tmin > tmax) return@Prim null
        }
        if (axisHit < 0) return@Prim null
        val n = FloatArray(3); n[axisHit] = if (d[axisHit] < 0) 1f else -1f
        Hit(tmin, n, color, id)
    }

    /** Upright cylinder centred at (cx, cz), radius r, from the table to height h. */
    private fun cylinder(cx: Float, cz: Float, r: Float, h: Float, color: Color, id: Int) = Prim { o, d ->
        var best: Hit? = null
        val ox = o[0] - cx; val oz = o[2] - cz
        val a = d[0] * d[0] + d[2] * d[2]; val b = 2 * (ox * d[0] + oz * d[2]); val c = ox * ox + oz * oz - r * r
        val disc = b * b - 4 * a * c
        if (a > 1e-9f && disc >= 0) {
            val t = (-b - sqrt(disc)) / (2 * a)
            val y = o[1] + d[1] * t
            if (t > 1e-4f && y in 0f..h) {
                val px = ox + d[0] * t; val pz = oz + d[2] * t
                best = Hit(t, floatArrayOf(px / r, 0f, pz / r), color, id)
            }
        }
        if (abs(d[1]) > 1e-9f) {
            val t = (h - o[1]) / d[1]
            val px = ox + d[0] * t; val pz = oz + d[2] * t
            if (t > 1e-4f && px * px + pz * pz <= r * r && (best == null || t < best.t)) best = Hit(t, floatArrayOf(0f, 1f, 0f), color, id)
        }
        best
    }

    private class Render(
        val w: Int, val h: Int, val f: Float,
        val depthMm: IntArray, val world: Array<FloatArray?>, val ids: IntArray, val photo: BufferedImage,
        val forward: FloatArray,
    )

    private fun render(scene: List<Prim>, eye: FloatArray, target: FloatArray, w: Int = 256, h: Int = 192, f: Float = 200f): Render {
        val fw = norm(sub(target, eye))
        val right = norm(cross(fw, floatArrayOf(0f, 1f, 0f)))
        val up = cross(right, fw)
        val light = norm(floatArrayOf(0.3f, 1f, 0.5f))
        val depth = IntArray(w * h); val world = arrayOfNulls<FloatArray>(w * h); val ids = IntArray(w * h) { -1 }
        val photo = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        for (v in 0 until h) for (u in 0 until w) {
            val d = norm(FloatArray(3) { fw[it] + (u - w / 2f) / f * right[it] - (v - h / 2f) / f * up[it] })
            val hit = scene.mapNotNull { it.hit(eye, d) }.minByOrNull { it.t }
            val i = v * w + u
            if (hit == null) { photo.setRGB(u, v, 0x202020); continue }
            val p = FloatArray(3) { eye[it] + d[it] * hit.t }
            world[i] = p; ids[i] = hit.id
            depth[i] = (hit.t * dot(d, fw) * 1000f).toInt()
            val shade = 0.35f + 0.65f * max(0f, dot(hit.n, light))
            photo.setRGB(u, v, Color(
                (hit.color.red * shade).toInt().coerceIn(0, 255),
                (hit.color.green * shade).toInt().coerceIn(0, 255),
                (hit.color.blue * shade).toInt().coerceIn(0, 255),
            ).rgb)
        }
        return Render(w, h, f, depth, world, ids, photo, fw)
    }

    // ---------------------------------------------------------------- scenes

    private val tableColor = Color(58, 40, 32)
    private val wallColor = Color(214, 205, 190)

    private fun tableAndWall() = listOf(
        rect(1, 0f, -0.7f, 0.7f, -0.55f, 0.5f, tableColor),
        rect(2, -0.55f, -0.7f, 0.7f, 0f, 0.9f, wallColor),
    )

    private class Truth(val name: String, val w: Int, val d: Int, val h: Int)

    @Test
    fun `cup and food container on a table, front and side`() {
        val truths = mapOf(1 to Truth("Cup", 80, 80, 120), 2 to Truth("Food container", 180, 120, 70))
        val scene = tableAndWall() + listOf(
            cylinder(-0.10f, 0.0f, 0.04f, 0.12f, Color(30, 90, 200), 1),
            box(0.02f, -0.06f, 0.20f, 0.06f, 0.07f, Color(225, 225, 230), 2),
        )
        val front = render(scene, floatArrayOf(0f, 0.38f, 0.50f), floatArrayOf(0f, 0.04f, 0f))
        val side = render(scene, floatArrayOf(0.50f, 0.38f, 0.02f), floatArrayOf(0f, 0.04f, 0f))

        val views = TwoViewSizeByObject()
        val frontFound = measure(front); views.add(front, frontFound, yaw = 0f)
        val sideFound = measure(side); views.add(side, sideFound, yaw = 90f)

        val img = sheet(3, front.w, front.h, 2)
        panel(img, 0, 0, front, "Front: what the camera sees") { }
        panel(img, 1, 0, front, "Front: depth (near = warm)", depthView(front)) { }
        panel(img, 2, 0, front, "Front: found and measured") { g -> boxes(g, front, frontFound, truths) }
        panel(img, 0, 1, side, "Side view (stepped 90° round)") { }
        panel(img, 1, 1, side, "Side: depth", depthView(side)) { }
        panel(img, 2, 1, side, "Side: found and measured") { g -> boxes(g, side, sideFound, truths) }
        footer(img, truths.map { (id, t) -> "${t.name}: ${views.text(id)}   true ${t.w} × ${t.d} × ${t.h} mm" })
        ImageIO.write(img, "png", File(out, "1_cup_and_container.png"))
    }

    @Test
    fun `five things on a table`() {
        val truths = mapOf(
            1 to Truth("Bottle", 70, 70, 250), 2 to Truth("Mug", 90, 90, 100), 3 to Truth("Book (standing)", 150, 30, 220),
            4 to Truth("Jar", 100, 100, 80), 5 to Truth("Small box", 110, 80, 60),
        )
        val scene = tableAndWall() + listOf(
            cylinder(-0.24f, -0.12f, 0.035f, 0.25f, Color(90, 170, 220), 1),
            cylinder(-0.08f, 0.08f, 0.045f, 0.10f, Color(200, 60, 50), 2),
            box(0.02f, -0.18f, 0.17f, -0.15f, 0.22f, Color(60, 120, 70), 3),
            cylinder(0.24f, 0.04f, 0.05f, 0.08f, Color(220, 210, 160), 4),
            box(-0.02f, 0.16f, 0.09f, 0.24f, 0.06f, Color(170, 120, 200), 5),
        )
        val front = render(scene, floatArrayOf(0f, 0.55f, 0.65f), floatArrayOf(0f, 0.04f, -0.02f))
        val found = measure(front)
        val img = sheet(3, front.w, front.h, 1)
        panel(img, 0, 0, front, "What the camera sees") { }
        panel(img, 1, 0, front, "Depth", depthView(front)) { }
        panel(img, 2, 0, front, "Found: ${found.size} of ${truths.size}") { g -> boxes(g, front, found, truths) }
        footer(img, listOf(
            "Each box: W × H from this one view (w·d/f), true front W × H beside it.",
            "Depth (front to back) needs a side view; see picture 1.",
        ))
        ImageIO.write(img, "png", File(out, "2_five_things.png"))
    }

    @Test
    fun `a car boot, swept from behind`() {
        val (bw, bd, bh) = Triple(1.04f, 0.86f, 0.57f)
        val carpet = Color(45, 45, 50); val trim = Color(70, 70, 78)
        val x0 = -bw / 2; val x1 = bw / 2; val z0 = -bd; val z1 = 0f
        val scene = listOf(
            rect(1, 0f, x0, x1, z0, z1, carpet),
            rect(0, x0, 0f, bh, z0, z1, trim), rect(0, x1, 0f, bh, z0, z1, trim),
            rect(2, z0, x0, x1, 0f, bh, trim),
            rect(1, bh, x0, x1, z0, z1, Color(55, 55, 60)),
            rect(2, z1, x0, x1, -0.6f, 0.09f, Color(200, 200, 205)), // sill / bumper
        )
        // A sweep from behind the car, as the space scan asks for.
        val cams = ArrayList<PlanePoint>()
        val points = ArrayList<PlanePoint>()
        var shown: Render? = null
        for (k in 0 until 9) {
            val x = -0.6f + k * 0.15f
            val eye = floatArrayOf(x, 0.95f, 0.75f)
            val r = render(scene, eye, floatArrayOf(x * 0.3f, 0.15f, -0.45f), 160, 120, 120f)
            if (k == 4) shown = r
            cams += PlanePoint(eye[0] * 1000, eye[2] * 1000, eye[1] * 1000)
            for (i in r.world.indices step 2) r.world[i]?.let { p ->
                if (p[1] >= -0.001f && p[2] <= 0.001f && p[0] in x0 - 0.01f..x1 + 0.01f) points += PlanePoint(p[0] * 1000, p[2] * 1000, p[1] * 1000)
            }
        }
        val fit = SpaceFitter.fit(points, cams)
        val view = shown!!
        val img = sheet(2, 256, 192, 1)
        val big = scale(view.photo, 512, 384)
        val g = img.createGraphics(); g.drawImage(big, 0, HEADER, null)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(41, 34, 29); g.font = Font(Font.SANS_SERIF, Font.BOLD, 15)
        g.drawString("Car boot, seen from behind", 8, 19)
        g.drawString("From above: dashed grey = true, white = measured", 520, 19)
        // Plan view.
        val ox = 512 + 256; val oy = HEADER + 40; val s = 0.34f
        g.color = Color(40, 40, 40); g.fillRect(512, HEADER, 512, 384)
        g.color = Color(170, 170, 170); g.stroke = BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(8f, 6f), 0f)
        g.drawRect((ox + x0 * 1000 * s).toInt(), oy, (bw * 1000 * s).toInt(), (bd * 1000 * s).toInt())
        if (fit != null) {
            g.color = Color(255, 255, 255); g.stroke = BasicStroke(2f)
            val fw = fit.widthMm * s; val fd = fit.depthMm * s
            g.drawRect((ox + fit.centreXMm * s - fw / 2).toInt(), (oy + (fit.centreYMm + bd * 1000) * s - fd / 2).toInt(), fw.toInt(), fd.toInt())
        }
        g.dispose()
        footer(img, listOf(
            if (fit == null) "No fit." else "Measured ${fit.widthMm.toInt()} × ${fit.depthMm.toInt()} × ${fit.heightMm.toInt()} mm (W × D × H)",
            "True       ${(bw * 1000).toInt()} × ${(bd * 1000).toInt()} × ${(bh * 1000).toInt()} mm",
        ))
        ImageIO.write(img, "png", File(out, "3_car_boot.png"))
    }

    // ---------------------------------------------------------------- measuring, as the app does

    private fun measure(r: Render): List<DepthEdgeObjects.Found> {
        val background = BooleanArray(r.w * r.h) { i -> r.world[i]?.let { it[1] < StandingObjects.MIN_HEIGHT_M } ?: true }
        val heights = IntArray(r.w * r.h) { i -> r.world[i]?.let { (it[1] * 1000).toInt() } ?: 0 }
        return DepthEdgeObjects.find(r.depthMm, r.w, r.h, r.f, r.f, background, heights)
    }

    /** Which true object a found box is on: the one under most of its pixels. */
    private fun idOf(r: Render, o: DepthEdgeObjects.Found): Int {
        val count = HashMap<Int, Int>()
        for (y in o.top until o.bottom) for (x in o.left until o.right) { val id = r.ids[y * r.w + x]; if (id > 0) count[id] = (count[id] ?: 0) + 1 }
        return count.maxByOrNull { it.value }?.key ?: -1
    }

    private inner class TwoViewSizeByObject {
        val sizes = HashMap<Int, TwoViewSize>()
        fun add(r: Render, found: List<DepthEdgeObjects.Found>, yaw: Float) {
            for (o in found) sizes.getOrPut(idOf(r, o)) { TwoViewSize() }.add(yaw, o.widthMm, o.heightMm)
        }
        fun text(id: Int): String {
            val s = sizes[id] ?: return "not found"
            fun f(v: Float?) = v?.toInt()?.toString() ?: "?"
            return "measured ${f(s.widthMm)} × ${f(s.depthMm)} × ${f(s.heightMm)} mm"
        }
    }

    // ---------------------------------------------------------------- drawing

    private val SCALE = 2
    private val HEADER = 28

    private fun sheet(cols: Int, w: Int, h: Int, rows: Int) =
        BufferedImage(cols * w * SCALE, rows * (h * SCALE + HEADER) + 110, BufferedImage.TYPE_INT_RGB).also {
            val g = it.createGraphics(); g.color = Color(247, 239, 230); g.fillRect(0, 0, it.width, it.height); g.dispose()
        }

    private fun panel(img: BufferedImage, col: Int, row: Int, r: Render, title: String, picture: BufferedImage = r.photo, draw: (java.awt.Graphics2D) -> Unit) {
        val x = col * r.w * SCALE; val y = row * (r.h * SCALE + HEADER)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color(41, 34, 29); g.font = Font(Font.SANS_SERIF, Font.BOLD, 15); g.drawString(title, x + 8, y + 19)
        g.drawImage(scale(picture, r.w * SCALE, r.h * SCALE), x, y + HEADER, null)
        g.translate(x, y + HEADER); g.scale(SCALE.toDouble(), SCALE.toDouble())
        draw(g)
        g.dispose()
    }

    private fun boxes(g: java.awt.Graphics2D, r: Render, found: List<DepthEdgeObjects.Found>, truths: Map<Int, Truth>) {
        g.font = Font(Font.SANS_SERIF, Font.BOLD, 6)
        for (o in found) {
            val t = truths[idOf(r, o)]
            g.color = Color.WHITE; g.stroke = BasicStroke(1f)
            g.drawRect(o.left, o.top, o.right - o.left, o.bottom - o.top)
            val text = "${t?.name ?: "?"}: ${o.widthMm.toInt()}×${o.heightMm.toInt()}" + (t?.let { " (true ${it.w}×${it.h})" } ?: "")
            val tw = g.fontMetrics.stringWidth(text)
            g.color = Color(0, 0, 0, 170); g.fillRect(o.left, o.top - 8, tw + 4, 8)
            g.color = Color.WHITE; g.drawString(text, o.left + 2, o.top - 2)
        }
    }

    private fun depthView(r: Render) = BufferedImage(r.w, r.h, BufferedImage.TYPE_INT_RGB).also { img ->
        val valid = r.depthMm.filter { it > 0 }
        val lo = valid.minOrNull() ?: 0; val hi = valid.maxOrNull() ?: 1
        for (i in r.depthMm.indices) {
            val d = r.depthMm[i]
            val t = if (d <= 0) 0f else 1f - (d - lo).toFloat() / max(1, hi - lo)
            img.setRGB(i % r.w, i / r.w, Color.HSBtoRGB(0.66f - 0.66f * t, 0.8f, if (d <= 0) 0f else 0.95f))
        }
    }

    private fun label(g: java.awt.Graphics2D, col: Int, row: Int, text: String) {
        g.color = Color(41, 34, 29); g.font = Font(Font.SANS_SERIF, Font.BOLD, 15)
        g.drawString(text, col * 256 + 8, row * (192 + HEADER) + 19)
    }

    private fun footer(img: BufferedImage, lines: List<String>) {
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color(41, 34, 29); g.font = Font(Font.MONOSPACED, Font.BOLD, 14)
        var y = img.height - 110 + 24
        for (l in lines) { g.drawString(l, 10, y); y += 20 }
        g.font = Font(Font.SANS_SERIF, Font.ITALIC, 12); g.color = Color(110, 98, 91)
        g.drawString("Simulation: real sizes, perfect depth, no noise or glare. Shows the method, not the phone.", 10, img.height - 10)
        g.dispose()
    }

    private fun scale(src: BufferedImage, w: Int, h: Int) = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB).also {
        val g = it.createGraphics(); g.drawImage(src, 0, 0, w, h, null); g.dispose()
    }

    private fun sub(a: FloatArray, b: FloatArray) = FloatArray(3) { a[it] - b[it] }
    private fun dot(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    private fun cross(a: FloatArray, b: FloatArray) = floatArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
    private fun norm(a: FloatArray): FloatArray { val l = sqrt(dot(a, a)); return FloatArray(3) { a[it] / l } }
    @Suppress("unused") private fun clamp(v: Float) = min(1f, max(0f, v))
}
