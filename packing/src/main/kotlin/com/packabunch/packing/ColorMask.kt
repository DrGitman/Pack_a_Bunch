package com.packabunch.packing

import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * An object's own pixels inside its detector box — the "instance mask" of instance segmentation,
 * made the way PyImageSearch's colour tracking makes its masks, without a training set.
 *
 * Their method thresholds a fixed colour range. Here the range is learned per box: the colours in
 * a thin ring just outside the box are the background (table, card, wall), and anything inside
 * that is unlike all of them is the object. Then, as theirs does, small specks are eroded away
 * and the shape dilated back, and the piece that owns the middle of the box is kept with its
 * holes filled.
 *
 * Measuring only these pixels is what keeps a mouse's size from taking in the card beside it.
 * Returns null when the object and its background are too alike to tell apart (a white cup on a
 * white table) — then the scan falls back to depth alone rather than drawing a wrong outline.
 */
object ColorMask {

    /** Colour difference (CIE76, Lab) that counts as "not the background". Raised by a noisy ring. */
    private const val MIN_DELTA = 14.0
    private const val RING = 0.06f
    private const val RING_SAMPLES = 160
    private const val MIN_SHARE = 0.05
    private const val MAX_SHARE = 0.97

    /**
     * @param argb the picture, row by row.
     * @param box `[left, top, right, bottom]` in 0..1 of the picture.
     * @return true for the object's pixels (same size as the picture), or null.
     */
    fun segment(argb: IntArray, w: Int, h: Int, box: FloatArray): BooleanArray? {
        val x0 = (box[0] * w).toInt().coerceIn(0, w - 1); val x1 = (box[2] * w).toInt().coerceIn(x0 + 1, w)
        val y0 = (box[1] * h).toInt().coerceIn(0, h - 1); val y1 = (box[3] * h).toInt().coerceIn(y0 + 1, h)
        val bw = x1 - x0; val bh = y1 - y0
        if (bw < 6 || bh < 6) return null
        val rx = maxOf(2, (bw * RING).toInt()); val ry = maxOf(2, (bh * RING).toInt())

        // Background colours: the ring round the box.
        val ring = ArrayList<DoubleArray>()
        for (y in (y0 - ry).coerceAtLeast(0) until (y1 + ry).coerceAtMost(h)) for (x in (x0 - rx).coerceAtLeast(0) until (x1 + rx).coerceAtMost(w)) {
            if (x in x0 until x1 && y in y0 until y1) continue
            ring += lab(argb[y * w + x])
        }
        if (ring.size < 8) return null
        val samples = if (ring.size <= RING_SAMPLES) ring else List(RING_SAMPLES) { ring[it * ring.size / RING_SAMPLES] }
        // How much the background varies within itself sets how different the object must be.
        val spread = samples.indices.map { i -> samples.indices.filter { it != i }.minOf { j -> dist(samples[i], samples[j]) } }.sorted()
        val threshold = maxOf(MIN_DELTA, spread[(spread.size * 0.9).toInt().coerceAtMost(spread.size - 1)] * 2.5)

        val labs = Array(bw * bh) { i -> lab(argb[(y0 + i / bw) * w + x0 + i % bw]) }
        var fg = BooleanArray(bw * bh) { i -> samples.none { dist(it, labs[i]) < threshold } }
        // GrabCut's loop, without the graph cut: from the first guess, learn the object's own
        // colours and the background's (the ring plus whatever in the box is not the object), and
        // give each pixel to the closer of the two; twice. Shadows and the far side of the
        // object, unlike the ring but like the table, go back to the background.
        repeat(REFINE) {
            val share = fg.count { it }.toDouble() / fg.size
            if (share < MIN_SHARE || share > MAX_SHARE) return@repeat
            val fgModel = spread(labs.filterIndexed { i, _ -> fg[i] })
            val bgModel = samples + spread(labs.filterIndexed { i, _ -> !fg[i] })
            fg = BooleanArray(bw * bh) { i -> val c = labs[i]; fgModel.minOf { dist(it, c) } < bgModel.minOf { dist(it, c) } }
        }
        // Remove specks, then grow back (open), then bridge small gaps (close).
        fg = dilate(erode(fg, bw, bh), bw, bh)
        fg = erode(dilate(fg, bw, bh), bw, bh)
        return keepMiddle(fg, bw, bh)?.let { place(it, x0, y0, bw, w, h) }
            ?: byEdges(argb, w, h, (x0 - rx).coerceAtLeast(0), (y0 - ry).coerceAtLeast(0), (x1 + rx).coerceAtMost(w), (y1 + ry).coerceAtMost(h))
    }

