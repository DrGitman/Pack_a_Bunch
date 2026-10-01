package com.packabunch.packing

import kotlin.math.sqrt

/**
 * HarshdeepJ's Object_Volume_Detector (`main.py`), ported to run on the phone's depth image.
 *
 * Their method, step for step:
 *  1. normalise the depth map, Gaussian blur (kernel 5), Sobel gradient;
 *  2. edges are where the gradient passes 0.05 of its maximum; close, then dilate;
 *  3. the regions *between* edges are the candidate objects (8-connected), each kept if large
 *     enough, padded by 10%, nearest first;
 *  4. each object's distance `d` is the median depth inside its box;
 *  5. `W = w_px · d / f`, `H = h_px · d / f` — the pinhole relation `h = a·d/f`.
 *
 * Two changes, both because the input is better, not different: their depth is MiDaS output,
 * which is only relative and needs an ID card in shot to scale it; ARCore's is already in
 * millimetres, so `d` is used directly and no reference object is needed. And a region touching
 * the edge of the picture is dropped (their `>95% of image` filter, tightened the way Intel's
 * object-size-detector does it), since on a phone that is always the table or the wall.
 */
object DepthEdgeObjects {

    /** `gradient_threshold=0.05` in `multistage_object_detection`. */
    const val GRADIENT_THRESHOLD = 0.05f

    /** `padding_ratio=0.1`. */
    const val PADDING = 0.1f

    /**
     * `min_object_size=300` pixels on a 4032×3024 photo, as a share of the picture so it holds on a
     * depth image a fraction of that size.
     */
    const val MIN_AREA_SHARE = 0.0015f

    /** A region filling more of the picture than this is the scene. */
    const val MAX_AREA_SHARE = 0.5f

    /** Growing back to the outline accepts pixels this close to the object's own depth... */
    const val GROW_TOLERANCE_MM = 25

    /** ...or this share of its distance, whichever is looser, since depth noise grows with range. */
    const val GROW_TOLERANCE_SHARE = 0.04f

    /** Theirs is 5; the free plan counts up to 20 items. */
    const val MAX_OBJECTS = 20

    /**
     * One object. The box is in depth-image pixels, right and bottom exclusive. [widthMm] and
     * [heightMm] are the object's face as seen from this viewpoint.
     */
    class Found(
        val left: Int, val top: Int, val right: Int, val bottom: Int,
        val depthMm: Int, val widthMm: Float, val heightMm: Float,
    )

    /**
     * @param depth millimetres per pixel, 0 where there is no reading.
     * @param fx focal length in depth-image pixels, horizontal.
     * @param fy focal length in depth-image pixels, vertical.
     * @param background pixels at or beyond the supporting surface. Their own debug image shows
     *   why this is needed: depth edges trace an object's sides and top, but where it stands on
     *   the table there is no depth jump, so the bottom of the outline stays open and the object
     *   leaks into the table. Marking the table itself as edge closes it.
     */
    fun find(
        depth: IntArray, width: Int, height: Int, fx: Float, fy: Float,
        background: BooleanArray? = null,
    ): List<Found> {
        val n = width * height
        require(depth.size == n)
        if (n == 0) return emptyList()

        // 1. Normalise to 0..255 over the valid range. Unlike MiDaS, ARCore leaves holes; fill each
        //    from its row so a hole is not mistaken for an object's edge.
        var lo = Int.MAX_VALUE; var hi = Int.MIN_VALUE
        for (d in depth) if (d > 0) { lo = minOf(lo, d); hi = maxOf(hi, d) }
        if (lo == Int.MAX_VALUE) return emptyList()
        val span = (hi - lo).coerceAtLeast(1).toFloat()
        val filled = FloatArray(n)
        for (y in 0 until height) {
            var last = -1f
            for (x in 0 until width) {
                val d = depth[y * width + x]
                if (d > 0) last = (d - lo) / span * 255f
                filled[y * width + x] = last
            }
            // Leading holes take the first reading to their right.
            var next = -1f
            for (x in width - 1 downTo 0) {
                val i = y * width + x
                if (filled[i] >= 0f) next = filled[i] else filled[i] = next
            }
        }
        for (i in 0 until n) if (filled[i] < 0f) filled[i] = 0f

        // Gaussian blur, kernel 5: the binomial 1-4-6-4-1, separable.
        val blurred = blur5(blur5(filled, width, height, horizontal = true), width, height, horizontal = false)

        // 2. Sobel magnitude, normalised by its maximum, thresholded.
        val grad = FloatArray(n)
        var gMax = 0f
        for (y in 1 until height - 1) for (x in 1 until width - 1) {
            fun p(dx: Int, dy: Int) = blurred[(y + dy) * width + x + dx]
            val gx = (p(1, -1) + 2 * p(1, 0) + p(1, 1)) - (p(-1, -1) + 2 * p(-1, 0) + p(-1, 1))
            val gy = (p(-1, 1) + 2 * p(0, 1) + p(1, 1)) - (p(-1, -1) + 2 * p(0, -1) + p(1, -1))
            val g = sqrt(gx * gx + gy * gy)
            grad[y * width + x] = g
            if (g > gMax) gMax = g
        }
        if (gMax <= 0f) return emptyList()
        var edges = BooleanArray(n) { grad[it] / gMax > GRADIENT_THRESHOLD }
        // Close (edge_closing_size=2), then their dilations: once on the edges, twice on the contours.
        edges = erode(dilate(edges, width, height), width, height)
        repeat(3) { edges = dilate(edges, width, height) }
        if (background != null) {
            require(background.size == n)
            edges = BooleanArray(n) { edges[it] || background[it] }
        }

        // 3. Regions between edges, 8-connected.
        val label = IntArray(n) { -1 }
        val minArea = (n * MIN_AREA_SHARE).toInt().coerceAtLeast(12)
        val maxArea = (n * MAX_AREA_SHARE).toInt()
        val found = ArrayList<Found>()
        val queue = IntArray(n)
        var next = 0
        for (start in 0 until n) {
            if (edges[start] || label[start] >= 0 || depth[start] <= 0) continue
            var head = 0; var tail = 0
            queue[tail++] = start; label[start] = next
            var l = width; var t = height; var r = -1; var b = -1
            val readings = ArrayList<Int>()
            while (head < tail) {
                val i = queue[head++]
                val x = i % width; val y = i / width
                l = minOf(l, x); r = maxOf(r, x); t = minOf(t, y); b = maxOf(b, y)
                if (depth[i] > 0) readings += depth[i]
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx !in 0 until width || ny !in 0 until height) continue
                    val j = ny * width + nx
                    if (!edges[j] && label[j] < 0 && depth[j] > 0) { label[j] = next; queue[tail++] = j }
                }
            }
            if (tail < minArea || tail > maxArea) { next++; continue }

