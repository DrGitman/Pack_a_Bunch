package com.packabunch.packing

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One straight piece of an object's outline, in the support surface's frame.
 *
 * [axis] is the length this edge runs along — the edge that turns amber when that length has
 * not been seen yet (`ItemScan · Outline states`, "Another angle"). Curves on a round object
 * run along both footprint lengths at once and carry [Axis.WIDTH]; the renderer treats a
 * round object's width and depth as one.
 */
data class OutlineSegment(val a: PlanePoint, val b: PlanePoint, val axis: Axis)

/**
 * The lines that hug an object, the way RoomPlan draws them: a box's twelve edges; a can's two
 * rims and the two sides you can see; a ball's silhouette and equator; an irregular thing's
 * footprint hull at the bottom and the top, with the two uprights at its visible edges.
 *
 * View-dependent pieces (silhouettes) take the [camera] so they sit where the eye expects an
 * edge, and are regenerated as the camera moves.
 */
object OutlineGeometry {

    fun of(fit: FittedObject, camera: PlanePoint, curveSegments: Int = 48): List<OutlineSegment> = when (fit.shape) {
        ShapeFamily.BOX -> box(fit)
        ShapeFamily.CYLINDER, ShapeFamily.TAPERED -> round(fit, camera, curveSegments)
        ShapeFamily.SPHERE -> sphere(fit, camera, curveSegments)
        ShapeFamily.IRREGULAR -> if (fit.footprintHull.size >= 3) hull(fit, camera) else box(fit)
    }

    /** The footprint at surface level, for the faint fill under a measured object. */
    fun footprint(fit: FittedObject, curveSegments: Int = 48): List<PlanePoint> = when (fit.shape) {
        ShapeFamily.CYLINDER, ShapeFamily.TAPERED, ShapeFamily.SPHERE -> {
            val r = if (fit.shape == ShapeFamily.SPHERE) fit.widthMm / 2 * 0.35f else (fit.bottomRadiusMm ?: fit.widthMm / 2)
            circle(fit.centreXMm, fit.centreYMm, 0f, r, curveSegments)
        }
        ShapeFamily.IRREGULAR -> if (fit.footprintHull.size >= 3) simplify(fit.footprintHull).map { PlanePoint(it.first, it.second, 0f) } else corners(fit, 0f)
        ShapeFamily.BOX -> corners(fit, 0f)
    }

    // -------------------------------------------------------------------------------------

