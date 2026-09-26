package com.packabunch.packing

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * What an object's measured points look like, used for the outline drawn around it and as a
 * hint for which library model to show. Never used for fit calculations — see [FittedObject].
 */
enum class ShapeFamily { BOX, CYLINDER, TAPERED, SPHERE, IRREGULAR }

/**
 * One object, fitted.
 *
 * The **bounding box** ([widthMm], [depthMm], [heightMm] at [yawDegrees] around the centre) is
 * the conservative geometry the solver may use. Everything about shape — [shape], the radii,
 * the hull — exists only to draw an outline that hugs the real object and to suggest a model
 * family. That split is deliberate: recognising "this is a cup" must never change how much
 * space the solver thinks the cup takes.
 *
 * [widthMm] is always the longer footprint edge, so the two cannot swap between frames and
 * make a perfectly still object look unstable.
 */
data class FittedObject(
    val centreXMm: Float,
    val centreYMm: Float,
    /** Direction of the width edge from the surface's X axis, degrees in [0, 180). */
    val yawDegrees: Float,
    val widthMm: Float,
    val depthMm: Float,
    val heightMm: Float,
    val shape: ShapeFamily,
    /** Round shapes only: radius of the topmost and bottommost cross-sections that could be fitted. */
    val topRadiusMm: Float? = null,
    val bottomRadiusMm: Float? = null,
    /** Irregular shapes only: the footprint's convex hull, anticlockwise, for a hugging outline. */
    val footprintHull: List<Pair<Float, Float>> = emptyList(),
    /** Which of the three lengths are backed by something actually seen, rather than inferred. */
    val observedAxes: Set<Axis>,
    val pointCount: Int,
) {
    val dimensions: Dimensions
        get() = Dimensions(
            widthMm = kotlin.math.round(widthMm).toInt(),
            depthMm = kotlin.math.round(depthMm).toInt(),
            heightMm = kotlin.math.round(heightMm).toInt(),
        )
}

/**
 * Fits a shape to an object's points, in its support surface's frame.
 *
 * ### Footprint
 * The tightest rectangle around the points, found by sweeping the rotation — a rotated item is
 * measured as itself, not as the larger axis-aligned box around it.
 *
 * ### Round objects
 * Each horizontal slice is tested for being a circle. A circle is fully determined by an arc of
 * it, so a cup seen only from the front still gets its true diameter — the half it has never
 * shown is not guessed, it is implied by the half it has. If every usable slice is a circle,
 * the object is round, and the slices say whether it is a cylinder, tapers, or is a sphere.
 *
 * ### What was actually seen
 * A face only counts as observed if a camera looked at it from more than [MIN_VIEW_ANGLE_DEG]
 * off grazing *and* points landed on it. The camera half matters: from a single straight-on
 * view, a box's front face collapses the fitted depth to a few millimetres of noise, and the
 * points alone are self-consistent with that. Only the viewpoint reveals that the depth was
 * never visible.
 */
object ShapeFitter {

    const val MIN_POINTS = 20
    const val MIN_VIEW_ANGLE_DEG = 15.0

