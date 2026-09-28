package com.packabunch.packing

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** The six sides of a space, named as the person scanning it sees them. */
enum class SpaceFace(val label: String) {
    FLOOR("floor"), LEFT("left side"), RIGHT("right side"), BACK("back"), FRONT("front"), TOP("top"),
}

/**
 * A space fitted as a box, in its floor's frame (see [PlanePoint]).
 *
 * The box is oriented to the person, not to the longest wall: [yawDegrees] is the direction
 * of the **width** — left to right as seen from where they stand — and depth runs away from
 * them. That is what makes "W 104 cm" on the screen mean the edge they are looking along, and
 * it is the grid convention the solver uses (X left→right, Y front→back, Z up).
 */
data class SpaceBox(
    val centreXMm: Float,
    val centreYMm: Float,
    val yawDegrees: Float,
    val widthMm: Float,
    val depthMm: Float,
    val heightMm: Float,
    /** Share of each face that has been seen, 0..1. */
    val coverage: Map<SpaceFace, Float>,
    /** The way in, when the space has one (a boot, a box, a cupboard) rather than surrounding the camera. */
    val opening: Opening?,
    /** True when the camera stood inside the footprint — a room rather than a container. */
    val cameraInside: Boolean,
    val pointCount: Int,
) {
    /**
     * How much of the space is known, for the "72 % mapped" readout: the faces that bound
     * what can be packed. The front of a container is its opening and has nothing to see;
     * the top of an open box likewise — so neither is required. A room's walls all count.
     */
    val mapped: Float
        get() {
            val required = requiredFaces
            return required.map { coverage[it] ?: 0f }.average().toFloat()
        }

    val requiredFaces: List<SpaceFace>
        get() = buildList {
            add(SpaceFace.FLOOR); add(SpaceFace.LEFT); add(SpaceFace.RIGHT); add(SpaceFace.BACK)
            // Standing in a room, its height is only known once the ceiling has been in view.
            if (cameraInside) { add(SpaceFace.FRONT); add(SpaceFace.TOP) }
        }

    /** The required face seen least — what the guidance asks for next. */
    val weakestFace: SpaceFace? get() = requiredFaces.minByOrNull { coverage[it] ?: 0f }?.takeIf { (coverage[it] ?: 0f) < WELL_SEEN }

    val dimensions: Dimensions get() = Dimensions(widthMm.roundToInt(), depthMm.roundToInt(), heightMm.roundToInt())

    /** Plan position of a point given in box coordinates (u along width from centre, v along depth). */
    fun toPlan(u: Float, v: Float): Pair<Float, Float> {
        val r = yawDegrees * PI.toFloat() / 180f
        val c = cos(r); val s = sin(r)
        return (centreXMm + u * c - v * s) to (centreYMm + u * s + v * c)
    }

    fun toBox(x: Float, y: Float): Pair<Float, Float> {
        val r = yawDegrees * PI.toFloat() / 180f
        val c = cos(r); val s = sin(r)
        val dx = x - centreXMm; val dy = y - centreYMm
        return (dx * c + dy * s) to (-dx * s + dy * c)
    }

    companion object {
        /** A face counts as seen once this much of it has points on it. */
        const val WELL_SEEN = 0.6f
    }
}

/**
 * Fits the inside of a space — a carton, a cupboard, a car boot, a room — from what the
 * camera has seen of its surfaces.
 *
 * Seen from inside, a space's point cloud *is* its inner surfaces, so its extent is the
 * space's usable extent. Each wall is placed at the weighted median of the points on it
 * (depth noise is symmetric about a real surface), not at its outermost point, so a wall does
 * not creep outwards the longer someone sweeps it.
 *
 * What it does not claim: the box is the space's bounding interior. Wheel arches, a sill,
 * a shelf are not subtracted here; they are left in the voxel grid as solid cells, where the
 * solver already knows how to pack around them ([toScannedSpace]).
 */
object SpaceFitter {

    /** Points lower than this are floor. */
    const val FLOOR_BAND_MM = 25f

