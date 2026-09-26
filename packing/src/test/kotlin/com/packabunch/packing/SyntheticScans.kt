package com.packabunch.packing

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Fake depth frames of known objects, so the measuring code can be held to real numbers.
 *
 * What makes these honest rather than flattering:
 * - only surfaces facing the camera produce points, weighted by how squarely they face it
 *   (a face seen at a grazing angle returns few samples, as on a phone);
 * - noise is applied **along the viewing ray**, which is how depth-from-motion errs;
 * - the bottom face never appears, because it is on the table;
 * - a share of samples are outliers thrown well off the surface.
 */
object SyntheticScans {

    /** A surface patch that can be sampled: returns a point and its outward normal. */
    fun interface Surface {
        fun sample(rnd: Random): Pair<FloatArray, FloatArray>
    }

    class Shape(val parts: List<Pair<Surface, Float>>, val reject: (FloatArray) -> Boolean = { false })

    fun box(w: Float, d: Float, h: Float, yawDeg: Float = 0f, cx: Float = 0f, cy: Float = 0f): Shape {
        val r = yawDeg * PI.toFloat() / 180f
        val c = cos(r); val s = sin(r)
        fun world(u: Float, v: Float, z: Float) = floatArrayOf(cx + u * c - v * s, cy + u * s + v * c, z)
        fun dir(u: Float, v: Float, z: Float) = floatArrayOf(u * c - v * s, u * s + v * c, z)
        val faces = listOf(
            Surface { rnd -> world((rnd.nextFloat() - .5f) * w, (rnd.nextFloat() - .5f) * d, h) to dir(0f, 0f, 1f) } to w * d,
            Surface { rnd -> world(w / 2, (rnd.nextFloat() - .5f) * d, rnd.nextFloat() * h) to dir(1f, 0f, 0f) } to d * h,
            Surface { rnd -> world(-w / 2, (rnd.nextFloat() - .5f) * d, rnd.nextFloat() * h) to dir(-1f, 0f, 0f) } to d * h,
            Surface { rnd -> world((rnd.nextFloat() - .5f) * w, d / 2, rnd.nextFloat() * h) to dir(0f, 1f, 0f) } to w * h,
            Surface { rnd -> world((rnd.nextFloat() - .5f) * w, -d / 2, rnd.nextFloat() * h) to dir(0f, -1f, 0f) } to w * h,
        )
        return Shape(faces)
    }

    /** A frustum: a cylinder when the radii match, a cup when the top is wider. */
    fun frustum(rBottom: Float, rTop: Float, h: Float, cx: Float = 0f, cy: Float = 0f): Shape {
        val slope = (rBottom - rTop) / h
        val side = Surface { rnd ->
            val a = rnd.nextFloat() * 2 * PI.toFloat()
            val z = rnd.nextFloat() * h
            val r = rBottom + (rTop - rBottom) * z / h
            val n = norm(floatArrayOf(cos(a), sin(a), slope))
            floatArrayOf(cx + r * cos(a), cy + r * sin(a), z) to n
        }
        val top = Surface { rnd ->
            val a = rnd.nextFloat() * 2 * PI.toFloat()
            val r = rTop * sqrt(rnd.nextFloat())
            floatArrayOf(cx + r * cos(a), cy + r * sin(a), h) to floatArrayOf(0f, 0f, 1f)
        }
        val meanR = (rBottom + rTop) / 2
        return Shape(listOf(side to 2 * PI.toFloat() * meanR * h, top to PI.toFloat() * rTop * rTop))
    }

    fun sphere(r: Float, cx: Float = 0f, cy: Float = 0f): Shape = Shape(listOf(Surface { rnd ->
        val z = rnd.nextFloat() * 2 - 1
        val a = rnd.nextFloat() * 2 * PI.toFloat()
        val q = sqrt(1 - z * z)
        val n = floatArrayOf(q * cos(a), q * sin(a), z)
        floatArrayOf(cx + r * n[0], cy + r * n[1], r + r * n[2]) to n
    } to 1f))

