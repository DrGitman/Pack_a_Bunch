package com.packabunch.packing

/**
 * Re-checks a finished plan from scratch, without reusing anything the search believed
 * while building it.
 *
 * This is the reason a wrong arrangement should never reach a screen. The heuristic is
 * allowed to be imperfect about *which* arrangement it finds; it is not allowed to hand
 * back one that overlaps, floats or pokes through a wall. Any plan that fails here is
 * discarded rather than shown with a caveat.
 */
object PlanValidator {

    enum class Code {
        OUT_OF_BOUNDS,
        OVERLAP,
        UNSUPPORTED,
        WRONG_ORIENTED_SIZE,
        UPRIGHT_RULE_BROKEN,
        SUPPORTER_FORBIDS_STACKING,
        DUPLICATE_INSTANCE,
        UNKNOWN_INSTANCE,
        INSTANCE_UNACCOUNTED_FOR,
        BAD_SEQUENCE,
        METRICS_MISMATCH,
    }

    data class Violation(val code: Code, val detail: String)

    data class Result(val violations: List<Violation>) {
        val isValid: Boolean get() = violations.isEmpty()
    }

    fun validate(request: PackingRequest, plan: PackingPlan): Result {
        val violations = mutableListOf<Violation>()
        val instances = request.expandInstances().associateBy { it.instanceId }
        val volume = request.space.volume()

        // --- identity: every requested instance is accounted for exactly once ----------
        val seen = mutableSetOf<String>()
        (plan.placements.map { it.instanceId } + plan.unplaced.map { it.instanceId }).forEach { id ->
            if (!seen.add(id)) {
                violations += Violation(Code.DUPLICATE_INSTANCE, "$id appears twice in the plan")
            }
            if (id !in instances) {
                violations += Violation(Code.UNKNOWN_INSTANCE, "$id was never requested")
            }
        }
        instances.keys.forEach { id ->
            if (id !in seen) {
                violations += Violation(
                    Code.INSTANCE_UNACCOUNTED_FOR,
                    "$id is neither placed nor listed as unplaced",
                )
            }
        }

        // --- each placement on its own -------------------------------------------------
        plan.placements.forEach { placement ->
            val spec = instances[placement.instanceId]?.spec ?: return@forEach

            val expected = placement.orientation.apply(spec.dimensions)
            if (expected.widthMm != placement.orientedWidthMm ||
                expected.depthMm != placement.orientedDepthMm ||
                expected.heightMm != placement.orientedHeightMm
            ) {
                violations += Violation(
                    Code.WRONG_ORIENTED_SIZE,
                    "${placement.instanceId} is stored as " +
                        "${placement.orientedWidthMm}×${placement.orientedDepthMm}×" +
                        "${placement.orientedHeightMm} but ${placement.orientation} of " +
                        "${spec.dimensions.widthMm}×${spec.dimensions.depthMm}×" +
                        "${spec.dimensions.heightMm} is " +
                        "${expected.widthMm}×${expected.depthMm}×${expected.heightMm}",
                )
            }

            if (spec.keepUpright && !placement.orientation.isUpright) {
                violations += Violation(
                    Code.UPRIGHT_RULE_BROKEN,
                    "${placement.instanceId} is marked keep-upright but is placed as " +
                        "${placement.orientation}",
                )
            }

            if (!volume.admits(placement.box)) {
                violations += Violation(
                    Code.OUT_OF_BOUNDS,
                    "${placement.instanceId} at (${placement.xMm},${placement.yMm}," +
                        "${placement.zMm}) is not inside space that was observed and empty",
                )
            }
        }

        // --- pairs ----------------------------------------------------------------------
        for (i in plan.placements.indices) {
            for (j in i + 1 until plan.placements.size) {
                val a = plan.placements[i]
                val b = plan.placements[j]
                if (a.box.overlaps(b.box)) {
                    violations += Violation(
                        Code.OVERLAP,
                        "${a.instanceId} and ${b.instanceId} share space",
                    )
                }
            }
        }

        // --- support --------------------------------------------------------------------
        plan.placements.forEach { placement ->
            // Carried by the space itself — the floor of a crate, or the uneven floor and
            // arches of a scan, checked column by column rather than against a flat plane.
            if (volume.restsOnStructure(placement.box)) return@forEach

            val supporter = plan.placements.firstOrNull { other ->
                other.instanceId != placement.instanceId &&
                    other.box.maxZMm == placement.zMm &&
                    other.box.coversFootprintOf(placement.box)
            }

            if (supporter == null) {
                violations += Violation(
                    Code.UNSUPPORTED,
                    "${placement.instanceId} floats: its base at z=${placement.zMm} is not " +
                        "the floor and is not wholly on top of one other item",
                )
                return@forEach
            }

            val supporterSpec = instances[supporter.instanceId]?.spec
            if (supporterSpec != null && !supporterSpec.maySupportItems) {
                violations += Violation(
                    Code.SUPPORTER_FORBIDS_STACKING,
                    "${placement.instanceId} is stacked on ${supporter.instanceId}, which is " +
                        "marked nothing-on-top",
                )
            }
        }

        // --- packing order ---------------------------------------------------------------
        val indices = plan.placements.map { it.sequenceIndex }.sorted()
        if (indices != plan.placements.indices.toList()) {
            violations += Violation(
                Code.BAD_SEQUENCE,
                "sequence indices are $indices, expected 0..${plan.placements.size - 1}",
            )
        }
        val bySequence = plan.placements.sortedBy { it.sequenceIndex }
        bySequence.forEachIndexed { position, placement ->
            val supporterComesLater = bySequence.drop(position + 1).any { later ->
                later.box.maxZMm == placement.zMm && later.box.coversFootprintOf(placement.box)
            }
            if (supporterComesLater) {
                violations += Violation(
                    Code.BAD_SEQUENCE,
                    "${placement.instanceId} is packed before the item it rests on",
                )
            }
        }

        // --- metrics ---------------------------------------------------------------------
        val expectedMetrics = metricsFor(request, plan.placements, plan.unplaced)
        if (plan.metrics != expectedMetrics) {
            violations += Violation(
                Code.METRICS_MISMATCH,
                "stored metrics ${plan.metrics} do not match ${expectedMetrics} recomputed " +
                    "from the placements",
            )
        }

        return Result(violations)
    }
}

/** The single place metrics are computed, so the engine and the validator cannot disagree. */
internal fun metricsFor(
    request: PackingRequest,
    placements: List<Placement>,
    unplaced: List<UnplacedInstance>,
): PackingMetrics {
    val placedVolume = placements.sumOf { it.volumeMm3 }
    val boundsVolume = if (placements.isEmpty()) {
        0L
    } else {
        val minX = placements.minOf { it.box.minXMm }
        val maxX = placements.maxOf { it.box.maxXMm }
        val minY = placements.minOf { it.box.minYMm }
        val maxY = placements.maxOf { it.box.maxYMm }
        val minZ = placements.minOf { it.box.minZMm }
        val maxZ = placements.maxOf { it.box.maxZMm }
        (maxX - minX).toLong() * (maxY - minY).toLong() * (maxZ - minZ).toLong()
    }

    return PackingMetrics(
        placedInstanceCount = placements.size,
        requestedInstanceCount = placements.size + unplaced.size,
        placedVolumeMm3 = placedVolume,
        usableVolumeMm3 = request.space.usableVolumeMm3,
        occupiedBoundsVolumeMm3 = boundsVolume,
    )
}
