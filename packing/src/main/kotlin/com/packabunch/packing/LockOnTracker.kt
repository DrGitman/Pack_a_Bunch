package com.packabunch.packing

import kotlin.math.hypot

/**
 * Target lock-on: one steady id per object across frames, as the camera moves.
 *
 * Tracking by detection, the way the Pysource and Ultralytics (ByteTrack) tutorials do it: every
 * frame's detections are matched to existing targets, best pairs first, by how much a detection
 * overlaps where the target is predicted to be (its last box moved on by its own velocity), with
 * the label agreeing. A target that misses a few frames is kept, coasting on its prediction, so a
 * blurred frame or a missed detection does not split one mouse into three. Unmatched detections
 * start new targets.
 *
 * Boxes are `[left, top, right, bottom]` in 0..1 of the picture.
 */
class LockOnTracker(private val keepFrames: Int = 10) {

    class Detection(val box: FloatArray, val label: String?)

    private class Target(val id: Int, var box: FloatArray, var label: String?) {
        val velocity = FloatArray(4)
        var missed = 0
        var hits = 1
        fun predicted() = FloatArray(4) { box[it] + velocity[it] }
    }

    private val targets = ArrayList<Target>()
    private var nextId = 1

    /** The id each detection is locked to, in the order given. */
    fun update(detections: List<Detection>): IntArray {
        val ids = IntArray(detections.size) { -1 }
        val pairs = ArrayList<Triple<Int, Int, Float>>()
        for ((t, target) in targets.withIndex()) {
            val p = target.predicted()
            for ((d, det) in detections.withIndex()) {
                if (target.label != null && det.label != null && target.label != det.label) continue
                val iou = StandingObjects.overlap(p, det.box)
                // Close centres count too: a fast pan can leave no overlap between frames.
                val near = 1f - (centreDistance(p, det.box) / NEAR_SHARE).coerceAtMost(1f)
                val score = maxOf(iou, near * 0.6f)
                if (score >= MIN_SCORE) pairs += Triple(t, d, score)
            }
        }
        val takenT = HashSet<Int>(); val takenD = HashSet<Int>()
        for ((t, d, _) in pairs.sortedByDescending { it.third }) {
            if (t in takenT || d in takenD) continue
            takenT += t; takenD += d
            val target = targets[t]; val box = detections[d].box
            for (i in 0 until 4) target.velocity[i] = 0.5f * target.velocity[i] + 0.5f * (box[i] - target.box[i])
            target.box = box.copyOf(); target.missed = 0; target.hits++
            if (detections[d].label != null) target.label = detections[d].label
            ids[d] = target.id
        }
        for ((t, target) in targets.withIndex()) if (t !in takenT) {
            target.missed++
            target.box = target.predicted()
            for (i in 0 until 4) target.velocity[i] *= 0.5f
        }
        targets.removeAll { it.missed > keepFrames }
        for ((d, det) in detections.withIndex()) if (ids[d] < 0) {
            val target = Target(nextId++, det.box.copyOf(), det.label)
            targets += target; ids[d] = target.id
        }
        return ids
    }

    fun clear() { targets.clear() }

    private fun centreDistance(a: FloatArray, b: FloatArray) =
        hypot((a[0] + a[2]) / 2 - (b[0] + b[2]) / 2, (a[1] + a[3]) / 2 - (b[1] + b[3]) / 2)

    companion object {
        private const val MIN_SCORE = 0.2f
        /** Centres closer than this share of the picture are near enough to be the same target. */
        private const val NEAR_SHARE = 0.12f
    }
}