    /** Two boxes forming an L, with the faces where they touch removed. */
    fun lShape(): Shape {
        val a = box(300f, 100f, 120f, cx = 0f, cy = -100f)     // x −150..150, y −150..−50
        val b = box(100f, 200f, 120f, cx = -100f, cy = 50f)    // x −150..−50, y −50..150
        fun inside(p: FloatArray, x0: Float, x1: Float, y0: Float, y1: Float) =
            p[0] > x0 - 0.5f && p[0] < x1 + 0.5f && p[1] > y0 - 0.5f && p[1] < y1 + 0.5f && p[2] < 119.5f
        return Shape(a.parts + b.parts) { p ->
            // Drop wall samples that lie on the shared boundary strip.
            (abs(p[1] + 50f) < 0.5f && p[0] < -49.5f && p[2] < 119.5f) ||
                (inside(p, -150f, -50f, -150f, -50f) && abs(p[1] + 50f) < 0.5f)
        }
    }

    /** Cameras on an arc around the origin: [fromDeg]..[toDeg] azimuth, at [distance] and [height]. */
    fun arc(fromDeg: Float, toDeg: Float, frames: Int, distance: Float = 600f, height: Float = 400f): List<PlanePoint> =
        (0 until frames).map {
            val t = if (frames == 1) 0f else it / (frames - 1f)
            val a = (fromDeg + (toDeg - fromDeg) * t) * PI.toFloat() / 180f
            PlanePoint(distance * cos(a), distance * sin(a), height)
        }

    /**
     * One depth frame of [shape] from [camera]: [count] samples, noise [sigmaMm] along the ray,
     * [outlierShare] of samples thrown 20–80 mm off the surface.
     */
    fun frame(
        shape: Shape,
        camera: PlanePoint,
        rnd: Random,
        count: Int = 400,
        sigmaMm: Float = 3f,
        outlierShare: Float = 0.01f,
    ): List<PlanePoint> {
        val total = shape.parts.sumOf { it.second.toDouble() }
        val out = ArrayList<PlanePoint>(count)
        var attempts = 0
        while (out.size < count && attempts < count * 40) {
            attempts++
            var pick = rnd.nextDouble() * total
            var surface = shape.parts.last().first
            for ((s, area) in shape.parts) { pick -= area; if (pick <= 0) { surface = s; break } }
            val (p, n) = surface.sample(rnd)
            if (shape.reject(p)) continue
            val toCam = floatArrayOf(camera.xMm - p[0], camera.yMm - p[1], camera.hMm - p[2])
            val dist = sqrt(toCam[0] * toCam[0] + toCam[1] * toCam[1] + toCam[2] * toCam[2])
            val facing = (toCam[0] * n[0] + toCam[1] * n[1] + toCam[2] * n[2]) / dist
            if (facing <= 0f || rnd.nextFloat() > facing) continue          // hidden, or grazing
            val ray = floatArrayOf(-toCam[0] / dist, -toCam[1] / dist, -toCam[2] / dist)
            val e = if (rnd.nextFloat() < outlierShare) (20f + rnd.nextFloat() * 60f) * (if (rnd.nextBoolean()) 1 else -1)
            else gaussian(rnd) * sigmaMm
            out += PlanePoint(p[0] + ray[0] * e, p[1] + ray[1] * e, p[2] + ray[2] * e)
        }
        return out
    }

    /** Runs a whole sweep through a fresh [ItemMeasurement]. */
    fun measure(
        shape: Shape,
        cameras: List<PlanePoint>,
        seed: Int = 7,
        sigmaMm: Float = 3f,
        count: Int = 400,
    ): ItemMeasurement {
        val rnd = Random(seed)
        val m = ItemMeasurement()
        for (cam in cameras) m.addFrame(frame(shape, cam, rnd, count, sigmaMm), cam)
        return m
    }

    private fun gaussian(rnd: Random): Float {
        val u = rnd.nextDouble().coerceAtLeast(1e-9)
        val v = rnd.nextDouble()
        return (sqrt(-2 * kotlin.math.ln(u)) * cos(2 * PI * v)).toFloat()
    }

    private fun norm(v: FloatArray): FloatArray {
        val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        return floatArrayOf(v[0] / l, v[1] / l, v[2] / l)
    }
}
