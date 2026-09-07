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
        val shapeLookup = request.items.associate { it.id to it.effectiveShape }

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
                val attempt = packGreedily(request, ordering, packable, supportLookup, shapeLookup, budget)
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
        shapeLookup: Map<String, ItemShape>,
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
            val choice = bestFitFor(
                instance, candidates, placed, volume, supportLookup, shapeLookup,
            ) ?: continue

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
            candidates = expandCorners(
                candidates, choice, volume, placed, shapeLookup[instance.spec.id], shapeLookup,
            )
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
        shapeLookup: Map<String, ItemShape>,
    ): Choice? {
        var best: Choice? = null
        var bestKey: IntArray? = null

        for (corner in candidates) {
            for (orientation in instance.spec.allowedOrientations) {
                val oriented = orientation.apply(instance.spec.dimensions)
                val choice = Choice(corner, orientation, oriented)
                val box = choice.box

                if (!volume.admits(box)) continue

                // Shape against shape, not box against box. `collide` still rejects on the
                // boxes first when it can, so the ordinary case costs what it always did —
                // but a block that fits inside an L's notch is now found rather than
                // refused for overlapping air.
                val myShape = shapeLookup[instance.spec.id] ?: ItemShape.Cuboid(oriented)
                val hits = placed.any { other ->
                    ItemShape.collide(
                        myShape,
                        box,
                        shapeLookup[other.specId] ?: ItemShape.Cuboid(
                            Dimensions(
                                other.orientedWidthMm,
                                other.orientedDepthMm,
                                other.orientedHeightMm,
                            ),
                        ),
                        other.box,
                    )
                }
                if (hits) continue
                if (!isSupported(box, placed, volume, supportLookup, shapeLookup, myShape)) continue

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
        shapeLookup: Map<String, ItemShape>,
        shape: ItemShape,
    ): Boolean {
        // Carried by the space itself: the floor of a crate, or the sloping floor and
        // wheel arches of a scanned boot, checked column by column.
        if (volume.restsOnStructure(box)) return true

        // The box case, unchanged and cheap: wholly on top of one flat-topped item.
        val flatSupporter = placed.firstOrNull { candidate ->
            candidate.box.maxZMm == box.minZMm && candidate.box.coversFootprintOf(box)
        }
        if (flatSupporter != null) return supportLookup[flatSupporter.specId] ?: true

        // The shaped case. An item sitting in an L's notch rests on the L's bottom arm, not
        // on the top of the L's bounding box — so "whose box top equals my base" can never
        // find it. Ask instead whether there is material directly under this base, and whose.
        return restsOnMaterialBelow(box, shape, placed, shapeLookup, supportLookup)
    }

    /**
     * Whether something solid sits immediately beneath every part of this base.
     *
     * Walks the base layer a cell at a time and looks one cell down. A shaped supporter
     * counts wherever its own material actually is, which is what makes nesting work; and if
     * any of that material belongs to an item marked nothing-on-top, the placement is
     * refused outright rather than partially allowed.
     */
    private fun restsOnMaterialBelow(
        box: Box,
        shape: ItemShape,
        placed: List<Placement>,
        shapeLookup: Map<String, ItemShape>,
        supportLookup: Map<String, Boolean>,
    ): Boolean {
        val mask = shape as? ItemShape.VoxelMask
        val res = mask?.resolutionMm
            ?: (placed.firstNotNullOfOrNull { shapeLookup[it.specId] as? ItemShape.VoxelMask }
                ?.resolutionMm ?: return false)

        var anySupport = false
        var x = box.minXMm
        while (x < box.maxXMm) {
            var y = box.minYMm
            while (y < box.maxYMm) {
                val occupiedHere = mask == null || mask.isOccupied(
                    (x - box.minXMm) / res,
                    (y - box.minYMm) / res,
                    0,
                )
                if (occupiedHere) {
                    val supporter = materialAt(x, y, box.minZMm - res, placed, shapeLookup, res)
                        ?: return false
                    if (supportLookup[supporter] == false) return false
                    anySupport = true
                }
                y += res
            }
            x += res
        }
        return anySupport
    }

    /** Which placed item, if any, has material in the cell containing this point. */
    private fun materialAt(
        xMm: Int,
        yMm: Int,
        zMm: Int,
        placed: List<Placement>,
        shapeLookup: Map<String, ItemShape>,
        res: Int,
    ): String? = placed.firstOrNull { candidate ->
        val b = candidate.box
        if (xMm < b.minXMm || xMm >= b.maxXMm) return@firstOrNull false
        if (yMm < b.minYMm || yMm >= b.maxYMm) return@firstOrNull false
        if (zMm < b.minZMm || zMm >= b.maxZMm) return@firstOrNull false

        when (val other = shapeLookup[candidate.specId]) {
            is ItemShape.VoxelMask -> other.isOccupied(
                (xMm - b.minXMm) / other.resolutionMm,
                (yMm - b.minYMm) / other.resolutionMm,
                (zMm - b.minZMm) / other.resolutionMm,
            )
            // No measured shape means it fills its box.
            else -> true
        }
    }?.specId

    private fun expandCorners(
        existing: List<Position>,
        choice: Choice,
        volume: PackingVolume,
        placed: List<Placement>,
        shape: ItemShape?,
        shapeLookup: Map<String, ItemShape>,
    ): List<Position> {
        val bounds = volume.boundsMm
        val box = choice.box
        val grown = existing + listOf(
            Position(box.maxXMm, box.minYMm, box.minZMm),
            Position(box.minXMm, box.maxYMm, box.minZMm),
            Position(box.minXMm, box.minYMm, box.maxZMm),
        ) + notchPositions(box, shape)
        return grown
            .distinct()
            .filter { corner ->
                corner.xMm in bounds.minXMm until bounds.maxXMm &&
                    corner.yMm in bounds.minYMm until bounds.maxYMm &&
                    corner.zMm in bounds.minZMm until bounds.maxZMm &&
                    // A point buried in another item's *material* can never start a box.
                    //
                    // Material, not bounding box: a point inside an L's envelope but in its
                    // hollow is precisely where something should be tried. Pruning by box
                    // here would throw away every notch position the moment it was created.
                    placed.none { placement ->
                        val b = placement.box
                        val inside = corner.xMm >= b.minXMm && corner.xMm < b.maxXMm &&
                            corner.yMm >= b.minYMm && corner.yMm < b.maxYMm &&
                            corner.zMm >= b.minZMm && corner.zMm < b.maxZMm
                        if (!inside) return@none false

                        when (val other = shapeLookup[placement.specId]) {
                            is ItemShape.VoxelMask -> other.isOccupied(
                                (corner.xMm - b.minXMm) / other.resolutionMm,
                                (corner.yMm - b.minYMm) / other.resolutionMm,
                                (corner.zMm - b.minZMm) / other.resolutionMm,
                            )
                            else -> true
                        }
                    }
            }
            .sortedWith(compareBy({ it.zMm }, { it.yMm }, { it.xMm }))
    }

    /**
     * Positions inside a concave item's own bounding box.
     *
     * Without these, shape-aware collision is useless. The search only ever offers the three
     * exposed corners of a placed box, so the hollow of an L — the very space its shape was
     * kept in order to use — is never a position anything is tried at. Colliding shapes
     * correctly and then never proposing the one place they nest is no better than packing
     * boxes.
     *
     * Only produced for shapes that are genuinely not box-like, and only for the empty cells
     * within that one item's bounds, so the candidate list stays small.
     */
    private fun notchPositions(box: Box, shape: ItemShape?): List<Position> {
        val mask = shape as? ItemShape.VoxelMask ?: return emptyList()
        if (mask.isBoxLike) return emptyList()

        val res = mask.resolutionMm
        val found = mutableListOf<Position>()
        for (i in 0 until mask.countX) {
            for (j in 0 until mask.countY) {
                for (k in 0 until mask.countZ) {
                    if (mask.isOccupied(i, j, k)) continue
                    // An empty cell inside the shape's own envelope: somewhere another item
                    // might tuck into.
                    found += Position(
                        xMm = box.minXMm + i * res,
                        yMm = box.minYMm + j * res,
                        zMm = box.minZMm + k * res,
                    )
                }
            }
        }
        return found
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
