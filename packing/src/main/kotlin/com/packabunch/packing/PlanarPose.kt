package com.packabunch.packing

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Where the camera is, from a flat thing of known size lying in view — a bank card or a sheet of
 * A4 on the table. The same maths a printed marker uses: the card's four corners in the picture
 * fix a homography, and with the camera's focal length that splits into the camera's rotation and
 * position relative to the card.
 *
 * This is what places the camera. Every frame is placed in one fixed 3D frame —
 * the card's — so the phone can move round the items and everything it sees lines up, and the
 * card's known size makes every length real millimetres.
 *
 * World frame: origin at the card's centre, X along its long edge, Z along its short edge, both on
 * the table, Y straight up out of it. Metres. Camera frame is the usual pinhole one: x right,
 * y down, z forward (into the scene). `camera = R · world + t`.
 */
object PlanarPose {

    class Intrinsics(val fx: Double, val fy: Double, val cx: Double, val cy: Double)

    class Pose(
        /** Camera-from-world rotation, row major. Columns are world X, Y, Z seen from the camera. */
        val r: DoubleArray,
        /** Camera-from-world translation, metres. */
        val t: DoubleArray,
    ) {
        /** The camera's position on the card's frame. */
        val cameraPosition: DoubleArray get() = DoubleArray(3) { i -> -(r[i] * t[0] + r[3 + i] * t[1] + r[6 + i] * t[2]) }

        fun toCamera(w: DoubleArray) = DoubleArray(3) { i -> r[3 * i] * w[0] + r[3 * i + 1] * w[1] + r[3 * i + 2] * w[2] + t[i] }

        fun toWorld(c: DoubleArray): DoubleArray {
            val d = doubleArrayOf(c[0] - t[0], c[1] - t[1], c[2] - t[2])
            return DoubleArray(3) { i -> r[i] * d[0] + r[3 + i] * d[1] + r[6 + i] * d[2] }
        }

        /** Pixel of a world point, or null if it is behind the camera. */
        fun project(k: Intrinsics, w: DoubleArray): DoubleArray? {
            val c = toCamera(w)
            if (c[2] <= 1e-6) return null
            return doubleArrayOf(k.fx * c[0] / c[2] + k.cx, k.fy * c[1] / c[2] + k.cy)
        }

        /** World point seen at pixel (u, v) at distance [zMetres] along the optical axis. */
        fun unproject(k: Intrinsics, u: Double, v: Double, zMetres: Double) =
            toWorld(doubleArrayOf((u - k.cx) / k.fx * zMetres, (v - k.cy) / k.fy * zMetres, zMetres))

        /** The direction (world) through pixel (u, v), and where it meets the table (y = 0). */
        fun rayToTable(k: Intrinsics, u: Double, v: Double): DoubleArray? {
            val o = cameraPosition
            val dirCam = doubleArrayOf((u - k.cx) / k.fx, (v - k.cy) / k.fy, 1.0)
            val d = DoubleArray(3) { i -> r[i] * dirCam[0] + r[3 + i] * dirCam[1] + r[6 + i] * dirCam[2] }
            if (d[1] >= -1e-9) return null
            val s = -o[1] / d[1]
            return doubleArrayOf(o[0] + d[0] * s, 0.0, o[2] + d[2] * s, s) // last: distance along the z = 1 ray
        }
    }

    /**
     * The homography taking table points (x, z) to pixels (u, v), from four or more pairs.
     * Row-major 3×3 with the last element 1, or null if the points are degenerate.
     */
    fun homography(table: DoubleArray, pixels: DoubleArray): DoubleArray? {
        val n = table.size / 2
        require(n >= 4 && pixels.size == 2 * n)
        // Least squares on the 2n × 8 system (exact for n = 4).
        val ata = Array(8) { DoubleArray(8) }; val atb = DoubleArray(8)
        for (i in 0 until n) {
            val x = table[2 * i]; val z = table[2 * i + 1]; val u = pixels[2 * i]; val v = pixels[2 * i + 1]
            val rows = arrayOf(
                doubleArrayOf(x, z, 1.0, 0.0, 0.0, 0.0, -u * x, -u * z) to u,
                doubleArrayOf(0.0, 0.0, 0.0, x, z, 1.0, -v * x, -v * z) to v,
            )
            for ((row, b) in rows) for (a in 0 until 8) {
                atb[a] += row[a] * b
                for (c in 0 until 8) ata[a][c] += row[a] * row[c]
            }
        }
        val h = solve(ata, atb) ?: return null
        return doubleArrayOf(h[0], h[1], h[2], h[3], h[4], h[5], h[6], h[7], 1.0)
    }

