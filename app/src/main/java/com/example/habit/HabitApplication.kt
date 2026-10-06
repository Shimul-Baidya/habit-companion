package com.example.habit

import android.app.Application
import android.content.Context
import com.example.habit.data.local.CompletionDao
import com.example.habit.data.local.HabitDao
import com.example.habit.data.local.HabitDatabase
import com.example.habit.data.prefs.SettingsRepository

/**
 * Hand-rolled container rather than a DI framework — the graph is small enough that the
 * wiring stays readable, and there is no build-time cost.
 */
class AppContainer(context: Context) {
    private val database: HabitDatabase by lazy { HabitDatabase.build(context) }

    val habitDao: HabitDao by lazy { database.habitDao() }
    val completionDao: CompletionDao by lazy { database.completionDao() }
    val settings: SettingsRepository by lazy { SettingsRepository(context) }
}

class HabitApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
