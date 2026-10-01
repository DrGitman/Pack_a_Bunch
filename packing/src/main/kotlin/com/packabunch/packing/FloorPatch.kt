package com.packabunch.packing

import kotlin.math.floor

/**
 * The floor of a space, grown out from where the reference lies.
 *
 * A space scan measures from the surface its card or sheet of A4 lies on — the boot floor, the
 * cupboard shelf, the room's floor. Which surface that is beyond the card is found by flooding
 * outward over depth points at the card's own height, frame after frame: everything joined to the
 * card at that height is the floor. The road outside a boot is lower than the boot floor, so it
 * never joins; that is what keeps the measured space the boot and not the car park.
 */
class FloorPatch(private val cellM: Float = 0.05f) {

    private val floor = HashSet<Long>()

    /** Cells seen at floor height but not (yet) joined to the reference. */
    private val pending = HashSet<Long>()

    val isEmpty get() = floor.isEmpty()

    /**
     * Adds one frame's depth points (x, y, z triples, metres, y up, the reference's surface at
     * y = 0). Points within [TOLERANCE_M] of the surface are floor candidates.
     */
    fun add(world: FloatArray) {
        if (floor.isEmpty()) floor += key(0, 0) // the reference itself lies on the floor
        for (i in 0 until world.size / 3) {
            if (kotlin.math.abs(world[3 * i + 1]) > TOLERANCE_M) continue
            pending += key(cell(world[3 * i]), cell(world[3 * i + 2]))
        }
        // Flood from what is already floor into what is now seen at floor height.
        val queue = ArrayDeque(floor)
        while (queue.isNotEmpty()) {
            val k = queue.removeFirst()
            val cx = (k shr 32).toInt(); val cz = k.toInt()
            for (dx in -1..1) for (dz in -1..1) {
                val n = key(cx + dx, cz + dz)
                if (n in pending) { pending -= n; floor += n; queue.add(n) }
            }
        }
    }

    /** The floor's outline as an x, z polygon in metres (its convex hull), or null before any is seen. */
    fun polygon(): FloatArray? {
        if (floor.size < 2) return null
        val pts = ArrayList<Pair<Float, Float>>()
        for (k in floor) {
            val x = (k shr 32).toInt() * cellM; val z = k.toInt() * cellM
            pts += x to z; pts += (x + cellM) to z; pts += x to (z + cellM); pts += (x + cellM) to (z + cellM)
        }
        val hull = hull(pts)
        return FloatArray(hull.size * 2) { if (it % 2 == 0) hull[it / 2].first else hull[it / 2].second }
    }

    fun clear() { floor.clear(); pending.clear() }

    private fun cell(m: Float) = floor(m / cellM).toInt()
    private fun key(x: Int, z: Int) = (x.toLong() shl 32) or (z.toLong() and 0xffffffffL)

    private fun hull(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        val p = points.distinct().sortedWith(compareBy({ it.first }, { it.second }))
        if (p.size < 3) return p
        fun cross(o: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>) =
            (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)
        val lower = ArrayList<Pair<Float, Float>>()
        for (q in p) { while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), q) <= 0) lower.removeAt(lower.size - 1); lower += q }
        val upper = ArrayList<Pair<Float, Float>>()
        for (q in p.asReversed()) { while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), q) <= 0) upper.removeAt(upper.size - 1); upper += q }
        return lower.dropLast(1) + upper.dropLast(1)
    }

    companion object {
        /** Depth noise on a floor at a metre or two, give or take. */
        const val TOLERANCE_M = 0.025f
    }
}
