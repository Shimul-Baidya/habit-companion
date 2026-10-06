package com.example.habit.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("settings")

/** SCR-15: theme is a three-option choice, not a boolean. */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/**
 * Preferences live in DataStore; counts live in Room (SCR-15). Every setting writes on
 * change — there is no save button anywhere in the app.
 */
interface ProfileSettings {
    val configuration: Flow<SettingsRepository.Configuration>
    suspend fun setUserName(name: String)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setWeekStart(day: DayOfWeek)
    suspend fun setCoachEnabled(enabled: Boolean)
}

class SettingsRepository(private val store: DataStore<Preferences>) : ProfileSettings {
    constructor(context: Context) : this(context.settingsStore)

    data class Configuration(
        val onboardingComplete: Boolean = false,
        val userName: String = "",
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val weekStart: DayOfWeek = DayOfWeek.MONDAY,
        val coachEnabled: Boolean = true,
        val reminderEnabled: Boolean = false,
        val reminderMinute: Int = 20 * 60,
    )

    override val configuration: Flow<Configuration> = store.data.map { prefs ->
        Configuration(prefs[Keys.ONBOARDING_COMPLETE] ?: false, prefs[Keys.USER_NAME] ?: "",
            prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            prefs[Keys.WEEK_START]?.let { runCatching { DayOfWeek.of(it) }.getOrNull() } ?: DayOfWeek.MONDAY,
            prefs[Keys.COACH_ENABLED] ?: true, prefs[Keys.REMINDER_ENABLED] ?: false,
            prefs[Keys.REMINDER_MINUTE]?.takeIf { it in 0..1439 } ?: 20 * 60)
    }
    val weekStart: Flow<DayOfWeek> = configuration.map { it.weekStart }
    val coachEnabled: Flow<Boolean> = configuration.map { it.coachEnabled }
    val reminderEnabled: Flow<Boolean> = configuration.map { it.reminderEnabled }
    val reminderMinute: Flow<Int> = configuration.map { it.reminderMinute }

    override suspend fun setWeekStart(day: DayOfWeek) { store.edit { it[Keys.WEEK_START] = day.value } }
    override suspend fun setCoachEnabled(enabled: Boolean) { store.edit { it[Keys.COACH_ENABLED] = enabled } }
    suspend fun setReminder(enabled: Boolean, minute: Int) {
        require(minute in 0..1439)
        store.edit { it[Keys.REMINDER_ENABLED] = enabled; it[Keys.REMINDER_MINUTE] = minute }
    }

    /** SCR-01 routes on this; SCR-02 writes it on skip or on finishing the last pane. */
    val onboardingComplete: Flow<Boolean> =
        store.data.map { it[Keys.ONBOARDING_COMPLETE] ?: false }

    /** Used by the SCR-04 greeting and the SCR-15 avatar initials. */
    val userName: Flow<String> = store.data.map { it[Keys.USER_NAME] ?: "" }

    val themeMode: Flow<ThemeMode> = store.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        store.edit { it[Keys.ONBOARDING_COMPLETE] = complete }
    }

    override suspend fun setUserName(name: String) {
        store.edit { it[Keys.USER_NAME] = name }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[Keys.THEME_MODE] = mode.name }
    }

    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val USER_NAME = stringPreferencesKey("user_name")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val WEEK_START = intPreferencesKey("week_start")
        val COACH_ENABLED = booleanPreferencesKey("coach_enabled")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
    }
}
