package com.packabunch.packing

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** A picture as ARGB pixels, row by row — what both the phone and the computer test hand the engine. */
class Raster(val argb: IntArray, val width: Int, val height: Int) {

    /**
     * Resized with bilinear filtering. The phone and the computer test both use this one, so the
     * models see exactly the same pixels on both.
     */
    fun scaled(w: Int, h: Int): Raster {
        if (w == width && h == height) return this
        val out = IntArray(w * h)
        val sx = width.toFloat() / w; val sy = height.toFloat() / h
        for (y in 0 until h) {
            val fy = ((y + 0.5f) * sy - 0.5f).coerceIn(0f, height - 1f)
            val y0 = fy.toInt(); val y1 = minOf(y0 + 1, height - 1); val ty = fy - y0
            for (x in 0 until w) {
                val fx = ((x + 0.5f) * sx - 0.5f).coerceIn(0f, width - 1f)
                val x0 = fx.toInt(); val x1 = minOf(x0 + 1, width - 1); val tx = fx - x0
                val a = argb[y0 * width + x0]; val b = argb[y0 * width + x1]; val c = argb[y1 * width + x0]; val d = argb[y1 * width + x1]
                var p = 0xFF shl 24
                for (shift in intArrayOf(16, 8, 0)) {
                    val top = ((a shr shift) and 0xFF) * (1 - tx) + ((b shr shift) and 0xFF) * tx
                    val bottom = ((c shr shift) and 0xFF) * (1 - tx) + ((d shr shift) and 0xFF) * tx
                    p = p or ((top * (1 - ty) + bottom * ty + 0.5f).toInt().coerceIn(0, 255) shl shift)
                }
                out[y * w + x] = p
            }
        }
        return Raster(out, w, h)
    }
}

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
 * Measures everything in one photo — the same code on the phone and in the computer test.
 *
 * The models run outside (ONNX Runtime and ML Kit on the phone, ONNX Runtime on the computer) and
 * hand in what they saw; everything after that is here.
 *
 * Items: (1) find the things — YOLOX, which also names them, ML Kit's boxes for anything YOLOX does
 * not know, and the depth for anything both missed; (2) trace each one's outline (its colour or
 * edge mask); (3) work out the sizes — the depth model shaped by gravity and the surface
 * ([PhotoGeometry]), scaled by a card if there is one or by the usual sizes of the things
 * recognised ([PhotoScale]), each object's own points fitted (box, cylinder, tapered, ball).
 *
 * Space: (1) find the floor; (2) the walls and the opening; (3) the inside size.
 */
class PhotoEngine(private val math: ScanMath, private val log: (String) -> Unit = {}) {

    /** One thing measured from a photo. Sizes are camera estimates; the person checks each one. */
    class Item(
        /** What it was recognised as (YOLOX's label turned into a name), or null if unknown. */
        val name: String?,
        /** `[left, top, right, bottom]` in 0..1 of the picture. */
        val box: FloatArray,
        /** Its traced outline then its fitted 3D box, drawn on the picture: polylines, x, y pairs in 0..1. */
        val outline: List<FloatArray>,
        /** Where its name tag goes, 0..1 of the picture: the top of its outline. */
        val tag: Pair<Float, Float>,
        /** Its footprint on the surface, a closed polygon in 0..1 of the picture, for the faint glow under it. */
        val footprint: FloatArray?,
        val dimensions: Dimensions,
        val shape: ShapeFamily,
        val form: ItemForm?,
        /** Where the photo's scale came from; the same for every item in it. */
        val scale: ScaleSource,
    )

