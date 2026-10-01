package com.packabunch.packing

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Finds four-sided flat things in a picture — the card or the sheet of A4 the scan measures by.
 *
 * The OpenCV recipe the measuring tutorials use (blur → edges → contours → the four-cornered one),
 * written out in plain Kotlin because there is no OpenCV on the phone:
 *  1. blur, then gradient magnitude; strong gradient is edge;
 *  2. the regions *between* edges are candidate faces — a card's face is one region;
 *  3. each region's outline is reduced to the largest four-sided shape inside its convex hull, kept
 *     only if the region nearly fills it (a card is solid; a gap between objects is not);
 *  4. each side is then re-fitted to where the brightness changes most steeply across it, to a
 *     fraction of a pixel, and the corners are where those lines cross.
 *
 * Step 4 is what makes it a measuring tool and not just a finder: on a card 150 pixels across, two
 * pixels of corner error is 3% on every length the scan reports.
 *
 * Which quad is actually the card is decided by [PlanarPose.fromQuad]: only a true 85.6 × 54 mm
 * rectangle seen in perspective gives square, equal axes.
 */
object CardFinder {

    /**
     * A candidate: corners in picture pixels in order round the shape, how solid it is, and the
     * sub-pixel edge points found along each side (side s runs from corner s to corner s + 1) —
     * the pose is fitted to all of them, not just the four corners.
     */
    class Quad(val corners: DoubleArray, val solidity: Double, val areaPx: Double, val sides: List<DoubleArray> = emptyList())

    private const val MIN_AREA_SHARE = 0.0015
    private const val MAX_AREA_SHARE = 0.35
    private const val MIN_SOLIDITY = 0.80
    private const val HULL_POINTS = 48
    private const val REFINE_SAMPLES = 24
    private const val REFINE_REACH = 4

    /** @param gray brightness 0..255, row by row. */
    fun find(gray: IntArray, width: Int, height: Int, maxQuads: Int = 6): List<Quad> {
        val n = width * height
        require(gray.size == n)
        val blurred = blur(gray.map { it.toFloat() }.toFloatArray(), width, height)
        val mag = FloatArray(n)
        for (y in 1 until height - 1) for (x in 1 until width - 1) {
            fun p(dx: Int, dy: Int) = blurred[(y + dy) * width + x + dx]
            val gx = (p(1, -1) + 2 * p(1, 0) + p(1, 1)) - (p(-1, -1) + 2 * p(-1, 0) + p(-1, 1))
            val gy = (p(-1, 1) + 2 * p(0, 1) + p(1, 1)) - (p(-1, -1) + 2 * p(0, -1) + p(1, -1))
            mag[y * width + x] = sqrt(gx * gx + gy * gy)
        }
        val sorted = mag.copyOf().also { it.sort() }
        val threshold = maxOf(24f, 0.25f * sorted[(n * 0.99).toInt().coerceAtMost(n - 1)])
        var edge = BooleanArray(n) { mag[it] > threshold }
        edge = dilate(edge, width, height)

        val label = IntArray(n) { -1 }
        val queue = IntArray(n)
        val quads = ArrayList<Quad>()
        var next = 0
        for (start in 0 until n) {
            if (edge[start] || label[start] >= 0) continue
            var head = 0; var tail = 0
            queue[tail++] = start; label[start] = next
            var touches = false
            while (head < tail) {
                val i = queue[head++]
                val x = i % width; val y = i / width
                if (x == 0 || y == 0 || x == width - 1 || y == height - 1) touches = true
                if (x > 0) visit(i - 1, edge, label, next, queue, tail).also { tail = it }
                if (x < width - 1) visit(i + 1, edge, label, next, queue, tail).also { tail = it }
                if (y > 0) visit(i - width, edge, label, next, queue, tail).also { tail = it }
                if (y < height - 1) visit(i + width, edge, label, next, queue, tail).also { tail = it }
            }
            val id = next++
            if (touches || tail < n * MIN_AREA_SHARE || tail > n * MAX_AREA_SHARE) continue

            // Outline points: region pixels with a neighbour outside it.
            val boundary = ArrayList<Int>()
            for (q in 0 until tail) {
                val i = queue[q]; val x = i % width; val y = i / width
                if (label[i - 1] != id || label[i + 1] != id || label[i - width] != id || label[i + width] != id) boundary += i
            }
            val hull = convexHull(boundary.map { (it % width).toDouble() to (it / width).toDouble() })
            if (hull.size < 4) continue
            val quad = largestQuad(thin(hull, HULL_POINTS)) ?: continue
            val area = polygonArea(quad)
            // The region lost ~1.5 px all round to the edge band; allow for it before judging.
            val perimeter = (0 until 4).sumOf { hypot(quad[2 * ((it + 1) % 4)] - quad[2 * it], quad[2 * ((it + 1) % 4) + 1] - quad[2 * it + 1]) }
            val solidity = (tail + perimeter * 1.5) / area
            if (solidity < MIN_SOLIDITY) continue
            val refined = refine(quad, blurred, width, height)
            quads += Quad(refined?.first ?: quad, solidity.coerceAtMost(1.0), area, refined?.second.orEmpty())
        }
        return quads.sortedByDescending { it.solidity * sqrt(it.areaPx) }.take(maxQuads)
    }

