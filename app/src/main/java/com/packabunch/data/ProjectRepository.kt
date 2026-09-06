package com.packabunch.data

import com.packabunch.packing.Dimensions
import com.packabunch.packing.ItemSpec
import com.packabunch.packing.MeasurementSource
import com.packabunch.packing.Space
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Where packs live.
 *
 * In memory for now, deliberately: the screens need something to read long before Room
 * entities and migrations are worth writing, and keeping the interface narrow means
 * swapping the implementation later touches this file and nothing else.
 *
 * **This does not survive process death yet.** Nothing here is persisted, so a pack is lost
 * when the app is killed. That is a known gap, not a design choice — the plan's own release
 * gate requires projects to survive a restart, and it is not met until Room lands.
 */
class ProjectRepository {

    private val _projects = MutableStateFlow<List<Project>>(listOf(sampleProject()))
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    fun project(id: String): Project? = _projects.value.firstOrNull { it.id == id }

    fun upsert(project: Project) {
        _projects.update { current ->
            val stamped = project.copy(updatedAtMillis = System.currentTimeMillis())
            val index = current.indexOfFirst { it.id == project.id }
            if (index >= 0) current.toMutableList().apply { set(index, stamped) }
            else current + stamped
        }
    }

    fun delete(id: String) {
        _projects.update { current -> current.filterNot { it.id == id } }
    }

    /** For the undo on the "deleted" state — the row goes back exactly as it was. */
    fun restore(project: Project) {
        _projects.update { current -> current + project }
    }

    companion object {
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