    /**
     * When colour cannot tell them apart — a white cup on a white table — the object's edge often
     * still shows, as a line of shading. Canny finds it (blur, Sobel gradients, thinning to the
     * ridge, strong edges plus the weak ones joined to them, thresholds set from the picture's own
     * gradients instead of a slider); the edges are thickened to close small gaps, as before
     * findContours, and whatever they enclose round the middle is the object.
     */
    private fun byEdges(argb: IntArray, w: Int, h: Int, x0: Int, y0: Int, x1: Int, y1: Int): BooleanArray? {
        val bw = x1 - x0; val bh = y1 - y0
        if (bw < 8 || bh < 8) return null
        val edge = canny(FloatArray(bw * bh) { i -> luma(argb[(y0 + i / bw) * w + x0 + i % bw]) }, bw, bh) ?: return null
        var closedEdge = dilate(dilate(edge, bw, bh), bw, bh)
        // Inside = not reachable from the border without crossing an edge.
        val outside = BooleanArray(bw * bh); val queue = IntArray(bw * bh); var head = 0; var tail = 0
        for (i in 0 until bw * bh) {
            val x = i % bw; val y = i / bw
            if ((x == 0 || y == 0 || x == bw - 1 || y == bh - 1) && !closedEdge[i]) { outside[i] = true; queue[tail++] = i }
        }
        while (head < tail) {
            val i = queue[head++]; val x = i % bw; val y = i / bw
            for ((dx, dy) in NEIGHBOURS) {
                val nx = x + dx; val ny = y + dy
                if (nx !in 0 until bw || ny !in 0 until bh) continue
                val j = ny * bw + nx
                if (!outside[j] && !closedEdge[j]) { outside[j] = true; queue[tail++] = j }
            }
        }
        closedEdge = erode(erode(BooleanArray(bw * bh) { !outside[it] }, bw, bh), bw, bh) // undo the thickening
        return keepMiddle(closedEdge, bw, bh)?.let { place(it, x0, y0, bw, w, h) }
    }

