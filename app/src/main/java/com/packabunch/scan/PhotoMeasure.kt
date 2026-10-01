package com.packabunch.scan

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.packabunch.packing.CardFinder
import com.packabunch.packing.ColorMask
import com.packabunch.packing.DetectionPoints
import com.packabunch.packing.Dimensions
import com.packabunch.packing.FittedObject
import com.packabunch.packing.ItemForm
import com.packabunch.packing.FormFamily
import com.packabunch.packing.OutlineGeometry
import com.packabunch.packing.PhotoGeometry
import com.packabunch.packing.PhotoScale
import com.packabunch.packing.PlanarPose
import com.packabunch.packing.PlanePoint
import com.packabunch.packing.ShapeFamily
import com.packabunch.packing.SpaceBox
import com.packabunch.packing.StandingObjects
import com.packabunch.packing.YoloxDecode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

/** One photo to measure: upright, its lens geometry in its own pixels, and which way was up. */
class PhotoInput(
    val picture: Bitmap,
    val intrinsics: PlanarPose.Intrinsics,
    /** Up in camera axes (x right, y down, z forward), from the phone's gravity sensor; null for a gallery photo. */
    val up: DoubleArray?,
)

/** How far the work has got, for the Finding sizes sheet. */
data class PhotoProgress(
    /** 0..3: the four steps on the sheet. */
    val stage: Int,
    /** 0..1 within that step. */
    val fraction: Float,
    /** Things found so far (items only). */
    val found: Int = 0,
    /** Objects traced so far. */
    val traced: Int = 0,
    /** Outlines drawn so far, x, y pairs in 0..1 of the picture, each closed. */
    val outlines: List<FloatArray> = emptyList(),
)

/** One thing measured from a photo. Sizes are camera estimates; the person checks each one. */
class PhotoItem(
    val name: String?,
    /** `[left, top, right, bottom]` in 0..1 of the picture. */
    val box: FloatArray,
    /** Its fitted 3D outline drawn on the picture: polylines, x, y pairs in 0..1. */
    val outline: List<FloatArray>,
    /** Where its name tag goes, 0..1 of the picture: the top of its outline. */
    val tag: Pair<Float, Float>,
    /** Its footprint on the surface, a closed polygon in 0..1 of the picture, for the faint glow under it. */
    val footprint: FloatArray?,
    val dimensions: Dimensions,
    val shape: ShapeFamily,
    val form: ItemForm?,
    /** Its own crop, for its photo. Never leaves the phone. */
    val crop: Bitmap?,
)

/** A space measured from a photo. */
class PhotoSpace(
    val dimensions: Dimensions,
    /** Width × height of the way in, mm, if it has one. */
    val opening: Pair<Int, Int>?,
    /** The box's edges on the picture, x, y pairs in 0..1, with whether each is part of the opening. */
    val edges: List<Pair<FloatArray, Boolean>>,
    /** The floor of the space, four corners in 0..1 of the picture, for the faint glow on it; null if off the picture. */
    val floor: FloatArray?,
    /** Where the W, D, H pills and the opening tag go, 0..1 of the picture; null when off the picture. */
    val widthAt: Pair<Float, Float>?,
    val depthAt: Pair<Float, Float>?,
    val heightAt: Pair<Float, Float>?,
    val openingAt: Pair<Float, Float>?,
)

/**
 * Measures everything in one photo, on the phone.
 *
 * Items: (1) find the things — YOLOX, which also names them, and ML Kit for anything YOLOX does not
 * know, such as cartons; (2) trace each one's outline (its colour mask); (3) work out the sizes —
 * the depth model shaped by gravity and the surface ([PhotoGeometry]), scaled by a card if there is
 * one or by the usual sizes of the things recognised ([PhotoScale]), each object's own points fitted
 * by the Python engine (box, cylinder, tapered, ball); (4) name them.
 *
 * Space: (1) find the floor; (2) the walls and the opening (the Python space fitter); (3) the inside
 * size; (4) a look for wheel arches.
 *
 * Nothing leaves the phone. Every size is labelled as coming from a photo.
 */
