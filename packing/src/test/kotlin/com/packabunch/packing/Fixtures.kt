package com.packabunch.packing

/** Shorthand for readable fixtures. Everything is millimetres. */
fun space(
    widthMm: Int,
    depthMm: Int,
    heightMm: Int,
    edgeGapMm: Int = 0,
    id: String = "space",
): Space = Space(
    id = id,
    name = "Test space",
    dimensions = Dimensions(widthMm, depthMm, heightMm),
    edgeGapMm = edgeGapMm,
)

fun item(
    id: String,
    widthMm: Int,
    depthMm: Int,
    heightMm: Int,
    quantity: Int = 1,
    keepUpright: Boolean = false,
    maySupportItems: Boolean = true,
): ItemSpec = ItemSpec(
    id = id,
    name = id,
    dimensions = Dimensions(widthMm, depthMm, heightMm),
    quantity = quantity,
    keepUpright = keepUpright,
    maySupportItems = maySupportItems,
)

/** Solves with no time pressure, so a result reflects geometry and nothing else. */
fun solved(space: Space, vararg items: ItemSpec): PackingPlan {
    val request = PackingRequest(space, items.toList())
    val result = PackingEngine.solve(request, SolveBudget.unlimited())
    val plan = (result as? SolveResult.Solved)?.plan
        ?: error("expected a plan, got $result")

    // Every fixture asserts this, so it lives here: nothing the engine returns is allowed
    // to fail the independent validator, whatever else the individual test is checking.
    val validation = PlanValidator.validate(request, plan)
    check(validation.isValid) { "engine returned an invalid plan: ${validation.violations}" }
    return plan
}

fun problems(space: Space, vararg items: ItemSpec): List<InputProblem> {
    val result = PackingEngine.solve(PackingRequest(space, items.toList()), SolveBudget.unlimited())
    return (result as? SolveResult.InvalidInput)?.problems
        ?: error("expected invalid input, got $result")
}

fun PackingPlan.placementOf(instanceId: String): Placement =
    placements.first { it.instanceId == instanceId }

fun PackingPlan.reasonFor(instanceId: String): UnplacedReason =
    unplaced.first { it.instanceId == instanceId }.reason
