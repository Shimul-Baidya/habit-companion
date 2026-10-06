package com.example.habit.ui.controls

import android.content.Intent
import androidx.core.net.toUri
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.*
import com.example.habit.data.controls.DataControls
import com.example.habit.data.prefs.SettingsRepository
import com.example.habit.ui.container
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.OutputStream

interface ProfileActions {
    suspend fun reminder(enabled: Boolean, minute: Int)
    suspend fun export(uri: String)
    suspend fun clear()
}
class LocalProfileActions(private val settings: SettingsRepository, private val data: DataControls,
    private val output: suspend (String) -> OutputStream) : ProfileActions {
    override suspend fun reminder(enabled: Boolean, minute: Int) = settings.setReminder(enabled, minute)
    override suspend fun export(uri: String) = data.export { output(uri) }
    override suspend fun clear() = data.clear()
}
data class ActionsState(val dialog: String? = null, val busy: Boolean = false, val error: String? = null,
    val message: String? = null, val availability: String? = null, val confirmation: String = "", val pickerPending: Boolean = false)
class ProfileActionsViewModel(private val operations: ProfileActions, private val saved: SavedStateHandle, availability: Flow<String> = flowOf("Android may delay reminders, especially during battery saving.")) : ViewModel() {
    private val mutable = MutableStateFlow(ActionsState(dialog = saved["controlDialog"], confirmation = saved["clearText"] ?: "",
        pickerPending = saved["exportPicker"] ?: false))
    val state = mutable.asStateFlow()
    init { viewModelScope.launch { availability.collect { value -> mutable.update { it.copy(availability = value) } } } }
    fun open(dialog: String?) {
        if (state.value.busy || state.value.pickerPending) return
        saved["controlDialog"] = dialog; saved["clearText"] = ""
        mutable.update { it.copy(dialog = dialog, confirmation = "", error = null, message = null) }
    }
    fun confirmation(value: String) { if (!state.value.busy) { saved["clearText"] = value; mutable.update { it.copy(confirmation = value) } } }
    fun reminder(enabled: Boolean, minute: Int) = run { operations.reminder(enabled, minute) }
    fun pick(): Boolean {
        if (state.value.busy || state.value.pickerPending) return false
        saved["exportPicker"] = true; mutable.update { it.copy(pickerPending = true, error = null) }; return true
    }
    fun picked(uri: String?) {
        if (!state.value.pickerPending) return
        saved["exportPicker"] = false; mutable.update { it.copy(pickerPending = false) }
        if (uri == null) { open(null); return }
        saved["exportUri"] = uri
        export()
    }
    fun pickerFailed() { saved["exportPicker"] = false; mutable.update { it.copy(pickerPending = false, error = "Couldn’t open the file picker. Try again.") } }
    fun export() {
        val uri = saved.get<String>("exportUri") ?: return
        run(success = "Export saved to the location you chose.", failure = "Couldn’t finish export. The selected file may be incomplete. Choose a location again to retry.") { operations.export(uri) }
    }
    fun clear() {
        if (state.value.dialog != "clear" || state.value.confirmation != "CLEAR") return
        run { operations.clear() }
    }
    private fun run(success: String? = null, failure: String = "Couldn’t save this local change. Your input is kept; try again.", operation: suspend () -> Unit) {
        if (state.value.busy || state.value.pickerPending) return
        mutable.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            try { operation(); mutable.update { it.copy(message = success) }
                if (success != null) { saved["controlDialog"] = null; mutable.update { it.copy(dialog = null) } }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mutable.update { it.copy(error = failure) }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    companion object { val Factory = viewModelFactory { initializer {
        val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as com.example.habit.HabitApplication
        val resolver = app.contentResolver
        ProfileActionsViewModel(LocalProfileActions(container.settings, container.localData) { uri ->
            requireNotNull(resolver.openOutputStream(uri.toUri(), "wt")) { "No writable destination" }
        }, createSavedStateHandle(), container.reminders.status)
    } } }
}