class PhotoMeasure(private val context: Context) {

    private val math = PythonEngine.lazy(context)

    suspend fun items(input: PhotoInput, maxItems: Int, onProgress: (PhotoProgress) -> Unit): List<PhotoItem> = withContext(Dispatchers.Default) {
        val pic = input.picture
        val w = pic.width; val h = pic.height

        // 1. Finding the things.
        onProgress(PhotoProgress(0, 0.1f))
        val yolo = runCatching { YoloxDetector(context).use { it.detect(pic) } }.getOrElse { Log.w(SCAN_TAG, "yolox", it); emptyList() }
        onProgress(PhotoProgress(0, 0.4f))
        val mlkit = runCatching { ItemScanDetector().let { d -> d.detect(pic, 0, 0L).also { d.close() } } }.getOrElse { emptyList() }
        val card = findCard(pic, input.intrinsics)
        val boxes = ArrayList<Pair<FloatArray, String?>>()
        for (d in yolo) boxes += d.box to d.label
        for (m in mlkit) if (boxes.none { StandingObjects.overlap(it.first, m.upright) > 0.3f }) boxes += m.upright to null
        // The card is the reference, not an item; and anything filling the frame is the scene.
        val kept = boxes.filter { (b, _) ->
            val area = (b[2] - b[0]) * (b[3] - b[1])
            area in 0.002f..0.6f && (card == null || StandingObjects.overlap(b, card.second) < 0.3f)
        }.take(maxItems)
        onProgress(PhotoProgress(0, 0.7f, found = kept.size))
        val rel = runCatching { MidasDepth(context).use { it.run(pic) } }.getOrElse { Log.w(SCAN_TAG, "depth", it); null }
        onProgress(PhotoProgress(0, 1f, found = kept.size))
        if (kept.isEmpty()) return@withContext emptyList()

        // 2. Tracing their outlines.
        val tw = 320; val th = (h * tw / w.toFloat()).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(pic, tw, th, true)
        val argb = IntArray(tw * th).also { small.getPixels(it, 0, tw, 0, 0, tw, th) }
        if (small !== pic) small.recycle()
        val masks = ArrayList<BooleanArray?>(); val outlines = ArrayList<FloatArray>()
        for ((i, pair) in kept.withIndex()) {
            val mask = ColorMask.segment(argb, tw, th, pair.first)
            masks += mask
            outlines += (mask?.let { ColorMask.hull(it, tw, th) } ?: boxOutline(pair.first)).let(::closed)
            onProgress(PhotoProgress(1, (i + 1f) / kept.size, found = kept.size, traced = i + 1, outlines = outlines.toList()))
        }

        // 3. Working out the sizes.
        val up = input.up ?: PhotoGeometry.upForPitch(35.0)
        val g = MidasDepth.SIZE
        fun inAnyBox(gx: Int, gy: Int): Boolean {
            val u = (gx + 0.5f) / g; val v = (gy + 0.5f) / g
            return kept.any { (b, _) -> u in b[0] - 0.02f..b[2] + 0.02f && v in b[1] - 0.02f..b[3] + 0.02f }
        }
        val geo = rel?.let { PhotoGeometry.fit(input.intrinsics, up, it, g, w, h, { gx, gy -> gy > g * 0.25 && !inAnyBox(gx, gy) }) }
        onProgress(PhotoProgress(2, 0.3f, found = kept.size, traced = kept.size, outlines = outlines))
        // Each object's own depth cells: inside its mask where it has one, else inside its box.
        fun cellsOf(i: Int): List<Pair<Int, Int>> {
            val (b, _) = kept[i]; val mask = masks[i]
            val out = ArrayList<Pair<Int, Int>>()
            for (gy in (b[1] * g).toInt().coerceIn(0, g - 1)..(b[3] * g).toInt().coerceIn(0, g - 1))
                for (gx in (b[0] * g).toInt().coerceIn(0, g - 1)..(b[2] * g).toInt().coerceIn(0, g - 1)) {
                    val u = (gx + 0.5f) / g; val v = (gy + 0.5f) / g
                    if (mask != null && !mask[(v * th).toInt().coerceIn(0, th - 1) * tw + (u * tw).toInt().coerceIn(0, tw - 1)]) continue
                    out += gx to gy
                }
            return out
        }
        val cells = kept.indices.map(::cellsOf)
        // The camera's height above the surface: the card, else the things recognised, else typical.
        val heightM = card?.first ?: geo?.let { gm ->
            PhotoScale.fromPriors(kept.indices.mapNotNull { i ->
                val label = kept[i].second ?: return@mapNotNull null
                val pts = cells[i].mapNotNull { (gx, gy) -> gm.pointAt(gx, gy, 1.0) }.filter { it.hMm > 4f }
                if (pts.size < 12) return@mapNotNull null
                val (hh, longest) = extents(pts)
                Triple(label, hh, longest)
            })
        } ?: PhotoScale.TYPICAL_ITEMS_M
        Log.i(SCAN_TAG, "photo items: found=${kept.size} geometry=${geo != null} cameraHeight=%.2fm from %s".format(heightM,
            if (card != null) "card" else if (geo != null && heightM != PhotoScale.TYPICAL_ITEMS_M) "recognised things" else "typical height"))

        val results = ArrayList<PhotoItem>()
        for (i in kept.indices) {
            val (b, label) = kept[i]
            val camera = geo?.camera(heightM)
            val pts = if (geo == null) emptyList() else cells[i].mapNotNull { (gx, gy) -> geo.pointAt(gx, gy, heightM) }.filter { it.hMm > 6f }
            val fit: FittedObject? = if (geo != null && camera != null && pts.size >= 12) runCatching { math.fit(pts, listOf(camera), 4f, null) }.getOrNull() else null
            val dims = when {
                fit != null -> Dimensions(fit.widthMm.toInt().coerceAtLeast(1), fit.depthMm.toInt().coerceAtLeast(1), fit.heightMm.toInt().coerceAtLeast(1))
                pts.size >= 6 -> extents(pts).let { (hh, longest) -> Dimensions(longest.toInt(), (longest * 0.7f).toInt().coerceAtLeast(1), hh.toInt().coerceAtLeast(1)) }
                else -> flatEstimate(b, input.intrinsics, w, h, heightM)
            }
            val outline3d = if (fit != null && geo != null && camera != null) {
                OutlineGeometry.of(fit, camera).mapNotNull { s ->
                    val a = geo.pixelOf(s.a, heightM) ?: return@mapNotNull null
                    val c = geo.pixelOf(s.b, heightM) ?: return@mapNotNull null
                    floatArrayOf((a[0] / w).toFloat(), (a[1] / h).toFloat(), (c[0] / w).toFloat(), (c[1] / h).toFloat())
                }
            } else listOf(outlines[i])
            val footprint = if (fit != null && geo != null) {
                val pts = OutlineGeometry.footprint(fit).map { geo.pixelOf(it, heightM) }
                if (pts.all { it != null }) FloatArray(pts.size * 2) { j -> (pts[j / 2]!![j % 2] / (if (j % 2 == 0) w else h)).toFloat() } else null
            } else null
            val top = outline3d.flatMap { l -> (0 until l.size / 2).map { j -> l[2 * j] to l[2 * j + 1] } }
            val tag = if (top.isEmpty()) ((b[0] + b[2]) / 2 to b[1]) else ((top.minOf { it.first } + top.maxOf { it.first }) / 2 to top.minOf { it.second })
            results += PhotoItem(
                name = label?.let(YoloxDecode::itemName), box = b, outline = outline3d, tag = tag, footprint = footprint,
                dimensions = dims, shape = fit?.shape ?: ShapeFamily.BOX,
                form = fit?.let(ItemForm::fromFit), crop = crop(pic, b),
            )
            onProgress(PhotoProgress(2, 0.3f + 0.7f * (i + 1) / kept.size, found = kept.size, traced = kept.size, outlines = outlines))
        }

        // 4. Naming them: YOLOX's names already; the on-phone labeller for anything it did not know.
        val recogniser = com.packabunch.data.catalogue.ItemRecogniser()
        val named = results.mapIndexed { i, item ->
            onProgress(PhotoProgress(3, (i + 0.5f) / results.size, found = kept.size, traced = kept.size, outlines = outlines))
            if (item.name != null || item.crop == null) item
            else {
                val guess = runCatching { recogniser.scanCategory(item.crop) }.getOrNull()?.replaceFirstChar { it.uppercase() }
                if (guess == null) item else PhotoItem(guess, item.box, item.outline, item.tag, item.footprint, item.dimensions,
                    item.shape, item.form ?: ItemForm.guess(guess), item.crop)
            }
        }
        onProgress(PhotoProgress(3, 1f, found = kept.size, traced = kept.size, outlines = outlines))
        named
    }