    private fun visit(j: Int, edge: BooleanArray, label: IntArray, id: Int, queue: IntArray, tail: Int): Int {
        if (edge[j] || label[j] >= 0) return tail
        label[j] = id; queue[tail] = j
        return tail + 1
    }

    /**
     * Moves each side onto the steepest brightness change across it, then intersects neighbours.
     * Null if a side cannot be found (then the rough quad stands).
     */
    private fun refine(quad: DoubleArray, img: FloatArray, w: Int, h: Int): Pair<DoubleArray, List<DoubleArray>>? {
        val lines = ArrayList<DoubleArray>() // a, b, c with a·x + b·y = c, (a, b) unit
        val sides = ArrayList<DoubleArray>()
        for (s in 0 until 4) {
            val x0 = quad[2 * s]; val y0 = quad[2 * s + 1]
            val x1 = quad[2 * ((s + 1) % 4)]; val y1 = quad[2 * ((s + 1) % 4) + 1]
            val len = hypot(x1 - x0, y1 - y0)
            if (len < 8) return null
            val nx = -(y1 - y0) / len; val ny = (x1 - x0) / len
            val xs = ArrayList<Double>(); val ys = ArrayList<Double>()
            for (k in 1 until REFINE_SAMPLES) {
                val f = k.toDouble() / REFINE_SAMPLES
                if (f < 0.15 || f > 0.85) continue // corners are rounded on cards; use the straight middle
                val px = x0 + (x1 - x0) * f; val py = y0 + (y1 - y0) * f
                var best = 0.0; var bestOff = Double.NaN
                for (o in -REFINE_REACH * 4..REFINE_REACH * 4) {
                    val off = o / 4.0
                    val g = abs(sample(img, w, h, px + nx * (off + 0.5), py + ny * (off + 0.5)) -
                        sample(img, w, h, px + nx * (off - 0.5), py + ny * (off - 0.5)))
                    if (g > best) { best = g; bestOff = off }
                }
                if (!bestOff.isNaN()) { xs += px + nx * bestOff; ys += py + ny * bestOff }
            }
            if (xs.size < 4) return null
            lines += fitLine(xs, ys)
            sides += DoubleArray(xs.size * 2) { if (it % 2 == 0) xs[it / 2] else ys[it / 2] }
        }
        val out = DoubleArray(8)
        for (c in 0 until 4) {
            val a = lines[(c + 3) % 4]; val b = lines[c]
            val det = a[0] * b[1] - a[1] * b[0]
            if (abs(det) < 1e-6) return null
            out[2 * c] = (a[2] * b[1] - a[1] * b[2]) / det
            out[2 * c + 1] = (a[0] * b[2] - a[2] * b[0]) / det
        }
        // A refinement that moved a corner far was misled; keep the rough one.
        for (c in 0 until 4) if (hypot(out[2 * c] - quad[2 * c], out[2 * c + 1] - quad[2 * c + 1]) > REFINE_REACH * 2.0) return null
        return out to sides
    }

