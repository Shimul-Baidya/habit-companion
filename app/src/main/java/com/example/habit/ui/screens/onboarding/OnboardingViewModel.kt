package com.example.habit.ui.screens.onboarding

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.habit.R
import com.example.habit.ui.container
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OnboardingPane(@param:StringRes val titleRes: Int, @param:StringRes val bodyRes: Int)
val OnboardingPanes = listOf(
    OnboardingPane(R.string.onboarding_title_1, R.string.onboarding_body_1),
    OnboardingPane(R.string.onboarding_title_2, R.string.onboarding_body_2),
    OnboardingPane(R.string.onboarding_title_3, R.string.onboarding_body_3),
)
data class OnboardingState(val saving: Boolean = false, val failed: Boolean = false, val finished: Boolean = false)

/** Navigation observes success only after DataStore completes; failure stays on this pager. */
class OnboardingViewModel(private val writeComplete: suspend () -> Unit, private val saved: SavedStateHandle) : ViewModel() {
    private val mutable = MutableStateFlow(OnboardingState(finished = saved["setupFinished"] ?: false))
    val state = mutable.asStateFlow()
    fun finishOnboarding() {
        if (state.value.saving || state.value.finished) return
        mutable.value = OnboardingState(saving = true)
        viewModelScope.launch {
            try {
                writeComplete()
                saved["setupFinished"] = true
                mutable.value = OnboardingState(finished = true)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { mutable.value = OnboardingState(failed = true) }
        }
    }
    companion object {
        val Factory = viewModelFactory { initializer {
            val settings = container.settings
            OnboardingViewModel({ settings.setOnboardingComplete(true) }, createSavedStateHandle())
        } }
    }
}
