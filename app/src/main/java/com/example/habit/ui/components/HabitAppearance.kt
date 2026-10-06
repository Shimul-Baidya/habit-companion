package com.example.habit.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.habit.R

data class HabitAppearanceOption(val iconKey: String, val colorKey: String, val label: Int)
val HabitAppearanceOptions = listOf(
    HabitAppearanceOption("mindful", "blue", R.string.habit_appearance_mindful),
    HabitAppearanceOption("activity", "teal", R.string.habit_appearance_activity),
    HabitAppearanceOption("book", "purple", R.string.habit_appearance_reading),
    HabitAppearanceOption("leaf", "green", R.string.habit_appearance_growth),
    HabitAppearanceOption("focus", "orange", R.string.habit_appearance_focus),
)
fun habitIcon(key: String): ImageVector = when (key) {
    "activity", "walk", "run" -> Icons.Filled.MonitorHeart
    "book" -> Icons.AutoMirrored.Filled.MenuBook
    "leaf" -> Icons.Filled.Eco
    "focus" -> Icons.Filled.Block
    else -> Icons.Outlined.WaterDrop
}
