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
import kotlin.math.abs
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
    /**
     * Outlines drawn so far, in the order they were found: one group per object (or per part of a
     * space — floor, walls, opening), each a list of line pieces, x, y pairs in 0..1 of the picture.
     * The sheet draws each group's pieces in one after another.
     */
    val outlines: List<List<FloatArray>> = emptyList(),
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
        // A still photo can afford to look harder than a live feed: a lower YOLOX bar, and ML Kit in
        // single-image mode.
        val yolo = runCatching { YoloxDetector(context).use { it.detect(pic, PHOTO_MIN_SCORE) } }.getOrElse { Log.w(SCAN_TAG, "yolox", it); emptyList() }
        onProgress(PhotoProgress(0, 0.4f))
        val mlkit = runCatching { ItemScanDetector(stream = false).let { d -> d.detect(pic, 0, 0L).also { d.close() } } }.getOrElse { emptyList() }
        val boxes = ArrayList<Pair<FloatArray, String?>>()
        for (d in yolo) boxes += d.box to d.label
        for (m in mlkit) if (boxes.none { StandingObjects.overlap(it.first, m.upright) > 0.3f }) boxes += m.upright to null
        onProgress(PhotoProgress(0, 0.5f, found = boxes.size))
        val rel = runCatching { MidasDepth(context).use { it.run(pic) } }.getOrElse { Log.w(SCAN_TAG, "depth", it); null }
        val g = MidasDepth.SIZE
        val up = input.up ?: upFromDepth(rel, input, boxes.map { it.first })
        val card = findCard(pic, input.intrinsics, up)
        // The card is the reference, not an item (nor is its holder); anything filling the frame is the scene.
        val detected = boxes.filter { (b, _) ->
            val area = (b[2] - b[0]) * (b[3] - b[1])
            area in 0.002f..0.6f && (card == null || !card.covers(b))
        }
        val tw = 320; val th = (h * tw / w.toFloat()).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(pic, tw, th, true)
        val argb = IntArray(tw * th).also { small.getPixels(it, 0, tw, 0, 0, tw, th) }
        if (small !== pic) small.recycle()
        // Anything standing up off the surface that neither detector named — HarshdeepJ's depth
        // stage, so an unknown thing is still found and measured, just without a name. Depth alone
        // also rises at walls and glare, so it counts only where colour or an edge agrees.
        val fromDepth = rel?.let { standingThings(it, input, up, card?.heightM, detected.map { d -> d.first }) }.orEmpty()
            .filter { b -> (card == null || !card.covers(b)) && ColorMask.segment(argb, tw, th, b) != null }
        val kept = (detected + fromDepth.map { it to null }).take(maxItems)
        fun FloatArray.s() = joinToString(",", "[", "]") { "%.2f".format(it) }
        Log.i(SCAN_TAG, "photo found: yolox=${yolo.map { it.label + it.box.s() }} mlkit=${mlkit.map { it.upright.s() }} " +
            "depth=${fromDepth.map { it.s() }} card=${card?.box?.s()} kept=${kept.size}")
        onProgress(PhotoProgress(0, 1f, found = kept.size))
        if (kept.isEmpty()) return@withContext emptyList()

        // 2. Tracing their outlines: each one's mask, then its 3D shape fitted to its own depth —
        //    the outline the sheet draws in piece by piece, object after object.
        val masks = kept.map { (b, _) -> ColorMask.segment(argb, tw, th, b) }
        onProgress(PhotoProgress(1, 0.1f, found = kept.size))

        fun inAnyBox(gx: Int, gy: Int): Boolean {
            val u = (gx + 0.5f) / g; val v = (gy + 0.5f) / g
            return kept.any { (b, _) -> u in b[0] - 0.02f..b[2] + 0.02f && v in b[1] - 0.02f..b[3] + 0.02f }
        }
        val geo = rel?.let { PhotoGeometry.fit(input.intrinsics, up, it, g, w, h, { gx, gy -> gy > g * 0.5 && !inAnyBox(gx, gy) }) }
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
        val heightM = card?.heightM ?: geo?.let { card?.heightVia(it) } ?: geo?.let { gm ->
            PhotoScale.fromPriors(kept.indices.mapNotNull { i ->
                val label = kept[i].second ?: return@mapNotNull null
                val pts = cells[i].mapNotNull { (gx, gy) -> gm.pointAt(gx, gy, 1.0) }.filter { it.hMm > 4f }
                if (pts.size < 12) return@mapNotNull null
                val (hh, longest) = extents(pts)
                label to maxOf(hh, longest)
            })
        } ?: PhotoScale.TYPICAL_ITEMS_M
        Log.i(SCAN_TAG, "photo items: found=${kept.size} geometry=${geo != null} cameraHeight=%.2fm from %s".format(heightM,
            if (card != null) "card" else if (geo != null && heightM != PhotoScale.TYPICAL_ITEMS_M) "recognised things" else "typical height"))
        val camera = geo?.camera(heightM)
        fun toPicture(p: PlanePoint) = geo?.pixelOf(p, heightM)?.let { floatArrayOf((it[0] / w).toFloat(), (it[1] / h).toFloat()) }

        val fits = ArrayList<FittedObject?>(); val pointsOf = ArrayList<List<PlanePoint>>()
        val outlines = ArrayList<List<FloatArray>>()
        for (i in kept.indices) {
            val pts = if (geo == null) emptyList() else settle(trim(cells[i].mapNotNull { (gx, gy) -> geo.pointAt(gx, gy, heightM) })).filter { it.hMm > 6f }
            // A fit wider than the points it came from is the fitter's rectangle running off along
            // a thin slanted cloud (a flask seen side-on came out 57 cm): the points' own spread wins.
            // ponytail: 1.5× bar from the flask photo; find the overshoot in the fitter if real fits get dropped.
            val fit = if (geo != null && camera != null && pts.size >= 12) runCatching { math.fit(pts, listOf(camera), 4f, null) }.getOrNull()
                ?.takeIf { f -> maxOf(f.widthMm, f.depthMm) <= 1.5f * extents(pts).second + 20f } else null
            fits += fit; pointsOf += pts
            if (pts.isNotEmpty()) {
                fun q(v: List<Float>) = v.sorted().let { s -> "%.0f/%.0f/%.0f".format(s[s.size / 20], s[s.size / 2], s[s.size * 19 / 20]) }
                Log.i(SCAN_TAG, "photo item $i: ${pts.size} pts x=${q(pts.map { it.xMm })} y=${q(pts.map { it.yMm })} h=${q(pts.map { it.hMm })} " +
                    "fit=${fit?.let { "${it.widthMm.toInt()}x${it.depthMm.toInt()}x${it.heightMm.toInt()} ${it.shape}" }}")
            }
            // Its own shape first — the traced contour of its mask, whatever it is shaped like —
            // then the 3D box it was measured as: base, top, sides, the order they are drawn in.
            val silhouette = masks[i]?.let { ColorMask.contour(it, tw, th) }
            val box3d = if (fit != null && camera != null) {
                OutlineGeometry.of(fit, camera).sortedBy { s -> if (s.a.hMm < 1f && s.b.hMm < 1f) 0 else if (s.a.hMm > 1f && s.b.hMm > 1f) 1 else 2 }
                    .mapNotNull { s -> val a = toPicture(s.a) ?: return@mapNotNull null; val c = toPicture(s.b) ?: return@mapNotNull null; floatArrayOf(a[0], a[1], c[0], c[1]) }
            } else emptyList()
            outlines += when {
                silhouette != null -> listOf(closed(silhouette)) + box3d
                box3d.isNotEmpty() -> box3d
                else -> listOf(closed(boxOutline(kept[i].first)))
            }
            onProgress(PhotoProgress(1, 0.1f + 0.9f * (i + 1) / kept.size, found = kept.size, traced = i + 1, outlines = outlines.toList()))
        }

        // 3. Working out the sizes.
        val results = ArrayList<PhotoItem>()
        for (i in kept.indices) {
            val (b, label) = kept[i]
            val fit = fits[i]; val pts = pointsOf[i]
            val dims = when {
                fit != null -> Dimensions(fit.widthMm.toInt().coerceAtLeast(1), fit.depthMm.toInt().coerceAtLeast(1), fit.heightMm.toInt().coerceAtLeast(1))
                pts.size >= 6 -> extents(pts).let { (hh, longest) -> Dimensions(longest.toInt(), (longest * 0.7f).toInt().coerceAtLeast(1), hh.toInt().coerceAtLeast(1)) }
                else -> flatEstimate(b, input.intrinsics, w, h, heightM)
            }
            val footprint = fit?.let { f ->
                val corners = OutlineGeometry.footprint(f).map { toPicture(it) }
                if (corners.all { it != null }) FloatArray(corners.size * 2) { j -> corners[j / 2]!![j % 2] } else null
            }
            val all = outlines[i].flatMap { l -> (0 until l.size / 2).map { j -> l[2 * j] to l[2 * j + 1] } }
            // Above its top edge, centred on its centre of mass (an L-shaped thing's tag sits over the bulk of it).
            val centreX = masks[i]?.let { ColorMask.centroid(it, tw, th)?.first }
            val tag = if (all.isEmpty()) ((b[0] + b[2]) / 2 to b[1]) else ((centreX ?: ((all.minOf { it.first } + all.maxOf { it.first }) / 2)) to all.minOf { it.second })
            results += PhotoItem(
                name = label?.let { PhotoScale.nameFor(it, maxOf(dims.widthMm, dims.depthMm, dims.heightMm)) }, box = b, outline = outlines[i], tag = tag, footprint = footprint,
                dimensions = dims, shape = fit?.shape ?: ShapeFamily.BOX,
                form = fit?.let(ItemForm::fromFit), crop = crop(pic, b),
            )
            onProgress(PhotoProgress(2, (i + 1f) / kept.size, found = kept.size, traced = kept.size, outlines = outlines))
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
        // The floor of the space: the lower middle of the picture, where the instructions put it.
        val isFloor = { gx: Int, gy: Int -> gy > g * 0.45 && gx > g * 0.2 && gx < g * 0.8 }
        val up = input.up ?: PhotoGeometry.estimatePitch(input.intrinsics, rel, g, h, isFloor)?.let(PhotoGeometry::upForPitch)
            ?: PhotoGeometry.upForPitch(30.0)
        val card = findCard(pic, input.intrinsics, up)
        val geo = PhotoGeometry.fit(input.intrinsics, up, rel, g, w, h, isFloor) ?: return@withContext null
        val heightM = card?.heightM ?: card?.heightVia(geo) ?: PhotoScale.typicalForSpace(spaceName)
        Log.i(SCAN_TAG, "photo space: tilt from ${if (input.up != null) "gravity" else "depth"}, up=${up.joinToString { "%.2f".format(it) }}")
        onProgress(PhotoProgress(0, 0.5f))

        // 2. The walls and the opening: the Python space fitter, on everything in the middle of the picture.
        val samples = ArrayList<DetectionPoints.Sample>()
        for (gy in (g * 0.05).toInt() until g step 2) for (gx in (g * 0.08).toInt() until (g * 0.92).toInt() step 2) {
            val p = geo.pointAt(gx, gy, heightM) ?: continue
            if (p.hMm < -60f || p.hMm > 3000f) continue
            samples += DetectionPoints.Sample(p, gx > g * 0.3 && gx < g * 0.7 && gy > g * 0.3 && gy < g * 0.7)
        }
        val camera = geo.camera(heightM)
        val keep = runCatching { math.selectSpace(samples, 3000f).toHashSet() }.getOrElse { samples.indices.toHashSet() }
        val points = samples.indices.filter { it in keep || samples[it].point.hMm <= 25f }.map { samples[it].point }
        val box = runCatching { math.fitSpace(points, listOf(camera), null) }.getOrNull()
        val corners = box?.let { boxCorners(it.centreXMm, it.centreYMm, it.yawDegrees, it.widthMm, it.depthMm, it.heightMm) }
            ?: fallbackBox(points) ?: return@withContext null
        val (cw, cd, ch) = corners.second
        fun seg(i: Int, j: Int) = geo.pixelOf(corners.first[i], heightM)?.let { a -> geo.pixelOf(corners.first[j], heightM)?.let { b ->
            floatArrayOf((a[0] / w).toFloat(), (a[1] / h).toFloat(), (b[0] / w).toFloat(), (b[1] / h).toFloat()) } }
        // The floor, drawn in as "Found the floor" completes; the walls, then the way in, as they are found.
        val floorGroup = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0).mapNotNull { (i, j) -> seg(i, j) }
        val wallGroup = listOf(2 to 6, 3 to 7, 6 to 7, 5 to 6, 7 to 4).mapNotNull { (i, j) -> seg(i, j) }
        val openingGroup = listOf(0 to 4, 4 to 5, 5 to 1).mapNotNull { (i, j) -> seg(i, j) }
        onProgress(PhotoProgress(0, 1f, outlines = listOf(floorGroup)))
        onProgress(PhotoProgress(1, 0.5f, outlines = listOf(floorGroup, wallGroup)))
        onProgress(PhotoProgress(1, 1f, outlines = listOf(floorGroup, wallGroup, openingGroup)))

        // 3. Working out the inside size.
        val groups = listOf(floorGroup, wallGroup, openingGroup)
        Log.i(SCAN_TAG, "photo space: points=${points.size} fitted=${box != null} cameraHeight=%.2fm from %s size=%.0fx%.0fx%.0fmm".format(
            heightM, if (card != null) "card" else "typical height", cw, cd, ch))
        onProgress(PhotoProgress(2, 1f, outlines = groups))

        // 4. Checking for wheel arches — the plan uses the clear box inside them, which is what was fitted.
        onProgress(PhotoProgress(3, 1f, outlines = groups))
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

    /** Up for a photo with no gravity reading: the tilt read from the table's depth, else a typical 35°. */
    private fun upFromDepth(rel: FloatArray?, input: PhotoInput, boxes: List<FloatArray>): DoubleArray {
        val g = MidasDepth.SIZE
        val pitch = rel?.let {
            PhotoGeometry.estimatePitch(input.intrinsics, it, g, input.picture.height) { gx, gy ->
                val u = (gx + 0.5f) / g; val v = (gy + 0.5f) / g
                boxes.none { b -> u in b[0] - 0.02f..b[2] + 0.02f && v in b[1] - 0.02f..b[3] + 0.02f }
            }
        }
        Log.i(SCAN_TAG, "photo tilt: ${pitch?.let { "%.0f° from depth".format(it) } ?: "35° assumed"}")
        return PhotoGeometry.upForPitch(pitch ?: 35.0)
    }

    /**
     * Boxes, 0..1 of the picture, round things standing up off the surface that are not already
     * in [known] — found from the depth alone, as the reference project's detector finds them.
     */
    private fun standingThings(rel: FloatArray, input: PhotoInput, up: DoubleArray, cardHeight: Double?, known: List<FloatArray>): List<FloatArray> {
        val g = MidasDepth.SIZE; val w = input.picture.width; val h = input.picture.height
        fun inKnown(gx: Int, gy: Int): Boolean {
            val u = (gx + 0.5f) / g; val v = (gy + 0.5f) / g
            return known.any { b -> u in b[0] - 0.02f..b[2] + 0.02f && v in b[1] - 0.02f..b[3] + 0.02f }
        }
        val geo = PhotoGeometry.fit(input.intrinsics, up, rel, g, w, h, { gx, gy -> gy > g * 0.5 && !inKnown(gx, gy) }) ?: return emptyList()
        val heightM = cardHeight ?: PhotoScale.TYPICAL_ITEMS_M
        val world = ArrayList<Float>(); val uv = ArrayList<Float>()
        for (gy in 0 until g step 2) for (gx in 0 until g step 2) {
            if (inKnown(gx, gy)) continue
            val p = geo.pointAt(gx, gy, heightM) ?: continue
            world += p.xMm / 1000f; world += p.hMm / 1000f; world += p.yMm / 1000f
            uv += (gx + 0.5f) / g; uv += (gy + 0.5f) / g
        }
        return StandingObjects.find(world.toFloatArray(), uv.toFloatArray(), 0f).map { it.box }
            .filter { b -> (b[2] - b[0]) * (b[3] - b[1]) > 0.004f }
    }

    /**
     * A card or sheet of A4 in the photo. Lying flat, its pose gives the camera's height above the
     * surface outright. Standing up (in a holder, against a wall) it still gives its own true
     * distance, and the depth model's distance to the same spot then puts the whole picture to
     * scale — HarshdeepJ's ID-card calibration.
     */
    private class Card(val box: FloatArray, val heightM: Double?, private val distanceM: Double, private val centre: Pair<Float, Float>) {
        /** True for the card itself, or something holding it. */
        fun covers(b: FloatArray): Boolean {
            if (StandingObjects.overlap(b, box) > 0.3f) return true
            val cx = (b[0] + b[2]) / 2; val cy = (b[1] + b[3]) / 2
            val mx = (box[2] - box[0]) * 0.15f; val my = (box[3] - box[1]) * 0.15f
            return cx in box[0] - mx..box[2] + mx && cy in box[1] - my..box[3] + my
        }

        /** Camera height that makes the depth model agree with the card's own distance. */
        fun heightVia(geo: PhotoGeometry): Double? {
            val g = geo.grid
            val d1 = geo.depthM((centre.first * g).toInt().coerceIn(0, g - 1), (centre.second * g).toInt().coerceIn(0, g - 1), 1.0)
            if (d1 <= 0.0) return null
            return (distanceM / d1).takeIf { it in PhotoScale.MIN_M..PhotoScale.MAX_M }
        }
    }

    private fun findCard(pic: Bitmap, k: PlanarPose.Intrinsics, up: DoubleArray): Card? = runCatching {
        val s = (960.0 / pic.width).coerceAtMost(1.0)
        val w = (pic.width * s).toInt(); val h = (pic.height * s).toInt()
        val small = Bitmap.createScaledBitmap(pic, w, h, true)
        val argb = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
        if (small !== pic) small.recycle()
        val gray = IntArray(w * h) { i -> val p = argb[i]; (((p shr 16) and 0xFF) * 77 + ((p shr 8) and 0xFF) * 150 + (p and 0xFF) * 29) shr 8 }
        val ks = PlanarPose.Intrinsics(k.fx * s, k.fy * s, k.cx * s, k.cy * s)
        // Every four-sided thing that could be a card, and how truly rectangular it is at a card's
        // size; the truest wins. A flask's front seen face-on is card-shaped too, but its curved top
        // and bottom make it a worse rectangle than the real card.
        val candidates = ArrayList<Pair<Double, Card>>()
        for (q in CardFinder.find(gray, w, h)) for (ref in Reference.entries) {
            val fit = PlanarPose.fromQuad(q.corners, ref.longM, ref.shortM, ks, null, q.sides) ?: continue
            val r = fit.pose.r; val t = fit.pose.t
            val distance = t[2]
            if (distance !in PhotoScale.MIN_M..PhotoScale.MAX_M) continue
            // The card's own up (world Y) seen from the camera, against gravity's: flat if within ~35°.
            val flat = abs(r[1] * up[0] + r[4] * up[1] + r[7] * up[2]) > 0.82
            val height = abs(fit.pose.cameraPosition[1])
            if (flat && height !in PhotoScale.MIN_M..PhotoScale.MAX_M) continue
            val xs = (0 until 4).map { q.corners[2 * it] / w }; val ys = (0 until 4).map { q.corners[2 * it + 1] / h }
            val centre = ((ks.fx * t[0] / t[2] + ks.cx) / w).toFloat() to ((ks.fy * t[1] / t[2] + ks.cy) / h).toFloat()
            val card = Card(floatArrayOf(xs.min().toFloat(), ys.min().toFloat(), xs.max().toFloat(), ys.max().toFloat()), if (flat) height else null, distance, centre)
            val error = fit.axisRatio - 1 + fit.skew
            Log.i(SCAN_TAG, "photo card? ${ref.name} error %.3f at %s, %s".format(error, card.box.joinToString(",") { "%.2f".format(it) },
                if (flat) "flat, camera %.2fm up".format(height) else "standing, %.2fm away".format(distance)))
            candidates += error to card
        }
        // ponytail: fixed bar from one real photo (flask face 0.098); tune if real cards get rejected.
        candidates.filter { it.first < MAX_CARD_ERROR }.minByOrNull { it.first }?.second
    }.getOrNull()

    /**
     * Drops the stray few: anything outside the 3rd–97th percentile on any axis. One edge cell
     * that MiDaS put on the wall behind was enough to make a 9 cm flask a metre wide.
     */
    private fun trim(pts: List<PlanePoint>): List<PlanePoint> {
        if (pts.size < 20) return pts
        fun range(v: List<Float>) = v.sorted().let { it[it.size * 3 / 100]..it[it.size * 97 / 100] }
        val x = range(pts.map { it.xMm }); val y = range(pts.map { it.yMm }); val h = range(pts.map { it.hMm })
        return pts.filter { it.xMm in x && it.yMm in y && it.hMm in h }
    }

    /**
     * Sets a thing down on the surface. Things rest on the table, but the depth model often puts
     * one nearer than the table round it, so it seems to float; its height is then counted from
     * its own lowest points. ponytail: shifts height only; rescaling along the rays is the exact fix if footprints come out off.
     */
    private fun settle(pts: List<PlanePoint>): List<PlanePoint> {
        if (pts.size < 20) return pts
        val base = pts.map { it.hMm }.sorted()[pts.size * 3 / 100]
        return if (base <= 0f) pts else pts.map { PlanePoint(it.xMm, it.yMm, it.hMm - base) }
    }

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

/** How far from a true card-sized rectangle (axis ratio − 1 + skew) a quad may be and still be the card. */
private const val MAX_CARD_ERROR = 0.06

/** A still photo's YOLOX bar: lower than live video's, since there is time to check each find. */
private const val PHOTO_MIN_SCORE = 0.25f

/** Closes models after one photo: they are big, and a photo scan is a one-off. */
private inline fun <T : java.io.Closeable, R> T.use(block: (T) -> R): R = try { block(this) } finally { close() }

@Suppress("unused") private val keepImports = listOf(SpaceBox::class, FormFamily::class)