    fun fit(
        points: List<PlanePoint>,
        cameras: List<PlanePoint>,
        voxelMm: Float = ObjectCloud.DEFAULT_VOXEL_MM,
        /** Sightings per point (see [ObjectCloud.snapshot]). Null treats every point equally. */
        weights: FloatArray? = null,
    ): FittedObject? {
        val n = points.size
        if (n < MIN_POINTS) return null
        val wts = weights ?: FloatArray(n) { 1f }
        // Rotation is found from the well-seen surface only; a few stray voxels far out would
        // otherwise tilt the tightest rectangle.
        val strong = run {
            val sorted = wts.copyOf().also { it.sort() }
            val cut = 0.3f * sorted[((n - 1) * 0.9).toInt()]
            (0 until n).filter { wts[it] >= cut }.let { if (it.size >= MIN_POINTS) it else (0 until n).toList() }
        }
        val xs = FloatArray(n) { points[it].xMm }
        val ys = FloatArray(n) { points[it].yMm }
        val hs = FloatArray(n) { points[it].hMm }

        // ---- tightest footprint rectangle
        var bestDeg = 0.0
        var bestArea = Double.MAX_VALUE
        fun areaAt(deg: Double): Double {
            val r = deg * PI / 180
            val c = cos(r); val s = sin(r)
            var u0 = Double.MAX_VALUE; var u1 = -Double.MAX_VALUE
            var v0 = Double.MAX_VALUE; var v1 = -Double.MAX_VALUE
            for (i in strong) {
                val u = xs[i] * c + ys[i] * s
                val v = -xs[i] * s + ys[i] * c
                if (u < u0) u0 = u; if (u > u1) u1 = u
                if (v < v0) v0 = v; if (v > v1) v1 = v
            }
            return (u1 - u0) * (v1 - v0)
        }
        // Coarse to fine: every 3°, then ±3° by 0.5°, then ±0.5° by 0.1°. Area is smooth in
        // the angle near its minimum, so this finds the same answer as a 0.1° sweep for a
        // twentieth of the work — it runs for every object, every few frames, on a phone.
        for (d in 0 until 90 step 3) { val a = areaAt(d.toDouble()); if (a < bestArea) { bestArea = a; bestDeg = d.toDouble() } }
        for ((span, step) in listOf(3.0 to 0.5, 0.5 to 0.1)) {
            val base = bestDeg
            var d = base - span
            while (d <= base + span + 1e-9) { val a = areaAt(d); if (a < bestArea) { bestArea = a; bestDeg = d }; d += step }
        }
        val rad = bestDeg * PI / 180
        val c = cos(rad).toFloat(); val s = sin(rad).toFloat()
        val us = FloatArray(n) { xs[it] * c + ys[it] * s }
        val vs = FloatArray(n) { -xs[it] * s + ys[it] * c }
        val (uMin, uMax) = robustRange(us, wts)
        val (vMin, vMax) = robustRange(vs, wts)
        var height = robustRange(hs, wts).second.coerceAtLeast(voxelMm)
        val uMid = (uMin + uMax) / 2; val vMid = (vMin + vMax) / 2
        val rectCx = uMid * c - vMid * s
        val rectCy = uMid * s + vMid * c
        val extU = uMax - uMin
        val extV = vMax - vMin

        // ---- which faces were really seen
        val tol = max(2f * voxelMm, 10f)
        val need = max(5, (n * 0.03f).toInt())
        val ux = c; val uy = s            // û
        val vx = -s; val vy = c           // v̂
        fun faceSeen(nx: Float, ny: Float, nh: Float, fx: Float, fy: Float, fh: Float, onFace: (Int) -> Boolean): Boolean {
            var count = 0
            for (i in 0 until n) if (onFace(i)) { count++; if (count >= need) break }
            if (count < need) return false
            val minCos = sin(MIN_VIEW_ANGLE_DEG * PI / 180).toFloat()
            return cameras.any { cam ->
                val dx = cam.xMm - fx; val dy = cam.yMm - fy; val dh = cam.hMm - fh
                val len = sqrt(dx * dx + dy * dy + dh * dh)
                len > 1f && (dx * nx + dy * ny + dh * nh) / len >= minCos
            }
        }
        val midH = height / 2
        val topSeen = faceSeen(0f, 0f, 1f, rectCx, rectCy, height) { hs[it] >= height - tol }
        val minusV = faceSeen(-vx, -vy, 0f, rectCx + vx * (vMin - vMid), rectCy + vy * (vMin - vMid), midH) { vs[it] <= vMin + tol }
        val plusV = faceSeen(vx, vy, 0f, rectCx + vx * (vMax - vMid), rectCy + vy * (vMax - vMid), midH) { vs[it] >= vMax - tol }
        val minusU = faceSeen(-ux, -uy, 0f, rectCx + ux * (uMin - uMid), rectCy + uy * (uMin - uMid), midH) { us[it] <= uMin + tol }
        val plusU = faceSeen(ux, uy, 0f, rectCx + ux * (uMax - uMid), rectCy + uy * (uMax - uMid), midH) { us[it] >= uMax - tol }
        // A face spans the two axes it lies along: ±v faces show u and height, ±u faces v and height.
        var uObserved = topSeen || minusV || plusV
        var vObserved = topSeen || minusU || plusU
        var hObserved = minusV || plusV || minusU || plusU

        // ---- round?
        val round = fitRound(xs, ys, hs, height, voxelMm)
        var shape: ShapeFamily
        var cx = rectCx; var cy = rectCy
        var w: Float; var d: Float; var yaw: Float
        var topR: Float? = null; var botR: Float? = null
        var hull: List<Pair<Float, Float>> = emptyList()
        if (round != null) {
            shape = round.family
            cx = round.cx; cy = round.cy
            w = 2 * round.rMax; d = w; yaw = 0f
            // A ball's surface is spread evenly over its height, so trimming shaves its crown;
            // resting on the surface, its height is its diameter.
            if (shape == ShapeFamily.SPHERE) height = max(height, w)
            topR = round.rTop; botR = round.rBottom
            // An arc this wide pins the circle down in every direction — both footprint lengths
            // are known even though the far side was never in view. Any slice at all means a
            // side was seen, so height is established too.
            if (round.bestArcDeg >= ROUND_OBSERVED_ARC_DEG || topSeen) { uObserved = true; vObserved = true }
            hObserved = true
        } else {
            // Width is the longer edge, always, so the two can never swap between frames.
            val swap = extV > extU
            w = if (swap) extV else extU
            d = if (swap) extU else extV
            val widthDir = if (swap) bestDeg + 90 else bestDeg
            yaw = (((widthDir % 180) + 180) % 180).toFloat()
            if (swap) { val t = uObserved; uObserved = vObserved; vObserved = t }
            // How much of the cloud lies on the box's own faces. A box is nearly all face; a
            // shoe or a bag is not, and gets a hull outline instead of pretending to be a box.
            var onBox = 0
            for (i in 0 until n) {
                if (hs[i] >= height - tol || us[i] <= uMin + tol || us[i] >= uMax - tol || vs[i] <= vMin + tol || vs[i] >= vMax - tol) onBox++
            }
            // An L-shaped or wedge-shaped thing can be nearly all face and still not fill its
            // rectangle. Only judged once both footprint lengths were seen — from one side a
            // box's footprint is a thin sliver and says nothing about fill.
            val candidateHull by lazy { convexHull(xs, ys) }
            val fillsRect = !(uObserved && vObserved) || extU * extV <= 1f ||
                polygonArea(candidateHull) / (extU * extV) >= BOX_HULL_FILL
            shape = if (onBox >= n * BOX_FACE_SHARE && fillsRect) ShapeFamily.BOX else ShapeFamily.IRREGULAR
            if (shape == ShapeFamily.IRREGULAR) hull = candidateHull
            if (shape == ShapeFamily.BOX) {
                // Place each seen face at the weighted median of the points on it, not at its
                // outermost point. Depth noise is symmetric about a real face, so the median
                // sits on it; the extreme sits a couple of noise-widths outside, and drifts
                // further out the longer the sweep runs.
                val sideOnly = { i: Int -> hs[i] < height - tol }
                fun face(seen: Boolean, v: FloatArray, fallback: Float, sel: (Int) -> Boolean): Float {
                    if (!seen) return fallback
                    val idx = (0 until n).filter(sel)
                    return if (idx.size < need) fallback else weightedMedian(idx, v, wts)
                }
                val u0 = face(minusU, us, uMin) { us[it] <= uMin + tol && sideOnly(it) }
                val u1 = face(plusU, us, uMax) { us[it] >= uMax - tol && sideOnly(it) }
                val v0 = face(minusV, vs, vMin) { vs[it] <= vMin + tol && sideOnly(it) }
                val v1 = face(plusV, vs, vMax) { vs[it] >= vMax - tol && sideOnly(it) }
                height = face(topSeen, hs, height) { hs[it] >= height - tol }
                val eu = u1 - u0; val ev = v1 - v0
                w = if (swap) ev else eu
                d = if (swap) eu else ev
                val um = (u0 + u1) / 2; val vm = (v0 + v1) / 2
                cx = um * c - vm * s
                cy = um * s + vm * c
            }
        }

        val observed = buildSet {
            if (uObserved) add(Axis.WIDTH)
            if (vObserved) add(Axis.DEPTH)
            if (hObserved) add(Axis.HEIGHT)
        }
        return FittedObject(
            centreXMm = cx, centreYMm = cy, yawDegrees = yaw,
            widthMm = w, depthMm = d, heightMm = height,
            shape = shape, topRadiusMm = topR, bottomRadiusMm = botR,
            footprintHull = hull, observedAxes = observed, pointCount = n,
        )
    }