    fun fit(
        points: List<PlanePoint>,
        cameras: List<PlanePoint>,
        weights: FloatArray? = null,
    ): SpaceBox? {
        val walls = points.indices.filter { points[it].hMm > FLOOR_BAND_MM }
        if (walls.size < MIN_WALL_POINTS || cameras.isEmpty()) return null
        val w = weights ?: FloatArray(points.size) { 1f }

        // Orientation: tightest rectangle round the walls, as for objects.
        var bestDeg = 0.0; var bestArea = Double.MAX_VALUE
        fun area(deg: Double): Double {
            val r = deg * PI / 180; val c = cos(r); val s = sin(r)
            var u0 = Double.MAX_VALUE; var u1 = -Double.MAX_VALUE; var v0 = Double.MAX_VALUE; var v1 = -Double.MAX_VALUE
            for (i in walls) {
                val p = points[i]
                val u = p.xMm * c + p.yMm * s; val v = -p.xMm * s + p.yMm * c
                if (u < u0) u0 = u; if (u > u1) u1 = u; if (v < v0) v0 = v; if (v > v1) v1 = v
            }
            return (u1 - u0) * (v1 - v0)
        }
        for (d in 0 until 90 step 2) { val a = area(d.toDouble()); if (a < bestArea) { bestArea = a; bestDeg = d.toDouble() } }
        for ((span, step) in listOf(2.0 to 0.5, 0.5 to 0.1)) {
            val base = bestDeg; var d = base - span
            while (d <= base + span + 1e-9) { val a = area(d); if (a < bestArea) { bestArea = a; bestDeg = d }; d += step }
        }

        // Which rectangle axis is "width": the one most across the person's line of sight.
        val camX = cameras.map { it.xMm }.average().toFloat()
        val camY = cameras.map { it.yMm }.average().toFloat()
        val wallCx = walls.map { points[it].xMm }.average().toFloat()
        val wallCy = walls.map { points[it].yMm }.average().toFloat()
        val lookX = wallCx - camX; val lookY = wallCy - camY
        val lookLen = hypot(lookX, lookY)
        val base = bestDeg * PI / 180
        // Depth axis should point away from the camera. Try the four quarter-turns and keep the
        // one whose depth direction best matches the look direction.
        var yaw = base
        if (lookLen > 50f) {
            var best = -Double.MAX_VALUE
            for (q in 0 until 4) {
                val a = base + q * PI / 2
                val depthX = -sin(a); val depthY = cos(a)
                val dot = (depthX * lookX + depthY * lookY) / lookLen
                if (dot > best) { best = dot; yaw = a }
            }
        }
        val c = cos(yaw).toFloat(); val s = sin(yaw).toFloat()
        val n = points.size
        val us = FloatArray(n) { points[it].xMm * c + points[it].yMm * s }
        val vs = FloatArray(n) { -points[it].xMm * s + points[it].yMm * c }
        val hs = FloatArray(n) { points[it].hMm }

        val wallIdx = walls
        val (uLo, uHi) = range(wallIdx, us, w)
        val (vLo, vHi) = range(wallIdx, vs, w)
        val band = max(40f, 0.06f * max(uHi - uLo, vHi - vLo))
        val wallTop = range(wallIdx, hs, w).second
        // A wall stands on the floor: its lower half alone runs the length of the side. What
        // only exists up high — a carton's flap folded out over the rim, the car's roof beside
        // the boot — is outside the space, and what is low only at one end — the tail-light
        // pillars beside a boot's opening — is the end of another wall.
        val lowerHalf = wallTop * 0.5f

        /**
         * One side: where it stands, how many points it has (0 when it is open), and how thick
         * its points lie about that — the depth noise at its distance.
         */
        class Side(val at: Float, val count: Int, val spread: Float = 0f)

        // A side is a wall when the points near it run most of its length and reach down to the
        // floor; then it is placed at their median. The outermost points are not always it: a
        // car's tail-light pillars stand outside the boot's trim, the flaps of an open carton
        // hang outside its walls, the kitchen wall runs on past a shelf's sides. So the search
        // walks inwards from the outermost points, up to a third of the way across, and takes
        // the first band that is a wall. When none is — the open front of a boot or a shelf —
        // those points are just the ends of the neighbouring walls, and the extreme is right.
        fun side(vals: FloatArray, along: FloatArray, alongLen: Float, outer: Float, inward: Float, span: Float): Side {
            val found = ArrayList<Side>()
            var offset = 0f
            while (offset <= span * 0.35f) {
                val a = outer + inward * offset
                val lower = wallIdx.filter { val d = (vals[it] - a) * inward; d >= 0f && d <= band && hs[it] <= lowerHalf }
                if (lower.size >= MIN_FACE_POINTS) {
                    // Judged on the densest few centimetres of the band, not all of it: the band
                    // can also hold the pillar or trim standing right beside a wall.
                    val peak = densest(lower, vals)
                    val on = wallIdx.filter { abs(vals[it] - peak) <= max(20f, band / 4) && hs[it] <= lowerHalf }
                    // Run along its length in ten bins: a wall fills most of them; the two ends
                    // of the neighbouring walls fill only the first and last.
                    if (on.size >= MIN_FACE_POINTS) {
                        val lo = on.minOf { along[it] }
                        val bins = BooleanArray(10)
                        for (i in on) bins[((along[i] - lo) / alongLen * 10).toInt().coerceIn(0, 9)] = true
                        if (bins.count { it } >= 6) {
                            val at = weightedMedian(on, vals, w)
                            val spread = on.map { abs(vals[it] - at) }.sorted()[on.size / 2] * 1.4826f
                            if (found.none { abs(it.at - at) < 5f }) found += Side(at, on.size, spread)
                        }
                    }
                }
                offset += band / 2
            }
            // The outermost band that is plainly a wall: a pillar's corner caught with a little
            // of the wall beside it passes too, but on a fraction of the wall's own points.
            if (found.isNotEmpty()) {
                val most = found.maxOf { it.count }
                return found.filter { it.count >= most / 2 }.minBy { (it.at - outer) * inward }
            }
            // Open: bounded by the furthest thing standing, not by a flap reaching out over it.
            val standing = wallIdx.filter { hs[it] <= lowerHalf }
            if (standing.size < MIN_FACE_POINTS) return Side(outer, 0)
            // Barely trimmed: an open side's last few centimetres are seen edge-on and are sparse.
            val (lo, hi) = range(standing, vals, w, trim = 0.002)
            return Side(if (inward > 0) lo else hi, 0)
        }
        val leftSide = side(us, vs, vHi - vLo, uLo, 1f, uHi - uLo)
        val rightSide = side(us, vs, vHi - vLo, uHi, -1f, uHi - uLo)
        val backSide = side(vs, us, uHi - uLo, vHi, -1f, vHi - vLo)
        val frontSide = side(vs, us, uHi - uLo, vLo, 1f, vHi - vLo)
        val left = leftSide.at; val right = rightSide.at; val back = backSide.at; val front = frontSide.at
        val width = (right - left).coerceAtLeast(1f)
        val depth = (back - front).coerceAtLeast(1f)

        // Height: where the walls stop. Each wall is followed up from the floor, on the points
        // right against it and away from its corners, until the points run out; a flap folded
        // out over the rim, or a roof seen from above, sits beyond a gap or off the wall's plane.
        // The median over the walls, so one wall that runs on (the kitchen wall behind a shelf)
        // does not carry the rest. A ceiling, lid or parcel shelf seen spread over the
        // footprint at that height wins when there is one.
        /** A wall's top, and whether the surface carries on above it (a steep flap). */
        fun topOf(s: Side, vals: FloatArray, along: FloatArray, lo: Float, hi: Float): Pair<Float, Boolean>? {
            if (s.count == 0) return null
            val len = hi - lo
            fun inMiddle(i: Int) = along[i] > lo + 0.1f * len && along[i] < hi - 0.1f * len
            // As tight to the wall as its own points allow: a flap leaning out from the rim
            // leaves a band this thin within a centimetre or two.
            val tight = max(8f, 2f * s.spread)
            val tight2 = max(5f, 1.5f * s.spread)
            val hsOn = wallIdx.filter { abs(vals[it] - s.at) <= tight2 && inMiddle(it) }.map { hs[it] }.sorted()
            if (wallIdx.count { abs(vals[it] - s.at) <= tight && inMiddle(it) } < MIN_FACE_POINTS || hsOn.size < MIN_FACE_POINTS) return null
            val gap = max(50f, 0.08f * wallTop)
            var top = hsOn.first()
            for (hh in hsOn) { if (hh - top > gap) break; top = hh }
            // A steep flap carries the wall on past its rim: surface just above the top, leaning
            // off the wall's line by no more than it rises. A wall whose top is its rim has
            // nothing there (a ceiling sits level with the top, not above it).
            val flap = wallIdx.count {
                val rise = hs[it] - top
                rise > 15f && rise <= 120f && abs(vals[it] - s.at) <= tight + rise && inMiddle(it)
            } >= MIN_FACE_POINTS
            return top to flap
        }
        // Not the front: that is where the person stands, and in a boot or a cupboard it is only
        // the lip they lift things over.
        val found = listOfNotNull(
            topOf(leftSide, us, vs, front, back), topOf(rightSide, us, vs, front, back), topOf(backSide, vs, us, left, right),
        )
        // A flap only ever adds height to its wall: the walls without one give the rim. When every
        // wall carries on above (a shelf against a taller wall), they are all read as they are.
        val clean = found.filter { !it.second }.map { it.first }.sorted()
        val tops = clean.ifEmpty { found.map { it.first }.sorted() }
        val wallsTop = if (tops.isEmpty()) wallTop else tops[tops.size / 2]
        val topBand = max(60f, 0.1f * wallsTop)
        val high = wallIdx.filter { abs(hs[it] - wallsTop) <= topBand && us[it] > left + band && us[it] < right - band && vs[it] > front + band && vs[it] < back - band }
        val height = if (high.size >= MIN_FACE_POINTS * 3) weightedMedian(high, hs, w) else wallsTop

        val um = (left + right) / 2; val vm = (front + back) / 2
        val box0 = SpaceBox(
            centreXMm = um * c - vm * s, centreYMm = um * s + vm * c,
            yawDegrees = ((yaw * 180 / PI).toFloat() % 360f + 360f) % 360f,
            widthMm = width, depthMm = depth, heightMm = height,
            coverage = emptyMap(), opening = null, cameraInside = false, pointCount = n,
        )
        // Inside means standing in it — a room — not leaning over an open box: the camera must
        // also be below the space's top.
        val camInside = cameras.any { cam ->
            val (cu, cv) = box0.toBox(cam.xMm, cam.yMm)
            abs(cu) < width / 2 && abs(cv) < depth / 2 && cam.hMm < height
        }

        // Coverage: cut each face into cells and count the cells a point landed near.
        val cell = max(40f, max(width, max(depth, height)) / 24f)
        fun cover(face: SpaceFace): Float {
            val (aLen, bLen) = when (face) {
                SpaceFace.FLOOR, SpaceFace.TOP -> width to depth
                SpaceFace.LEFT, SpaceFace.RIGHT -> depth to height
                SpaceFace.BACK, SpaceFace.FRONT -> width to height
            }
            val na = ceil(aLen / cell).toInt().coerceAtLeast(1); val nb = ceil(bLen / cell).toInt().coerceAtLeast(1)
            val hit = BooleanArray(na * nb)
            val tol = band
            for (i in 0 until n) {
                val u = us[i] - left; val v = vs[i] - front; val h = hs[i]
                val (a, b, near) = when (face) {
                    SpaceFace.FLOOR -> Triple(u, v, abs(h) <= FLOOR_BAND_MM)
                    SpaceFace.TOP -> Triple(u, v, abs(h - height) <= tol)
                    SpaceFace.LEFT -> Triple(v, h, abs(us[i] - left) <= tol)
                    SpaceFace.RIGHT -> Triple(v, h, abs(us[i] - right) <= tol)
                    SpaceFace.BACK -> Triple(u, h, abs(vs[i] - back) <= tol)
                    SpaceFace.FRONT -> Triple(u, h, abs(vs[i] - front) <= tol)
                }
                if (!near || a < 0 || b < 0 || a >= aLen || b >= bLen) continue
                hit[min(na - 1, floor(a / cell).toInt()) * nb + min(nb - 1, floor(b / cell).toInt())] = true
            }
            return hit.count { it } / hit.size.toFloat()
        }
        val coverage = SpaceFace.entries.associateWith { cover(it) }

        // The opening: the front, when the camera looked in from outside. Its height is what is
        // left above the sill — the highest thing standing along the front edge.
        val opening = if (camInside) null else {
            // Away from the side walls, whose front ends stand at full height.
            // Only the lower part: the frame above a tailgate stands at the front too, and is the
            // top of the opening, not a lip.
            val sillPts = (0 until n).filter {
                abs(vs[it] - front) <= band && hs[it] > FLOOR_BAND_MM && hs[it] < height * 0.6f &&
                    us[it] > left + 2 * band && us[it] < right - 2 * band
            }
            val sill = if (sillPts.size >= MIN_FACE_POINTS) {
                // A lip only: a front wall reaching most of the way up makes this a box seen over
                // the top, and its opening is the whole top.
                val sillTop = range(sillPts, hs, w).second
                if (sillTop < height * 0.5f) sillTop else 0f
            } else 0f
            Opening(widthMm = width.roundToInt(), heightMm = (height - sill).coerceAtLeast(0f).roundToInt())
        }

        return box0.copy(coverage = coverage, opening = opening, cameraInside = camInside)
    }