    private fun corners(fit: FittedObject, h: Float): List<PlanePoint> {
        val r = fit.yawDegrees * PI.toFloat() / 180f
        val ux = cos(r); val uy = sin(r)
        val vx = -uy; val vy = ux
        val hw = fit.widthMm / 2; val hd = fit.depthMm / 2
        return listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f).map { (su, sv) ->
            PlanePoint(fit.centreXMm + su * hw * ux + sv * hd * vx, fit.centreYMm + su * hw * uy + sv * hd * vy, h)
        }
    }

    private fun box(fit: FittedObject): List<OutlineSegment> {
        val b = corners(fit, 0f); val t = corners(fit, fit.heightMm)
        val out = ArrayList<OutlineSegment>(12)
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            // Corners run −u−v, +u−v, +u+v, −u+v: edges 0→1 and 2→3 run along the width.
            val axis = if (i % 2 == 0) Axis.WIDTH else Axis.DEPTH
            out += OutlineSegment(b[i], b[j], axis)
            out += OutlineSegment(t[i], t[j], axis)
            out += OutlineSegment(b[i], t[i], Axis.HEIGHT)
        }
        return out
    }

    private fun round(fit: FittedObject, camera: PlanePoint, n: Int): List<OutlineSegment> {
        val rb = fit.bottomRadiusMm ?: (fit.widthMm / 2)
        val rt = fit.topRadiusMm ?: (fit.widthMm / 2)
        val out = ArrayList<OutlineSegment>(2 * n + 2)
        ring(out, circle(fit.centreXMm, fit.centreYMm, 0f, rb, n))
        ring(out, circle(fit.centreXMm, fit.centreYMm, fit.heightMm, rt, n))
        // The two sides as seen from here: perpendicular to the line of sight in plan.
        val dx = camera.xMm - fit.centreXMm; val dy = camera.yMm - fit.centreYMm
        val len = hypot(dx, dy).coerceAtLeast(1f)
        val px = -dy / len; val py = dx / len
        for (s in listOf(-1f, 1f)) {
            out += OutlineSegment(
                PlanePoint(fit.centreXMm + s * px * rb, fit.centreYMm + s * py * rb, 0f),
                PlanePoint(fit.centreXMm + s * px * rt, fit.centreYMm + s * py * rt, fit.heightMm),
                Axis.HEIGHT,
            )
        }
        return out
    }

    private fun sphere(fit: FittedObject, camera: PlanePoint, n: Int): List<OutlineSegment> {
        val r = fit.widthMm / 2
        val c = floatArrayOf(fit.centreXMm, fit.centreYMm, fit.heightMm / 2)
        val out = ArrayList<OutlineSegment>(2 * n)
        ring(out, circle(c[0], c[1], c[2], r, n))
        // Silhouette: the circle facing the camera.
        var d = floatArrayOf(camera.xMm - c[0], camera.yMm - c[1], camera.hMm - c[2])
        val dl = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]).coerceAtLeast(1f)
        d = floatArrayOf(d[0] / dl, d[1] / dl, d[2] / dl)
        val helper = if (kotlin.math.abs(d[2]) < 0.9f) floatArrayOf(0f, 0f, 1f) else floatArrayOf(1f, 0f, 0f)
        val e1 = normalise(cross(d, helper)); val e2 = cross(d, e1)
        val pts = (0 until n).map { i ->
            val a = 2 * PI.toFloat() * i / n
            PlanePoint(
                c[0] + r * (cos(a) * e1[0] + sin(a) * e2[0]),
                c[1] + r * (cos(a) * e1[1] + sin(a) * e2[1]),
                c[2] + r * (cos(a) * e1[2] + sin(a) * e2[2]),
            )
        }
        ring(out, pts, Axis.HEIGHT)
        return out
    }

    private fun hull(fit: FittedObject, camera: PlanePoint): List<OutlineSegment> {
        val poly = simplify(fit.footprintHull)
        val out = ArrayList<OutlineSegment>(poly.size * 2 + 2)
        val bottom = poly.map { PlanePoint(it.first, it.second, 0f) }
        val top = poly.map { PlanePoint(it.first, it.second, fit.heightMm) }
        ring(out, bottom); ring(out, top)
        // Uprights only where the outline turns from facing the camera to facing away.
        val n = poly.size
        fun facing(i: Int): Boolean {
            val a = poly[i]; val b = poly[(i + 1) % n]
            val nx = b.second - a.second; val ny = -(b.first - a.first)   // outward for anticlockwise
            val mx = (a.first + b.first) / 2; val my = (a.second + b.second) / 2
            return nx * (camera.xMm - mx) + ny * (camera.yMm - my) > 0
        }
        for (i in 0 until n) {
            val prev = facing((i - 1 + n) % n)
            if (prev != facing(i)) out += OutlineSegment(bottom[i], top[i], Axis.HEIGHT)
        }
        return out
    }

    private fun ring(out: MutableList<OutlineSegment>, pts: List<PlanePoint>, axis: Axis = Axis.WIDTH) {
        for (i in pts.indices) out += OutlineSegment(pts[i], pts[(i + 1) % pts.size], axis)
    }

    private fun circle(cx: Float, cy: Float, h: Float, r: Float, n: Int) = (0 until n).map { i ->
        val a = 2 * PI.toFloat() * i / n
        PlanePoint(cx + r * cos(a), cy + r * sin(a), h)
    }

    /** Drops hull vertices that barely change the outline, down to at most [max]. */
    internal fun simplify(hull: List<Pair<Float, Float>>, max: Int = 16): List<Pair<Float, Float>> {
        val pts = hull.toMutableList()
        while (pts.size > 3) {
            var best = -1; var bestArea = Float.MAX_VALUE
            for (i in pts.indices) {
                val a = pts[(i - 1 + pts.size) % pts.size]; val b = pts[i]; val c = pts[(i + 1) % pts.size]
                val area = kotlin.math.abs((b.first - a.first) * (c.second - a.second) - (b.second - a.second) * (c.first - a.first)) / 2
                if (area < bestArea) { bestArea = area; best = i }
            }
            if (pts.size <= max && bestArea > 40f) break   // 40 mm²: a sliver, not a corner
            pts.removeAt(best)
        }
        return pts
    }

    private fun cross(a: FloatArray, b: FloatArray) = floatArrayOf(
        a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0],
    )

    private fun normalise(v: FloatArray): FloatArray {
        val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).coerceAtLeast(1e-6f)
        return floatArrayOf(v[0] / l, v[1] / l, v[2] / l)
    }
}
