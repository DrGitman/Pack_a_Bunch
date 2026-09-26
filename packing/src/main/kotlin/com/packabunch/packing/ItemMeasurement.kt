package com.packabunch.packing

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot

/** What to ask the person to do when a side has not been seen yet. */
enum class AngleHint {
    /** The camera is low — raising it and looking down shows the top, which pins two lengths at once. */
    TILT_DOWN,

    /** The camera is already above the object — one side is just out of view. */
    STEP_AROUND,
}

enum class CannotMeasureReason {
    /** The camera is producing no depth for this object — too dark, too shiny, too far. */
    NO_DEPTH,

    /** Smaller than depth-from-motion can resolve. Type it in. */
    TOO_SMALL,

    /** Too thin to lift off the surface. The footprint is still offered as a prefill. */
    TOO_FLAT,
}

/**
 * Where one object's measurement stands. These map one-to-one onto the outline styles in the
 * Figma frame `ItemScan · Outline states`:
 *
 * | state            | outline                                              |
 * |------------------|------------------------------------------------------|
 * | [Scanning]       | white 95 %, 1.7 px, dashed 6/4, soft glow            |
 * | [NeedsAngle]     | white, with only the edges along [unseen] amber, dotted |
 * | [Measured]       | white 2.2 px solid, glow, draw-on 400 ms, 16 % fill  |
 * | [CannotMeasure]  | no outline; a tag with "Type it"                     |
 */
sealed interface ItemScanState {
    /** Still gathering. [fit] is null until enough of the surface has been confirmed to fit anything. */
    data class Scanning(val fit: FittedObject?, val progress: Float) : ItemScanState

    /** Stopped changing, but at least one length was never in view. */
    data class NeedsAngle(
        val fit: FittedObject,
        val hint: AngleHint,
        val unseen: Set<Axis>,
        val progress: Float,
    ) : ItemScanState

    /** Every length seen and holding still. Sticky: nothing later un-measures it. */
    data class Measured(val fit: FittedObject, val dimensions: MeasuredDimensions) : ItemScanState

    data class CannotMeasure(val reason: CannotMeasureReason, val prefill: Dimensions?) : ItemScanState
}

/**
 * Measures one object from a stream of depth frames.
 *
 * Feed it each frame's points for this object (already in the support surface's frame) and
 * the camera position; read [state] back. It decides *measured* on evidence alone:
 *
 * 1. **Every length seen.** Not "the phone visited three compass directions" — each of width,
 *    depth and height must be backed by a face a camera actually looked at (see
 *    [ShapeFitter]). Round things are the exception that proves the rule: a wide enough arc
 *    determines the diameter, so a cup does not need to be walked around.
 * 2. **Holding still.** The last [STABLE_WINDOW] fits agree within
 *    `max(6 mm, 1.5 voxels, 4 %)` on every length. That threshold sits *above* the voxel size
 *    on purpose; the old one (8 mm against 20 mm voxels) could never be met.
 *
 * Once measured, the result is frozen. Dimensions are the median of the stable window, and
 * the tolerance is what that window actually varied by — never a made-up number.
 */
class ItemMeasurement(voxelMm: Float = ObjectCloud.DEFAULT_VOXEL_MM) {

    val cloud = ObjectCloud(voxelMm)
    private val cameras = ArrayList<PlanePoint>()
    private val window = ArrayDeque<FittedObject>()
    private var emptyFrames = 0

    /** The latest fit, stable or not. What the live 3D preview draws. */
    var lastFit: FittedObject? = null
        private set

    /** Share of the object's visible surface confirmed so far (0–1+). For the coverage ring. */
    var lastCoverage: Float = 0f
        private set

    var state: ItemScanState = ItemScanState.Scanning(null, 0f)
        private set

    val isMeasured: Boolean get() = state is ItemScanState.Measured

    /** Cameras the fit has been judged against, decimated. */
    val cameraCount: Int get() = cameras.size

    /**
     * One frame. [points] may be empty — that is itself information (no depth here).
     * Returns the new [state].
     */
    fun addFrame(points: List<PlanePoint>, camera: PlanePoint): ItemScanState {
        if (isMeasured && framesSinceMeasured++ >= REFINE_FRAMES) return state
        recordCamera(camera)
        if (points.isEmpty()) emptyFrames++ else emptyFrames = 0
        cloud.add(points)
        val before = state
        val after = refit(camera)
        // Measured is sticky. For a short while afterwards the numbers may still sharpen as the
        // last edges fill in, but nothing un-measures an object.
        if (before is ItemScanState.Measured && after !is ItemScanState.Measured) state = before
        return state
    }

    private var framesSinceMeasured = 0