    // ------------------------------------------------------------------ round shapes

    /**
     * One horizontal band fitted as a circle. The radius is allowed to change linearly with
     * height inside the band — that is what lets a tapered cup or the shoulder of a ball pass,
     * while a box corner (vertical walls, no taper to explain its misfit) still fails.
     */
    private class Circle(
        val cx: Float, val cy: Float,
        /** Radius at the band's middle, bottom edge and top edge. */
        val r: Float, val rLow: Float, val rHigh: Float,
        val rms: Float, val arcDeg: Float, val count: Int,
    )

    private class Round(
        val family: ShapeFamily, val cx: Float, val cy: Float,
        val rMax: Float, val rTop: Float, val rBottom: Float, val bestArcDeg: Float,
    )

    private fun fitRound(xs: FloatArray, ys: FloatArray, hs: FloatArray, height: Float, voxelMm: Float): Round? {
        // The lid of a can is a disc, not a ring; leave the top few millimetres out of the bands.
        val top = height - max(2f * voxelMm, 8f)
        if (top <= 2 * voxelMm) return null
        val bands = (top / BAND_MM).toInt().coerceIn(2, 8)
        val good = ArrayList<Pair<Float, Circle>>()   // band mid-height to circle
        for (b in 0 until bands) {
            val h0 = top * b / bands
            val h1 = top * (b + 1) / bands
            val idx = (hs.indices).filter { hs[it] >= h0 && hs[it] < h1 }
            if (idx.size < 12) continue
            val circle = fitCircle(
                FloatArray(idx.size) { xs[idx[it]] }, FloatArray(idx.size) { ys[idx[it]] },
                FloatArray(idx.size) { hs[idx[it]] }, h0, h1,
            ) ?: return null
            // A band whose surface is closer to lying flat than standing up (the shoulder of
            // a ball) says little about roundness either way, and neither does a short arc.
            val steep = abs(circle.rHigh - circle.rLow) > STEEP_SLOPE * (h1 - h0)
            val maxRms = max(2.5f + voxelMm * 0.3f, 0.07f * circle.r)
            if (circle.r > 450f || (!steep && circle.rms > maxRms)) return null   // one square band: not round
            if (steep || circle.arcDeg < MIN_ROUND_ARC_DEG || circle.r < 8f) continue
            good += (h0 + h1) / 2 to circle
        }
        if (good.size < 2) return null
        val fitted = good.map { it.second }
        // Centres must line up — a stack of offset circles is not one object.
        val meanX = fitted.sumOf { it.cx.toDouble() * it.count } / fitted.sumOf { it.count }
        val meanY = fitted.sumOf { it.cy.toDouble() * it.count } / fitted.sumOf { it.count }
        val rMid = fitted.maxOf { it.r }
        if (fitted.any { hypot(it.cx - meanX, it.cy - meanY) > max(6.0, 0.2 * rMid) }) return null

        // Radius profile r² = a + b·h + c·h². A sphere is exactly c = −1; a cylinder or cone
        // has c ≥ 0. Anything clearly bulging is treated as a ball.
        val sphere = if (good.size >= 3) quadratic(good.map { it.first.toDouble() }, good.map { (it.second.r * it.second.r).toDouble() }) else null
        if (sphere != null && sphere[2] < -SPHERE_CURVATURE) {
            val (qa, qb, qc) = Triple(sphere[0], sphere[1], sphere[2])
            val peak = sqrt((qa - qb * qb / (4 * qc)).coerceAtLeast(0.0)).toFloat()
            val r = max(peak, rMid)
            return Round(ShapeFamily.SPHERE, meanX.toFloat(), meanY.toFloat(), r, fitted.last().r, fitted.first().r, fitted.maxOf { it.arcDeg })
        }
        // The widest point can sit on a band edge (a cup's rim), so edges count — but never
        // more than a little past the widest band middle, because extrapolation is not evidence.
        val rMax = fitted.maxOf { max(it.r, min(max(it.rLow, it.rHigh), it.r * 1.15f)) }
        val rBottom = fitted.first().rLow.coerceAtLeast(0f)
        val rTop = fitted.last().rHigh.coerceAtLeast(0f)
        val family = if (abs(fitted.last().r - fitted.first().r) / rMid >= 0.10f) ShapeFamily.TAPERED else ShapeFamily.CYLINDER
        return Round(family, meanX.toFloat(), meanY.toFloat(), rMax, rTop, rBottom, fitted.maxOf { it.arcDeg })
    }

