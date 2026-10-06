package com.example.habit.ui.screens.profile

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.habit.data.*
import com.example.habit.data.prefs.*
import com.example.habit.domain.DateProvider
import com.example.habit.ui.container
import com.example.habit.ui.screens.insights.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.DayOfWeek
import java.util.Locale

fun profileInitials(name: String): String = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    .let { parts -> if (parts.isEmpty()) "HC" else (parts.take(1) + if (parts.size > 1) parts.takeLast(1) else emptyList())
        .joinToString("") { String(Character.toChars(it.codePointAt(0))) }.uppercase(Locale.ROOT) }

data class ProfileUiState(
    val read: SnapshotRead = SnapshotRead(), val preferences: SettingsRepository.Configuration? = null,
    val preferenceError: Boolean = false, val writing: Boolean = false, val writeError: Boolean = false,
    val editingName: Boolean = false, val nameInput: String = "", val invalidName: Boolean = false,
    val dialog: String? = null,
)
class ProfileViewModel(habits: HabitDataSource, private val settings: ProfileSettings, dates: DateProvider,
    private val saved: SavedStateHandle) : ViewModel() {
    private val reader = SnapshotReader(viewModelScope, habits, settings.configuration, dates)
    private val attempts = MutableStateFlow(0)
    private val mutable = MutableStateFlow(ProfileUiState(editingName = saved["editingName"] ?: false,
        nameInput = saved["nameInput"] ?: "", dialog = saved["profileDialog"]))
    val state = combine(mutable, reader.state) { local, read -> local.copy(read = read) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, mutable.value)
    init { viewModelScope.launch { attempts.collectLatest {
        try { settings.configuration.collect { prefs -> mutable.update { it.copy(preferences = prefs, preferenceError = false) } }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { mutable.update { it.copy(preferenceError = true) } }
    } } }
    fun retry() { reader.retry(); attempts.update { it + 1 } }
    fun openDialog(dialog: String?) {
        if (mutable.value.writing) return
        saved["profileDialog"] = dialog; mutable.update { it.copy(dialog = dialog, writeError = false) }
    }
    fun editName() {
        val current = mutable.value
        if (current.writing || current.preferences == null || current.preferenceError) return
        saved["editingName"] = true; saved["nameInput"] = current.preferences.userName
        mutable.update { it.copy(editingName = true, nameInput = current.preferences.userName, invalidName = false, writeError = false) }
    }
    fun changeName(value: String) {
        if (!mutable.value.editingName || mutable.value.writing) return
        saved["nameInput"] = value; mutable.update { it.copy(nameInput = value, invalidName = false, writeError = false) }
    }
    fun cancelName() {
        if (mutable.value.writing) return
        saved["editingName"] = false; mutable.update { it.copy(editingName = false, invalidName = false, writeError = false) }
    }
    fun finishName() {
        val current = mutable.value
        if (!current.editingName) return
        val name = current.nameInput.trim()
        if (name.length > 80 || name.any { it.isISOControl() }) { mutable.update { it.copy(invalidName = true) }; return }
        write({ settings.setUserName(name) }, { it.copy(userName = name) }, nameSaved = true)
    }
    fun theme(mode: ThemeMode) = write({ settings.setThemeMode(mode) }, { it.copy(themeMode = mode) })
    fun weekStart(day: DayOfWeek) = write({ settings.setWeekStart(day) }, { it.copy(weekStart = day) })
    fun coach(enabled: Boolean) = write({ settings.setCoachEnabled(enabled) }, { it.copy(coachEnabled = enabled) })
    private fun write(operation: suspend () -> Unit, update: (SettingsRepository.Configuration) -> SettingsRepository.Configuration,
        nameSaved: Boolean = false) {
        val before = mutable.value
        if (before.writing || before.preferenceError || before.preferences == null) return
        mutable.update { it.copy(writing = true, writeError = false) }
        viewModelScope.launch {
            try {
                operation()
                saved["profileDialog"] = null
                if (nameSaved) saved["editingName"] = false
                // Apply only the field confirmed by the write, keeping other observed preferences.
                mutable.update { it.copy(preferences = update(requireNotNull(it.preferences)), dialog = null,
                    editingName = if (nameSaved) false else it.editingName) }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { mutable.update { it.copy(writeError = true) }
            } finally { mutable.update { it.copy(writing = false) } }
        }
    }
    companion object { val Factory = viewModelFactory { initializer {
        ProfileViewModel(container.habits, container.settings, container.dates, createSavedStateHandle())
    } } }
}