            // 4. Median depth of the region.
            readings.sort()
            val d = readings[readings.size / 2]

            // Grow back out to the true outline. Their blur and dilations eat a few pixels round
            // every object — nothing on a 4032-pixel photo, most of a cup on a 160-pixel depth
            // image. The edge pixels still at the object's own depth are the object; the ones at
            // the table's or the wall's are not.
            val tolerance = maxOf(GROW_TOLERANCE_MM, (d * GROW_TOLERANCE_SHARE).toInt())
            var h2 = 0
            while (h2 < tail) {
                val i = queue[h2++]
                val x = i % width; val y = i / width
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx !in 0 until width || ny !in 0 until height) continue
                    val j = ny * width + nx
                    if (label[j] >= 0 || depth[j] <= 0 || background?.get(j) == true) continue
                    if (kotlin.math.abs(depth[j] - d) > tolerance) continue
                    label[j] = next; queue[tail++] = j
                    l = minOf(l, nx); r = maxOf(r, nx); t = minOf(t, ny); b = maxOf(b, ny)
                }
            }
            next++
            // Touching the picture's edge: the table or the wall, never a whole item.
            if (l == 0 || t == 0 || r == width - 1 || b == height - 1) continue

            // Padding, as theirs, applied to the box they measure.
            val padX = ((r - l + 1) * PADDING).toInt(); val padY = ((b - t + 1) * PADDING).toInt()
            val pl = (l - padX).coerceAtLeast(0); val pt = (t - padY).coerceAtLeast(0)
            val pr = (r + 1 + padX).coerceAtMost(width); val pb = (b + 1 + padY).coerceAtMost(height)

            // 5. W = w·d/f, H = h·d/f, on the unpadded region: padding is for the crop, not the size.
            found += Found(
                pl, pt, pr, pb, d,
                widthMm = (r - l + 1) * d / fx,
                heightMm = (b - t + 1) * d / fy,
            )
        }
        // Nearest first, as theirs.
        return found.sortedBy { it.depthMm }.take(MAX_OBJECTS)
    }

    private fun blur5(src: FloatArray, w: Int, h: Int, horizontal: Boolean): FloatArray {
        val k = floatArrayOf(1f, 4f, 6f, 4f, 1f)
        val out = FloatArray(src.size)
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0f; var weight = 0f
            for (o in -2..2) {
                val sx = if (horizontal) x + o else x
                val sy = if (horizontal) y else y + o
                if (sx !in 0 until w || sy !in 0 until h) continue
                sum += src[sy * w + sx] * k[o + 2]; weight += k[o + 2]
            }
            out[y * w + x] = sum / weight
        }
        return out
    }

    private fun dilate(src: BooleanArray, w: Int, h: Int) = BooleanArray(src.size) { i ->
        val x = i % w; val y = i / w
        var hit = false
        for (dy in -1..1) for (dx in -1..1) {
            val nx = x + dx; val ny = y + dy
            if (nx in 0 until w && ny in 0 until h && src[ny * w + nx]) { hit = true }
        }
        hit
    }

    private fun erode(src: BooleanArray, w: Int, h: Int) = BooleanArray(src.size) { i ->
        val x = i % w; val y = i / w
        var all = true
        for (dy in -1..1) for (dx in -1..1) {
            val nx = x + dx; val ny = y + dy
            if (nx in 0 until w && ny in 0 until h && !src[ny * w + nx]) { all = false }
        }
        all
    }
}
