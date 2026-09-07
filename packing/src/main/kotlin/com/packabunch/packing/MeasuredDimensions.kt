package com.packabunch.packing

/**
 * A size where each of the three lengths knows separately where it came from.
 *
 * ### Why per axis rather than per object
 *
 * Push a box against a wall and sweep it. You get its width and its height cleanly, from
 * several angles, and you never once see its back — so its depth is unobserved. With a
 * single source per object there are only two things you can do with that, and both are
 * wrong: throw away two good measurements and treat the whole object as a guess, or keep
 * all three and quietly present an estimated depth as though it had been measured.
 *
 * Splitting provenance by axis makes the honest answer expressible: *width and height were
 * measured, depth came from a catalogue, and here is which is which.* That is also the
 * common case rather than an edge case — most things people pack are against something.
 *
 * It cannot be added later without touching every screen that displays a dimension, which
 * is why it exists before those screens are finished.
 */
data class MeasuredLength(
    val millimetres: Int,
    val source: MeasurementSource,
    /** How far either way this could be. Zero for a figure somebody typed. */
    val toleranceMm: Int,
    /** Shown next to the number. "Measured from your scan", "IKEA SAMLA — published size". */
    val note: String? = null,
) {
    val isMeasured: Boolean get() = source == MeasurementSource.CAMERA_ESTIMATE

    val isGuess: Boolean get() = source == MeasurementSource.SUGGESTED

    companion object {
        fun typed(millimetres: Int) = MeasuredLength(
            millimetres = millimetres,
            source = MeasurementSource.TYPED_IN,
            toleranceMm = 0,
        )

        fun measured(millimetres: Int, toleranceMm: Int, note: String? = null) = MeasuredLength(
            millimetres = millimetres,
            source = MeasurementSource.CAMERA_ESTIMATE,
            toleranceMm = toleranceMm,
            note = note,
        )

        fun suggested(millimetres: Int, toleranceMm: Int, note: String) = MeasuredLength(
            millimetres = millimetres,
            source = MeasurementSource.SUGGESTED,
            toleranceMm = toleranceMm,
            note = note,
        )
    }
}

/**
 * Three lengths, each with its own provenance.
 *
 * [asDimensions] is what the solver receives — it only ever needed the numbers. Everything
 * about where they came from is for the person deciding whether to trust the plan, which is
 * a different question from whether the geometry is valid.
 */
