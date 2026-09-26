package com.packabunch.packing

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Following many objects at once through a single continuous sweep.
 *
 * ### The idea
 *
 * You are not analysing frames, you are accumulating one model of the room that gets denser
 * as somebody walks around. Each object found in that model keeps its own record: how many
 * directions it has been seen from, which of its axes have genuinely been observed, and
 * whether its fitted box has stopped moving.
 *
 * Objects therefore **finish at different times**. A shoe box out in the open settles in a
 * couple of seconds and stops consuming anything; the thing wedged behind the sofa is still
 * waiting for somebody to walk round it. The list fills up as you move rather than arriving
 * all at once at the end, and each row can say honestly what it is still waiting for.
 *
 * ### Why tracking is easy here and hard elsewhere
 *
 * Everything is anchored in world space, so objects do not move between updates — only the
 * camera does. Matching this update's blobs to last update's is therefore nearest-centroid
 * and nothing more. No appearance model, no re-identification, none of the machinery video
 * tracking normally needs.
 */
/**
 * Passes a candidate may go unseen before it is dropped.
 *
 * Segmentation runs about twice a second, so this is roughly a second and a half of not
 * being found. Long enough to survive a hand wobble or a frame where the object was
 * occluded, short enough that noise does not accumulate.
 */
private const val FORGET_AFTER_MISSES = 3

class SweepTracker(
    /** How far a blob's centre may move between updates and still be the same object. */
    private val matchRadiusMm: Int = 150,
) {
    private val tracked = mutableMapOf<String, TrackedObject>()
    private var nextId = 1

    /**
     * Folds one segmentation pass in.
     *
     * [viewDirectionDegrees] is where the camera was looking, quantised by the caller. It is
     * how "seen from three angles" is counted — without it, standing still and staring would
     * look exactly like walking around, and an object would settle having only ever shown
     * one face.
     */
    fun update(detected: List<DetectedObject>, viewDirectionDegrees: Int) {
        val unmatched = tracked.keys.toMutableSet()

        detected.forEach { candidate ->
            val match = tracked.values
                .filter { it.id in unmatched }
                .minByOrNull { it.distanceTo(candidate) }
                ?.takeIf { it.distanceTo(candidate) <= matchRadiusMm }

            if (match == null) {
                val id = "swept-${nextId++}"
                tracked[id] = TrackedObject(
                    id = id,
                    latest = candidate,
                    viewDirections = mutableSetOf(quantise(viewDirectionDegrees)),
                    observedAxes = axesObservedFrom(viewDirectionDegrees),
                    stableUpdates = 0,
                    lastChangeMm = Int.MAX_VALUE,
                    missedUpdates = 0,
                )
            } else {
                unmatched -= match.id
                tracked[match.id] = match.absorb(candidate, viewDirectionDegrees)
            }
        }

        // Anything the segmentation stopped finding.
        //
        // Without this the map only ever grows: a blob of depth noise became an object and
        // stayed one for the rest of the session, which is how a table holding two things
        // reported four, then nine, then eighteen, and never came back down when the camera
        // moved away. Only unsettled candidates expire — an object that has been measured
        // properly is a fact about the room, and walking out of the frame must not delete it.
        unmatched.forEach { id ->
            val stale = tracked[id] ?: return@forEach
            if (stale.settledEnoughToKeep) return@forEach
            if (stale.missedUpdates + 1 >= FORGET_AFTER_MISSES) tracked.remove(id)
            else tracked[id] = stale.copy(missedUpdates = stale.missedUpdates + 1)
        }
    }

    /** Everything found so far, with the settled ones first because they are actionable. */
    fun objects(): List<SweptObject> = tracked.values
        .map { it.asSweptObject() }
        .sortedWith(
            compareByDescending<SweptObject> { it.progress.settled }
                .thenByDescending { it.detected.dimensions.volumeMm3 },
        )

    fun clear() {
        tracked.clear()
        nextId = 1
    }

    /**
     * Which axes a look from this direction can actually establish.
     *
     * Seeing an object from the front tells you its width and its height. It tells you
     * nothing about its depth — the far face was never in view, and a bounding box drawn
     * around what was seen would report the *visible* extent as though it were the whole
     * thing. Depth only becomes known once it has also been seen from the side.
     */
    private fun axesObservedFrom(directionDegrees: Int): MutableSet<Axis> {
        val normalised = ((directionDegrees % 360) + 360) % 360
        // Height is established from any horizontal viewpoint.
        val axes = mutableSetOf(Axis.HEIGHT)
        val fromFrontOrBack = normalised < 45 || normalised > 315 ||
            (normalised in 135..225)
        if (fromFrontOrBack) axes += Axis.WIDTH else axes += Axis.DEPTH
        return axes
    }

    /** 45° buckets: enough to tell walking round from standing still, coarse enough to be stable. */
    private fun quantise(degrees: Int): Int = (((degrees % 360) + 360) % 360) / 45

    private data class TrackedObject(
        val id: String,
        val latest: DetectedObject,
        val viewDirections: MutableSet<Int>,
        val observedAxes: MutableSet<Axis>,
        val stableUpdates: Int,
        val lastChangeMm: Int,
        /** Consecutive passes in which segmentation did not find this again. */
        val missedUpdates: Int = 0,
    ) {
        /** Measured objects are kept whatever the camera is pointing at now. */
        val settledEnoughToKeep: Boolean
            get() = ObservationProgress(
                viewpoints = viewDirections.size,
                stableUpdates = stableUpdates,
                observedAxes = observedAxes.toSet(),
                lastChangeMm = lastChangeMm,
            ).settled

        fun distanceTo(other: DetectedObject): Int {
            val dx = (latest.centroidXMm - other.centroidXMm).toDouble()
            val dy = (latest.centroidYMm - other.centroidYMm).toDouble()
            return sqrt(dx * dx + dy * dy).toInt()
        }

        fun absorb(candidate: DetectedObject, directionDegrees: Int): TrackedObject {
            val change = maxOf(
                abs(latest.dimensions.widthMm - candidate.dimensions.widthMm),
                abs(latest.dimensions.depthMm - candidate.dimensions.depthMm),
                abs(latest.dimensions.heightMm - candidate.dimensions.heightMm),
            )
            val holding = change <= ObservationProgress.STABLE_THRESHOLD_MM

            val bucket = (((directionDegrees % 360) + 360) % 360) / 45
            viewDirections += bucket

            val normalised = ((directionDegrees % 360) + 360) % 360
            observedAxes += Axis.HEIGHT
            if (normalised < 45 || normalised > 315 || normalised in 135..225) {
                observedAxes += Axis.WIDTH
            } else {
                observedAxes += Axis.DEPTH
            }

            return copy(
                // The larger reading wins. A sweep only ever reveals more of an object, so a
                // smaller measurement means part of it was hidden that time — never that it
                // shrank. Taking the maximum also errs the safe way: too big beats too small.
                latest = if (candidate.dimensions.volumeMm3 > latest.dimensions.volumeMm3 ||
                    candidate.dimensions == latest.dimensions && candidate.cellCount >= latest.cellCount) {
                    candidate
                } else {
                    latest
                },
                stableUpdates = if (holding) stableUpdates + 1 else 0,
                lastChangeMm = change,
            )
        }

        fun asSweptObject() = SweptObject(
            id = id,
            detected = latest,
            progress = ObservationProgress(
                viewpoints = viewDirections.size,
                observedAxes = observedAxes.toSet(),
                stableUpdates = stableUpdates,
                lastChangeMm = lastChangeMm,
            ),
        )
    }
}

