package com.packabunch.packing

/**
 * Which depths inside one detector box can be the boxed object at all.
 *
 * The middle of a detector box is the object; its edges are often the room behind it. A
 * thing's depth, front to back, is about as large as it looks across, so anything much further
 * back than the middle of the box by more than the box is wide is background — the wall behind
 * a heater, the far side of the room past a mug — and anything that much nearer is something
 * in front of it. Cutting those off before the points are grouped stops the wall joining the
 * object and the outline growing to the size of the room.
 */
object BoxDepth {

    /**
     * The depth range, in mm, to keep. [depthsMm] are the box's samples and [central] marks the
     * ones from its middle. [boxSpanPx] is the box's longer side and [focalPx] the focal length,
     * both in the depth image's pixels. Null when the middle of the box has no depth.
     */
    fun keepRange(depthsMm: IntArray, central: BooleanArray, boxSpanPx: Float, focalPx: Float): IntRange? {
        var n = 0
        for (c in central) if (c) n++
        if (n < MIN_CENTRAL) return null
        val middle = IntArray(n)
        var i = 0
        for (k in depthsMm.indices) if (central[k]) middle[i++] = depthsMm[k]
        middle.sort()
        val median = middle[n / 2]
        val across = boxSpanPx * median / focalPx
        val reach = across.coerceIn(MIN_REACH_MM, MAX_REACH_MM).toInt()
        return (median - reach)..(median + reach)
    }

    /** Fewer middle samples than this and there is no telling where the object is. */
    const val MIN_CENTRAL = 6

    /** Never cut closer than this: a small cup still has a back. */
    const val MIN_REACH_MM = 120f

    /** Nothing an item scan measures is deeper than this from its front. */
    const val MAX_REACH_MM = 900f
}

/**
 * Holds what the screen shows for each object steady.
 *
 * The measurement's own state can flip between "scanning" and "another angle" from one frame
 * to the next while an edge is on the verge of being seen. Shown directly, the tag's words and
 * the outline's style flickered ten times a second and the rolling text never finished one
 * change before the next began, leaving two lines of text on top of each other. A new state is
 * shown once it has held for [holdMs]; "measured" is shown at once, since it never goes back.
 */
class ScanStateSmoother(private val holdMs: Long = DEFAULT_HOLD_MS) {

    private class Entry(var shown: ItemScanState, var pendingKind: Any?, var pendingSinceMs: Long)

    private val entries = HashMap<Int, Entry>()

    /** What to show for object [id] now that its measurement says [state]. */
    fun smooth(id: Int, state: ItemScanState, nowMs: Long): ItemScanState {
        val e = entries[id] ?: return state.also { entries[id] = Entry(it, null, nowMs) }
        val kind = kindOf(state)
        if (state is ItemScanState.Measured || kind == kindOf(e.shown)) {
            e.shown = state; e.pendingKind = null
            return state
        }
        if (e.pendingKind != kind) { e.pendingKind = kind; e.pendingSinceMs = nowMs }
        if (nowMs - e.pendingSinceMs >= holdMs) { e.shown = state; e.pendingKind = null }
        return e.shown
    }

    /** Forgets every object not in [ids]. */
    fun retain(ids: Set<Int>) { entries.keys.retainAll(ids) }

    fun clear() = entries.clear()

    private fun kindOf(s: ItemScanState): Any = when (s) {
        is ItemScanState.Scanning -> "scanning"
        is ItemScanState.NeedsAngle -> s.hint
        is ItemScanState.Measured -> "measured"
        is ItemScanState.CannotMeasure -> s.reason
    }

    companion object {
        const val DEFAULT_HOLD_MS = 700L
    }
}

/**
 * A wall, a door, a fridge front: a vertical plane big enough that it can never be an item's
 * side. Depth points lying on one are the room, and are dropped before an object is picked out
 * of a detector box. In world metres; [normal], [axisU] and [axisV] are unit vectors.
 */
class WallPlane(
    private val origin: FloatArray,
    private val normal: FloatArray,
    private val axisU: FloatArray,
    private val axisV: FloatArray,
    /** Half the plane's seen size along [axisU] and [axisV], before [MARGIN_M] is added. */
    halfU: Float,
    halfV: Float,
) {
    // The scan only knows the part of a wall it has seen; the wall carries on past that.
    private val reachU = halfU + MARGIN_M
    private val reachV = halfV + MARGIN_M

    fun contains(x: Float, y: Float, z: Float): Boolean {
        val dx = x - origin[0]; val dy = y - origin[1]; val dz = z - origin[2]
        fun dot(a: FloatArray) = dx * a[0] + dy * a[1] + dz * a[2]
        return kotlin.math.abs(dot(normal)) <= THICKNESS_M &&
            kotlin.math.abs(dot(axisU)) <= reachU && kotlin.math.abs(dot(axisV)) <= reachV
    }

    companion object {
        /** A vertical plane this long, or this big, is a wall or a door — never an item's side. */
        const val MIN_EXTENT_M = 1.0f
        const val MIN_AREA_M2 = 0.5f

        /** Points this close to a wall are the wall. */
        const val THICKNESS_M = 0.03f

        /** How far past the seen part of a wall it is still taken to carry on. */
        const val MARGIN_M = 0.5f

        fun isWallSized(extentU: Float, extentV: Float) = maxOf(extentU, extentV) >= MIN_EXTENT_M || extentU * extentV >= MIN_AREA_M2
    }
}

/** The floor found under a space, as its outline in world X / Z (metres). */
object FloorOutline {

    /**
     * How far past the outline a wall may stand, in metres. The found floor rarely reaches the
     * walls — less so the bigger the floor: a boot's reaches its trim, a room's stops short of
     * the skirting by a hand's width or more, and a room's walls were being left out.
     */
    fun wallMargin(poly: FloatArray): Float {
        var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var z0 = Float.MAX_VALUE; var z1 = -Float.MAX_VALUE
        for (i in 0 until poly.size / 2) {
            x0 = minOf(x0, poly[2 * i]); x1 = maxOf(x1, poly[2 * i]); z0 = minOf(z0, poly[2 * i + 1]); z1 = maxOf(z1, poly[2 * i + 1])
        }
        return maxOf(MIN_WALL_MARGIN_M, 0.15f * maxOf(x1 - x0, z1 - z0))
    }

    const val MIN_WALL_MARGIN_M = 0.2f

    /** Inside the outline, or within [margin] metres of it — where the walls stand. */
    fun near(poly: FloatArray, x: Float, z: Float, margin: Float): Boolean {
        val n = poly.size / 2
        if (n < 3) return false
        var inside = false
        var j = n - 1
        var best = Float.MAX_VALUE
        for (i in 0 until n) {
            val xi = poly[2 * i]; val zi = poly[2 * i + 1]; val xj = poly[2 * j]; val zj = poly[2 * j + 1]
            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) inside = !inside
            val ex = xi - xj; val ez = zi - zj
            val len2 = ex * ex + ez * ez
            val t = if (len2 > 0f) (((x - xj) * ex + (z - zj) * ez) / len2).coerceIn(0f, 1f) else 0f
            val dx = x - (xj + t * ex); val dz = z - (zj + t * ez)
            best = minOf(best, dx * dx + dz * dz)
            j = i
        }
        return inside || best <= margin * margin
    }
}