data class MeasuredDimensions(
    val width: MeasuredLength,
    val depth: MeasuredLength,
    val height: MeasuredLength,
) {
    fun asDimensions(): Dimensions = Dimensions(
        widthMm = width.millimetres,
        depthMm = depth.millimetres,
        heightMm = height.millimetres,
    )

    val axes: List<MeasuredLength> get() = listOf(width, depth, height)

    /** True when every axis was actually observed. The clean case. */
    val fullyMeasured: Boolean get() = axes.all { it.isMeasured }

    /** The axes that were filled in rather than seen. Named so the UI can point at them. */
    fun estimatedAxes(): List<String> = buildList {
        if (width.isGuess) add("width")
        if (depth.isGuess) add("depth")
        if (height.isGuess) add("height")
    }

    /**
     * The worst uncertainty across the three, because a box is only as well known as its
     * vaguest edge. A perfectly measured width does not rescue a guessed depth.
     */
    val toleranceMm: Int get() = axes.maxOf { it.toleranceMm }

    /**
     * How this reads on a card. Deliberately not a single badge: an object with two
     * measured axes and one guessed one is neither "measured" nor "a guess", and calling it
     * either would be a small lie repeated on every screen.
     */
    val summary: Provenance
        get() = when {
            axes.all { it.source == MeasurementSource.TYPED_IN } -> Provenance.TYPED
            fullyMeasured -> Provenance.MEASURED
            axes.none { it.isMeasured } -> Provenance.ESTIMATED
            else -> Provenance.PARTLY_MEASURED
        }

    enum class Provenance { TYPED, MEASURED, PARTLY_MEASURED, ESTIMATED }

    companion object {
        fun allTyped(dimensions: Dimensions) = MeasuredDimensions(
            width = MeasuredLength.typed(dimensions.widthMm),
            depth = MeasuredLength.typed(dimensions.depthMm),
            height = MeasuredLength.typed(dimensions.heightMm),
        )

        /**
         * Builds a size from a partial scan, filling only the axes that were never seen.
         *
         * This is the case the whole type exists for. [observedAxes] comes from the scan —
         * an axis is observed when the sweep actually saw both faces along it, not merely
         * when a bounding box happens to have an extent there.
         *
         * If an axis was not observed and there is no fallback for it, the caller gets null
         * rather than a made-up number.
         */
        fun fromPartialScan(
            scanned: Dimensions,
            observedAxes: Set<Axis>,
            toleranceMm: Int,
            fallback: SuggestedDimensions?,
        ): MeasuredDimensions? {
            fun axis(
                which: Axis,
                scannedMm: Int,
                fallbackMm: Int?,
            ): MeasuredLength? = when {
                which in observedAxes -> MeasuredLength.measured(
                    millimetres = scannedMm,
                    toleranceMm = toleranceMm,
                    note = "Measured from your scan",
                )

                fallbackMm != null && fallback != null -> MeasuredLength.suggested(
                    millimetres = fallbackMm,
                    toleranceMm = fallback.toleranceMm,
                    note = "${fallback.sourceNote} — this side was never in view",
                )

                else -> null
            }

            val width = axis(Axis.WIDTH, scanned.widthMm, fallback?.dimensions?.widthMm)
            val depth = axis(Axis.DEPTH, scanned.depthMm, fallback?.dimensions?.depthMm)
            val height = axis(Axis.HEIGHT, scanned.heightMm, fallback?.dimensions?.heightMm)

            if (width == null || depth == null || height == null) return null
            return MeasuredDimensions(width, depth, height)
        }
    }
}

/**
 * How much of an object the sweep actually saw, and whether it has stopped changing.
 *
 * Each object being scanned carries one of these and settles on its own schedule. A shoe
 * box in the open finishes in a couple of seconds; the thing jammed behind the sofa is
 * still waiting for somebody to walk round it. That is what makes one continuous pass work
 * for a room full of things rather than forcing a scan-one-then-the-next rhythm.
 */
data class ObservationProgress(
    /** Distinct viewing directions this object has been seen from. */
    val viewpoints: Int,
    /** Axes seen from both sides, and therefore genuinely measured rather than assumed. */
    val observedAxes: Set<Axis>,
    /** How many consecutive updates the fitted box has held still. */
    val stableUpdates: Int,
    /** Largest change in any edge over the last update, in mm. */
    val lastChangeMm: Int,
) {
    /**
     * Settled means: seen from enough angles, dimensions no longer moving, and at least two
     * axes genuinely observed.
     *
     * Two rather than three on purpose. Waiting for all three would mean anything against a
     * wall never finishes, and that is most of what people pack — the third axis is what
     * the fallback ladder is for.
     */
    val settled: Boolean
        get() = viewpoints >= MIN_VIEWPOINTS &&
            stableUpdates >= MIN_STABLE_UPDATES &&
            observedAxes.size >= 2

    /**
     * Stalled means it has been looked at but is not getting any better — usually something
     * is in the way. The UI should stop implying that more sweeping will help and offer the
     * fallback instead.
     */
    val stalled: Boolean
        get() = !settled && stableUpdates >= STALL_UPDATES

    val unobservedAxes: Set<Axis> get() = Axis.entries.toSet() - observedAxes

    companion object {
        const val MIN_VIEWPOINTS = 3
        const val MIN_STABLE_UPDATES = 4
        const val STALL_UPDATES = 12

        /** An edge that moves less than this between updates counts as having settled. */
        const val STABLE_THRESHOLD_MM = 8
    }
}