    /** Canny edges of a [w] × [h] luma patch; null when nothing in it is a clear edge. */
    internal fun canny(gray: FloatArray, w: Int, h: Int): BooleanArray? {
        // 1. Smooth: 3 × 3 binomial (Gaussian) blur.
        val k = floatArrayOf(1f, 2f, 1f)
        val s = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0f
            for (dy in -1..1) for (dx in -1..1) sum += k[dx + 1] * k[dy + 1] * gray[(y + dy).coerceIn(0, h - 1) * w + (x + dx).coerceIn(0, w - 1)]
            s[y * w + x] = sum / 16f
        }
        // 2. Sobel gradients.
        val mag = FloatArray(w * h); val dir = IntArray(w * h)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            fun p(dx: Int, dy: Int) = s[(y + dy) * w + x + dx]
            val gx = p(1, -1) + 2 * p(1, 0) + p(1, 1) - p(-1, -1) - 2 * p(-1, 0) - p(-1, 1)
            val gy = p(-1, 1) + 2 * p(0, 1) + p(1, 1) - p(-1, -1) - 2 * p(0, -1) - p(1, -1)
            mag[y * w + x] = hypot(gx, gy)
            val a = (Math.toDegrees(kotlin.math.atan2(gy, gx).toDouble()) + 180.0) % 180.0
            dir[y * w + x] = when { a < 22.5 || a >= 157.5 -> 0; a < 67.5 -> 1; a < 112.5 -> 2; else -> 3 }
        }
        // 3. Thin to the ridge: keep only local maxima across the edge.
        val thin = FloatArray(w * h)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x; val m = mag[i]
            val (a, b) = when (dir[i]) { 0 -> mag[i - 1] to mag[i + 1]; 1 -> mag[i - w - 1] to mag[i + w + 1]; 2 -> mag[i - w] to mag[i + w]; else -> mag[i - w + 1] to mag[i + w - 1] }
            if (m >= a && m >= b) thin[i] = m
        }
        // 4. Double threshold from the patch's own gradients, never below what noise makes.
        val ridge = thin.filter { it > 0f }.sorted()
        if (ridge.isEmpty()) return null
        val high = maxOf(EDGE_MIN, ridge[(ridge.size * 0.85).toInt().coerceAtMost(ridge.size - 1)])
        val low = high * 0.4f
        val out = BooleanArray(w * h); val queue = IntArray(w * h); var head = 0; var tail = 0
        for (i in thin.indices) if (thin[i] >= high) { out[i] = true; queue[tail++] = i }
        if (tail == 0) return null
        while (head < tail) { // weak edges count when joined to strong ones
            val i = queue[head++]; val x = i % w; val y = i / w
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx; val ny = y + dy
                if (nx !in 0 until w || ny !in 0 until h) continue
                val j = ny * w + nx
                if (!out[j] && thin[j] >= low) { out[j] = true; queue[tail++] = j }
            }
        }
        return out
    }

    private fun luma(argb: Int) = 0.299f * ((argb shr 16) and 0xFF) + 0.587f * ((argb shr 8) and 0xFF) + 0.114f * (argb and 0xFF)

    private fun place(inBox: BooleanArray, x0: Int, y0: Int, bw: Int, w: Int, h: Int): BooleanArray {
        val out = BooleanArray(w * h)
        for (i in inBox.indices) if (inBox[i]) out[(y0 + i / bw) * w + x0 + i % bw] = true
        return out
    }

    /** The piece that owns the middle of the box, its holes filled; null if none or implausibly sized. */
    private fun keepMiddle(fg: BooleanArray, bw: Int, bh: Int): BooleanArray? {
        val label = IntArray(bw * bh) { -1 }
        var best = -1; var bestCentral = 0; var next = 0
        val cx0 = bw * 0.3; val cx1 = bw * 0.7; val cy0 = bh * 0.3; val cy1 = bh * 0.7
        val queue = IntArray(bw * bh)
        for (s in fg.indices) {
            if (!fg[s] || label[s] >= 0) continue
            var head = 0; var tail = 0; queue[tail++] = s; label[s] = next; var central = 0
            while (head < tail) {
                val i = queue[head++]; val x = i % bw; val y = i / bw
                if (x >= cx0 && x <= cx1 && y >= cy0 && y <= cy1) central++
                for ((dx, dy) in NEIGHBOURS) {
                    val nx = x + dx; val ny = y + dy
                    if (nx !in 0 until bw || ny !in 0 until bh) continue
                    val j = ny * bw + nx
                    if (fg[j] && label[j] < 0) { label[j] = next; queue[tail++] = j }
                }
            }
            if (central > bestCentral) { bestCentral = central; best = next }
            next++
        }
        if (best < 0) return null
        // Fill holes: anything not reachable from the box edge without crossing the object.
        val outside = BooleanArray(bw * bh)
        var head = 0; var tail = 0
        for (i in 0 until bw * bh) {
            val x = i % bw; val y = i / bw
            if ((x == 0 || y == 0 || x == bw - 1 || y == bh - 1) && label[i] != best) { outside[i] = true; queue[tail++] = i }
        }
        while (head < tail) {
            val i = queue[head++]; val x = i % bw; val y = i / bw
            for ((dx, dy) in NEIGHBOURS) {
                val nx = x + dx; val ny = y + dy
                if (nx !in 0 until bw || ny !in 0 until bh) continue
                val j = ny * bw + nx
                if (!outside[j] && label[j] != best) { outside[j] = true; queue[tail++] = j }
            }
        }
        val inBox = smooth(BooleanArray(bw * bh) { !outside[it] }, bw, bh)
        val area = inBox.count { it }
        if (area < bw * bh * MIN_SHARE || area > bw * bh * MAX_SHARE) return null
        return inBox
    }

    /**
     * The mask's outline as a clean closed shape: the convex hull of its edge pixels, as x, y
     * pairs in 0..1 of the picture. Null for an empty mask.
     */
    fun hull(mask: BooleanArray, w: Int, h: Int): FloatArray? {
        val pts = ArrayList<Pair<Float, Float>>()
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (!mask[i]) continue
            val edge = x == 0 || y == 0 || x == w - 1 || y == h - 1 || !mask[i - 1] || !mask[i + 1] || !mask[i - w] || !mask[i + w]
            if (edge) pts += (x + 0.5f) / w to (y + 0.5f) / h
        }
        val p = pts.distinct().sortedWith(compareBy({ it.first }, { it.second }))
        if (p.size < 3) return null
        fun cross(o: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>) =
            (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)
        val lower = ArrayList<Pair<Float, Float>>()
        for (q in p) { while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), q) <= 0) lower.removeAt(lower.size - 1); lower += q }
        val upper = ArrayList<Pair<Float, Float>>()
        for (q in p.asReversed()) { while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), q) <= 0) upper.removeAt(upper.size - 1); upper += q }
        val hull = lower.dropLast(1) + upper.dropLast(1)
        if (hull.size < 3) return null
        return FloatArray(hull.size * 2) { if (it % 2 == 0) hull[it / 2].first else hull[it / 2].second }
    }

    /**
     * The mask's true outline, dents and all — OpenCV's findContours then approxPolyDP: the edge
     * traced pixel by pixel round the outside (Moore neighbours), then thinned to the corners that
     * matter (Douglas–Peucker, tolerance a share of the perimeter). A mug keeps its handle and a
     * mouse its waist, where [hull] would wrap them in a smooth shell. x, y pairs in 0..1 of the
     * picture; null for an empty or one-pixel mask.
     */
    fun contour(mask: BooleanArray, w: Int, h: Int, tolerance: Double = 0.008): FloatArray? {
        val start = mask.indexOfFirst { it }
        if (start < 0) return null
        fun on(x: Int, y: Int) = x in 0 until w && y in 0 until h && mask[y * w + x]
        val sx = start % w; val sy = start / w
        val xs = ArrayList<Int>(); val ys = ArrayList<Int>()
        var x = sx; var y = sy; var from = 0 // the first pixel in reading order has nothing to its west
        var first = -1
        for (step in 0 until 4 * w * h) {
            var d = -1
            for (k in 0 until 8) { val c = (from + k) % 8; if (on(x + DX[c], y + DY[c])) { d = c; break } }
            if (d < 0) return null
            if (x == sx && y == sy) { if (first == d) break; if (first < 0) first = d }
            xs += x; ys += y
            x += DX[d]; y += DY[d]
            from = if (d % 2 == 0) (d + 6) % 8 else (d + 5) % 8 // the empty pixel checked last, seen from here
        }
        if (xs.size < 3) return null
        var perimeter = 0.0
        for (i in xs.indices) { val j = (i + 1) % xs.size; perimeter += hypot((xs[j] - xs[i]).toDouble(), (ys[j] - ys[i]).toDouble()) }
        // Closed shape: split at the point farthest from the start, simplify each half.
        val far = xs.indices.maxBy { (xs[it] - sx) * (xs[it] - sx) + (ys[it] - sy) * (ys[it] - sy) }
        val eps = (tolerance * perimeter).coerceAtLeast(1.0)
        val keep = BooleanArray(xs.size + 1); keep[0] = true; keep[far] = true; keep[xs.size] = true
        fun at(i: Int) = i % xs.size
        fun simplify(a: Int, b: Int) {
            var worst = -1; var worstD = eps
            val ax = xs[at(a)].toDouble(); val ay = ys[at(a)].toDouble(); val bx = xs[at(b)].toDouble(); val by = ys[at(b)].toDouble()
            val len = hypot(bx - ax, by - ay)
            for (i in a + 1 until b) {
                val px = xs[at(i)].toDouble(); val py = ys[at(i)].toDouble()
                val dist = if (len < 1e-9) hypot(px - ax, py - ay) else abs((bx - ax) * (ay - py) - (ax - px) * (by - ay)) / len
                if (dist > worstD) { worstD = dist; worst = i }
            }
            if (worst < 0) return
            keep[worst] = true; simplify(a, worst); simplify(worst, b)
        }
        simplify(0, far); simplify(far, xs.size)
        val idx = (0 until xs.size).filter { keep[it] }
        if (idx.size < 3) return null
        return FloatArray(idx.size * 2) { k -> val i = idx[k / 2]; if (k % 2 == 0) (xs[i] + 0.5f) / w else (ys[i] + 0.5f) / h }
    }

    /** The mask's centre of mass (image moments m10/m00, m01/m00), 0..1 of the picture; null if empty. */
    fun centroid(mask: BooleanArray, w: Int, h: Int): Pair<Float, Float>? {
        var m00 = 0L; var m10 = 0L; var m01 = 0L
        for (i in mask.indices) if (mask[i]) { m00++; m10 += i % w; m01 += i / w }
        if (m00 == 0L) return null
        return (m10.toFloat() / m00 + 0.5f) / w to (m01.toFloat() / m00 + 0.5f) / h
    }

    /** Eight neighbours, clockwise on screen from west. */
    private val DX = intArrayOf(-1, -1, 0, 1, 1, 1, 0, -1)
    private val DY = intArrayOf(0, -1, -1, -1, 0, 1, 1, 1)

    private val NEIGHBOURS = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

    private const val REFINE = 2

    /** Weakest gradient (Sobel, on 0..255 luma) that counts as a strong edge: above sensor noise. */
    private const val EDGE_MIN = 40f

    /** Up to [RING_SAMPLES] colours spread evenly through [all]: a colour model, as GrabCut's mixtures are. */
    private fun spread(all: List<DoubleArray>) =
        if (all.size <= RING_SAMPLES) all else List(RING_SAMPLES) { all[it * all.size / RING_SAMPLES] }

    private fun dist(a: DoubleArray, b: DoubleArray): Double {
        val dl = a[0] - b[0]; val da = a[1] - b[1]; val db = a[2] - b[2]
        return sqrt(dl * dl + da * da + db * db)
    }

    /** sRGB to CIE Lab (D65): distances in Lab follow what the eye calls different. */
    fun lab(argb: Int): DoubleArray {
        fun lin(c: Int): Double { val v = c / 255.0; return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4) }
        val r = lin((argb shr 16) and 0xFF); val g = lin((argb shr 8) and 0xFF); val b = lin(argb and 0xFF)
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
        fun f(t: Double) = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116
        val fx = f(x); val fy = f(y); val fz = f(z)
        return doubleArrayOf(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    /**
     * A 5 × 5 majority vote: each pixel becomes what most of its neighbours are. Clears the
     * saw-tooth a noisy edge leaves (a leather wallet's outline came out as zigzags) without
     * moving a clean edge.
     */
    private fun smooth(src: BooleanArray, w: Int, h: Int): BooleanArray {
        val sum = IntArray((w + 1) * (h + 1)) // integral image
        for (y in 0 until h) { var row = 0; for (x in 0 until w) { if (src[y * w + x]) row++; sum[(y + 1) * (w + 1) + x + 1] = sum[y * (w + 1) + x + 1] + row } }
        return BooleanArray(src.size) { i ->
            val x = i % w; val y = i / w
            val x0 = maxOf(0, x - 2); val x1 = minOf(w, x + 3); val y0 = maxOf(0, y - 2); val y1 = minOf(h, y + 3)
            val on = sum[y1 * (w + 1) + x1] - sum[y0 * (w + 1) + x1] - sum[y1 * (w + 1) + x0] + sum[y0 * (w + 1) + x0]
            on * 2 > (x1 - x0) * (y1 - y0)
        }
    }

    private fun erode(src: BooleanArray, w: Int, h: Int) = BooleanArray(src.size) { i ->
        val x = i % w; val y = i / w
        src[i] && (x == 0 || src[i - 1]) && (x == w - 1 || src[i + 1]) && (y == 0 || src[i - w]) && (y == h - 1 || src[i + w])
    }

    private fun dilate(src: BooleanArray, w: Int, h: Int) = BooleanArray(src.size) { i ->
        val x = i % w; val y = i / w
        src[i] || (x > 0 && src[i - 1]) || (x < w - 1 && src[i + 1]) || (y > 0 && src[i - w]) || (y < h - 1 && src[i + w])
    }
}
