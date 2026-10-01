package com.packabunch.packing

import kotlin.math.abs

/**
 * HarshdeepJ's front-and-side fusion (`app.py`): the front view gives width and height, a side
 * view taken about 90° round gives depth, and height is averaged over both.
 *
 * Theirs takes two photos. On the phone every frame is a view, so each one is filed by the
 * direction the camera was facing: views near the first direction are "front", views roughly
 * square to it are "side". Medians, not single frames, so one noisy depth reading cannot move a
 * length — the consistency Intel's object-size-detector asks for before it trusts a size.
 */
class TwoViewSize {

    private val views = ArrayList<FloatArray>()

    fun add(cameraYawDeg: Float, widthMm: Float, heightMm: Float) {
        if (widthMm <= 0f || heightMm <= 0f) return
        views += floatArrayOf(cameraYawDeg, widthMm, heightMm)
        if (views.size > MAX_VIEWS) views.removeAt(1) // keep the first: it defines "front"
    }

    val viewCount get() = views.size

    /** Median width across front views. */
    val widthMm: Float? get() = median(views.filter { turn(it) <= FRONT_DEG }.map { it[1] })

    /** Median width across side views — the object's depth. Null until you have stepped round. */
    val depthMm: Float? get() = median(views.filter { turn(it) >= SIDE_DEG }.map { it[1] })

    /** Median height across every view: height looks the same from every side. */
    val heightMm: Float? get() = median(views.map { it[2] })

    /** How far round from the first view, folded to 0..90° (a view from behind is still front). */
    private fun turn(v: FloatArray): Float {
        var d = abs(v[0] - views[0][0]) % 180f
        if (d > 90f) d = 180f - d
        return d
    }

    private fun median(xs: List<Float>): Float? = if (xs.isEmpty()) null else xs.sorted()[xs.size / 2]

    companion object {
        const val FRONT_DEG = 30f
        const val SIDE_DEG = 60f
        private const val MAX_VIEWS = 240
    }
}