    /**
     * Camera pose from the homography, or null if it is not one a flat rectangle of this size could
     * make — which is how a card is told apart from any other rectangle: with the wrong size the
     * two in-plane axes come out unequal in length or not at right angles.
     */
    fun fromHomography(h: DoubleArray, k: Intrinsics): Fit? {
        // M = K⁻¹ H, whose columns are λ·(X axis, Z axis, translation).
        val m = DoubleArray(9)
        for (c in 0 until 3) {
            val a = h[c]; val b = h[3 + c]; val w = h[6 + c]
            m[c] = (a - k.cx * w) / k.fx
            m[3 + c] = (b - k.cy * w) / k.fy
            m[6 + c] = w
        }
        val m1 = doubleArrayOf(m[0], m[3], m[6]); val m2 = doubleArrayOf(m[1], m[4], m[7]); val m3 = doubleArrayOf(m[2], m[5], m[8])
        val n1 = norm(m1); val n2 = norm(m2)
        if (n1 < 1e-12 || n2 < 1e-12) return null
        val ratio = maxOf(n1, n2) / minOf(n1, n2)
        val skew = abs(dot(m1, m2)) / (n1 * n2)
        var lambda = 2.0 / (n1 + n2)
        if (m3[2] * lambda < 0) lambda = -lambda // the card is in front of the camera
        val x = scale(m1, lambda); val zRaw = scale(m2, lambda); val t = scale(m3, lambda)
        // Nearest rotation: X as measured, Z made square to it, Y = Z × X.
        val xn = scale(x, 1 / norm(x))
        val zp = sub(zRaw, scale(xn, dot(zRaw, xn))); val zn = scale(zp, 1 / norm(zp))
        val yn = cross(zn, xn)
        val r = doubleArrayOf(xn[0], yn[0], zn[0], xn[1], yn[1], zn[1], xn[2], yn[2], zn[2])
        return Fit(Pose(r, t), ratio, skew)
    }

    /** A pose with how well the homography agreed with a true rectangle of that size. */
    class Fit(val pose: Pose, val axisRatio: Double, val skew: Double) {
        val plausible get() = axisRatio < MAX_AXIS_RATIO && skew < MAX_SKEW
    }

    /** In-plane axes may differ in length by this ratio and still be the card. */
    const val MAX_AXIS_RATIO = 1.12

    /** ...and be this far from square (cosine). */
    const val MAX_SKEW = 0.12

    /**
     * Pose from a quad of picture corners for a rectangle [longM] × [shortM]. The quad's corner
     * order is unknown, so every way of laying the rectangle on it is tried; only a plausible one
     * that puts the camera above the table is kept. Of two that fit (a rectangle can be read from
     * either end), the one nearer [previous] wins, so the frame does not flip between frames.
     */
    fun fromQuad(
        corners: DoubleArray, longM: Double, shortM: Double, k: Intrinsics, previous: Pose? = null,
        /** Sub-pixel points along each quad side (side s: corner s to s + 1); the pose is fitted to them all. */
        sides: List<DoubleArray> = emptyList(),
    ): Fit? {
        val a = longM / 2; val b = shortM / 2
        val rect = doubleArrayOf(-a, -b, a, -b, a, b, -a, b)
        val fits = ArrayList<Fit>()
        for (start in 0 until 4) for (reverse in listOf(false, true)) {
            val px = DoubleArray(8)
            for (i in 0 until 4) {
                val j = if (reverse) (start - i + 4) % 4 else (start + i) % 4
                px[2 * i] = corners[2 * j]; px[2 * i + 1] = corners[2 * j + 1]
            }
            val h = homography(rect, px) ?: continue
            val f = fromHomography(h, k) ?: continue
            if (!f.plausible) continue
            if (f.pose.cameraPosition[1] <= 0) continue // camera must be above the table
            // Rectangle side i runs between rectangle corners i and i + 1, which sit on quad corners
            // j(i) and j(i + 1): that is quad side j(i) going forwards, j(i + 1) going backwards.
            val edgePoints = if (sides.size == 4) List(4) { i ->
                val j = if (reverse) (start - i + 4) % 4 else (start + i) % 4
                sides[if (reverse) (j + 3) % 4 else j]
            } else emptyList()
            fits += if (edgePoints.isEmpty()) f else Fit(refine(f.pose, k, rect, px, edgePoints), f.axisRatio, f.skew)
        }
        if (fits.isEmpty()) return null
        val ref = previous ?: return fits.minByOrNull { it.axisRatio + it.skew }
        return fits.maxByOrNull { f -> (0 until 9).sumOf { f.pose.r[it] * ref.r[it] } }
    }

