package com.packabunch.packing

/**
 * Where a stored length came from. It travels with the number and is shown beside it.
 *
 * Three sources, in descending order of how much they can be trusted, and the distinction
 * is the whole reason provenance exists. A number is not just a number: "I measured this
 * with a tape" and "a catalogue says this model is usually about this big" are different
 * claims, and the second must never be displayed as though it were the first.
 */
enum class MeasurementSource {
    /** The user typed it, from a tape measure or a label. The strongest thing we have. */
    TYPED_IN,

    /** Derived from an AR hit test the user then confirmed. Never a guess, never scored. */
    CAMERA_ESTIMATE,

    /**
     * A looked-up figure for this *kind* of thing, not this thing.
     *
     * A catalogue can say what a Samsung A13 or an IKEA Samla usually measures. It cannot
     * know whether the one in front of you has a case on it, a lid, or a dented corner. So
     * this is a **starting point that must be checked**, it is labelled that way wherever it
     * appears, and it carries [SuggestedDimensions.sourceNote] so the user can judge it:
     * "published internal size" earns trust in a way "found online" does not.
     *
     * It is never silently promoted. Editing the value makes it [TYPED_IN], because at that
     * point it is the user's number.
     */
    SUGGESTED,
}

/**
 * A catalogue's answer about a kind of object.
 *
 * Deliberately a **range**, not a figure. The spread inside a category is usually wider
 * than the tolerance packing needs, and collapsing it to a single number throws away
 * exactly the information that would tell you whether to go and measure.
 */
data class SuggestedDimensions(
    val dimensions: Dimensions,
    /** How far either way the real thing is likely to be, in mm. Zero means an exact spec. */
    val toleranceMm: Int,
    /** Shown to the user. "IKEA SAMLA 22L — published internal size", not "found online". */
    val sourceNote: String,
    val confidence: SuggestionConfidence,
) {
    /**
     * Whether the uncertainty actually changes the answer.
     *
     * This is the rule that decides if the user is interrupted. 30 cm going into 58 cm with
     * ±4 cm of doubt does not need confirming; 57.5 cm going into 58 cm with the same doubt
     * very much does. Tolerance is judged against the fit margin, never in the abstract.
     */
    fun needsConfirming(clearanceMm: Int): Boolean = toleranceMm >= clearanceMm
}

/** How the suggestion was arrived at, which decides how much weight it may be given. */
enum class SuggestionConfidence {
    /** Barcode scan to an exact SKU. A specific product, with published measurements. */
    EXACT_PRODUCT,

    /** Brand and model read from the object, matched to a catalogue entry. */
    MATCHED_MODEL,

    /** Visual category only. A prior with a wide range — never close to a measurement. */
    CATEGORY_ONLY,
}

/**
 * The space being packed into.
 *
 * Two kinds, and which one this is depends on whether [scan] is present:
 *
 *  - **Typed or measured as a rectangle.** One empty box loaded through an open top. Exact,
 *    and the right model for a crate, a drawer or a storage tub.
 *  - **Scanned.** An occupancy grid with obstructions, an opening, and — crucially — a
 *    record of what the scan never saw. This is what a car boot needs, because a boot is
 *    not a rectangle and cannot be honestly described as one.
 *
 * [dimensions] are the **inside** measurements, and for a scan they are the envelope the
 * grid sits in — never the usable volume, which is almost always smaller.
 */
data class Space(
    val id: String,
    val name: String,
    val dimensions: Dimensions,
    /**
     * Breathing room left around the load, in mm. Applied as an inset on all four sides
     * and at the top; the floor is not inset because items rest on it. A visible setting,
     * not a hidden tolerance — and not a calibrated guarantee that anything will fit.
     *
     * Only meaningful for the rectangular case: a scan already knows the real surface, so
     * inflating a margin around an irregular shape would be inventing geometry.
     */
    val edgeGapMm: Int = 0,
    val measurementSource: MeasurementSource = MeasurementSource.TYPED_IN,
    /** Present when this space came from a scan rather than from three numbers. */
    val scan: ScannedSpace? = null,
) {
    val isScanned: Boolean get() = scan != null

    /** The rectangular region the solver may place into. Meaningless for a scanned space. */
    val usableBox: Box
        get() = Box(
            minXMm = edgeGapMm,
            minYMm = edgeGapMm,
            minZMm = 0,
            widthMm = dimensions.widthMm - 2 * edgeGapMm,
            depthMm = dimensions.depthMm - 2 * edgeGapMm,
            heightMm = dimensions.heightMm - edgeGapMm,
        )

    /** The one thing the solver actually talks to. */
    fun volume(): PackingVolume =
        scan?.let { ScannedVolume(it) } ?: RectangularVolume(usableBox)

    val usableVolumeMm3: Long get() = volume().usableVolumeMm3

    /** The way in, when one is known. Absent for an open-top crate. */
    val opening: Opening? get() = scan?.opening
}