    /**
     * The fitted space as the solver's grid: inside the box is free, except cells where the
     * scan saw something standing (a wheel arch, a shelf, a sill) — those are solid. Cells
     * against a face nobody has seen are unknown, so the solver treats them as occupied
     * rather than trusting space that was never looked at.
     */
    fun toScannedSpace(
        box: SpaceBox,
        points: List<PlanePoint>,
        weights: FloatArray? = null,
        resolutionMm: Int = if (max(box.widthMm, box.depthMm) > 1200f) 40 else 20,
    ): ScannedSpace {
        val nx = ceil(box.widthMm / resolutionMm).toInt().coerceAtLeast(1)
        val ny = ceil(box.depthMm / resolutionMm).toInt().coerceAtLeast(1)
        val nz = ceil(box.heightMm / resolutionMm).toInt().coerceAtLeast(1)
        val cells = ByteArray(nx * ny * nz) // Cell.FREE.ordinal == 0
        fun idx(i: Int, j: Int, k: Int) = (i * ny + j) * nz + k
        val hits = IntArray(nx * ny * nz)
        val w = weights ?: FloatArray(points.size) { 1f }
        for ((pi, p) in points.withIndex()) {
            val (u, v) = box.toBox(p.xMm, p.yMm)
            val i = floor((u + box.widthMm / 2) / resolutionMm).toInt()
            val j = floor((v + box.depthMm / 2) / resolutionMm).toInt()
            val k = floor(p.hMm / resolutionMm).toInt()
            // One cell in from every face: the faces themselves are the boundary, not contents.
            if (i < 1 || j < 1 || k < 1 || i >= nx - 1 || j >= ny - 1 || k >= nz - 1) continue
            hits[idx(i, j, k)] += w[pi].roundToInt().coerceAtLeast(1)
        }
        for (x in hits.indices) if (hits[x] >= SOLID_HITS) cells[x] = Cell.SOLID.ordinal.toByte()
        // Unseen faces: the first cell layer behind them is unknown.
        val unknown = Cell.UNKNOWN.ordinal.toByte()
        fun unseen(face: SpaceFace) = (box.coverage[face] ?: 0f) < UNSEEN_FACE
        for (i in 0 until nx) for (j in 0 until ny) for (k in 0 until nz) {
            val u = when {
                i == 0 && unseen(SpaceFace.LEFT) -> true
                i == nx - 1 && unseen(SpaceFace.RIGHT) -> true
                j == ny - 1 && unseen(SpaceFace.BACK) -> true
                j == 0 && box.cameraInside && unseen(SpaceFace.FRONT) -> true
                k == 0 && unseen(SpaceFace.FLOOR) -> true
                else -> false
            }
            if (u && cells[idx(i, j, k)] == 0.toByte()) cells[idx(i, j, k)] = unknown
        }
        return ScannedSpace(
            baseGrid = VoxelGrid(0, 0, 0, resolutionMm, nx, ny, nz, cells),
            opening = box.opening,
        )
    }