    private fun fitLine(xs: List<Double>, ys: List<Double>): DoubleArray {
        val mx = xs.average(); val my = ys.average()
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for (i in xs.indices) { val dx = xs[i] - mx; val dy = ys[i] - my; sxx += dx * dx; syy += dy * dy; sxy += dx * dy }
        // Normal = eigenvector of the smaller eigenvalue of the scatter matrix.
        val theta = 0.5 * kotlin.math.atan2(2 * sxy, sxx - syy)
        val a = -kotlin.math.sin(theta); val b = kotlin.math.cos(theta)
        return doubleArrayOf(a, b, a * mx + b * my)
    }

    private fun sample(img: FloatArray, w: Int, h: Int, x: Double, y: Double): Double {
        val xi = x.toInt().coerceIn(0, w - 2); val yi = y.toInt().coerceIn(0, h - 2)
        val fx = (x - xi).coerceIn(0.0, 1.0); val fy = (y - yi).coerceIn(0.0, 1.0)
        val a = img[yi * w + xi]; val b = img[yi * w + xi + 1]; val c = img[(yi + 1) * w + xi]; val d = img[(yi + 1) * w + xi + 1]
        return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy
    }

    /** Largest-area quadrilateral with corners on the hull. */
    private fun largestQuad(h: List<Pair<Double, Double>>): DoubleArray? {
        val n = h.size
        if (n < 4) return null
        fun tri(a: Int, b: Int, c: Int) = abs((h[b].first - h[a].first) * (h[c].second - h[a].second) -
            (h[c].first - h[a].first) * (h[b].second - h[a].second)) / 2
        var best = 0.0; var bi = intArrayOf()
        for (i in 0 until n) for (k in i + 2 until n) {
            var bj = -1; var aj = 0.0
            for (j in i + 1 until k) { val a = tri(i, j, k); if (a > aj) { aj = a; bj = j } }
            var bl = -1; var al = 0.0
            for (l in k + 1 until n + i) { val li = l % n; val a = tri(k, li, i); if (a > al) { al = a; bl = li } }
            if (bj >= 0 && bl >= 0 && aj + al > best) { best = aj + al; bi = intArrayOf(i, bj, k, bl) }
        }
        if (bi.isEmpty()) return null
        return DoubleArray(8) { if (it % 2 == 0) h[bi[it / 2]].first else h[bi[it / 2]].second }
    }

    private fun polygonArea(q: DoubleArray): Double {
        var a = 0.0
        for (i in 0 until 4) { val j = (i + 1) % 4; a += q[2 * i] * q[2 * j + 1] - q[2 * j] * q[2 * i + 1] }
        return abs(a) / 2
    }

    private fun thin(h: List<Pair<Double, Double>>, max: Int): List<Pair<Double, Double>> =
        if (h.size <= max) h else List(max) { h[it * h.size / max] }

    private fun convexHull(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val p = points.distinct().sortedWith(compareBy({ it.first }, { it.second }))
        if (p.size < 3) return p
        fun cross(o: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>) =
            (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)
        val lower = ArrayList<Pair<Double, Double>>()
        for (q in p) { while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), q) <= 0) lower.removeAt(lower.size - 1); lower += q }
        val upper = ArrayList<Pair<Double, Double>>()
        for (q in p.asReversed()) { while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), q) <= 0) upper.removeAt(upper.size - 1); upper += q }
        return lower.dropLast(1) + upper.dropLast(1)
    }

    private fun blur(src: FloatArray, w: Int, h: Int): FloatArray {
        val k = floatArrayOf(1f, 4f, 6f, 4f, 1f)
        val tmp = FloatArray(src.size); val out = FloatArray(src.size)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0f; var wt = 0f
            for (o in -2..2) { val xx = x + o; if (xx in 0 until w) { s += src[y * w + xx] * k[o + 2]; wt += k[o + 2] } }
            tmp[y * w + x] = s / wt
        }
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0f; var wt = 0f
            for (o in -2..2) { val yy = y + o; if (yy in 0 until h) { s += tmp[yy * w + x] * k[o + 2]; wt += k[o + 2] } }
            out[y * w + x] = s / wt
        }
        return out
    }

    private fun dilate(src: BooleanArray, w: Int, h: Int) = BooleanArray(src.size) { i ->
        val x = i % w; val y = i / w
        src[i] || (x > 0 && src[i - 1]) || (x < w - 1 && src[i + 1]) || (y > 0 && src[i - w]) || (y < h - 1 && src[i + w])
    }
}
