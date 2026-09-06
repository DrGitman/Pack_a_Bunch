package com.packabunch.packing

/**
 * A bounded, deterministic packing heuristic.
 *
 * What it is: several fixed orderings of the items, each packed greedily into the lowest
 * feasible corner, with the best independently validated result returned. Same inputs,
 * same answer, every time.
 *
 * What it is **not**: a proof of anything. It does not find the optimal arrangement, and
 * failing to place an item only means this search found no room — which is why
 * [UnplacedReason] separates "larger than the space", which really is a proof, from
 * "no room in this arrangement", which is not. Nothing here may be surfaced as
 * "impossible", "won't fit" or "optimised".
 *
 * The engine is pure: no Android classes, no I/O, no clock it does not own. Run it off the
 * UI thread and hand it a [SolveBudget] wired to the calling job's cancellation.
 */
object PackingEngine {

    /** Bump when the geometry or the objective changes, so stored plans can be spotted as stale. */
    const val SOLVER_VERSION: Int = 1

    /**
     * The count the published performance figure is measured at. Not a limit — the engine
     * solves well past it. It exists so there is one fixed size to benchmark against on a
     * real phone rather than a number quoted from nowhere.
     */
    const val BENCHMARK_INSTANCE_COUNT: Int = 20

    /**
     * Technical ceiling, not a product one.
     *
     * Product limits belong to [Tier], not here: the engine must not know what somebody
     * paid. This number exists only so a mistyped quantity cannot start a search that
     * allocates unboundedly. Past it, the honest answer is not a worse plan but no plan.
     *
     * Above roughly a hundred pieces the search still returns — the time budget guarantees
     * that — but it returns a best-effort partial answer, and items it never reached are
     * reported as [UnplacedReason.TIME_BUDGET_REACHED] rather than passed off as unplaceable.
     */
    const val MAX_INSTANCE_COUNT: Int = 400

    private const val MAX_QUANTITY: Int = 199

    fun solve(request: PackingRequest, budget: SolveBudget = SolveBudget()): SolveResult {
        val problems = validateInput(request)
        if (problems.isNotEmpty()) return SolveResult.InvalidInput(problems)

        val instances = request.expandInstances()
        val volume = request.space.volume()
        val opening = request.space.opening
        val supportLookup = request.items.associate { it.id to it.maySupportItems }

        // Two separations before the search, both of which are proofs rather than search
        // failures, and both of which the user is told about differently.
        //
        // Too big for the space at all — no pose of it fits anywhere inside.
        val oversize = instances.filter {
            volume.couldNeverHold(it.spec.dimensions, it.spec.allowedOrientations)
        }
        // Fits inside, but will not pass through the way in. "There's room inside, but no
        // way in" is a different problem with different fixes, so it is a different reason.
        val blockedByOpening = instances.filter { instance ->
            instance !in oversize &&
                opening?.admits(instance.spec.dimensions)?.passes == false
        }

        val excluded = (oversize + blockedByOpening).mapTo(mutableSetOf()) { it.instanceId }
        val packable = instances.filter { it.instanceId !in excluded }

        var orderingsTried = 0
        val attempts = buildList {
            for (ordering in Ordering.entries) {
                if (budget.isExhausted()) break
                orderingsTried++
                val attempt = packGreedily(request, ordering, packable, supportLookup, budget)
                // A plan that fails its own validator is a bug in this file. Drop it rather
                // than show it: an arrangement that overlaps or floats is worse than none.
                val candidate = attempt.asPlan(request, oversize, blockedByOpening)
                if (PlanValidator.validate(request, candidate).isValid) {
                    add(attempt)
                }
            }
        }

        val stoppedOnBudget = orderingsTried < Ordering.entries.size ||
            attempts.any { it.stoppedOnTimeBudget }

        val best = attempts.minWithOrNull(BEST_FIRST)
            ?: Attempt(
                ordering = Ordering.LARGEST_VOLUME_FIRST,
                placements = emptyList(),
                unreached = packable.map { it.instanceId }.toSet(),
                stoppedOnTimeBudget = true,
            )

        return SolveResult.Solved(
            best.asPlan(request, oversize, blockedByOpening, stoppedOnBudget),
        )
    }

    // -- input ----------------------------------------------------------------------------