    suspend fun space(input: PhotoInput, spaceName: String?, onProgress: (PhotoProgress) -> Unit): PhotoSpace? = withContext(Dispatchers.Default) {
        val pic = input.picture
        val w = pic.width; val h = pic.height
        val g = MidasDepth.SIZE

        // 1. Finding the floor.
        onProgress(PhotoProgress(0, 0.1f))
        val rel = runCatching { MidasDepth(context).use { it.run(pic) } }.getOrElse { Log.w(SCAN_TAG, "depth", it); return@withContext null }
        val card = findCard(pic, input.intrinsics)
        val up = input.up ?: PhotoGeometry.upForPitch(30.0)
        // The floor of the space: the lower middle of the picture, where the instructions put it.
        val geo = PhotoGeometry.fit(input.intrinsics, up, rel, g, w, h, { gx, gy -> gy > g * 0.45 && gx > g * 0.2 && gx < g * 0.8 })
            ?: return@withContext null
        val heightM = card?.first ?: PhotoScale.typicalForSpace(spaceName)
        onProgress(PhotoProgress(0, 1f))

        // 2. The walls and the opening: the Python space fitter, on everything in the middle of the picture.
        val samples = ArrayList<DetectionPoints.Sample>()
        for (gy in (g * 0.05).toInt() until g step 2) for (gx in (g * 0.08).toInt() until (g * 0.92).toInt() step 2) {
            val p = geo.pointAt(gx, gy, heightM) ?: continue
            if (p.hMm < -60f || p.hMm > 3000f) continue
            samples += DetectionPoints.Sample(p, gx > g * 0.3 && gx < g * 0.7 && gy > g * 0.3 && gy < g * 0.7)
        }
        onProgress(PhotoProgress(1, 0.4f))
        val camera = geo.camera(heightM)
        val keep = runCatching { math.selectSpace(samples, 3000f).toHashSet() }.getOrElse { samples.indices.toHashSet() }
        val points = samples.indices.filter { it in keep || samples[it].point.hMm <= 25f }.map { samples[it].point }
        val box = runCatching { math.fitSpace(points, listOf(camera), null) }.getOrNull()
        onProgress(PhotoProgress(1, 1f))

        // 3. Working out the inside size.
        val corners = box?.let { boxCorners(it.centreXMm, it.centreYMm, it.yawDegrees, it.widthMm, it.depthMm, it.heightMm) }
            ?: fallbackBox(points) ?: return@withContext null
        val (cw, cd, ch) = corners.second
        Log.i(SCAN_TAG, "photo space: points=${points.size} fitted=${box != null} cameraHeight=%.2fm from %s size=%.0fx%.0fx%.0fmm".format(
            heightM, if (card != null) "card" else "typical height", cw, cd, ch))
        onProgress(PhotoProgress(2, 1f))

        // 4. Checking for wheel arches — the plan uses the clear box inside them, which is what was fitted.
        onProgress(PhotoProgress(3, 1f))
        val c = corners.first // fl0, fr0, br0, bl0, fl1, fr1, br1, bl1
        fun px(p: PlanePoint) = geo.pixelOf(p, heightM)?.let { floatArrayOf((it[0] / w).toFloat(), (it[1] / h).toFloat()) }
        val edgeIdx = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 4 to 5, 5 to 6, 6 to 7, 7 to 4, 0 to 4, 1 to 5, 2 to 6, 3 to 7)
        val edges = edgeIdx.mapNotNull { (a, b) ->
            val pa = px(c[a]) ?: return@mapNotNull null; val pb = px(c[b]) ?: return@mapNotNull null
            floatArrayOf(pa[0], pa[1], pb[0], pb[1]) to ((a == 4 && b == 5) || (a == 0 && b == 4) || (a == 1 && b == 5))
        }
        fun mid(a: PlanePoint, b: PlanePoint) = PlanePoint((a.xMm + b.xMm) / 2, (a.yMm + b.yMm) / 2, (a.hMm + b.hMm) / 2)
        fun at(p: PlanePoint) = px(p)?.takeIf { it[0] in 0f..1f && it[1] in 0f..1f }?.let { it[0] to it[1] }
        val opening = box?.opening?.let { it.widthMm to it.heightMm } ?: (cw.toInt() to ch.toInt())
        val floorCorners = (0..3).map { px(c[it]) }
        PhotoSpace(
            dimensions = Dimensions(cw.toInt().coerceAtLeast(1), cd.toInt().coerceAtLeast(1), ch.toInt().coerceAtLeast(1)),
            opening = opening,
            edges = edges,
            floor = if (floorCorners.all { it != null }) FloatArray(8) { j -> floorCorners[j / 2]!![j % 2] } else null,
            widthAt = at(mid(c[0], c[1])),
            depthAt = at(mid(c[3], c[0])),
            heightAt = at(mid(c[1], c[2]).let { PlanePoint(it.xMm, it.yMm, ch / 2) }),
            openingAt = at(mid(c[4], c[5])),
        )
    }

    // -- helpers ---------------------------------------------------------------------------------

    /** A card or sheet of A4 lying flat in the photo: the camera's height above it, and its box. */
    private fun findCard(pic: Bitmap, k: PlanarPose.Intrinsics): Pair<Double, FloatArray>? = runCatching {
        val s = (960.0 / pic.width).coerceAtMost(1.0)
        val w = (pic.width * s).toInt(); val h = (pic.height * s).toInt()
        val small = Bitmap.createScaledBitmap(pic, w, h, true)
        val argb = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
        if (small !== pic) small.recycle()
        val gray = IntArray(w * h) { i -> val p = argb[i]; (((p shr 16) and 0xFF) * 77 + ((p shr 8) and 0xFF) * 150 + (p and 0xFF) * 29) shr 8 }
        val ks = PlanarPose.Intrinsics(k.fx * s, k.fy * s, k.cx * s, k.cy * s)
        for (q in CardFinder.find(gray, w, h)) for (ref in Reference.entries) {
            val fit = PlanarPose.fromQuad(q.corners, ref.longM, ref.shortM, ks, null, q.sides) ?: continue
            val height = fit.pose.cameraPosition[1]
            if (height !in PhotoScale.MIN_M..PhotoScale.MAX_M) continue
            val xs = (0 until 4).map { q.corners[2 * it] / w }; val ys = (0 until 4).map { q.corners[2 * it + 1] / h }
            return@runCatching height to floatArrayOf(xs.min().toFloat(), ys.min().toFloat(), xs.max().toFloat(), ys.max().toFloat())
        }
        null
    }.getOrNull()

    /** Height and longest footprint side of a set of points, robust to stray ones. */
    private fun extents(pts: List<PlanePoint>): Pair<Float, Float> {
        val hs = pts.map { it.hMm }.sorted()
        val xs = pts.map { it.xMm }.sorted(); val ys = pts.map { it.yMm }.sorted()
        fun span(v: List<Float>) = v[(v.size * 0.95).toInt().coerceAtMost(v.size - 1)] - v[(v.size * 0.05).toInt()]
        return hs[(hs.size * 0.95).toInt().coerceAtMost(hs.size - 1)] to maxOf(span(xs), span(ys))
    }

    /** No depth: the box's pixels at the typical distance. A rough figure the person must check. */
    private fun flatEstimate(b: FloatArray, k: PlanarPose.Intrinsics, w: Int, h: Int, heightM: Double): Dimensions {
        val z = heightM * 1.2 * 1000
        val wd = ((b[2] - b[0]) * w * z / k.fx).toInt().coerceAtLeast(1)
        val ht = ((b[3] - b[1]) * h * z / k.fy).toInt().coerceAtLeast(1)
        return Dimensions(wd, (wd * 0.7).toInt().coerceAtLeast(1), ht)
    }

    private fun boxOutline(b: FloatArray) = floatArrayOf(b[0], b[1], b[2], b[1], b[2], b[3], b[0], b[3])

    private fun closed(p: FloatArray) = p + floatArrayOf(p[0], p[1])

    private fun crop(pic: Bitmap, b: FloatArray): Bitmap? {
        val m = 0.02f
        val x = ((b[0] - m) * pic.width).toInt().coerceIn(0, pic.width - 1)
        val y = ((b[1] - m) * pic.height).toInt().coerceIn(0, pic.height - 1)
        val cw = ((b[2] - b[0] + 2 * m) * pic.width).toInt().coerceAtMost(pic.width - x)
        val chh = ((b[3] - b[1] + 2 * m) * pic.height).toInt().coerceAtMost(pic.height - y)
        if (cw < 16 || chh < 16) return null
        return runCatching { Bitmap.createBitmap(pic, x, y, cw, chh) }.getOrNull()
    }

    /** The eight corners of a box (front-left, front-right, back-right, back-left; bottom then top) and its W, D, H. */
    private fun boxCorners(cx: Float, cy: Float, yawDeg: Float, w: Float, d: Float, h: Float): Pair<List<PlanePoint>, Triple<Float, Float, Float>> {
        val r = Math.toRadians(yawDeg.toDouble()); val c = cos(r).toFloat(); val s = sin(r).toFloat()
        fun at(u: Float, v: Float, z: Float) = PlanePoint(cx + u * c - v * s, cy + u * s + v * c, z)
        val hw = w / 2; val hd = d / 2
        return listOf(at(-hw, -hd, 0f), at(hw, -hd, 0f), at(hw, hd, 0f), at(-hw, hd, 0f),
            at(-hw, -hd, h), at(hw, -hd, h), at(hw, hd, h), at(-hw, hd, h)) to Triple(w, d, h)
    }

    /** When the space fitter finds nothing: the floor's extent and the height of what stands round it. */
    private fun fallbackBox(points: List<PlanePoint>): Pair<List<PlanePoint>, Triple<Float, Float, Float>>? {
        val floor = points.filter { kotlin.math.abs(it.hMm) < 30f }
        if (floor.size < 30) return null
        val xs = floor.map { it.xMm }.sorted(); val ys = floor.map { it.yMm }.sorted()
        fun q(v: List<Float>, f: Double) = v[(v.size * f).toInt().coerceIn(0, v.size - 1)]
        val x0 = q(xs, 0.03); val x1 = q(xs, 0.97); val y0 = q(ys, 0.03); val y1 = q(ys, 0.97)
        val walls = points.filter { it.hMm > 30f && it.xMm in x0..x1 && it.yMm in y0..y1 + 100f }.map { it.hMm }.sorted()
        val height = if (walls.size >= 10) q(walls, 0.95) else (x1 - x0) * 0.6f
        return boxCorners((x0 + x1) / 2, (y0 + y1) / 2, 0f, x1 - x0, y1 - y0, height)
    }
}

/** Closes models after one photo: they are big, and a photo scan is a one-off. */
private inline fun <T : java.io.Closeable, R> T.use(block: (T) -> R): R = try { block(this) } finally { close() }

@Suppress("unused") private val keepImports = listOf(SpaceBox::class, FormFamily::class)
