package com.example.habit.ui.screens.newhabit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.habit.data.*
import com.example.habit.coach.*
import com.example.habit.domain.DeviceClock
import com.example.habit.ui.container
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

enum class FormLoadError { READ_FAILED, UNAVAILABLE }
enum class FormSaveError { WRITE_FAILED, CONFLICT }
data class HabitFormUiState(
    val draft: HabitFormDraft,
    val editing: Boolean,
    val loading: Boolean = true,
    val loadError: FormLoadError? = null,
    val saving: Boolean = false,
    val saveError: FormSaveError? = null,
    val savedHabitId: Long? = null,
    val dirty: Boolean = false,
    val showValidation: Boolean = false,
    val duplicateName: Boolean = false,
    val confirmDuplicate: Boolean = false,
    val confirmDiscard: Boolean = false,
    val confirmReload: Boolean = false,
    val coachEnabled: Boolean = false,
    val coachReady: Boolean = false,
    val automaticCoach: Boolean = false,
) {
    val validation get() = draft.validate()
    val canSave get() = !loading && loadError == null && !saving && savedHabitId == null && validation.valid
}

/** New/Edit share one saved draft; persistence never occurs merely by entering or leaving. */
class NewHabitViewModel(
    private val forms: HabitFormDataSource,
    private val saved: SavedStateHandle,
    coachEnabled: Flow<Boolean>,
    private val clock: java.time.Clock = DeviceClock(),
) : ViewModel() {
    private val id = saved.get<Long>(ARG_HABIT_ID)?.takeIf { it > 0 }
    private val token = saved.get<String>(TOKEN) ?: UUID.randomUUID().toString().also { saved[TOKEN] = it }
    private var initial = readDraft("initial.")
    private val attempts = MutableStateFlow(0)
    private var names: List<Pair<Long, String>> = emptyList()
    private val mutableState = MutableStateFlow(HabitFormUiState(readDraft("draft."), id != null,
        savedHabitId = saved[SAVED_ID], dirty = readDraft("draft.") != initial,
        automaticCoach = saved.get<Boolean>(ARG_PLANNING) == true))
    val state = mutableState.asStateFlow()
    private val coachActions = DraftCoachActions(saved, token, clock, { state.value.draft }, ::change)
    fun beginCoach(draftToken: String, exchangeId: String, request: CoachRequest): Boolean {
        if (id != null || !state.value.coachEnabled || state.value.loading || state.value.loadError != null || state.value.saving || state.value.savedHabitId != null) return false
        coachActions.beginInteraction(draftToken, exchangeId, request)
        return true
    }
    val coachActionId = saved.getStateFlow<String?>("coach.latestAction", "")
    fun applyCoach(draftToken: String, exchangeId: String, index: Int, request: CoachRequest,
        response: ValidatedCoachResponse, catalog: StrategyCatalog): CoachApplyReceipt? {
        if (id != null || !state.value.coachEnabled || state.value.loading || state.value.loadError != null || state.value.saving || state.value.savedHabitId != null) return null
        return coachActions.apply(draftToken, exchangeId, index, request, response, catalog).also { saved["coach.latestAction"] = it.id }
    }
    fun undoCoach(actionId: String): CoachUndoResult {
        if (id != null || state.value.loading || state.value.loadError != null || state.value.saving || state.value.savedHabitId != null) return CoachUndoResult.UNAVAILABLE
        return coachActions.undo(actionId)
    }
    fun coachNow() = clock.millis()
    fun coachReceipt(actionId: String) = coachActions.receipt(actionId)

    init {
        viewModelScope.launch {
            coachEnabled.catch { emit(false) }.collect { enabled -> mutableState.update { it.copy(coachEnabled = enabled, coachReady = true) } }
        }
        viewModelScope.launch {
            attempts.collectLatest {
                mutableState.update { it.copy(loading = true, loadError = null) }
                try {
                    forms.records.collect { records ->
                        names = records.filter { it.habit.archivedAt == null }.map { it.habit.id to it.habit.name.trim() }
                        val record = id?.let { key -> records.singleOrNull { it.habit.id == key } }
                        if (id != null && (record == null || record.habit.archivedAt != null)) {
                            mutableState.update { it.copy(loading = false, loadError = FormLoadError.UNAVAILABLE) }
                        } else {
                            if (id != null && saved.get<Boolean>(INITIALIZED) != true) {
                                initial = HabitFormDraft.from(requireNotNull(record).editableDraft())
                                writeDraft("initial.", initial); writeDraft("draft.", initial)
                                saved[INITIALIZED] = true
                                mutableState.update { it.copy(draft = initial, dirty = false) }
                            }
                            mutableState.update { it.copy(loading = false, loadError = null, duplicateName = duplicate(it.draft.name)) }
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutableState.update { it.copy(loading = false, loadError = FormLoadError.READ_FAILED) } }
            }
        }
    }

    fun change(draft: HabitFormDraft) {
        if (state.value.saving || state.value.savedHabitId != null || (id != null && saved.get<Boolean>(INITIALIZED) != true)) return
        coachActions.changed(state.value.draft, draft)
        writeDraft("draft.", draft)
        mutableState.update { it.copy(draft = draft, dirty = draft != initial, saveError = null,
            showValidation = true, duplicateName = duplicate(draft.name), confirmDuplicate = false) }
    }
    fun toggleDay(day: java.time.DayOfWeek) = change(state.value.draft.copy(weekdayMask = state.value.draft.weekdayMask xor (1 shl (day.value - 1))))
    private fun duplicate(name: String) = name.isNotBlank() && names.any { (key, existing) -> key != id && existing.equals(name.trim(), ignoreCase = true) }
    fun retryLoad() { attempts.update { it + 1 } }
    fun requestReload() { mutableState.update { it.copy(confirmReload = true) } }
    fun dismissReload() { mutableState.update { it.copy(confirmReload = false) } }
    fun reload() {
        if (state.value.saving) return
        saved[INITIALIZED] = false
        mutableState.update { it.copy(confirmReload = false, saveError = null) }
        retryLoad()
    }
    fun requestBack(): Boolean {
        if (state.value.saving) return false
        if (!state.value.dirty || state.value.savedHabitId != null) return true
        mutableState.update { it.copy(confirmDiscard = true) }
        return false
    }
    fun dismissDiscard() { mutableState.update { it.copy(confirmDiscard = false) } }
    fun dismissDuplicate() { mutableState.update { it.copy(confirmDuplicate = false) } }
    fun consumeAutomaticCoach() { saved[ARG_PLANNING] = false; mutableState.update { it.copy(automaticCoach = false) } }
    fun coachEntry(): FormCoachEntry? {
        if (!state.value.coachEnabled || state.value.loading || state.value.loadError != null || state.value.saving) return null
        return if (id == null) FormCoachEntry.Planning(DraftPlanningRequest(token, state.value.draft, names.size)) else FormCoachEntry.Existing(id)
    }
    fun applyPlanning(result: DraftPlanningResult): Boolean {
        if (id != null || result.token != token || !state.value.coachEnabled || state.value.saving || state.value.savedHabitId != null) return false
        change(state.value.draft.apply(result.change))
        return true
    }

    fun save(allowDuplicate: Boolean = false) {
        val current = state.value
        if (current.saving || current.savedHabitId != null || current.loading || current.loadError != null) return
        mutableState.update { it.copy(showValidation = true) }
        if (!current.validation.valid) return
        if (current.duplicateName && !allowDuplicate) { mutableState.update { it.copy(confirmDuplicate = true) }; return }
        // Guard is set synchronously before launching, so rapid taps cannot start another save.
        mutableState.update { it.copy(saving = true, saveError = null, confirmDuplicate = false) }
        viewModelScope.launch {
            try {
                val result = forms.saveForm(id, current.draft.toHabitDraft(), if (id != null) initial.toHabitDraft() else null, allowDuplicate)
                saved[SAVED_ID] = result
                mutableState.update { it.copy(saving = false, savedHabitId = result, dirty = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: DuplicateHabitName) { mutableState.update { it.copy(saving = false, duplicateName = true, confirmDuplicate = true) } }
            catch (_: HabitFormConflict) { mutableState.update { it.copy(saving = false, saveError = FormSaveError.CONFLICT) } }
            catch (_: Exception) { mutableState.update { it.copy(saving = false, saveError = FormSaveError.WRITE_FAILED) } }
        }
    }

    private fun readDraft(prefix: String) = HabitFormDraft(
        saved[prefix + "name"] ?: "", saved[prefix + "icon"] ?: "mindful", saved[prefix + "color"] ?: "blue",
        saved[prefix + "frequency"] ?: "DAILY", saved[prefix + "quota"] ?: "3", saved[prefix + "days"] ?: 127,
        saved[prefix + "tracking"] ?: "BINARY", saved[prefix + "target"] ?: "", saved[prefix + "unit"] ?: "",
        saved[prefix + "cue"] ?: "", saved[prefix + "anchor"] ?: "", saved[prefix + "plan"] ?: "")
    private fun writeDraft(prefix: String, value: HabitFormDraft) {
        saved[prefix + "name"] = value.name; saved[prefix + "icon"] = value.iconKey; saved[prefix + "color"] = value.colorKey
        saved[prefix + "frequency"] = value.frequency; saved[prefix + "quota"] = value.quota; saved[prefix + "days"] = value.weekdayMask
        saved[prefix + "tracking"] = value.tracking; saved[prefix + "target"] = value.target; saved[prefix + "unit"] = value.unit
        saved[prefix + "cue"] = value.cue; saved[prefix + "anchor"] = value.anchor; saved[prefix + "plan"] = value.planNote
    }
    companion object {
        const val ARG_HABIT_ID = "habitId"
        const val ARG_PLANNING = "planning"
        private const val TOKEN = "draftToken"
        private const val INITIALIZED = "formInitialized"
        private const val SAVED_ID = "savedHabitId"
        val Factory = viewModelFactory { initializer {
            NewHabitViewModel(container.habitHistory, createSavedStateHandle(), container.settings.coachEnabled)
        } }
    }
}
