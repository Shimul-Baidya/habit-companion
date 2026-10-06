package com.example.habit.ui.screens.newhabit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.habit.data.HabitRepository
import com.example.habit.data.local.HabitEntity
import com.example.habit.ui.containerFactory
import kotlinx.coroutines.launch

/**
 * Stub state holder for SCR-05. It writes a real [HabitEntity] with the schema's
 * defaults; the icon, colour, frequency and custom-day fields specified on slide 12 are
 * not collected yet.
 */
class NewHabitViewModel(private val habits: HabitRepository) : ViewModel() {

    fun create(name: String, onCreated: () -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            habits.create(HabitEntity(name = trimmed))
            onCreated()
        }
    }

    companion object {
        val Factory = containerFactory {
            NewHabitViewModel(it.habits)
        }
    }
}