    private fun range(idx: List<Int>, v: FloatArray, w: FloatArray, trim: Double = 0.01): Pair<Float, Float> {
        if (idx.size < 100) return idx.minOf { v[it] } to idx.maxOf { v[it] }
        val sorted = idx.sortedBy { v[it] }
        val total = sorted.sumOf { w[it].toDouble() }
        val trim = total * trim
        var acc = 0.0; var lo = v[sorted.first()]
        for (i in sorted) { acc += w[i]; if (acc > trim) { lo = v[i]; break } }
        acc = 0.0; var hi = v[sorted.last()]
        for (i in sorted.asReversed()) { acc += w[i]; if (acc > trim) { hi = v[i]; break } }
        return lo to hi
    }

    /** Centre of the most crowded 10 mm of [v] over [idx]. */
    private fun densest(idx: List<Int>, v: FloatArray): Float {
        val counts = HashMap<Int, Int>()
        for (i in idx) { val b = floor(v[i] / 10f).toInt(); counts[b] = (counts[b] ?: 0) + 1 }
        // Three bins at a time, so a wall straddling a bin edge still counts as one.
        val best = counts.keys.maxBy { (counts[it - 1] ?: 0) + (counts[it] ?: 0) + (counts[it + 1] ?: 0) }
        return best * 10f + 5f
    }

    private fun weightedMedian(idx: List<Int>, v: FloatArray, w: FloatArray): Float {
        val sorted = idx.sortedBy { v[it] }
        val half = sorted.sumOf { w[it].toDouble() } / 2
        var acc = 0.0
        for (i in sorted) { acc += w[i]; if (acc >= half) return v[i] }
        return v[sorted.last()]
    }

    const val MIN_WALL_POINTS = 60
    private const val MIN_FACE_POINTS = 20
    private const val SOLID_HITS = 3
    private const val UNSEEN_FACE = 0.25f
}