    /** Least-squares y = a + b·x + c·x². */
    private fun quadratic(x: List<Double>, y: List<Double>): DoubleArray? {
        var s0 = 0.0; var s1 = 0.0; var s2 = 0.0; var s3 = 0.0; var s4 = 0.0; var t0 = 0.0; var t1 = 0.0; var t2 = 0.0
        for (i in x.indices) {
            val v = x[i]; val v2 = v * v
            s0 += 1; s1 += v; s2 += v2; s3 += v2 * v; s4 += v2 * v2
            t0 += y[i]; t1 += y[i] * v; t2 += y[i] * v2
        }
        return solve3(s0, s1, s2, s2, s3, s4, t0, t1, t2)
    }

    /** Algebraic (Kåsa) fit for a start, then geometric refinement so short arcs are not biased small. */
    private fun fitCircle(x: FloatArray, y: FloatArray, h: FloatArray, h0: Float, h1: Float): Circle? {
        // Fit, drop what sits far off the circle, fit again. Depth outliers are a few percent
        // of points and tens of millimetres out; left in, one of them fails a real can.
        val first = fitCircleOnce(x, y, h, h0, h1) ?: return null
        val keep = x.indices.filter { i ->
            val d = hypot(x[i] - first.cx, y[i] - first.cy)
            val expected = first.r + (first.rHigh - first.rLow) * ((h[i] - (h0 + h1) / 2) / max(h1 - h0, 1f))
            abs(d - expected) <= max(3f * first.rms, 6f)
        }
        if (keep.size < x.size * MIN_INLIER_SHARE) return first
        if (keep.size == x.size) return first
        return fitCircleOnce(
            FloatArray(keep.size) { x[keep[it]] }, FloatArray(keep.size) { y[keep[it]] },
            FloatArray(keep.size) { h[keep[it]] }, h0, h1,
        )
    }

