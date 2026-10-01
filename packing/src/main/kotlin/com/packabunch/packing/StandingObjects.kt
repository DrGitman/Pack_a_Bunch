package com.packabunch.packing

import kotlin.math.floor

/**
 * Objects found from geometry alone: anything standing up off the support surface.
 *
 * The scan used to find objects only through ML Kit's detector. In a dim room that returns no
 * boxes on almost every frame, while depth keeps arriving normally, so on the phone the log read
 * `sampleBoxes=0` frame after frame and nothing was ever measured. This is the method the OpenCV
 * measuring tutorials rely on — a known reference surface, and whatever rises from it is the
 * object — with the table standing in for their sheet of paper. It needs no light, no texture and
 * no classifier; the detector is left to supply names.
 */
object StandingObjects {

    /** Points lower than this above the surface are the surface itself, give or take depth noise. */
    const val MIN_HEIGHT_M = 0.02f

    /** Taller than this is a wall or a person, not an item on a table. */
    const val MAX_HEIGHT_M = 1.0f

    /** Footprint grid. Two items further apart than this come out as two objects. */
    const val CELL_M = 0.02f

    /** A cell needs this many points to count, so lone speckles do not bridge two items. */
    private const val MIN_PER_CELL = 2

    /** Fewer points than this is noise, not an object. */
    const val MIN_POINTS = 25

    /** A little margin around the found box, since depth thins out at an object's edges. */
    private const val PAD = 0.01f

    /** Within this of the picture's edge, an object has run out of shot. */
    const val BORDER = 0.01f

    /** A box covering more of the picture than this is the scene, not an item. */
    const val MAX_FRAME_SHARE = 0.5f

    /** A found object: its box in the same 0..1 image space as [uv], and how much depth backs it. */
    class Found(val box: FloatArray, val points: Int)

    /**
     * @param world x,y,z triples, y up, in metres.
     * @param uv matching u,v pairs in 0..1 image space.
     * @param supportY height of the surface the items stand on.
     * @return objects, most substantial first.
     */
    fun find(world: FloatArray, uv: FloatArray, supportY: Float): List<Found> {
        val count = world.size / 3
        require(uv.size == count * 2) { "uv must pair with every world point" }

        // Bucket everything standing on the surface by its footprint cell.
        val cells = HashMap<Long, MutableList<Int>>()
        for (i in 0 until count) {
            val h = world[3 * i + 1] - supportY
            if (h < MIN_HEIGHT_M || h > MAX_HEIGHT_M) continue
            cells.getOrPut(key(cell(world[3 * i]), cell(world[3 * i + 2]))) { ArrayList() } += i
        }
        val occupied = cells.filterValues { it.size >= MIN_PER_CELL }

        // Join touching cells into objects.
        val seen = HashSet<Long>()
        val found = ArrayList<Found>()
        for (start in occupied.keys) {
            if (!seen.add(start)) continue
            val members = ArrayList<Int>()
            val queue = ArrayDeque<Long>().apply { add(start) }
            while (queue.isNotEmpty()) {
                val k = queue.removeFirst()
                members += occupied.getValue(k)
                val cx = (k shr 32).toInt(); val cz = k.toInt()
                for (dx in -1..1) for (dz in -1..1) {
                    val n = key(cx + dx, cz + dz)
                    if (n in occupied && seen.add(n)) queue.add(n)
                }
            }
            if (members.size < MIN_POINTS) continue

            var l = 1f; var t = 1f; var r = 0f; var b = 0f
            for (i in members) {
                val u = uv[2 * i]; val v = uv[2 * i + 1]
                l = minOf(l, u); r = maxOf(r, u); t = minOf(t, v); b = maxOf(b, v)
            }
            // Intel's object-size-detector rejects anything touching the frame border, and
            // HarshdeepJ's drops boxes filling the frame: both are the table edge or the wall,
            // never an item. On the phone this was a "78 × 11 × 18 cm" item that was the table's
            // front edge running out of shot.
            if (l <= BORDER || t <= BORDER || r >= 1f - BORDER || b >= 1f - BORDER) continue
            if ((r - l) * (b - t) > MAX_FRAME_SHARE) continue
            found += Found(
                floatArrayOf(
                    (l - PAD).coerceIn(0f, 1f), (t - PAD).coerceIn(0f, 1f),
                    (r + PAD).coerceIn(0f, 1f), (b + PAD).coerceIn(0f, 1f),
                ),
                members.size,
            )
        }
        return found.sortedByDescending { it.points }
    }

    /**
     * Intersection over union of two `[left, top, right, bottom]` boxes.
     *
     * Not intersection over the smaller box: a big box containing a small one scores 1.0 on that
     * measure, so a carton would claim the mug in front of it.
     */
    fun overlap(a: FloatArray, b: FloatArray): Float {
        val w = minOf(a[2], b[2]) - maxOf(a[0], b[0])
        val h = minOf(a[3], b[3]) - maxOf(a[1], b[1])
        if (w <= 0f || h <= 0f) return 0f
        val inter = w * h
        val union = (a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - inter
        return if (union <= 0f) 0f else inter / union
    }

    private fun cell(m: Float) = floor(m / CELL_M).toInt()
    private fun key(x: Int, z: Int) = (x.toLong() shl 32) or (z.toLong() and 0xffffffffL)
}
