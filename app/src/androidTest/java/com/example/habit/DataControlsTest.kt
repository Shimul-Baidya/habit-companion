package com.example.habit

import android.app.NotificationManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.habit.data.*
import com.example.habit.data.controls.*
import com.example.habit.data.local.*
import com.example.habit.data.prefs.*
import com.example.habit.domain.*
import com.example.habit.reminders.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.*
import java.math.BigDecimal
import java.time.*

@RunWith(AndroidJUnit4::class)
class DataControlsTest {
    private lateinit var db: HabitDatabase
    private lateinit var file: File
    private lateinit var prefs: SettingsRepository
    private lateinit var store: FaultStore
    private lateinit var gate: DataGate
    private lateinit var history: HabitHistoryRepository
    private lateinit var data: LocalDataControls
    private var cancels = 0
    private val job = SupervisorJob()
    private val day = LocalDate.of(2026, 10, 6)
    private val clock = MovingClock(day.atTime(20, 0).toInstant(ZoneOffset.UTC))
    private class MovingClock(var at: Instant) : Clock() {
        override fun instant() = at
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: ZoneId) = Clock.fixed(at, zone)
    }
    private class FaultStore(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
        var failResetFinish = false
        var failResetBegin = false
        override val data = delegate.data
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = delegate.updateData {
            val next = transform(it)
            if (failResetBegin && next[booleanPreferencesKey("reset_pending")] == true) throw IOException("begin reset failure")
            if (failResetFinish && next[booleanPreferencesKey("reset_pending")] != true && it[booleanPreferencesKey("reset_pending")] == true) throw IOException("finish reset failure")
            next
        }
    }
    private class Dates(private val day: LocalDate) : DateProvider {
        override val dates = flowOf(day); override fun today() = day; override fun refresh() {}
    }
    private class Platform : ReminderPlatform {
        var allowed = true; var failSchedule = false; var slot: ReminderSlot? = null; var generation = -1L
        val posted = mutableListOf<Long>(); var kept = emptySet<Long>()
        override fun available() = allowed
        override fun schedule(slot: ReminderSlot, generation: Long) { if (failSchedule) throw IOException("alarm unavailable"); this.slot = slot; this.generation = generation }
        override fun cancelAlarm() { slot = null }
        override fun cancelNotifications(keep: Set<Long>) { kept = keep }
        override fun post(record: HabitRecord) { posted += record.habit.id }
    }
    @Before fun setup() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, HabitDatabase::class.java).build()
        file = File(context.cacheDir, "chunk08-${System.nanoTime()}.preferences_pb")
        gate = DataGate()
        store = FaultStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file })
        prefs = SettingsRepository(store, gate)
        history = HabitHistoryRepository(db, clock, gate)
        data = LocalDataControls(db, prefs, gate, clock) { cancels++ }
        data.initialize()
    }
    @After fun close() { job.cancel(); db.close(); file.delete() }
    @Test fun exportedVersionedFactsPreserveArchivedLegacyQuantityAndPendingHistories() = runBlocking {
        prefs.setUserName("Private person"); prefs.setOnboardingComplete(true); prefs.setReminder(true, 1200)
        val id = history.create(HabitDraft("Read", HabitSettings(HabitSchedule.Weekly(3), TrackingMode.Quantity(BigDecimal("5.00"), "pages")), cue = "after breakfast"))
        history.logToday(id, day, CompletionValue.Quantity(BigDecimal("2.50"), "pages"))
        history.changeSettings(id, listOf(HabitSettingChange.Tracking(TrackingMode.Quantity(BigDecimal("4.0"), "pages"))))
        history.setReminder(id, true, 900, null, null); history.archive(id)
        prefs.markDelivered(setOf("$id@${day.toEpochDay()}"))
        val out = ByteArrayOutputStream(); data.export { out }
        val text = out.toString("UTF-8"); val root = JSONObject(text)
        assertEquals(1, root.getInt("formatVersion")); assertEquals(2, root.getInt("roomSchemaVersion"))
        assertEquals("Private person", root.getJSONObject("settings").getString("userName"))
        val habit = root.getJSONArray("habits").getJSONObject(0)
        assertEquals(id, habit.getLong("id")); assertFalse(habit.isNull("archivedAt"))
        assertEquals(900, habit.getInt("reminderMinute")); assertEquals("after breakfast", habit.getString("cue"))
        assertEquals("2.50", habit.getJSONArray("completions").getJSONObject(0).getString("quantityAmount"))
        assertEquals("5.00", habit.getJSONArray("trackingHistory").getJSONObject(0).getString("target"))
        assertEquals(2, habit.getJSONArray("trackingHistory").length())
        assertEquals(0, root.getJSONObject("coachHistory").getInt("storageVersion"))
        assertFalse(text.contains("reminder_delivered")); assertFalse(text.contains("reset_pending"))
        assertEquals(1, db.historyDao().records().size)
    }
    @Test fun failedExportAndStorageFailureLeaveLocalDataIntact() = runBlocking {
        history.create(HabitDraft("Keep"))
        assertTrue(runCatching { data.export { throw IOException("chosen provider failure") } }.isFailure)
        assertTrue(runCatching { data.export { object : OutputStream() { override fun write(b: Int) { throw IOException("full") } } } }.isFailure)
        assertEquals(1, db.historyDao().records().size); assertTrue(data.state.value is DataState.Ready)
    }
    @Test fun fullClearResetsBothStoresCancelsRemindersAndLeavesNoCascadeOrUndoRecords() = runBlocking {
        prefs.setOnboardingComplete(true); prefs.setUserName("Person"); prefs.setCoachEnabled(false); prefs.setReminder(true, 100)
        val id = history.create(HabitDraft("Remove")); history.logToday(id, day, CompletionValue.Binary(true))
        val undo = history.archive(id)
        data.clear()
        assertEquals(1, cancels); assertTrue(db.historyDao().records().isEmpty())
        assertEquals(0L, db.openHelper.readableDatabase.query("SELECT count(*) FROM completions").use { it.moveToFirst(); it.getLong(0) })
        assertEquals(0L, db.openHelper.readableDatabase.query("SELECT count(*) FROM schedule_history").use { it.moveToFirst(); it.getLong(0) })
        assertEquals(SettingsRepository.Configuration(), prefs.configuration.first())
        assertEquals(DataState.Ready(1), data.state.value); assertFalse(history.undoArchive(undo))
        val next = history.create(HabitDraft("New")); assertTrue(next > id)
    }
    @Test fun interruptedDataStoreFinishRecoversIdempotentlyBeforeWritesOrReminders() = runBlocking {
        history.create(HabitDraft("Remove")); prefs.setUserName("Reset this")
        store.failResetFinish = true
        assertTrue(runCatching { data.clear() }.isFailure)
        assertEquals(DataState.RecoveryRequired, data.state.value); assertTrue(prefs.resetState().first)
        assertTrue(runCatching { history.create(HabitDraft("Stale")) }.isFailure)
        assertTrue(runCatching { prefs.setUserName("Stale") }.isFailure)
        store.failResetFinish = false
        val freshGate = DataGate(); val freshPrefs = SettingsRepository(store, freshGate)
        val fresh = LocalDataControls(db, freshPrefs, freshGate, clock) { cancels++ }
        fresh.initialize()
        assertEquals(DataState.Ready(1), fresh.state.value); assertFalse(freshPrefs.resetState().first)
        assertTrue(db.historyDao().records().isEmpty()); assertEquals(SettingsRepository.Configuration(), freshPrefs.configuration.first())
    }
    @Test fun failedResetIntentDoesNotEraseDataOnRecovery() = runBlocking {
        history.create(HabitDraft("Retain")); prefs.setUserName("Retain person")
        store.failResetBegin = true
        assertTrue(runCatching { data.clear() }.isFailure)
        assertFalse(prefs.resetState().first); assertEquals(DataState.RecoveryRequired, data.state.value)
        store.failResetBegin = false; data.initialize()
        assertEquals(DataState.Ready(0), data.state.value); assertEquals(0, cancels)
        assertEquals("Retain person", prefs.configuration.first().userName); assertEquals(1, db.historyDao().records().size)
    }
    @Test fun failedRoomClearRollsBackAndRecoveryMarkerPreventsPartialUse() = runBlocking {
        history.create(HabitDraft("Keep until retry"))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_clear BEFORE DELETE ON habits BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        assertTrue(runCatching { data.clear() }.isFailure)
        assertEquals(1, db.historyDao().records().size); assertTrue(prefs.resetState().first)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_clear")
        data.initialize(); assertEquals(DataState.Ready(1), data.state.value); assertTrue(db.historyDao().records().isEmpty())
    }
    private fun controller(platform: ReminderPlatform) = ReminderController(history.records, { db.historyDao().records() }, prefs, Dates(day), gate, data.state, platform, clock, CoroutineScope(job + Dispatchers.IO))
    @Test fun dispatchRechecksPermissionEligibilityAndDuplicateLedgerAcrossControllerRecreation() = runBlocking {
        prefs.setReminder(true, 1200)
        val pending = history.create(HabitDraft("Pending")); val done = history.create(HabitDraft("Done"))
        history.logToday(done, day, CompletionValue.Binary(true))
        clock.at = day.minusDays(1).atTime(12, 0).toInstant(ZoneOffset.UTC)
        val weekly = history.create(HabitDraft("Weekly", HabitSettings(HabitSchedule.Weekly(2))))
        history.logToday(weekly, day.minusDays(1), CompletionValue.Binary(true))
        val quantity = history.create(HabitDraft("Quantity", HabitSettings(HabitSchedule.Custom(setOf(day.dayOfWeek)), TrackingMode.Quantity(BigDecimal("5"), "pages"))))
        history.create(HabitDraft("Rest", HabitSettings(HabitSchedule.Custom(setOf(java.time.DayOfWeek.SUNDAY)))))
        clock.at = day.atTime(20, 0).toInstant(ZoneOffset.UTC)
        history.logToday(quantity, day, CompletionValue.Quantity(BigDecimal("2"), "pages"))
        val platform = Platform(); val controller = controller(platform)
        platform.allowed = false; controller.refresh(); assertNull(platform.slot); assertTrue(controller.status.value.contains("blocked"))
        controller.deliver(0, day, 1200, clock.millis()); assertTrue(platform.posted.isEmpty())
        platform.allowed = true; platform.failSchedule = true; controller.refresh(); assertNull(platform.slot); assertTrue(controller.status.value.contains("Couldn’t"))
        platform.failSchedule = false; controller.refresh(); assertEquals(day, platform.slot!!.date)
        controller.deliver(0, day, 1200, clock.millis()); controller(platform).deliver(0, day, 1200, clock.millis())
        assertEquals(setOf(pending, weekly, quantity), platform.posted.toSet()); assertEquals(3, platform.posted.size)
        history.logToday(pending, day, CompletionValue.Binary(true)); history.logToday(weekly, day, CompletionValue.Binary(true)); history.logToday(quantity, day, CompletionValue.Quantity(BigDecimal("5"), "pages")); controller.refresh(); assertTrue(platform.kept.isEmpty())
    }
    @Test fun overrideChangeIsFieldScopedConflictsAreRejectedAndArchiveDeleteCancel() = runBlocking {
        prefs.setReminder(true, 1200); val id = history.create(HabitDraft("Keep name", cue = "keep cue"))
        history.setReminder(id, true, 1260, null, null)
        assertTrue(runCatching { history.setReminder(id, false, null, null, null) }.isFailure)
        assertEquals("keep cue", history.record(id)!!.habit.cue)
        val platform = Platform(); val controller = controller(platform)
        controller.refresh(); assertEquals(1260, platform.slot!!.minute)
        history.archive(id); controller.refresh(); assertNull(platform.slot); assertTrue(platform.kept.isEmpty())
        history.delete(id); controller.refresh(); assertNull(platform.slot)
    }
    @Test fun staleGenerationTimeZoneOrYesterdayAlarmCannotPostAfterWipe() = runBlocking {
        prefs.setReminder(true, 1200); history.create(HabitDraft("Pending"))
        val p = Platform(); val c = controller(p)
        c.deliver(0, day.minusDays(1), 1200, clock.millis()); c.deliver(0, day, 1200, clock.millis() - 3600000)
        assertTrue(p.posted.isEmpty())
        data.clear(); prefs.setReminder(true, 1200); history.create(HabitDraft("New pending"))
        c.deliver(0, day, 1200, clock.millis()); assertTrue(p.posted.isEmpty())
        c.deliver(1, day, 1200, clock.millis()); assertEquals(1, p.posted.size)
    }
    @Test fun realAndroidInexactAlarmRegistersAndCancelsWithIsolatedIdentity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation(); val context = instrumentation.targetContext
        val platform = AndroidReminders(context, "chunk08qa", "habit_reminders_qa")
        try {
            platform.schedule(ReminderSlot(Instant.now().plusSeconds(86400), LocalDate.now().plusDays(1), 1200), 77)
            fun dump() = instrumentation.uiAutomation.executeShellCommand("dumpsys alarm").use { descriptor -> FileInputStream(descriptor.fileDescriptor).bufferedReader().readText().substringBefore("Removal history:") }
            assertTrue(dump().contains("com.example.habit.REMIND.chunk08qa"))
            platform.cancelAlarm(); assertFalse(dump().contains("*walarm*:com.example.habit.REMIND.chunk08qa"))
        } finally { platform.cancelAlarm(); platform.cancelNotifications(emptySet()); context.getSystemService(NotificationManager::class.java).deleteNotificationChannel("habit_reminders_qa") }
    }
}