    private fun fitCircleOnce(x: FloatArray, y: FloatArray, h: FloatArray, h0: Float, h1: Float): Circle? {
        val n = x.size
        val mx = x.average(); val my = y.average()
        var suu = 0.0; var svv = 0.0; var suv = 0.0; var suuu = 0.0; var svvv = 0.0; var suvv = 0.0; var svuu = 0.0
        for (i in 0 until n) {
            val u = x[i] - mx; val v = y[i] - my
            suu += u * u; svv += v * v; suv += u * v
            suuu += u * u * u; svvv += v * v * v; suvv += u * v * v; svuu += v * u * u
        }
        val det = suu * svv - suv * suv
        if (abs(det) < 1e-9) return null
        val bu = 0.5 * (suuu + suvv); val bv = 0.5 * (svvv + svuu)
        val cu = (bu * svv - bv * suv) / det
        val cv = (suu * bv - suv * bu) / det
        var r = sqrt(cu * cu + cv * cv + (suu + svv) / n)
        var cx = mx + cu; var cy = my + cv
        // Gauss–Newton on the true geometric distance.
        repeat(8) {
            var j11 = 0.0; var j12 = 0.0; var j13 = 0.0; var j22 = 0.0; var j23 = 0.0; var j33 = 0.0
            var g1 = 0.0; var g2 = 0.0; var g3 = 0.0
            for (i in 0 until n) {
                val dx = x[i] - cx; val dy = y[i] - cy
                val di = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-6)
                val res = di - r
                val a = -dx / di; val b = -dy / di; val cc = -1.0
                j11 += a * a; j12 += a * b; j13 += a * cc; j22 += b * b; j23 += b * cc; j33 += cc * cc
                g1 += a * res; g2 += b * res; g3 += cc * res
            }
            val step = solve3(j11, j12, j13, j22, j23, j33, -g1, -g2, -g3) ?: return@repeat
            cx += step[0]; cy += step[1]; r += step[2]
        }
        if (!r.isFinite() || r <= 0 || !cx.isFinite() || !cy.isFinite()) return null
        // Radius as a straight line in height across the band, residuals from that line.
        val dist = DoubleArray(n)
        val angles = DoubleArray(n)
        var sh = 0.0; var sd = 0.0
        for (i in 0 until n) {
            val dx = x[i] - cx; val dy = y[i] - cy
            dist[i] = sqrt(dx * dx + dy * dy)
            angles[i] = atan2(dy, dx)
            sh += h[i]; sd += dist[i]
        }
        val mh = sh / n; val md = sd / n
        var shh = 0.0; var shd = 0.0
        for (i in 0 until n) { shh += (h[i] - mh) * (h[i] - mh); shd += (h[i] - mh) * (dist[i] - md) }
        val slope = if (shh > 1e-6) shd / shh else 0.0
        var ss = 0.0
        for (i in 0 until n) { val e = dist[i] - (md + slope * (h[i] - mh)); ss += e * e }
        val mid = (h0 + h1) / 2.0
        angles.sort()
        var maxGap = (angles[0] + 2 * PI) - angles[n - 1]
        for (i in 1 until n) maxGap = max(maxGap, angles[i] - angles[i - 1])
        val arc = (2 * PI - maxGap) * 180 / PI
        return Circle(
            cx.toFloat(), cy.toFloat(),
            r = (md + slope * (mid - mh)).toFloat(),
            rLow = (md + slope * (h0 - mh)).toFloat(),
            rHigh = (md + slope * (h1 - mh)).toFloat(),
            rms = sqrt(ss / n).toFloat(), arcDeg = arc.toFloat(), count = n,
        )
    }

    private fun solve3(a11: Double, a12: Double, a13: Double, a22: Double, a23: Double, a33: Double,
                       b1: Double, b2: Double, b3: Double): DoubleArray? {
        val det = a11 * (a22 * a33 - a23 * a23) - a12 * (a12 * a33 - a23 * a13) + a13 * (a12 * a23 - a22 * a13)
        if (abs(det) < 1e-12) return null
        val x1 = (b1 * (a22 * a33 - a23 * a23) - a12 * (b2 * a33 - a23 * b3) + a13 * (b2 * a23 - a22 * b3)) / det
        val x2 = (a11 * (b2 * a33 - a23 * b3) - b1 * (a12 * a33 - a23 * a13) + a13 * (a12 * b3 - b2 * a13)) / det
        val x3 = (a11 * (a22 * b3 - b2 * a23) - a12 * (a12 * b3 - b2 * a13) + b1 * (a12 * a23 - a22 * a13)) / det
        return doubleArrayOf(x1, x2, x3)
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The extent of a set of coordinates, trimming [TRIM_SHARE] of the *sighting weight* off
     * each end. A flat face puts a large share of all weight right at the edge, so trimming
     * barely moves it; a curved side puts its weight near the tangent (arcsine distribution),
     * so the same trim moves it by a fraction of a millimetre; stray voxels are light and go.
     */
    private fun robustRange(values: FloatArray, weights: FloatArray): Pair<Float, Float> {
        if (values.size < 100) return values.min() to values.max()
        // Sort (value, index) packed into one primitive long: no boxing on the hot path.
        val keys = LongArray(values.size) { i ->
            val bits = java.lang.Float.floatToIntBits(values[i])
            val sortable = if (bits < 0) bits xor 0x7fffffff else bits
            (sortable.toLong() shl 32) or i.toLong()
        }
        keys.sort()
        val total = weights.sum()
        val trim = total * TRIM_SHARE
        var acc = 0f; var lo = values[(keys.first() and 0xffffffffL).toInt()]
        for (k in keys) { val i = (k and 0xffffffffL).toInt(); acc += weights[i]; if (acc > trim) { lo = values[i]; break } }
        acc = 0f; var hi = values[(keys.last() and 0xffffffffL).toInt()]
        for (j in keys.indices.reversed()) { val i = (keys[j] and 0xffffffffL).toInt(); acc += weights[i]; if (acc > trim) { hi = values[i]; break } }
        return lo to hi
    }

    private fun weightedMedian(idx: List<Int>, v: FloatArray, w: FloatArray): Float {
        val sorted = idx.sortedBy { v[it] }
        val half = sorted.sumOf { w[it].toDouble() } / 2
        var acc = 0.0
        for (i in sorted) { acc += w[i]; if (acc >= half) return v[i] }
        return v[sorted.last()]
    }

    /** Andrew's monotone chain. */
    private fun convexHull(xs: FloatArray, ys: FloatArray): List<Pair<Float, Float>> {
        val pts = xs.indices.map { xs[it] to ys[it] }.distinct().sortedWith(compareBy({ it.first }, { it.second }))
        if (pts.size < 3) return pts
        fun cross(o: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>) =
            (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)
        val lower = ArrayList<Pair<Float, Float>>()
        for (p in pts) { while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0) lower.removeAt(lower.size - 1); lower += p }
        val upper = ArrayList<Pair<Float, Float>>()
        for (p in pts.asReversed()) { while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0) upper.removeAt(upper.size - 1); upper += p }
        lower.removeAt(lower.size - 1); upper.removeAt(upper.size - 1)
        return lower + upper
    }

    private fun polygonArea(poly: List<Pair<Float, Float>>): Float {
        if (poly.size < 3) return 0f
        var a = 0.0
        for (i in poly.indices) {
            val p = poly[i]; val q = poly[(i + 1) % poly.size]
            a += p.first.toDouble() * q.second - q.first.toDouble() * p.second
        }
        return abs(a / 2).toFloat()
    }

    /** Footprint hull must cover this share of the fitted rectangle for the object to be a box. */
    private const val BOX_HULL_FILL = 0.88f

    /** Share of points that must lie on the box's own faces for it to be called a box. */
    private const val BOX_FACE_SHARE = 0.72f

    private const val TRIM_SHARE = 0.004f

    /** A band where fewer than this share of points sit on the circle is not a circle. */
    private const val MIN_INLIER_SHARE = 0.85f

    /** A band whose radius changes faster than this per mm of height is shoulder, not wall. */
    private const val STEEP_SLOPE = 1.2f

    /** Curvature of the radius profile (see [fitRound]) beyond which a round thing is a ball. */
    private const val SPHERE_CURVATURE = 0.4

    /** Height of one band tested for roundness. */
    private const val BAND_MM = 25f

    /** A slice arc narrower than this is not evidence of roundness. */
    private const val MIN_ROUND_ARC_DEG = 100f

    /** An arc this wide determines the circle's full diameter. */
    private const val ROUND_OBSERVED_ARC_DEG = 140f

}
