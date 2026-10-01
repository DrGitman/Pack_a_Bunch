package com.packabunch.packing

import kotlin.math.cbrt
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

        var fg = BooleanArray(bw * bh) { i ->
            val c = lab(argb[(y0 + i / bw) * w + x0 + i % bw])
            samples.none { dist(it, c) < threshold }
        }
        // Remove specks, then grow back (open), then bridge small gaps (close).
        fg = dilate(erode(fg, bw, bh), bw, bh)
        fg = erode(dilate(fg, bw, bh), bw, bh)

        // The piece that owns the middle of the box.
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
        val inBox = BooleanArray(bw * bh) { !outside[it] }
        val area = inBox.count { it }
        if (area < bw * bh * MIN_SHARE || area > bw * bh * MAX_SHARE) return null
        val out = BooleanArray(w * h)
        for (i in inBox.indices) if (inBox[i]) out[(y0 + i / bw) * w + x0 + i % bw] = true
        return out
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

    private val NEIGHBOURS = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

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

    private fun erode(src: BooleanArray, w: Int, h: Int) = BooleanArray(src.size) { i ->
        val x = i % w; val y = i / w
        src[i] && (x == 0 || src[i - 1]) && (x == w - 1 || src[i + 1]) && (y == 0 || src[i - w]) && (y == h - 1 || src[i + w])
    }

    private fun dilate(src: BooleanArray, w: Int, h: Int) = BooleanArray(src.size) { i ->
        val x = i % w; val y = i / w
        src[i] || (x > 0 && src[i - 1]) || (x < w - 1 && src[i + 1]) || (y > 0 && src[i - w]) || (y < h - 1 && src[i + w])
    }
}
