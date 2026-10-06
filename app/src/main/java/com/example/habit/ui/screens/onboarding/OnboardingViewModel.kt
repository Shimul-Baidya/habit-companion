package com.example.habit.ui.screens.onboarding

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.habit.R
import com.example.habit.data.prefs.SettingsRepository
import com.example.habit.ui.containerFactory
import kotlinx.coroutines.launch

/** One pane of SCR-02. Three of them, and no more — the pager count is fixed. */
data class OnboardingPane(
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int,
)

val OnboardingPanes = listOf(
    OnboardingPane(R.string.onboarding_title_1, R.string.onboarding_body_1),
    OnboardingPane(R.string.onboarding_title_2, R.string.onboarding_body_2),
    OnboardingPane(R.string.onboarding_title_3, R.string.onboarding_body_3),
)

/**
 * SCR-02 — three panes that explain the one interaction the app depends on: a daily tap.
 *
 * Both exits from this screen, Skip and finishing the last pane, write the same flag, so
 * SCR-01 never routes back here.
 */
class OnboardingViewModel(private val settings: SettingsRepository) : ViewModel() {

    fun finishOnboarding(onFinished: () -> Unit) {
        viewModelScope.launch {
            settings.setOnboardingComplete(true)
            onFinished()
        }
    }

    companion object {
        val Factory = containerFactory { OnboardingViewModel(it.settings) }
    }
}
