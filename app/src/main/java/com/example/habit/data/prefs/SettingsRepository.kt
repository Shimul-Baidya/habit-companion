package com.example.habit.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("settings")

/** SCR-15: theme is a three-option choice, not a boolean. */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/**
 * Preferences live in DataStore; counts live in Room (SCR-15). Every setting writes on
 * change — there is no save button anywhere in the app.
 */
class SettingsRepository(context: Context) {

    private val store = context.settingsStore

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

    suspend fun setUserName(name: String) {
        store.edit { it[Keys.USER_NAME] = name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[Keys.THEME_MODE] = mode.name }
    }

    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val USER_NAME = stringPreferencesKey("user_name")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