    /**
     * @param yolo YOLOX's finds, boxes in 0..1.
     * @param unnamed other finders' boxes (ML Kit on the phone), in 0..1.
     * @param rel MiDaS's relative inverse depth on a [depthGrid] square, or null if it failed.
     * @param up up in camera axes from the gravity sensor; null for a gallery photo.
     */
    fun items(
        pic: Raster, k: PlanarPose.Intrinsics, up: DoubleArray?,
        yolo: List<YoloxDecode.Detection>, unnamed: List<FloatArray>, rel: FloatArray?, depthGrid: Int,
        maxItems: Int, onProgress: (PhotoProgress) -> Unit = {},
    ): List<Item> {
        val w = pic.width; val h = pic.height; val g = depthGrid
        val boxes = ArrayList<Pair<FloatArray, String?>>()
        val scoreOf = HashMap<FloatArray, Float>() // by identity
        // One object is one thing: YOLOX's overlap check is per name, so a mouse also came back as
        // a "cell phone" in the same place. The surer name wins.
        for (d in yolo.sortedByDescending { it.score }) {
            if (boxes.any { StandingObjects.overlap(it.first, d.box) > 0.5f || inside(d.box, it.first) > 0.85f && inside(it.first, d.box) > 0.85f }) continue
            boxes += d.box to d.label; scoreOf[d.box] = d.score
        }
        for (m in unnamed) if (boxes.none { StandingObjects.overlap(it.first, m) > 0.3f }) { boxes += m to null; scoreOf[m] = UNNAMED_SCORE }
        // A picture printed on a thing is not a thing: the "donuts" on a box of rusks. A weaker find
        // lying almost wholly inside a surer, much bigger one is dropped.
        val printed = boxes.filter { (b, _) ->
            boxes.any { (o, _) -> o !== b && inside(b, o) > 0.85f && area(o) > 2.5f * area(b) && scoreOf.getValue(b) < maxOf(scoreOf.getValue(o), PRINTED_MAX_SCORE) }
        }.toSet()
        boxes.removeAll(printed)
        onProgress(PhotoProgress(0, 0.6f, found = boxes.size))

        val upV = up ?: upFromDepth(rel, k, g, h, boxes.map { it.first })
        val card = findCard(pic, k, upV)
        // The card is the reference, not an item (nor is its holder). An unnamed box filling most of
        // the frame is the scene; a named one is a close-up of that thing (a shoe photographed up close).
        val detected = boxes.filter { (b, label) -> area(b) in 0.002f..(if (label != null) 0.97f else 0.6f) && (card == null || !card.covers(b)) }
        val tw = 320; val th = (h * tw / w.toFloat()).toInt().coerceAtLeast(1)
        val argb = pic.scaled(tw, th).argb
        // Anything standing up off the surface that neither detector named — HarshdeepJ's depth
        // stage, so an unknown thing is still found and measured, just without a name. Depth alone
        // also rises at walls and glare, so it counts only where colour or an edge agrees.
        val fromDepth = rel?.let { standingThings(it, g, k, w, h, upV, card?.heightM, detected.map { d -> d.first }) }.orEmpty()
            .filter { b -> (card == null || !card.covers(b)) && ColorMask.segment(argb, tw, th, b) != null }
        val kept = (detected + fromDepth.map { it to null }).take(maxItems)
        fun FloatArray.s() = joinToString(",", "[", "]") { "%.2f".format(it) }
        log("photo found: yolox=${yolo.map { it.label + "%.2f".format(it.score) + it.box.s() }} unnamed=${unnamed.map { it.s() }} " +
            "printed=${printed.size} depth=${fromDepth.map { it.s() }} card=${card?.box?.s()} kept=${kept.size}")
        onProgress(PhotoProgress(0, 1f, found = kept.size))
        if (kept.isEmpty()) return emptyList()

        // 2. Tracing their outlines: each one's mask, then its 3D shape fitted to its own depth —
        //    the outline the sheet draws in piece by piece, object after object.
        val masks = kept.map { (b, _) -> ColorMask.segment(argb, tw, th, b) }
        onProgress(PhotoProgress(1, 0.1f, found = kept.size))

        fun inAnyBox(gx: Int, gy: Int): Boolean {
            val u = (gx + 0.5f) / g; val v = (gy + 0.5f) / g
            return kept.any { (b, _) -> u in b[0] - 0.02f..b[2] + 0.02f && v in b[1] - 0.02f..b[3] + 0.02f }
        }
        val geo = rel?.let { PhotoGeometry.fit(k, upV, it, g, w, h, { gx, gy -> gy > g * 0.5 && !inAnyBox(gx, gy) }) }
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
        var scaleFrom = "typical height"; var scaleSource = ScaleSource.TYPICAL
        val heightM = card?.heightM?.also { scaleFrom = "card lying flat"; scaleSource = ScaleSource.CARD }
            ?: geo?.let { card?.heightVia(it) }?.also { scaleFrom = "card standing up"; scaleSource = ScaleSource.CARD }
            ?: geo?.let { gm ->
                // Only whole things: one cut off by the edge of the photo measures short and would
                // blow every size up (a corner of something taken for a 33 cm laptop made a mouse 29 cm).
                val refs = kept.indices.filter { i -> kept[i].second != null && kept[i].first.let { b -> b[0] > EDGE && b[1] > EDGE && b[2] < 1 - EDGE && b[3] < 1 - EDGE } }
                val measured = refs.mapNotNull { i ->
                    val pts = settle(trim(cells[i].mapNotNull { (gx, gy) -> gm.pointAt(gx, gy, 1.0) })).filter { it.hMm > 4f }
                    if (pts.size < 12) return@mapNotNull null
                    val (hh, longest) = extents(pts)
                    Triple(kept[i].second!!, maxOf(hh, longest), scoreOf.getValue(kept[i].first))
                }
                PhotoScale.fromPriors(measured.map { it.first to it.second }, measured.map { it.third })
                    ?.also { scaleFrom = "known size of " + measured.filter { m -> m.first in PhotoScale.PRIORS }.joinToString { m -> m.first }; scaleSource = ScaleSource.KNOWN_OBJECT }
            }
            ?: PhotoScale.TYPICAL_ITEMS_M
        log("photo items: found=${kept.size} geometry=${geo != null} cameraHeight=%.2fm from %s".format(heightM, scaleFrom))
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
                log("photo item $i ${kept[i].second}: mask=${masks[i] != null} ${pts.size} pts x=${q(pts.map { it.xMm })} y=${q(pts.map { it.yMm })} h=${q(pts.map { it.hMm })} " +
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
        val results = ArrayList<Item>()
        for (i in kept.indices) {
            val (b, label) = kept[i]
            val fit = fits[i]; val pts = pointsOf[i]
            val dims = when {
                fit != null -> Dimensions(fit.widthMm.toInt().coerceAtLeast(1), fit.depthMm.toInt().coerceAtLeast(1), fit.heightMm.toInt().coerceAtLeast(1))
                pts.size >= 6 -> extents(pts).let { (hh, longest) -> Dimensions(longest.toInt(), (longest * 0.7f).toInt().coerceAtLeast(1), hh.toInt().coerceAtLeast(1)) }
                else -> flatEstimate(b, k, w, h, heightM)
            }
            val footprint = fit?.let { f ->
                val corners = OutlineGeometry.footprint(f).map { toPicture(it) }
                if (corners.all { it != null }) FloatArray(corners.size * 2) { j -> corners[j / 2]!![j % 2] } else null
            }
            val all = outlines[i].flatMap { l -> (0 until l.size / 2).map { j -> l[2 * j] to l[2 * j + 1] } }
            // Above its top edge, centred on its centre of mass (an L-shaped thing's tag sits over the bulk of it).
            val centreX = masks[i]?.let { ColorMask.centroid(it, tw, th)?.first }
            val tag = if (all.isEmpty()) ((b[0] + b[2]) / 2 to b[1]) else ((centreX ?: ((all.minOf { it.first } + all.maxOf { it.first }) / 2)) to all.minOf { it.second })
            results += Item(
                name = label?.let { PhotoScale.nameFor(it, maxOf(dims.widthMm, dims.depthMm, dims.heightMm)) }, box = b, outline = outlines[i], tag = tag,
                footprint = footprint, dimensions = dims, shape = fit?.shape ?: ShapeFamily.BOX, form = fit?.let(ItemForm::fromFit),
                scale = scaleSource,
            )
            onProgress(PhotoProgress(2, (i + 1f) / kept.size, found = kept.size, traced = kept.size, outlines = outlines))
        }
        return results
    }

    fun space(
        pic: Raster, k: PlanarPose.Intrinsics, up: DoubleArray?, rel: FloatArray, depthGrid: Int, spaceName: String?,
        onProgress: (PhotoProgress) -> Unit = {},
    ): PhotoSpace? {
        val w = pic.width; val h = pic.height; val g = depthGrid
        // The floor of the space: the lower middle of the picture, where the instructions put it.
        val isFloor = { gx: Int, gy: Int -> gy > g * 0.45 && gx > g * 0.2 && gx < g * 0.8 }
        val upV = up ?: PhotoGeometry.estimatePitch(k, rel, g, h, isFloor)?.let(PhotoGeometry::upForPitch) ?: PhotoGeometry.upForPitch(30.0)
        val card = findCard(pic, k, upV)
        val geo = PhotoGeometry.fit(k, upV, rel, g, w, h, isFloor) ?: run { log("photo space: no floor"); return null }
        val heightM = card?.heightM ?: card?.heightVia(geo) ?: PhotoScale.typicalForSpace(spaceName)
        log("photo space: tilt from ${if (up != null) "gravity" else "depth"}, up=${upV.joinToString { "%.2f".format(it) }}")
        onProgress(PhotoProgress(0, 0.5f))

        // 2. The walls and the opening: the space fitter, on everything in the middle of the picture.
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
            ?: fallbackBox(points) ?: run { log("photo space: nothing fitted from ${points.size} points"); return null }
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
        log("photo space: points=${points.size} fitted=${box != null} cameraHeight=%.2fm from %s size=%.0fx%.0fx%.0fmm".format(
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
        return PhotoSpace(
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
    private fun upFromDepth(rel: FloatArray?, k: PlanarPose.Intrinsics, g: Int, picH: Int, boxes: List<FloatArray>): DoubleArray {
        val pitch = rel?.let {
            PhotoGeometry.estimatePitch(k, it, g, picH) { gx, gy ->
                val u = (gx + 0.5f) / g; val v = (gy + 0.5f) / g
                boxes.none { b -> u in b[0] - 0.02f..b[2] + 0.02f && v in b[1] - 0.02f..b[3] + 0.02f }
            }
        }
        log("photo tilt: ${pitch?.let { "%.0f° from depth".format(it) } ?: "35° assumed"}")
        return PhotoGeometry.upForPitch(pitch ?: 35.0)
    }

    /**
     * Boxes, 0..1 of the picture, round things standing up off the surface that are not already
     * in [known] — found from the depth alone, as the reference project's detector finds them.
     */
    private fun standingThings(rel: FloatArray, g: Int, k: PlanarPose.Intrinsics, w: Int, h: Int, up: DoubleArray, cardHeight: Double?, known: List<FloatArray>): List<FloatArray> {
        fun inKnown(gx: Int, gy: Int): Boolean {
            val u = (gx + 0.5f) / g; val v = (gy + 0.5f) / g
            return known.any { b -> u in b[0] - 0.02f..b[2] + 0.02f && v in b[1] - 0.02f..b[3] + 0.02f }
        }
        val geo = PhotoGeometry.fit(k, up, rel, g, w, h, { gx, gy -> gy > g * 0.5 && !inKnown(gx, gy) }) ?: return emptyList()
        val heightM = cardHeight ?: PhotoScale.TYPICAL_ITEMS_M
        val world = ArrayList<Float>(); val uv = ArrayList<Float>()
        for (gy in 0 until g step 2) for (gx in 0 until g step 2) {
            if (inKnown(gx, gy)) continue
            val p = geo.pointAt(gx, gy, heightM) ?: continue
            world += p.xMm / 1000f; world += p.hMm / 1000f; world += p.yMm / 1000f
            uv += (gx + 0.5f) / g; uv += (gy + 0.5f) / g
        }
        return StandingObjects.find(world.toFloatArray(), uv.toFloatArray(), 0f).map { it.box }.filter { b -> area(b) > 0.004f }
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

    private fun findCard(pic: Raster, k: PlanarPose.Intrinsics, up: DoubleArray): Card? = runCatching {
        val s = (960.0 / pic.width).coerceAtMost(1.0)
        val small = pic.scaled((pic.width * s).toInt(), (pic.height * s).toInt())
        val w = small.width; val h = small.height
        val gray = IntArray(w * h) { i -> val p = small.argb[i]; (((p shr 16) and 0xFF) * 77 + ((p shr 8) and 0xFF) * 150 + (p and 0xFF) * 29) shr 8 }
        val ks = PlanarPose.Intrinsics(k.fx * s, k.fy * s, k.cx * s, k.cy * s)
        // Every four-sided thing that could be a card, and how truly rectangular it is at a card's
        // size; the truest wins. A flask's front seen face-on is card-shaped too, but its curved top
        // and bottom make it a worse rectangle than the real card.
        val candidates = ArrayList<Pair<Double, Card>>()
        for (q in CardFinder.find(gray, w, h)) for ((name, size) in REFERENCES) {
            val fit = PlanarPose.fromQuad(q.corners, size.first, size.second, ks, null, q.sides) ?: continue
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
            log("photo card? $name error %.3f at %s, %s".format(error, card.box.joinToString(",") { "%.2f".format(it) },
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
        val floor = points.filter { abs(it.hMm) < 30f }
        if (floor.size < 30) return null
        val xs = floor.map { it.xMm }.sorted(); val ys = floor.map { it.yMm }.sorted()
        fun q(v: List<Float>, f: Double) = v[(v.size * f).toInt().coerceIn(0, v.size - 1)]
        val x0 = q(xs, 0.03); val x1 = q(xs, 0.97); val y0 = q(ys, 0.03); val y1 = q(ys, 0.97)
        val walls = points.filter { it.hMm > 30f && it.xMm in x0..x1 && it.yMm in y0..y1 + 100f }.map { it.hMm }.sorted()
        val height = if (walls.size >= 10) q(walls, 0.95) else (x1 - x0) * 0.6f
        return boxCorners((x0 + x1) / 2, (y0 + y1) / 2, 0f, x1 - x0, y1 - y0, height)
    }

    companion object {
        /** A still photo's YOLOX bar: lower than live video's, since there is time to check each find. */
        const val PHOTO_MIN_SCORE = 0.25f

        /** Unnamed finds (ML Kit) give no confidence; this ranks them below a sure YOLOX find. */
        private const val UNNAMED_SCORE = 0.5f

        /** A small find inside a much bigger one is a picture printed on it unless the detector is surer than this. */
        private const val PRINTED_MAX_SCORE = 0.6f

        /** A box this close to the photo's edge may be cut off. */
        private const val EDGE = 0.01f

        /** How far from a true card-sized rectangle (axis ratio − 1 + skew) a quad may be and still be the card. */
        private const val MAX_CARD_ERROR = 0.06

        private val REFERENCES = listOf(
            "CARD" to (PlanarPose.CARD_LONG_M to PlanarPose.CARD_SHORT_M),
            "A4" to (PlanarPose.A4_LONG_M to PlanarPose.A4_SHORT_M),
        )

        private fun area(b: FloatArray) = (b[2] - b[0]) * (b[3] - b[1])

        /** Share of [a]'s area that lies inside [b]. */
        private fun inside(a: FloatArray, b: FloatArray): Float {
            val w = minOf(a[2], b[2]) - maxOf(a[0], b[0]); val h = minOf(a[3], b[3]) - maxOf(a[1], b[1])
            return if (w <= 0f || h <= 0f || area(a) <= 0f) 0f else w * h / area(a)
        }

        /** YOLOX's input: scaled to fit, top-left aligned, grey 114 padding, BGR channels first, 0..255. */
        fun yoloxInput(pic: Raster): FloatArray {
            val size = YoloxDecode.INPUT
            val r = YoloxDecode.ratio(pic.width, pic.height)
            val scaled = pic.scaled((pic.width * r).toInt().coerceAtLeast(1), (pic.height * r).toInt().coerceAtLeast(1))
            val plane = size * size
            val out = FloatArray(3 * plane) { 114f }
            for (y in 0 until scaled.height) for (x in 0 until scaled.width) {
                val p = scaled.argb[y * scaled.width + x]; val i = y * size + x
                out[i] = (p and 0xFF).toFloat(); out[plane + i] = ((p shr 8) and 0xFF).toFloat(); out[2 * plane + i] = ((p shr 16) and 0xFF).toFloat()
            }
            return out
        }

        /** MiDaS small's input: stretched to [size] square, RGB channels first, ImageNet normalisation. */
        fun midasInput(pic: Raster, size: Int): FloatArray {
            val s = pic.scaled(size, size); val plane = size * size
            val out = FloatArray(3 * plane)
            for (i in 0 until plane) {
                val p = s.argb[i]
                out[i] = (((p shr 16) and 0xFF) / 255f - 0.485f) / 0.229f
                out[plane + i] = (((p shr 8) and 0xFF) / 255f - 0.456f) / 0.224f
                out[2 * plane + i] = ((p and 0xFF) / 255f - 0.406f) / 0.225f
            }
            return out
        }
    }
}
