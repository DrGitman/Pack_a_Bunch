package com.packabunch.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The stored schema.
 *
 * Two decisions worth knowing before changing anything here:
 *
 * **Lengths are stored as integer millimetres**, exactly as the engine holds them. No
 * floats, no unit column. A value typed in centimetres and read back in inches has to come
 * out as the same physical size, and that only holds if the stored form is canonical.
 *
 * **Placements are not stored.** The engine is deterministic: the same space and items
 * always produce the same arrangement. So the inputs are persisted and the plan is
 * recomputed on open, which removes a whole class of bug where a saved plan drifts out of
 * step with the items it was solved from. Only the *summary* is kept, so a project card can
 * show "71% modelled fill" without solving every row in the list — and it carries the input
 * revision, so a summary that no longer matches its inputs is detected and discarded rather
 * than shown.
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val spaceId: String,
    val spaceName: String,
    val widthMm: Int,
    val depthMm: Int,
    val heightMm: Int,
    val edgeGapMm: Int,
    /** Stored as the enum name, not its ordinal — reordering the enum must not rewrite data. */
    val measurementSource: String,
    val updatedAtMillis: Long,
    /** Comma-separated instance ids the user has ticked off in the guide. */
    val packedInstanceIds: String,
    val scanGeometry: ByteArray? = null,
)

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("projectId")],
)
data class ItemEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val widthMm: Int,
    val depthMm: Int,
    val heightMm: Int,
    val quantity: Int,
    val keepUpright: Boolean,
    val maySupportItems: Boolean,
    val measurementSource: String,
    val photoPath: String?,
    /** Preserves the order the user added them in, which is also their number on screen. */
    val position: Int,
    val shapeGeometry: ByteArray? = null,
    val visualGeometry: ByteArray? = null,
)

/**
 * Enough of a finished plan to draw a project card, and no more.
 *
 * [inputRevision] is the whole point. It fingerprints the space and items this was solved
 * from, so if either has been edited since, the summary is stale and is thrown away rather
 * than shown against numbers it does not describe.
 */
@Entity(
    tableName = "plan_summaries",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlanSummaryEntity(
    @PrimaryKey val projectId: String,
    val inputRevision: String,
    val solverVersion: Int,
    val strategy: String,
    val stoppedOnTimeBudget: Boolean,
    val placedInstanceCount: Int,
    val requestedInstanceCount: Int,
    val placedVolumeMm3: Long,
    val usableVolumeMm3: Long,
    val occupiedBoundsVolumeMm3: Long,
)