    /** Takes over another measurement of the same object — two tracks that turned out to be one. */
    fun absorb(other: ItemMeasurement) {
        if (isMeasured) return
        cloud.absorb(other.cloud)
        for (c in other.cameras) recordCamera(c)
        window.clear()
        cameras.lastOrNull()?.let { refit(it) }
    }

    /**
     * The honest answer for an object that could not be walked around: measured lengths where
     * they were seen, the [fallback]'s where they were not, or null if there is no fallback
     * for a missing length.
     */
    fun partial(fallback: SuggestedDimensions?): MeasuredDimensions? {
        (state as? ItemScanState.Measured)?.let { return it.dimensions }
        val fit = lastFit ?: return null
        return MeasuredDimensions.fromPartialScan(
            scanned = fit.dimensions,
            observedAxes = fit.observedAxes,
            toleranceMm = toleranceOf(window.toList()),
            fallback = fallback,
        )
    }

    fun reset() {
        window.clear()
        cameras.clear()
        emptyFrames = 0
        framesSinceMeasured = 0
        lastFit = null
        state = ItemScanState.Scanning(null, 0f)
    }

    // ---------------------------------------------------------------------------------------

    private fun recordCamera(c: PlanePoint) {
        val last = cameras.lastOrNull()
        if (last != null && hypot(hypot(c.xMm - last.xMm, c.yMm - last.yMm), c.hMm - last.hMm) < CAMERA_SPACING_MM) return
        cameras += c
        if (cameras.size > MAX_CAMERAS) {
            // Thin evenly rather than dropping the oldest: the first side seen still counts.
            val kept = cameras.filterIndexed { i, _ -> i % 2 == 0 }
            cameras.clear(); cameras += kept
        }
    }

    private fun refit(camera: PlanePoint): ItemScanState {
        val snap = cloud.snapshot()
        val points = snap.points
        if (points.size < ShapeFitter.MIN_POINTS) {
            state = if (emptyFrames >= NO_DEPTH_FRAMES && cloud.size == 0) {
                ItemScanState.CannotMeasure(CannotMeasureReason.NO_DEPTH, null)
            } else {
                ItemScanState.Scanning(null, 0.05f)
            }
            return state
        }
        val fit = ShapeFitter.fit(points, cameras, cloud.voxelMm, snap.weights) ?: return state
        lastFit = fit
        window.addLast(fit)
        while (window.size > STABLE_WINDOW) window.removeFirst()

        val stable = window.size == STABLE_WINDOW && isStable(window)
        val stableFraction = if (stable) 1f else countStableTail() / STABLE_WINDOW.toFloat()
        val coverage = coverageOf(fit)
        lastCoverage = coverage
        val progress = (
            (coverage / MIN_COVERAGE).coerceAtMost(1f) * 0.45f +
                fit.observedAxes.size / 3f * 0.35f +
                stableFraction * 0.2f
            ).coerceIn(0.05f, 0.98f)

        if (!stable || cloud.framesAdded < MIN_FRAMES) {
            state = ItemScanState.Scanning(fit, progress)
            return state
        }

        val median = medianFit(window.toList())
        val maxDim = maxOf(median.widthMm, median.depthMm, median.heightMm)
        when {
            maxDim < TOO_SMALL_MM -> {
                state = ItemScanState.CannotMeasure(CannotMeasureReason.TOO_SMALL, median.dimensions)
                return state
            }
            median.heightMm < TOO_FLAT_MM -> {
                state = ItemScanState.CannotMeasure(CannotMeasureReason.TOO_FLAT, median.dimensions)
                return state
            }
        }

        val unseen = Axis.entries.toSet() - median.observedAxes
        // Every side seen but the surface still thin: keep scanning, the edges are still filling in.
        if (unseen.isEmpty() && coverage < MIN_COVERAGE) {
            state = ItemScanState.Scanning(fit, progress)
            return state
        }
        state = if (unseen.isEmpty()) {
            val tolerance = toleranceOf(window.toList())
            val d = median.dimensions
            ItemScanState.Measured(
                fit = median,
                dimensions = MeasuredDimensions(
                    width = MeasuredLength.measured(d.widthMm, tolerance, "Measured from your scan"),
                    depth = MeasuredLength.measured(d.depthMm, tolerance, "Measured from your scan"),
                    height = MeasuredLength.measured(d.heightMm, tolerance, "Measured from your scan"),
                ),
            )
        } else {
            ItemScanState.NeedsAngle(median, hintFor(median, camera), unseen, progress)
        }
        return state
    }

