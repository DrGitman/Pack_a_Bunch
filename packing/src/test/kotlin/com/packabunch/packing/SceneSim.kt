package com.packabunch.packing

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A phone's depth camera pointed at a made-up scene, for testing the scans end to end.
 *
 * Scenes are signed distance functions in world metres, Y up as in ARCore, each returning the
 * distance to the nearest surface and an id for what that surface is. Depth images come out
 * the way ARCore's do: z-depth in millimetres, a percent or so of smooth correlated error, a
 * few millimetres of per-pixel noise, holes where it has no confidence, nothing past its
 * range, and silhouettes smeared between the near surface and the far one.
 */
object SceneSim {

    /** The depth image's size and focal length, in its own pixels. */
    class Lens(val width: Int, val height: Int, val focal: Float) {
        val cx get() = width / 2f
        val cy get() = height / 2f
    }

    class Camera(val pos: FloatArray, yawDeg: Float, pitchDeg: Float, val lens: Lens) {
        val fwd: FloatArray; val right: FloatArray; val up: FloatArray
        init {
            val y = yawDeg * PI.toFloat() / 180f; val p = pitchDeg * PI.toFloat() / 180f
            fwd = floatArrayOf(sin(y) * cos(p), sin(p), -cos(y) * cos(p))
            right = floatArrayOf(cos(y), 0f, sin(y))
            up = cross(right, fwd)
        }

        /** The world point at depth-image pixel (u, v) and z-depth [depthM]. */
        fun world(u: Float, v: Float, depthM: Float) = FloatArray(3) { k ->
            pos[k] + fwd[k] * depthM + right[k] * (u - lens.cx) / lens.focal * depthM - up[k] * (v - lens.cy) / lens.focal * depthM
        }

        companion object {
            fun lookingAt(pos: FloatArray, target: FloatArray, lens: Lens): Camera {
                val d = FloatArray(3) { target[it] - pos[it] }
                val l = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2])
                return Camera(pos, (atan2(d[0], -d[2]) * 180 / PI).toFloat(), (asin(d[1] / l) * 180 / PI).toFloat(), lens)
            }
        }
    }

    /** Millimetres per pixel (0 = no depth) and, for the test only, the id each pixel hit. */
    class DepthImage(val width: Int, val height: Int, val mm: IntArray, val hit: IntArray)

    const val NOTHING = Int.MIN_VALUE

    fun depthImage(
        scene: (Float, Float, Float) -> Pair<Float, Int>,
        cam: Camera,
        rnd: Random,
        maxRangeM: Float = 4f,
        /** Share of pixels with no depth: more on dark, glossy or bare surfaces. */
        holes: Float = 0.08f,
    ): DepthImage {
        val w = cam.lens.width; val h = cam.lens.height
        val mm = IntArray(w * h); val hit = IntArray(w * h) { NOTHING }
        val trueMm = FloatArray(w * h)
        val dir = FloatArray(3)
        for (v in 0 until h) for (u in 0 until w) {
            for (k in 0..2) dir[k] = cam.fwd[k] + cam.right[k] * (u + 0.5f - cam.lens.cx) / cam.lens.focal - cam.up[k] * (v + 0.5f - cam.lens.cy) / cam.lens.focal
            val l = sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2])
            for (k in 0..2) dir[k] /= l
            var t = 0.02f
            for (step in 0 until 160) {
                val (d, id) = scene(cam.pos[0] + dir[0] * t, cam.pos[1] + dir[1] * t, cam.pos[2] + dir[2] * t)
                if (d < 0.0005f) { hit[v * w + u] = id; break }
                t += max(d, 0.0008f)
                if (t > maxRangeM + 1f) break
            }
            val z = t * (dir[0] * cam.fwd[0] + dir[1] * cam.fwd[1] + dir[2] * cam.fwd[2])
            if (hit[v * w + u] != NOTHING && z <= maxRangeM) trueMm[v * w + u] = z * 1000f
        }
        val gw = 9; val gh = 6
        val field = FloatArray(gw * gh) { gaussian(rnd) }
        for (v in 0 until h) for (u in 0 until w) {
            val i = v * w + u
            if (trueMm[i] == 0f) continue
            val gx = u.toFloat() / w * (gw - 1); val gy = v.toFloat() / h * (gh - 1)
            val x0 = gx.toInt().coerceAtMost(gw - 2); val y0 = gy.toInt().coerceAtMost(gh - 2)
            val fx = gx - x0; val fy = gy - y0
            val smooth = field[y0 * gw + x0] * (1 - fx) * (1 - fy) + field[y0 * gw + x0 + 1] * fx * (1 - fy) +
                field[(y0 + 1) * gw + x0] * (1 - fx) * fy + field[(y0 + 1) * gw + x0 + 1] * fx * fy
            mm[i] = (trueMm[i] * (1 + 0.008f * smooth) + 2.5f * gaussian(rnd)).toInt()
            if (rnd.nextFloat() < holes) mm[i] = 0
        }
        val out = mm.copyOf()
        for (v in 1 until h - 1) for (u in 1 until w - 1) {
            val i = v * w + u
            if (mm[i] == 0) continue
            var lo = mm[i]; var hi = mm[i]
            for (dv in -1..1) for (du in -1..1) { val n = mm[i + dv * w + du]; if (n > 0) { lo = min(lo, n); hi = max(hi, n) } }
            if (hi - lo > 50 && rnd.nextFloat() < 0.5f) out[i] = lo + (rnd.nextFloat() * (hi - lo)).toInt()
        }
        return DepthImage(w, h, out, hit)
    }

    // -- shapes, in world metres -----------------------------------------------------------------

    /** Signed distance to an axis-aligned box given by its min and max corners. */
    fun box(px: Float, py: Float, pz: Float, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float): Float {
        val cx = (x0 + x1) / 2; val cy = (y0 + y1) / 2; val cz = (z0 + z1) / 2
        val dx = abs(px - cx) - (x1 - x0) / 2; val dy = abs(py - cy) - (y1 - y0) / 2; val dz = abs(pz - cz) - (z1 - z0) / 2
        return sqrt(sq(max(dx, 0f)) + sq(max(dy, 0f)) + sq(max(dz, 0f))) + min(max(dx, max(dy, dz)), 0f)
    }

    /**
     * A thin board hinged along a horizontal edge at ([hx], [hy], [hz]) running along X ([alongX])
     * or Z, [length] long and [reach] wide, swung [angleDeg] from straight up, outward along [outward].
     */
    fun flap(
        px: Float, py: Float, pz: Float, hx: Float, hy: Float, hz: Float,
        alongX: Boolean, length: Float, reach: Float, angleDeg: Float, outward: Float, thick: Float = 0.004f,
    ): Float {
        val a = angleDeg * PI.toFloat() / 180f
        // Into the flap's frame: s along the hinge, t up the board, n through it.
        val dx = px - hx; val dy = py - hy; val dz = pz - hz
        val across = if (alongX) dz * outward else dx * outward
        val s = if (alongX) dx else dz
        val t = dy * cos(a) + across * sin(a)
        val n = -dy * sin(a) + across * cos(a)
        return box(s, t, n, -length / 2, 0f, -thick / 2, length / 2, reach, thick / 2)
    }

    fun sq(x: Float) = x * x

    fun cross(a: FloatArray, b: FloatArray) = floatArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])

    fun gaussian(rnd: Random): Float {
        var u = 0.0
        while (u == 0.0) u = rnd.nextDouble()
        return (sqrt(-2 * kotlin.math.ln(u)) * cos(2 * PI * rnd.nextDouble())).toFloat()
    }

}