    private fun validateInput(request: PackingRequest): List<InputProblem> {
        val problems = mutableListOf<InputProblem>()
        val space = request.space

        if (!space.dimensions.isValid()) {
            problems += InputProblem(
                field = "space",
                message = "Every inside measurement needs to be between " +
                    "$MIN_LENGTH_MM mm and $MAX_LENGTH_MM mm.",
            )
        }
        if (space.edgeGapMm < 0) {
            problems += InputProblem("space.edgeGap", "The edge gap cannot be negative.")
        }
        if (problems.isEmpty()) {
            val usable = space.usableBox
            if (usable.widthMm <= 0 || usable.depthMm <= 0 || usable.heightMm <= 0) {
                problems += InputProblem(
                    field = "space.edgeGap",
                    message = "An edge gap of ${space.edgeGapMm} mm leaves no room inside " +
                        "this space.",
                )
            }
        }

        request.items.forEach { item ->
            if (!item.dimensions.isValid()) {
                problems += InputProblem(
                    field = "item:${item.id}",
                    message = "${item.name}: each measurement needs to be between " +
                        "$MIN_LENGTH_MM mm and $MAX_LENGTH_MM mm.",
                )
            }
            if (item.quantity !in 1..MAX_QUANTITY) {
                problems += InputProblem(
                    field = "item:${item.id}.quantity",
                    message = "${item.name}: how many needs to be between 1 and $MAX_QUANTITY.",
                )
            }
        }

        val duplicateIds = request.items.groupingBy { it.id }.eachCount().filterValues { it > 1 }
        duplicateIds.keys.forEach { id ->
            problems += InputProblem("item:$id", "Two items share the id $id.")
        }

        val total = request.items.sumOf { it.quantity.coerceIn(0, MAX_QUANTITY) }
        if (total > MAX_INSTANCE_COUNT) {
            problems += InputProblem(
                field = "items",
                message = "That is $total pieces, past what the planner can search " +
                    "($MAX_INSTANCE_COUNT). Split it into more than one pack.",
            )
        }

        return problems
    }

    // -- search ----------------------------------------------------------------------------

    /**
     * The fixed orderings the search tries. Different shapes reward different ones, which
     * is the whole reason more than one is tried — there is a fixture where the first
     * ordering fails and another succeeds on the same items.
     */
    enum class Ordering(val label: String) {
        LARGEST_VOLUME_FIRST("largest volume first"),
        LONGEST_EDGE_FIRST("longest edge first"),
        LARGEST_FOOTPRINT_FIRST("largest footprint first"),
        TALLEST_FIRST("tallest first");

        fun sort(instances: List<ItemInstance>): List<ItemInstance> = when (this) {
            LARGEST_VOLUME_FIRST -> instances.sortedWith(
                compareByDescending<ItemInstance> { it.spec.dimensions.volumeMm3 }
                    .thenBy { it.instanceId },
            )
            LONGEST_EDGE_FIRST -> instances.sortedWith(
                compareByDescending<ItemInstance> { it.spec.dimensions.longestEdgeMm }
                    .thenByDescending { it.spec.dimensions.volumeMm3 }
                    .thenBy { it.instanceId },
            )
            LARGEST_FOOTPRINT_FIRST -> instances.sortedWith(
                compareByDescending<ItemInstance> {
                    it.spec.dimensions.widthMm.toLong() * it.spec.dimensions.depthMm.toLong()
                }
                    .thenByDescending { it.spec.dimensions.heightMm }
                    .thenBy { it.instanceId },
            )
            TALLEST_FIRST -> instances.sortedWith(
                compareByDescending<ItemInstance> { it.spec.dimensions.heightMm }
                    .thenByDescending { it.spec.dimensions.volumeMm3 }
                    .thenBy { it.instanceId },
            )
        }
    }

    private data class Attempt(
        val ordering: Ordering,
        val placements: List<Placement>,
        /** Instances the search never reached, because the budget ran out mid-attempt. */
        val unreached: Set<String>,
        val stoppedOnTimeBudget: Boolean,
    ) {
        val placedVolumeMm3: Long get() = placements.sumOf { it.volumeMm3 }
    }

    /**
     * Ranking, in order: most pieces placed, then most volume placed, then the most
     * compact result (smallest enclosing box), then the earliest ordering so ties are
     * reproducible. This objective is written down because it is a choice, not a law —
     * it is not the same thing as a proven optimum.
     */
    private val BEST_FIRST = compareByDescending<Attempt> { it.placements.size }
        .thenByDescending { it.placedVolumeMm3 }
        .thenBy { boundsVolume(it.placements) }
        .thenBy { it.ordering.ordinal }

    private fun boundsVolume(placements: List<Placement>): Long {
        if (placements.isEmpty()) return 0L
        val w = placements.maxOf { it.box.maxXMm } - placements.minOf { it.box.minXMm }
        val d = placements.maxOf { it.box.maxYMm } - placements.minOf { it.box.minYMm }
        val h = placements.maxOf { it.box.maxZMm } - placements.minOf { it.box.minZMm }
        return w.toLong() * d.toLong() * h.toLong()
    }