    /**
     * Confirmed surface as a share of the fitted shape's visible surface (everything but the
     * bottom). Stops a sparse early cloud — whose edges have not filled in, so every length
     * reads short — from looking "stable" just because it is growing slowly.
     */
    private fun coverageOf(fit: FittedObject): Float {
        val v2 = cloud.voxelMm * cloud.voxelMm
        val area = when (fit.shape) {
            ShapeFamily.CYLINDER, ShapeFamily.TAPERED -> {
                val rt = fit.topRadiusMm ?: (fit.widthMm / 2); val rb = fit.bottomRadiusMm ?: rt
                Math.PI.toFloat() * (rt * rt + (rt + rb) * fit.heightMm)
            }
            ShapeFamily.SPHERE -> Math.PI.toFloat() * fit.widthMm * fit.heightMm
            else -> fit.widthMm * fit.depthMm + 2 * (fit.widthMm + fit.depthMm) * fit.heightMm
        }
        return if (area <= 0f) 0f else fit.pointCount * v2 / area
    }

    private fun hintFor(fit: FittedObject, camera: PlanePoint): AngleHint {
        val horizontal = hypot(camera.xMm - fit.centreXMm, camera.yMm - fit.centreYMm)
        val above = camera.hMm - fit.heightMm
        val elevationDeg = Math.toDegrees(atan2(above.toDouble(), horizontal.toDouble()))
        return if (elevationDeg < TILT_DOWN_BELOW_DEG && Axis.HEIGHT in fit.observedAxes) AngleHint.TILT_DOWN else AngleHint.STEP_AROUND
    }

    private fun threshold(valueMm: Float) = maxOf(6f, 1.5f * cloud.voxelMm, 0.04f * valueMm)

    private fun isStable(fits: Collection<FittedObject>): Boolean {
        val w = fits.map { it.widthMm }; val d = fits.map { it.depthMm }; val h = fits.map { it.heightMm }
        val shapeAgrees = fits.map { it.observedAxes }.toSet().size == 1
        // Still creeping outward as the edges fill in is not "still", even if each step is small.
        val drift = fits.first().let { a -> fits.last().let { b ->
            abs(b.widthMm - a.widthMm) <= threshold(b.widthMm) / 2 &&
                abs(b.depthMm - a.depthMm) <= threshold(b.depthMm) / 2 &&
                abs(b.heightMm - a.heightMm) <= threshold(b.heightMm) / 2
        } }
        return shapeAgrees && drift &&
            w.max() - w.min() <= threshold(w.average().toFloat()) &&
            d.max() - d.min() <= threshold(d.average().toFloat()) &&
            h.max() - h.min() <= threshold(h.average().toFloat())
    }

    /** How many of the most recent fits already agree — drives the progress ring. */
    private fun countStableTail(): Int {
        val list = window.toList()
        var k = 1
        while (k < list.size && isStable(list.subList(list.size - k - 1, list.size))) k++
        return if (list.isEmpty()) 0 else k
    }

    private fun toleranceOf(fits: List<FittedObject>): Int {
        if (fits.isEmpty()) return ceil(2 * cloud.voxelMm).toInt()
        val spread = listOf(
            fits.maxOf { it.widthMm } - fits.minOf { it.widthMm },
            fits.maxOf { it.depthMm } - fits.minOf { it.depthMm },
            fits.maxOf { it.heightMm } - fits.minOf { it.heightMm },
        ).max()
        val largest = fits.last().let { maxOf(it.widthMm, it.depthMm, it.heightMm) }
        // Half a voxel either side is the floor of what the cloud can resolve; 1 % is the floor
        // of what depth-from-motion can resolve at arm's length.
        return ceil(maxOf(spread, cloud.voxelMm, 0.01f * largest)).toInt()
    }

    private fun medianFit(fits: List<FittedObject>): FittedObject {
        fun med(v: List<Float>) = v.sorted().let { if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }
        val last = fits.last()
        return last.copy(
            widthMm = med(fits.map { it.widthMm }),
            depthMm = med(fits.map { it.depthMm }),
            heightMm = med(fits.map { it.heightMm }),
        )
    }

    companion object {
        const val STABLE_WINDOW = 5
        const val MIN_FRAMES = 8

        /** Frames after first reaching Measured during which the numbers may still sharpen. */
        const val REFINE_FRAMES = 30

        /** See [coverageOf]. A box seen from the front and above covers about half its surface. */
        const val MIN_COVERAGE = 0.30f
        const val NO_DEPTH_FRAMES = 25
        const val TOO_SMALL_MM = 45f
        const val TOO_FLAT_MM = 15f
        const val TILT_DOWN_BELOW_DEG = 25.0
        private const val CAMERA_SPACING_MM = 30f
        private const val MAX_CAMERAS = 240
    }
}