/**
 * One kind of thing being packed, with however many copies of it. Dimensions are the
 * item's widest external envelope — handles and anything that sticks out included —
 * because that is the only figure the cuboid model can honestly use.
 */
data class ItemSpec(
    val id: String,
    val name: String,
    val dimensions: Dimensions,
    val quantity: Int = 1,
    /** "Keep it upright" — leaves only the two poses where the item's height points up. */
    val keepUpright: Boolean = false,
    /**
     * Inverse of the "Nothing on top" toggle. False means nothing may be stacked on this
     * item. This is a geometry rule the solver obeys; it is not a load rating, and the
     * engine never infers weight or strength from anything.
     */
    val maySupportItems: Boolean = true,
    val measurementSource: MeasurementSource = MeasurementSource.TYPED_IN,
    /**
     * The item's real form, when a scan measured one.
     *
     * Null means "assume it fills its box", which is right for anything typed in or looked
     * up — we have three numbers and no shape, and inventing one would be worse than
     * admitting it. A scan produces an [ItemShape.VoxelMask] of the actual solid, so a
     * kettle's handle or an L-shaped thing stops reserving the air around it.
     */
    val shape: ItemShape? = null,
) {
    val allowedOrientations: List<Orientation> get() = Orientation.allowedFor(keepUpright)

    /** What the solver collides against: the measured form, or the box if there isn't one. */
    val effectiveShape: ItemShape get() = shape ?: ItemShape.Cuboid(dimensions)

    /**
     * Material rather than envelope.
     *
     * Modelled fill uses this so a shaped item stops counting the air inside its bounding
     * box as packed. A crate holding one L-shaped thing is not two-thirds full.
     */
    val solidVolumeMm3: Long get() = effectiveShape.solidVolumeMm3
}

/**
 * A single physical copy of an [ItemSpec]. Three shoe boxes are three instances that place
 * independently and are packed in three separate steps.
 */
data class ItemInstance(
    val instanceId: String,
    val spec: ItemSpec,
    /** 1-based, so the guide can say "shoe box 2 of 3". */
    val copyIndex: Int,
)

/** One item instance put somewhere, in space coordinates, at a known pose. */
data class Placement(
    val instanceId: String,
    val specId: String,
    val xMm: Int,
    val yMm: Int,
    val zMm: Int,
    val orientedWidthMm: Int,
    val orientedDepthMm: Int,
    val orientedHeightMm: Int,
    val orientation: Orientation,
    /** Position in the packing order, 0-based. Always support-respecting, bottom up. */
    val sequenceIndex: Int,
) {
    val box: Box
        get() = Box(xMm, yMm, zMm, orientedWidthMm, orientedDepthMm, orientedHeightMm)

    val volumeMm3: Long get() = box.volumeMm3
}

/** Why the search did not place something. Never a claim that it cannot be placed. */
enum class UnplacedReason {
    /**
     * No pose of this item fits inside the empty usable space. This one *is* a proof:
     * the item's three sorted edges do not fit the space's three sorted edges.
     */
    LARGER_THAN_THE_SPACE,

    /** This arrangement ran out of room for it. Another arrangement might not. */
    NO_ROOM_IN_THIS_ARRANGEMENT,

    /**
     * There is room inside, but no way in: the item's smallest cross-section is larger
     * than the opening, in every orientation. Reported separately because the fix is
     * completely different — fold the seats, re-measure the tailgate, or leave it out.
     */
    WILL_NOT_FIT_THROUGH_THE_OPENING,

    /** The search hit its time budget before reaching this item. */
    TIME_BUDGET_REACHED,
}

data class UnplacedInstance(
    val instanceId: String,
    val specId: String,
    val name: String,
    val reason: UnplacedReason,
)

