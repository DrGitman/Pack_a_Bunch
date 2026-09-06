package com.packabunch.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** A project with everything hanging off it, read in one go. */
data class StoredProject(
    @Embedded val project: ProjectEntity,
    @Relation(parentColumn = "id", entityColumn = "projectId")
    val items: List<ItemEntity>,
    @Relation(parentColumn = "id", entityColumn = "projectId")
    val summary: PlanSummaryEntity?,
)

@Dao
interface ProjectDao {

    @Transaction
    @Query("SELECT * FROM projects ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<StoredProject>>

    @Transaction
    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun byId(id: String): StoredProject?

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: PlanSummaryEntity)

    @Query("DELETE FROM items WHERE projectId = :projectId")
    suspend fun clearItems(projectId: String)

    @Query("DELETE FROM plan_summaries WHERE projectId = :projectId")
    suspend fun clearSummary(projectId: String)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProject(id: String)

    /**
     * Items are replaced wholesale rather than diffed. A pack has a handful of rows, and
     * deleting an item then re-adding it under the same name must not leave the old row
     * behind — the simplest correct thing is to rewrite the set inside one transaction.
     */
    @Transaction
    suspend fun save(
        project: ProjectEntity,
        items: List<ItemEntity>,
        summary: PlanSummaryEntity?,
    ) {
        insertProject(project)
        clearItems(project.id)
        insertItems(items)
        clearSummary(project.id)
        if (summary != null) insertSummary(summary)
    }
}
