package com.packabunch.data.db

import com.packabunch.data.Project
import com.packabunch.packing.Dimensions
import com.packabunch.packing.ItemSpec
import com.packabunch.packing.MeasurementSource
import com.packabunch.packing.PackingMetrics
import com.packabunch.packing.PackingPlan
import com.packabunch.packing.Space

/**
 * Between the stored rows and the engine's types.
 *
 * Explicit rather than reflective or generated. The engine module has no dependencies and
 * no annotations on purpose, so the mapping lives here where the storage concerns are —
 * and a schema change shows up as a compile error in this file rather than as a runtime
 * surprise on somebody's phone.
 */

fun StoredProject.toProject(): Project {
    val space = Space(
        id = project.spaceId,
        name = project.spaceName,
        dimensions = Dimensions(project.widthMm, project.depthMm, project.heightMm),
        edgeGapMm = project.edgeGapMm,
        measurementSource = project.measurementSource.toMeasurementSource(),
        scan = GeometryCodec.scan(project.scanGeometry),
    )

    val specs = items
        .sortedBy { it.position }
        .map { row ->
            ItemSpec(
                id = row.id,
                name = row.name,
                dimensions = Dimensions(row.widthMm, row.depthMm, row.heightMm),
                quantity = row.quantity,
                keepUpright = row.keepUpright,
                maySupportItems = row.maySupportItems,
                measurementSource = row.measurementSource.toMeasurementSource(),
                shape = GeometryCodec.shape(row.shapeGeometry),
                visualShape = GeometryCodec.shape(row.visualGeometry),
            )
        }

    val restored = Project(
        id = project.id,
        name = project.name,
        space = space,
        items = specs,
        updatedAtMillis = project.updatedAtMillis,
        packedInstanceIds = project.packedInstanceIds
            .split(',')
            .filter { it.isNotBlank() }
            .toSet(),
    )

    // A summary solved from different inputs describes a different pack. Drop it rather
    // than let a project card show a fill figure for items that have since changed.
    val usable = summary?.takeIf { it.inputRevision == restored.request.revision() }
    return restored.copy(plan = usable?.toPlanShell(restored))
}

/**
 * Metrics and provenance without placements.
 *
 * Enough to draw a card; deliberately not enough to draw an arrangement. Anything that
 * needs positions has to re-solve, which is cheap because the engine is deterministic and
 * gives back exactly what was stored here.
 */
private fun PlanSummaryEntity.toPlanShell(project: Project): PackingPlan = PackingPlan(
    spaceId = project.space.id,
    inputRevision = inputRevision,
    solverVersion = solverVersion,
    placements = emptyList(),
    unplaced = emptyList(),
    metrics = PackingMetrics(
        placedInstanceCount = placedInstanceCount,
        requestedInstanceCount = requestedInstanceCount,
        placedVolumeMm3 = placedVolumeMm3,
        usableVolumeMm3 = usableVolumeMm3,
        occupiedBoundsVolumeMm3 = occupiedBoundsVolumeMm3,
    ),
    strategy = strategy,
    stoppedOnTimeBudget = stoppedOnTimeBudget,
)

fun Project.toEntity(): ProjectEntity = ProjectEntity(
    id = id,
    name = name,
    spaceId = space.id,
    spaceName = space.name,
    widthMm = space.dimensions.widthMm,
    depthMm = space.dimensions.depthMm,
    heightMm = space.dimensions.heightMm,
    edgeGapMm = space.edgeGapMm,
    measurementSource = space.measurementSource.name,
    updatedAtMillis = updatedAtMillis,
    packedInstanceIds = packedInstanceIds.joinToString(","),
    scanGeometry = GeometryCodec.scan(space.scan),
)

fun Project.toItemEntities(): List<ItemEntity> = items.mapIndexed { index, spec ->
    ItemEntity(
        id = spec.id,
        projectId = id,
        name = spec.name,
        widthMm = spec.dimensions.widthMm,
        depthMm = spec.dimensions.depthMm,
        heightMm = spec.dimensions.heightMm,
        quantity = spec.quantity,
        keepUpright = spec.keepUpright,
        maySupportItems = spec.maySupportItems,
        measurementSource = spec.measurementSource.name,
        photoPath = null,
        position = index,
        shapeGeometry = GeometryCodec.shape(spec.shape),
        visualGeometry = GeometryCodec.shape(spec.visualShape),
    )
}

fun Project.toSummaryEntity(): PlanSummaryEntity? {
    val solved = plan ?: return null
    return PlanSummaryEntity(
        projectId = id,
        inputRevision = solved.inputRevision,
        solverVersion = solved.solverVersion,
        strategy = solved.strategy,
        stoppedOnTimeBudget = solved.stoppedOnTimeBudget,
        placedInstanceCount = solved.metrics.placedInstanceCount,
        requestedInstanceCount = solved.metrics.requestedInstanceCount,
        placedVolumeMm3 = solved.metrics.placedVolumeMm3,
        usableVolumeMm3 = solved.metrics.usableVolumeMm3,
        occupiedBoundsVolumeMm3 = solved.metrics.occupiedBoundsVolumeMm3,
    )
}

/**
 * Stored as the name, so an unrecognised value means the data is from a newer build than
 * this one. Falling back to "typed in" is the conservative reading: it never invents a
 * camera-estimate badge for a number whose origin we cannot establish.
 */
private fun String.toMeasurementSource(): MeasurementSource =
    MeasurementSource.entries.firstOrNull { it.name == this } ?: MeasurementSource.TYPED_IN