    private fun packGreedily(
        request: PackingRequest,
        ordering: Ordering,
        instances: List<ItemInstance>,
        supportLookup: Map<String, Boolean>,
        budget: SolveBudget,
    ): Attempt {
        val volume = request.space.volume()
        val placed = mutableListOf<Placement>()
        val unreached = mutableSetOf<String>()

        // Starting positions come from the space itself: one corner for a crate, every
        // resting surface for a scan. Placing an item then exposes three more corners.
        var candidates = volume.seedPositions()
        var hitBudget = false

        for (instance in ordering.sort(instances)) {
            if (hitBudget || budget.isExhausted()) {
                hitBudget = true
                unreached += instance.instanceId
                continue
            }

            // No fit found is not an error and not a failure of the item — it is recorded
            // against the finished plan as "no room in this arrangement".
            val choice = bestFitFor(instance, candidates, placed, volume, supportLookup) ?: continue

            placed += Placement(
                instanceId = instance.instanceId,
                specId = instance.spec.id,
                xMm = choice.corner.xMm,
                yMm = choice.corner.yMm,
                zMm = choice.corner.zMm,
                orientedWidthMm = choice.oriented.widthMm,
                orientedDepthMm = choice.oriented.depthMm,
                orientedHeightMm = choice.oriented.heightMm,
                orientation = choice.orientation,
                sequenceIndex = 0, // assigned bottom-up once the attempt is finished
            )
            candidates = expandCorners(candidates, choice, volume, placed)
        }

        return Attempt(
            ordering = ordering,
            placements = inPackingOrder(placed),
            unreached = unreached,
            stoppedOnTimeBudget = hitBudget,
        )
    }

    private data class Choice(
        val corner: Position,
        val orientation: Orientation,
        val oriented: Dimensions,
    ) {
        val box: Box
            get() = Box(
                minXMm = corner.xMm,
                minYMm = corner.yMm,
                minZMm = corner.zMm,
                widthMm = oriented.widthMm,
                depthMm = oriented.depthMm,
                heightMm = oriented.heightMm,
            )
    }

    /**
     * Lowest resulting top edge wins, then lowest base, then furthest forward, then
     * furthest left, then the earliest pose. Keeping the load low is the objective; the
     * rest is tie-breaking that exists so the answer is reproducible.
     */
    private fun bestFitFor(
        instance: ItemInstance,
        candidates: List<Position>,
        placed: List<Placement>,
        volume: PackingVolume,
        supportLookup: Map<String, Boolean>,
    ): Choice? {
        var best: Choice? = null
        var bestKey: IntArray? = null

        for (corner in candidates) {
            for (orientation in instance.spec.allowedOrientations) {
                val oriented = orientation.apply(instance.spec.dimensions)
                val choice = Choice(corner, orientation, oriented)
                val box = choice.box

                if (!volume.admits(box)) continue
                if (placed.any { it.box.overlaps(box) }) continue
                if (!isSupported(box, placed, volume, supportLookup)) continue

                val key = intArrayOf(
                    box.maxZMm,
                    corner.zMm,
                    corner.yMm,
                    corner.xMm,
                    orientation.ordinal,
                )
                val incumbent = bestKey
                if (incumbent == null || compareKeys(key, incumbent) < 0) {
                    best = choice
                    bestKey = key
                }
            }
        }
        return best
    }

    private fun compareKeys(a: IntArray, b: IntArray): Int {
        for (i in a.indices) {
            val c = a[i].compareTo(b[i])
            if (c != 0) return c
        }
        return 0
    }

    /**
     * The conservative support rule: an item rests on the floor, or its whole base sits on
     * the top face of exactly one item that is allowed to carry something. Partial support
     * spanning two neighbours is refused even though it would often work in reality —
     * this is a geometry rule, not a load rating, and it errs towards arrangements a
     * person can actually build without the stack sliding apart.
     */
    private fun isSupported(
        box: Box,
        placed: List<Placement>,
        volume: PackingVolume,
        supportLookup: Map<String, Boolean>,
    ): Boolean {
        // Carried by the space itself: the floor of a crate, or the sloping floor and
        // wheel arches of a scanned boot, checked column by column.
        if (volume.restsOnStructure(box)) return true

        val supporter = placed.firstOrNull { candidate ->
            candidate.box.maxZMm == box.minZMm && candidate.box.coversFootprintOf(box)
        } ?: return false
        return supportLookup[supporter.specId] ?: true
    }