    /**
     * Gauss-Newton on the six pose numbers, so the card's projected outline passes through every
     * edge point found along its sides, and its corners through the corners. Four corners alone
     * leave the tilt loosely pinned; a few dozen edge points pin it.
     */
    fun refine(start: Pose, k: Intrinsics, rect: DoubleArray, cornersPx: DoubleArray, sides: List<DoubleArray>): Pose {
        var rv = rotationVector(start.r); var t = start.t.copyOf()
        fun residuals(rvec: DoubleArray, tt: DoubleArray): DoubleArray? {
            val pose = Pose(rotationMatrix(rvec), tt)
            val pc = ArrayList<DoubleArray>()
            for (i in 0 until 4) pc += pose.project(k, doubleArrayOf(rect[2 * i], 0.0, rect[2 * i + 1])) ?: return null
            val out = ArrayList<Double>()
            for (i in 0 until 4) { out += pc[i][0] - cornersPx[2 * i]; out += pc[i][1] - cornersPx[2 * i + 1] }
            for (i in 0 until 4) {
                val p = pc[i]; val q = pc[(i + 1) % 4]
                val len = kotlin.math.hypot(q[0] - p[0], q[1] - p[1]).coerceAtLeast(1e-9)
                val nx = -(q[1] - p[1]) / len; val ny = (q[0] - p[0]) / len
                val pts = sides[i]
                for (j in 0 until pts.size / 2) out += (pts[2 * j] - p[0]) * nx + (pts[2 * j + 1] - p[1]) * ny
            }
            return out.toDoubleArray()
        }
        repeat(15) {
            val r0 = residuals(rv, t) ?: return start
            val params = rv + t
            val jac = Array(6) { pi ->
                val step = 1e-6 * (1 + abs(params[pi]))
                val p2 = params.copyOf(); p2[pi] += step
                val r1 = residuals(p2.copyOfRange(0, 3), p2.copyOfRange(3, 6)) ?: return start
                DoubleArray(r0.size) { (r1[it] - r0[it]) / step }
            }
            val jtj = Array(6) { a -> DoubleArray(6) { b -> var s = 0.0; for (i in r0.indices) s += jac[a][i] * jac[b][i]; s } }
            for (d in 0 until 6) jtj[d][d] *= 1.0 + 1e-6
            val jtr = DoubleArray(6) { a -> var s = 0.0; for (i in r0.indices) s += jac[a][i] * r0[i]; -s }
            val delta = solve(jtj, jtr) ?: return Pose(rotationMatrix(rv), t)
            rv = DoubleArray(3) { rv[it] + delta[it] }; t = DoubleArray(3) { t[it] + delta[3 + it] }
            if (delta.sumOf { it * it } < 1e-18) return Pose(rotationMatrix(rv), t)
        }
        return Pose(rotationMatrix(rv), t)
    }

    private fun rotationMatrix(v: DoubleArray): DoubleArray {
        val th = norm(v)
        if (th < 1e-12) return doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
        val x = v[0] / th; val y = v[1] / th; val z = v[2] / th
        val c = kotlin.math.cos(th); val s = kotlin.math.sin(th); val cc = 1 - c
        return doubleArrayOf(
            c + x * x * cc, x * y * cc - z * s, x * z * cc + y * s,
            y * x * cc + z * s, c + y * y * cc, y * z * cc - x * s,
            z * x * cc - y * s, z * y * cc + x * s, c + z * z * cc,
        )
    }

    private fun rotationVector(r: DoubleArray): DoubleArray {
        val cosT = ((r[0] + r[4] + r[8] - 1) / 2).coerceIn(-1.0, 1.0)
        val th = kotlin.math.acos(cosT)
        if (th < 1e-9) return doubleArrayOf(0.0, 0.0, 0.0)
        val s = 2 * kotlin.math.sin(th)
        return doubleArrayOf((r[7] - r[5]) / s * th, (r[2] - r[6]) / s * th, (r[3] - r[1]) / s * th)
    }

    // ---------------------------------------------------------------- small linear algebra

    private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = b.size
        val m = Array(n) { i -> a[i].copyOf(n + 1).also { it[n] = b[i] } }
        for (col in 0 until n) {
            var p = col
            for (r in col + 1 until n) if (abs(m[r][col]) > abs(m[p][col])) p = r
            if (abs(m[p][col]) < 1e-15) return null
            val tmp = m[p]; m[p] = m[col]; m[col] = tmp
            for (r in 0 until n) if (r != col) {
                val f = m[r][col] / m[col][col]
                if (f != 0.0) for (c in col..n) m[r][c] -= f * m[col][c]
            }
        }
        return DoubleArray(n) { m[it][n] / m[it][it] }
    }

    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    private fun norm(a: DoubleArray) = sqrt(dot(a, a))
    private fun scale(a: DoubleArray, s: Double) = DoubleArray(3) { a[it] * s }
    private fun sub(a: DoubleArray, b: DoubleArray) = DoubleArray(3) { a[it] - b[it] }
    private fun cross(a: DoubleArray, b: DoubleArray) =
        doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])

    /** ISO/IEC 7810 ID-1: every bank, ID and loyalty card. */
    const val CARD_LONG_M = 0.08560
    const val CARD_SHORT_M = 0.05398
    const val A4_LONG_M = 0.297
    const val A4_SHORT_M = 0.210
}
