package com.packabunch.data

import android.content.Context
import com.packabunch.data.db.PackDatabase
import com.packabunch.data.db.ProjectDao
import com.packabunch.data.db.toEntity
import com.packabunch.data.db.toItemEntities
import com.packabunch.data.db.toProject
import com.packabunch.data.db.toSummaryEntity
import com.packabunch.packing.Dimensions
import com.packabunch.packing.ItemSpec
import com.packabunch.packing.MeasurementSource
import com.packabunch.packing.PackingEngine
import com.packabunch.packing.SolveBudget
import com.packabunch.packing.SolveResult
import com.packabunch.packing.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Where packs live. Backed by Room, so they survive the app being killed.
 *
 * The arrangement itself is not stored — see [com.packabunch.data.db.PlanSummaryEntity].
 * The engine is deterministic, so inputs plus a revision fingerprint are enough to
 * reproduce a plan exactly, and [solvedProject] is how a saved pack gets its placements
 * back when it is opened.
 */
class ProjectRepository(private val dao: ProjectDao) {

    val projects: Flow<List<Project>> =
        dao.observeAll().map { rows -> rows.map { it.toProject() } }

    suspend fun project(id: String): Project? = withContext(Dispatchers.IO) {
        dao.byId(id)?.toProject()
    }

    /**
     * A saved project with its placements recomputed.
     *
     * Reading a project back gives metrics but no positions. This re-runs the search, which
     * returns the same arrangement it did when it was saved — that is what determinism buys.
     */
    suspend fun solvedProject(id: String): Project? = withContext(Dispatchers.Default) {
        val stored = project(id) ?: return@withContext null
        val exact = stored.currentPlan
        if (exact != null && com.packabunch.packing.PlanValidator.validate(stored.request,exact).isValid) return@withContext stored
        if (stored.items.isEmpty()) return@withContext stored

        when (val result = PackingEngine.solve(stored.request, SolveBudget(timeBudgetMillis = 2_000))) {
            is SolveResult.Solved -> stored.copy(plan = result.plan, packedInstanceIds = emptySet()).also { upsert(it) }
            is SolveResult.InvalidInput -> stored.copy(plan = null)
        }
    }

    suspend fun upsert(project: Project) = withContext(Dispatchers.IO) {
        val stamped = project.copy(updatedAtMillis = System.currentTimeMillis())
        dao.save(
            project = stamped.toEntity(),
            items = stamped.toItemEntities(),
            summary = stamped.toSummaryEntity(),
        )
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) { dao.deleteProject(id) }

    /**
     * Wipes every pack on the device.
     *
     * Items and plan summaries go with them through the foreign keys' cascade, so nothing
     * is left orphaned in the database after this returns.
     */
    suspend fun deleteAll() = withContext(Dispatchers.IO) { dao.deleteAllProjects() }

    /** Undo on the "deleted" state: the row goes back exactly as it was, id and all. */
    suspend fun restore(project: Project) = upsert(project)

    /** Seeds the sample pack, once, so a first run has something to look at. */
    suspend fun seedIfEmpty() = withContext(Dispatchers.IO) {
        if (dao.count() == 0) upsert(sampleProject())
    }

    companion object {
        fun create(context: Context): ProjectRepository =
            ProjectRepository(PackDatabase.get(context).projectDao())

        /**
         * The sample pack offered on first run. Real measurements of a real crate, so the
         * numbers on screen are plausible and the arrangement is one the engine actually
         * found rather than a picture of one.
         */
        fun sampleProject(): Project = Project(
            id = "sample",
            name = "Moving crate",
            space = Space(
                id = "sample-space",
                name = "Moving crate",
                dimensions = Dimensions(widthMm = 584, depthMm = 396, heightMm = 350),
                measurementSource = MeasurementSource.TYPED_IN,
            ),
            items = listOf(
                ItemSpec(
                    id = "toolbox", name = "Toolbox",
                    dimensions = Dimensions(330, 200, 120),
                ),
                ItemSpec(
                    id = "shoebox", name = "Shoe box",
                    dimensions = Dimensions(330, 200, 120), quantity = 3,
                ),
                ItemSpec(
                    id = "game", name = "Board game",
                    dimensions = Dimensions(270, 270, 55), quantity = 2,
                ),
                ItemSpec(
                    id = "kettle", name = "Kettle",
                    dimensions = Dimensions(220, 160, 240), keepUpright = true,
                ),
            ),
        )
    }
}
