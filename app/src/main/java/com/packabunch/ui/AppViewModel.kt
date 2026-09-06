package com.packabunch.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.packabunch.data.Project
import com.packabunch.data.ProjectRepository
import com.packabunch.packing.Dimensions
import com.packabunch.packing.InputProblem
import com.packabunch.packing.ItemSpec
import com.packabunch.packing.MeasurementSource
import com.packabunch.packing.PackingEngine
import com.packabunch.packing.PackingPlan
import com.packabunch.packing.PackingRequest
import com.packabunch.packing.SolveBudget
import com.packabunch.packing.SolveResult
import com.packabunch.packing.Space
import com.packabunch.packing.Tier
import com.packabunch.packing.TierLimits
import com.packabunch.ui.format.LengthUnit
import com.packabunch.ui.screens.LibraryItem
import com.packabunch.ui.screens.NotificationPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One store for the whole app.
 *
 * Small on purpose. The screens are many but the state is not: the saved packs, whichever
 * one is being edited, the unit preference and the tier. Splitting that across a dozen
 * ViewModels would add wiring without adding clarity.
 */
class AppViewModel(
    private val repository: ProjectRepository,
) : ViewModel() {

    val projects: StateFlow<List<Project>> = repository.projects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _editor = MutableStateFlow(PackEditorState())
    val editor: StateFlow<PackEditorState> = _editor.asStateFlow()

    val limits: TierLimits get() = TierLimits.forTier(_settings.value.tier)

    init {
        viewModelScope.launch { repository.seedIfEmpty() }
    }

    // -- settings -------------------------------------------------------------------------

    fun setUnit(unit: LengthUnit) = _settings.update { it.copy(unit = unit) }

    fun setTier(tier: Tier) = _settings.update { it.copy(tier = tier) }

    fun setNotificationPreferences(preferences: NotificationPreferences) =
        _settings.update { it.copy(notifications = preferences) }

    /** Turns off every notification the app has. There is no sixth kind hiding elsewhere. */
    fun turnAllNotificationsOff() = _settings.update {
        it.copy(
            notifications = NotificationPreferences(
                halfFinishedPack = false,
                backupState = false,
                billing = false,
                newKindsOfSpace = false,
                askHowItWent = false,
            ),
        )
    }

    /**
     * Every distinct item measured across every saved pack.
     *
     * Deduplicated by name and size rather than by id, because the same real object added
     * to two packs separately is one measured thing to the person who measured it.
     */
    fun libraryItems(projects: List<Project>): List<LibraryItem> = projects
        .flatMap { project -> project.items.map { project.id to it } }
        .groupBy { (_, spec) -> spec.name.trim().lowercase() to spec.dimensions }
        .map { (_, entries) ->
            LibraryItem(
                spec = entries.first().second,
                usedInPackCount = entries.map { it.first }.distinct().size,
            )
        }
        .sortedByDescending { it.usedInPackCount }

    // -- editing a pack -------------------------------------------------------------------

    fun startNewPack() {
        _editor.value = PackEditorState(
            projectId = "pack-${System.currentTimeMillis()}",
            isNew = true,
        )
    }

    /**
     * Opens a saved pack and puts its arrangement back.
     *
     * The placements are recomputed rather than loaded, because they were never stored.
     * The engine is deterministic, so what comes back is the same arrangement that was
     * saved — no drift, and nothing to migrate when the schema changes.
     */
    fun openPack(id: String) {
        viewModelScope.launch {
            val project = repository.solvedProject(id) ?: return@launch
            _editor.value = PackEditorState(
                projectId = project.id,
                name = project.name,
                space = project.space,
                items = project.items,
                plan = project.plan,
                packedInstanceIds = project.packedInstanceIds,
                isNew = false,
            )
        }
    }

    fun setSpaceName(name: String) = _editor.update { it.copy(name = name) }

    fun setSpaceDimensions(dimensions: Dimensions, source: MeasurementSource) {
        _editor.update { state ->
            state.copy(
                space = (state.space ?: blankSpace(state)).copy(
                    dimensions = dimensions,
                    measurementSource = source,
                ),
                // Any dimension change invalidates the arrangement it was solved from.
                plan = null,
            )
        }
        autosave()
    }

    fun setEdgeGap(millimetres: Int) {
        _editor.update { state ->
            state.copy(
                space = (state.space ?: blankSpace(state)).copy(edgeGapMm = millimetres),
                plan = null,
            )
        }
        autosave()
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
            state.copy(items = items, plan = null)
        }
        autosave()
    }

    fun removeItem(id: String) {
        _editor.update { state ->
            state.copy(items = state.items.filterNot { it.id == id }, plan = null)
        }
        autosave()
    }

    fun duplicateItem(id: String) {
        _editor.update { state ->
            val source = state.items.firstOrNull { it.id == id } ?: return@update state
            state.copy(
                items = state.items + source.copy(id = "${source.id}-${state.items.size + 1}"),
                plan = null,
            )
        }
        autosave()
    }

    fun piecesWouldExceedPlan(extra: Int = 1): Boolean =
        !limits.allowsPieces(_editor.value.pieceCount + extra)

    // -- solving ----------------------------------------------------------------------------

    /**
     * Runs the search off the main thread with a real time budget.
     *
     * The engine is pure, so there is nothing to synchronise — but it can take a second or
     * two on a large pack, and the UI thread is not where that belongs.
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
                    save()
                }
            }
        }
    }

    /**
     * Writes the pack out.
     *
     * Called after every confirmed edit rather than on a "save" button, because the plan's
     * own release gate requires a pack to survive an interrupted session — and a session
     * can be interrupted between any two taps.
     */
    fun save() {
        val state = _editor.value
        val space = state.space ?: return
        if (!space.dimensions.isValid()) return

        viewModelScope.launch {
            repository.upsert(
                Project(
                    id = state.projectId,
                    name = state.name,
                    space = space,
                    items = state.items,
                    plan = state.plan,
                    packedInstanceIds = state.packedInstanceIds,
                ),
            )
        }
    }

    private fun autosave() = save()

    fun deleteProject(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun restoreProject(project: Project) {
        viewModelScope.launch { repository.restore(project) }
    }

    // -- the packing guide ---------------------------------------------------------------------

    fun markPacked(instanceId: String, packed: Boolean) {
        _editor.update { state ->
            state.copy(
                packedInstanceIds = if (packed) state.packedInstanceIds + instanceId
                else state.packedInstanceIds - instanceId,
            )
        }
        save()
    }

    fun setGuideStep(step: Int) = _editor.update { it.copy(guideStep = step) }

    /**
     * "Did it actually go in?"
     *
     * The single most valuable signal the app can collect, because it is the only thing
     * that connects the geometric model to real crates. Held locally for now — it is not
     * sent anywhere, and it must not be until there is a privacy policy and a Data safety
     * declaration that cover it.
     */
    fun recordDidItFit(fitted: Boolean) {
        _editor.update { it.copy(realWorldFitReport = fitted) }
    }

    /**
     * Deletes everything on the device.
     *
     * Genuinely everything: the packs, and the item photo files with them. A delete that
     * leaves photos behind would make the Data safety declaration untrue.
     */
    fun deleteAllLocalData() {
        viewModelScope.launch {
            repository.deleteAll()
            _editor.value = PackEditorState()
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AppViewModel(ProjectRepository.create(context)) as T
    }
}

data class AppSettings(
    val unit: LengthUnit = LengthUnit.CENTIMETRES,
    val tier: Tier = Tier.FREE,
    val scansToday: Int = 0,
    val notifications: NotificationPreferences = NotificationPreferences(),
)

/** Everything the create-a-pack flow is holding, across its several screens. */
data class PackEditorState(
    val projectId: String = "",
    val name: String = "",
    val space: Space? = null,
    val items: List<ItemSpec> = emptyList(),
    val plan: PackingPlan? = null,
    val solving: Boolean = false,
    val inputProblems: List<InputProblem> = emptyList(),
    val packedInstanceIds: Set<String> = emptySet(),
    val guideStep: Int = 0,
    val isNew: Boolean = true,
    /** Null until the user answers "did it actually go in?" on the finished screen. */
    val realWorldFitReport: Boolean? = null,
) {
    val pieceCount: Int get() = items.sumOf { it.quantity }

    val hasUsableSpace: Boolean get() = space?.dimensions?.isValid() == true
}