/**
 * Numbers the result screen is allowed to show, and nothing else. In particular there is
 * no "efficiency" and no "space saved": the second would need a measured alternative
 * arrangement to compare against, and there isn't one.
 */
data class PackingMetrics(
    val placedInstanceCount: Int,
    val requestedInstanceCount: Int,
    /** Sum of the placed cuboid *envelopes*, not of the real objects inside them. */
    val placedVolumeMm3: Long,
    /** Interior volume minus the edge gap — the denominator for modelled fill. */
    val usableVolumeMm3: Long,
    /** Volume of the smallest box enclosing everything placed. Lower is more compact. */
    val occupiedBoundsVolumeMm3: Long,
) {
    /**
     * Placed envelope volume ÷ usable modelled volume, as a whole percent. The stated
     * basis is what makes this figure honest, so it is labelled "modelled fill"
     * everywhere it appears and never "optimised".
     */
    val modelledFillPercent: Int
        get() = if (usableVolumeMm3 <= 0L) 0
        else ((placedVolumeMm3 * 100L) / usableVolumeMm3).toInt()

    /**
     * Arithmetic remainder only. It is not a promise that another item would fit in it —
     * leftover volume is usually scattered in slivers.
     */
    val geometricEmptyVolumeMm3: Long get() = usableVolumeMm3 - placedVolumeMm3

    val unplacedInstanceCount: Int get() = requestedInstanceCount - placedInstanceCount
}

/**
 * A finished, independently validated arrangement.
 *
 * [inputRevision] fingerprints the space, items and settings it was solved from. Editing
 * any dimension or rotation flag changes the fingerprint, which is how a stale plan is
 * detected and discarded rather than shown against inputs it no longer matches.
 */
data class PackingPlan(
    val spaceId: String,
    val inputRevision: String,
    val solverVersion: Int,
    val placements: List<Placement>,
    val unplaced: List<UnplacedInstance>,
    val metrics: PackingMetrics,
    /** Which deterministic ordering produced this plan. Recorded so results are explainable. */
    val strategy: String,
    /** True when the search stopped on its time budget rather than exhausting the orderings. */
    val stoppedOnTimeBudget: Boolean,
)

/** Everything the solver needs. Nothing here is Android-specific by design. */
data class PackingRequest(
    val space: Space,
    val items: List<ItemSpec>,
) {
    fun expandInstances(): List<ItemInstance> = items.flatMap { spec ->
        (1..spec.quantity).map { copy ->
            ItemInstance(
                instanceId = "${spec.id}#$copy",
                spec = spec,
                copyIndex = copy,
            )
        }
    }

    /**
     * Stable fingerprint of every input that can change the answer. Deliberately not
     * `hashCode()`: this value is persisted next to a plan, so it has to mean the same
     * thing in a later process on a later version of the runtime.
     */
    fun revision(): String {
        val canonical = buildString {
            append(space.id).append('|')
            append(space.dimensions.widthMm).append(',')
            append(space.dimensions.depthMm).append(',')
            append(space.dimensions.heightMm).append(',')
            append(space.edgeGapMm).append('|')
            items.sortedBy { it.id }.forEach { item ->
                append(item.id).append(':')
                append(item.dimensions.widthMm).append(',')
                append(item.dimensions.depthMm).append(',')
                append(item.dimensions.heightMm).append(',')
                append(item.quantity).append(',')
                append(if (item.keepUpright) '1' else '0')
                append(if (item.maySupportItems) '1' else '0')
                append(';')
            }
        }
        return fnv1a64(canonical)
    }
}

/** FNV-1a, 64-bit. Small, dependency-free and stable across processes and releases. */
private fun fnv1a64(input: String): String {
    var hash = -0x340d631b7bdddcdbL // 14695981039346656037 unsigned
    for (byte in input.encodeToByteArray()) {
        hash = hash xor (byte.toLong() and 0xFF)
        hash *= 0x100000001B3L
    }
    return hash.toULong().toString(16).padStart(16, '0')
}

/** What the user typed cannot be solved from. Drives the inline "check this" states. */
data class InputProblem(
    val field: String,
    val message: String,
)

sealed interface SolveResult {
    data class Solved(val plan: PackingPlan) : SolveResult

    /** Bad input, reported per field. No plan is invented from unusable numbers. */
    data class InvalidInput(val problems: List<InputProblem>) : SolveResult
}