/**
 * One object being followed through the sweep, and how far along it is.
 *
 * [waitingFor] is written for a person reading a list on screen while still holding the
 * phone up — it says what to do next, per row, rather than showing an undifferentiated
 * spinner over everything.
 */
data class SweptObject(
    val id: String,
    val detected: DetectedObject,
    val progress: ObservationProgress,
) {
    val settled: Boolean get() = progress.settled

    val waitingFor: String
        get() = when {
            progress.settled -> "Measured"
            progress.stalled && detected.looksMerged ->
                "Can't separate this from what it's touching — move them apart"
            progress.stalled -> "Can't see enough of this one"
            progress.viewpoints < ObservationProgress.MIN_VIEWPOINTS ->
                "Walk round this one"
            progress.observedAxes.size < 2 -> "Needs another angle"
            else -> "Nearly there"
        }

    /**
     * The size, with each axis carrying where it came from, filling only what was never
     * seen. Null when an axis is unobserved and there is nothing to fall back on — at which
     * point the honest thing is to ask, not to invent.
     */
    fun measured(fallback: SuggestedDimensions?): MeasuredDimensions? =
        MeasuredDimensions.fromPartialScan(
            scanned = detected.dimensions,
            observedAxes = progress.observedAxes,
            toleranceMm = detected.toleranceMm,
            fallback = fallback,
        )
}
