package com.packabunch.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packabunch.data.Project
import com.packabunch.data.ProjectRepository
import com.packabunch.packing.Dimensions
import com.packabunch.packing.ItemSpec
import com.packabunch.packing.PackingEngine
import com.packabunch.packing.PackingRequest
import com.packabunch.packing.SolveBudget
import com.packabunch.packing.SolveResult
import com.packabunch.packing.Space
import com.packabunch.packing.Tier
import com.packabunch.packing.TierLimits
import com.packabunch.ui.format.LengthUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One store for the whole app.
 *
 * Small on purpose. The screens are many but the state is not: a list of packs, whichever
 * one is being edited, the unit preference and the tier. Splitting that across a dozen
 * ViewModels would add wiring without adding clarity.
 */
class AppViewModel(
    private val repository: ProjectRepository = ProjectRepository(),
) : ViewModel() {

    val projects: StateFlow<List<Project>> = repository.projects

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _editor = MutableStateFlow(PackEditorState())
    val editor: StateFlow<PackEditorState> = _editor.asStateFlow()

    val limits: TierLimits get() = TierLimits.forTier(_settings.value.tier)

    // -- settings -------------------------------------------------------------------------

    fun setUnit(unit: LengthUnit) = _settings.update { it.copy(unit = unit) }

    fun setTier(tier: Tier) = _settings.update { it.copy(tier = tier) }

    // -- editing a pack -------------------------------------------------------------------

    fun startNewPack() {
        _editor.value = PackEditorState(
            projectId = "pack-${System.currentTimeMillis()}",
            isNew = true,
        )
    }

    fun openPack(id: String) {
        val project = repository.project(id) ?: return
        _editor.value = PackEditorState(
            projectId = project.id,
            name = project.name,
            space = project.space,
            items = project.items,
            isNew = false,
        )
    }

    fun setSpaceName(name: String) = _editor.update { it.copy(name = name) }

    fun setSpaceDimensions(dimensions: Dimensions, source: com.packabunch.packing.MeasurementSource) {
        _editor.update { state ->
            state.copy(
                space = (state.space ?: blankSpace(state)).copy(
                    dimensions = dimensions,
                    measurementSource = source,
                ),
            )
        }
    }

    fun setEdgeGap(millimetres: Int) {
        _editor.update { state ->
            state.copy(space = (state.space ?: blankSpace(state)).copy(edgeGapMm = millimetres))
        }
    }

    private fun blankSpace(state: PackEditorState) = Space(
        id = "${state.projectId}-space",
        name = state.name,
        dimensions = Dimensions(0, 0, 0),
    )

    fun upsertItem(item: ItemSpec) {
        _editor.update { state ->
            val index = state.items.indexOfFirst { it.id == item.id }
            val items = if (index >= 0) {
                state.items.toMutableList().apply { set(index, item) }
            } else {
                state.items + item
            }
            state.copy(items = items)
        }
    }

    fun removeItem(id: String) {
        _editor.update { state -> state.copy(items = state.items.filterNot { it.id == id }) }
    }

    fun duplicateItem(id: String) {
        _editor.update { state ->
            val source = state.items.firstOrNull { it.id == id } ?: return@update state
            state.copy(
                items = state.items + source.copy(id = "${source.id}-${state.items.size + 1}"),
            )
        }
    }

    /** True when adding another piece would cross the free plan's ceiling. */
    fun piecesWouldExceedPlan(extra: Int = 1): Boolean =
        !limits.allowsPieces(_editor.value.pieceCount + extra)

    // -- solving ----------------------------------------------------------------------------

    /**
     * Runs the search off the main thread with a real time budget.
     *
     * The engine is deterministic and pure, so there is nothing to synchronise — but it can
     * take a second or two on a large pack, and the UI thread is not where that belongs.
     */
    fun solve() {
        val state = _editor.value
        val space = state.space ?: return
        val request = PackingRequest(space, state.items)

        _editor.update { it.copy(solving = true, inputProblems = emptyList()) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                PackingEngine.solve(request, SolveBudget(timeBudgetMillis = 2_000))
            }
            when (result) {
                is SolveResult.InvalidInput -> _editor.update {
                    it.copy(solving = false, inputProblems = result.problems)
                }
                is SolveResult.Solved -> {
                    _editor.update { it.copy(solving = false, plan = result.plan) }
                    repository.upsert(
                        Project(
                            id = state.projectId,
                            name = state.name,
                            space = space,
                            items = state.items,
                            plan = result.plan,
                        ),
                    )
                }
            }
        }
    }

    fun save() {
        val state = _editor.value
        val space = state.space ?: return
        repository.upsert(
            Project(
                id = state.projectId,
                name = state.name,
                space = space,
                items = state.items,
                plan = state.plan,
            ),
        )
    }

    fun deleteProject(id: String) = repository.delete(id)

    fun restoreProject(project: Project) = repository.restore(project)

    // -- the packing guide ---------------------------------------------------------------------

    fun markPacked(instanceId: String, packed: Boolean) {
        _editor.update { state ->
            state.copy(
                packedInstanceIds = if (packed) state.packedInstanceIds + instanceId
                else state.packedInstanceIds - instanceId,
            )
        }
    }

    fun setGuideStep(step: Int) = _editor.update { it.copy(guideStep = step) }
}

data class AppSettings(
    val unit: LengthUnit = LengthUnit.CENTIMETRES,
    val tier: Tier = Tier.FREE,
    val scansToday: Int = 0,
)

/** Everything the create-a-pack flow is holding, across its several screens. */
data class PackEditorState(
    val projectId: String = "",
    val name: String = "",
    val space: Space? = null,
    val items: List<ItemSpec> = emptyList(),
    val plan: com.packabunch.packing.PackingPlan? = null,
    val solving: Boolean = false,
    val inputProblems: List<com.packabunch.packing.InputProblem> = emptyList(),
    val packedInstanceIds: Set<String> = emptySet(),
    val guideStep: Int = 0,
    val isNew: Boolean = true,
) {
    val pieceCount: Int get() = items.sumOf { it.quantity }

    val hasUsableSpace: Boolean
        get() = space?.dimensions?.isValid() == true
}