    private fun expandCorners(
        existing: List<Position>,
        choice: Choice,
        volume: PackingVolume,
        placed: List<Placement>,
    ): List<Position> {
        val bounds = volume.boundsMm
        val box = choice.box
        val grown = existing + listOf(
            Position(box.maxXMm, box.minYMm, box.minZMm),
            Position(box.minXMm, box.maxYMm, box.minZMm),
            Position(box.minXMm, box.minYMm, box.maxZMm),
        )
        return grown
            .distinct()
            .filter { corner ->
                corner.xMm in bounds.minXMm until bounds.maxXMm &&
                    corner.yMm in bounds.minYMm until bounds.maxYMm &&
                    corner.zMm in bounds.minZMm until bounds.maxZMm &&
                    // A point buried inside something already placed can never start a box.
                    placed.none { placement ->
                        val b = placement.box
                        corner.xMm >= b.minXMm && corner.xMm < b.maxXMm &&
                            corner.yMm >= b.minYMm && corner.yMm < b.maxYMm &&
                            corner.zMm >= b.minZMm && corner.zMm < b.maxZMm
                    }
            }
            .sortedWith(compareBy({ it.zMm }, { it.yMm }, { it.xMm }))
    }

    /**
     * Bottom-up. A supporter's base is strictly below the base of whatever rests on it, so
     * ordering by height is enough to guarantee nothing is packed before the thing it sits
     * on. The validator re-checks that independently rather than trusting this comment.
     */
    private fun inPackingOrder(placements: List<Placement>): List<Placement> =
        placements
            .sortedWith(compareBy({ it.zMm }, { it.yMm }, { it.xMm }, { it.instanceId }))
            .mapIndexed { index, placement -> placement.copy(sequenceIndex = index) }

    // -- assembling the answer ---------------------------------------------------------------

    private fun Attempt.asPlan(
        request: PackingRequest,
        oversize: List<ItemInstance>,
        blockedByOpening: List<ItemInstance>,
        stoppedOnBudget: Boolean = stoppedOnTimeBudget,
    ): PackingPlan {
        val placedIds = placements.mapTo(mutableSetOf()) { it.instanceId }
        val oversizeIds = oversize.mapTo(mutableSetOf()) { it.instanceId }
        val openingIds = blockedByOpening.mapTo(mutableSetOf()) { it.instanceId }

        val unplaced = request.expandInstances()
            .filter { it.instanceId !in placedIds }
            .map { instance ->
                UnplacedInstance(
                    instanceId = instance.instanceId,
                    specId = instance.spec.id,
                    name = instance.spec.name,
                    reason = when {
                        instance.instanceId in oversizeIds -> UnplacedReason.LARGER_THAN_THE_SPACE
                        instance.instanceId in openingIds ->
                            UnplacedReason.WILL_NOT_FIT_THROUGH_THE_OPENING
                        instance.instanceId in unreached -> UnplacedReason.TIME_BUDGET_REACHED
                        else -> UnplacedReason.NO_ROOM_IN_THIS_ARRANGEMENT
                    },
                )
            }

        return PackingPlan(
            spaceId = request.space.id,
            inputRevision = request.revision(),
            solverVersion = SOLVER_VERSION,
            placements = placements,
            unplaced = unplaced,
            metrics = metricsFor(request, placements, unplaced),
            strategy = ordering.label,
            stoppedOnTimeBudget = stoppedOnBudget,
        )
    }
}

/**
 * The engine's stopping conditions. Both belong to the caller: an engine that reads the
 * wall clock on its own is an engine whose tests cannot be made deterministic.
 */
class SolveBudget(
    val timeBudgetMillis: Long = 2_000L,
    private val nowNanos: () -> Long = System::nanoTime,
    private val cancelled: () -> Boolean = { false },
) {
    private val startNanos: Long = nowNanos()

    /** Saturating, so an effectively-unlimited budget cannot overflow into "already over". */
    private val budgetNanos: Long =
        if (timeBudgetMillis >= Long.MAX_VALUE / 1_000_000L) Long.MAX_VALUE
        else timeBudgetMillis * 1_000_000L

    fun isExhausted(): Boolean = cancelled() || (nowNanos() - startNanos) >= budgetNanos

    companion object {
        /** For fixtures: never stops early, so a result is purely geometric. */
        fun unlimited(): SolveBudget = SolveBudget(
            timeBudgetMillis = Long.MAX_VALUE,
            nowNanos = { 0L },
            cancelled = { false },
        )

        /** Stops immediately, for testing the budget path. */
        fun exhausted(): SolveBudget = SolveBudget(
            timeBudgetMillis = 0L,
            nowNanos = { 0L },
            cancelled = { false },
        )
    }
}
