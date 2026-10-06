package com.example.habit

import android.app.Application
import android.content.Context
import com.example.habit.coach.StrategyRepository
import com.example.habit.coach.CoachService
import com.example.habit.coach.UnconfiguredCoachService
import com.example.habit.coach.GeminiCoachService
import com.example.habit.data.controls.*
import com.example.habit.reminders.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.example.habit.data.local.CompletionDao
import com.example.habit.data.local.HabitDao
import com.example.habit.data.local.HabitDatabase
import com.example.habit.data.prefs.SettingsRepository
import com.example.habit.data.HabitHistoryRepository
import com.example.habit.data.HabitRepository
import com.example.habit.domain.DateMonitor
import com.example.habit.domain.DeviceClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-rolled container rather than a DI framework — the graph is small enough that the
 * wiring stays readable, and there is no build-time cost.
 */
class AppContainer(context: Context) {
    val strategies by lazy { StrategyRepository { context.applicationContext.assets.open("coach_cards.json").use {
        val bytes = it.readBytes(); require(bytes.size <= 262_144); bytes
    } } }
    val coachConnection by lazy { com.example.habit.coach.CoachConnection(context.applicationContext) }
    val coachService: CoachService by lazy {
        if (BuildConfig.GEMINI_API_KEY.isBlank()) UnconfiguredCoachService()
        else GeminiCoachService(BuildConfig.GEMINI_API_KEY, BuildConfig.GEMINI_MODEL)
    }
    private val gate = DataGate()
    private val database: HabitDatabase by lazy { HabitDatabase.build(context) }

    val coachActions by lazy { com.example.habit.coach.CoachActionRepository(database, habitHistory, gate, clock,
        { (strategies.load() as? com.example.habit.coach.CatalogResult.Ready)?.catalog ?: error("Coach catalog unavailable") },
        { settings.coachEnabled.first() }) }
    val habitDao: HabitDao by lazy { database.habitDao() }
    val completionDao: CompletionDao by lazy { database.completionDao() }
    val settings: SettingsRepository by lazy { SettingsRepository(context, gate) }
    private val clock = DeviceClock()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val dates: DateMonitor by lazy { DateMonitor(clock, scope) }
    val habitHistory: HabitHistoryRepository by lazy { HabitHistoryRepository(database, clock, gate) }
    val localData: LocalDataControls by lazy { LocalDataControls(database, settings, gate, clock) { reminders.cancelAll() } }
    val reminderPlatform: AndroidReminders by lazy { AndroidReminders(context.applicationContext) }
    val reminders: ReminderController by lazy { ReminderController(habitHistory.records, { database.historyDao().records() }, settings,
        dates, gate, localData.state, reminderPlatform, clock, scope) }
    fun launch(block: suspend () -> Unit) { scope.launch { block() } }
    fun start() { launch { localData.initialize(); reminders.start() } }
    val habits: HabitRepository by lazy { HabitRepository(habitHistory) }
}

class HabitApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }
}
