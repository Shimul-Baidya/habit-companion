package com.example.habit

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.habit.coach.*
import com.example.habit.data.*
import com.example.habit.data.controls.*
import com.example.habit.data.local.*
import com.example.habit.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.*
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal
import java.time.*
import java.io.ByteArrayOutputStream

class CoachActionsPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: HabitDatabase
    private lateinit var history: HabitHistoryRepository
    private lateinit var coach: CoachActionRepository
    private lateinit var gate: DataGate
    private val day = LocalDate.of(2026, 10, 7)
    private var enabled = true
    private var now = day.minusDays(2).atTime(12, 0).toInstant(ZoneOffset.UTC)
    private val clock = object : Clock() {
        override fun instant() = now
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: ZoneId) = Clock.fixed(now, zone)
    }
    private val catalog = StrategyCatalog.validated((1..3).map { StrategyCard("card_$it", "Card", "Principle", "Prose", "Starting",
        listOf("simplicity", "cues", "planning", "scheduling"), "Attribution") }, byteArrayOf(1))
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, HabitDatabase::class.java).build()
        gate = DataGate(); history = HabitHistoryRepository(db, clock, gate); coach = repository()
    }
    @After fun close() { db.close() }
    private fun repository() = CoachActionRepository(db, history, gate, clock, { catalog }, { enabled })
    private suspend fun habit(schedule: HabitSchedule = HabitSchedule.Daily, quantity: Boolean = false): Long {
        now = day.minusDays(2).atTime(12, 0).toInstant(ZoneOffset.UTC)
        val id = history.create(HabitDraft("Test habit", HabitSettings(schedule,
            if (quantity) TrackingMode.Quantity(BigDecimal("5"), "pages") else TrackingMode.Binary), cue = "old cue", anchor = "old anchor", planNote = "old plan"))
        now = day.atTime(12, 0).toInstant(ZoneOffset.UTC)
        return id
    }
    private fun json(vararg pairs: Pair<String, Any?>) = JSONObject().apply { pairs.forEach { put(it.first, it.second ?: JSONObject.NULL) } }
    private fun action(value: CoachAction): JSONObject = when (value) {
        CoachAction.AdviceOnly -> json("type" to "ADVICE_ONLY")
        is CoachAction.Target -> json("type" to "TARGET", "amount" to value.amount, "unit" to value.unit)
        is CoachAction.Plan -> json("type" to "PLAN", "note" to value.note)
        is CoachAction.CueAnchor -> json("type" to "CUE_ANCHOR", "cue" to value.cue, "anchor" to value.anchor)
        is CoachAction.Schedule -> json("type" to "SCHEDULE", "schedule" to when (val s = value.value) {
            HabitSchedule.Daily -> json("kind" to "DAILY")
            is HabitSchedule.Weekly -> json("kind" to "WEEKLY", "quota" to s.completions)
            is HabitSchedule.Custom -> json("kind" to "CUSTOM", "weekdays" to JSONArray(s.weekdays.map { it.value }.sorted()))
        })
        is CoachAction.Reminder -> json("type" to "REMINDER", "setting" to when (val s = value.setting) {
            ReminderSetting.Inherit -> json("mode" to "INHERIT")
            ReminderSetting.Off -> json("mode" to "OFF")
            is ReminderSetting.At -> json("mode" to "AT", "minuteOfDay" to s.minuteOfDay)
        })
        is CoachAction.NewHabitCount -> json("type" to "NEW_HABIT_COUNT", "count" to value.count)
    }
    private suspend fun request(id: Long): CoachRequest = (CoachRequestBuilder.existing(catalog, history.record(id)!!.toHistory(), day,
        "simplicity planning cues scheduling", true) as RequestResult.Ready).request
    private fun response(request: CoachRequest, value: CoachAction): String {
        val summary = (request.context as CoachContext.Existing).summary
        val fact = if (summary.missed > 0) "RECENT_MISSES" else if (summary.pending > 0) "OPEN_EXPECTATIONS" else "RECENT_COMPLETIONS"
        return json("reading" to json("facts" to JSONArray(listOf(fact)), "possibleBarrier" to null),
            "suggestions" to JSONArray(request.strategies.mapIndexed { i, s -> json("strategyId" to s.card.id, "title" to "Suggestion",
                "advice" to "If it fits, try this", "action" to action(if (i == 0) value else CoachAction.AdviceOnly)) })).toString()
    }
    private suspend fun exchange(id: Long, value: CoachAction, key: String = "exchange"): Pair<String, CoachRequest> {
        val r = request(id); coach.beginInteraction(id, key, r); coach.saveExchange(id, key, r, response(r, value)); return key to r
    }
    private suspend fun apply(id: Long, value: Pair<String, CoachRequest>) = coach.apply(id, value.first, 0, value.second)

    @Test fun eachExistingActionHasAnImmediateScopedEffectAndExactInverse() = runBlocking {
        val values = listOf(CoachAction.Target("2.50", "pages"), CoachAction.Schedule(HabitSchedule.Daily), CoachAction.Schedule(HabitSchedule.Weekly(3)),
            CoachAction.Schedule(HabitSchedule.Custom(setOf(DayOfWeek.FRIDAY))), CoachAction.CueAnchor("new cue", "new anchor"),
            CoachAction.Plan("new plan"), CoachAction.Reminder(ReminderSetting.Off), CoachAction.Reminder(ReminderSetting.At(600)))
        values.forEachIndexed { i, value ->
            val id = habit(schedule = if (value == CoachAction.Schedule(HabitSchedule.Daily)) HabitSchedule.Weekly(3) else HabitSchedule.Daily, quantity = true); val before = history.record(id)!!
            val receipt = apply(id, exchange(id, value, "exchange_$i"))
            assertEquals("APPLIED", receipt.status); assertNotEquals(before, history.record(id))
            assertEquals(CoachUndoResult.UNDONE, coach.undo(receipt.id)); assertEquals(before, history.record(id))
        }
        val id = habit(); history.setReminder(id, true, 900, null, null)
        val before = history.record(id)!!
        val receipt = apply(id, exchange(id, CoachAction.Reminder(ReminderSetting.Inherit), "inherit"))
        assertNull(history.record(id)!!.habit.reminderEnabled); assertEquals(CoachUndoResult.UNDONE, coach.undo(receipt.id)); assertEquals(before, history.record(id))
    }
    @Test fun datedTargetAndWeeklyChangesPreserveLogsAndRestoreReplacedPendingRevision() = runBlocking {
        val id = habit(HabitSchedule.Weekly(3), true)
        history.logToday(id, day, CompletionValue.Quantity(BigDecimal("2.0"), "pages"))
        history.changeSettings(id, listOf(HabitSettingChange.Tracking(TrackingMode.Quantity(BigDecimal("4.0"), "pages"))))
        val original = history.record(id)!!
        val receipt = apply(id, exchange(id, CoachAction.Target("1.5", "pages")))
        val after = history.record(id)!!.toHistory()
        assertEquals(BigDecimal("5"), (after.settingsOn(day)!!.tracking as TrackingMode.Quantity).target)
        assertEquals(BigDecimal("1.5"), (after.settingsOn(day.plusDays(1))!!.tracking as TrackingMode.Quantity).target)
        assertEquals(original.completions, history.record(id)!!.completions)
        assertEquals(CoachUndoResult.UNDONE, coach.undo(receipt.id)); assertEquals(original, history.record(id))
        history.changeSettings(id, listOf(HabitSettingChange.Schedule(HabitSchedule.Weekly(2))))
        val withPending = history.record(id)!!
        val weekly = apply(id, exchange(id, CoachAction.Schedule(HabitSchedule.Weekly(1)), "weekly"))
        assertEquals(day.plusDays(5).toEpochDay(), history.record(id)!!.schedules.maxBy { it.effectiveDay }.effectiveDay)
        assertEquals(CoachUndoResult.UNDONE, coach.undo(weekly.id)); assertEquals(withPending, history.record(id))
    }
    @Test fun laterUnrelatedEditsAndPendingQuantityAmountSurviveUndo() = runBlocking {
        val id = habit(quantity = true)
        val receipt = apply(id, exchange(id, CoachAction.Target("2", "pages")))
        history.logToday(id, day, CompletionValue.Quantity(BigDecimal("3.25"), "pages"))
        val current = history.record(id)!!.habit
        history.updateMetadata(id, HabitMetadata("Renamed", current.iconKey, current.colorKey, "manual cue", current.anchor, current.planNote))
        assertEquals(CoachUndoResult.UNDONE, coach.undo(receipt.id))
        val record = history.record(id)!!; assertEquals("Renamed", record.habit.name); assertEquals("manual cue", record.habit.cue)
        assertEquals("3.25", record.completions.single().quantityAmount); assertEquals(1, record.tracking.size)
    }
    @Test fun changedSameFieldIncludingAbaBlocksUndoAndDoesNotOverwriteLaterEdit() = runBlocking {
        val id = habit(); val receipt = apply(id, exchange(id, CoachAction.Plan("applied")))
        val h = history.record(id)!!.habit
        history.updateMetadata(id, HabitMetadata(h.name, h.iconKey, h.colorKey, h.cue, h.anchor, "manual"))
        history.updateMetadata(id, HabitMetadata(h.name, h.iconKey, h.colorKey, h.cue, h.anchor, "applied"))
        assertEquals(CoachUndoResult.CONFLICT, coach.undo(receipt.id)); assertEquals("applied", history.record(id)!!.habit.planNote)
    }
    @Test fun laterTrackingAndScheduleEditsIncludingChangeBackRejectTheirOwnUndo() = runBlocking {
        val id = habit(quantity = true)
        val target = apply(id, exchange(id, CoachAction.Target("2", "pages"), "target"))
        history.changeSettings(id, listOf(HabitSettingChange.Tracking(TrackingMode.Quantity(BigDecimal("3"), "pages"))))
        history.changeSettings(id, listOf(HabitSettingChange.Tracking(TrackingMode.Quantity(BigDecimal("2"), "pages"))))
        assertEquals(CoachUndoResult.CONFLICT, coach.undo(target.id))
        val schedule = apply(id, exchange(id, CoachAction.Schedule(HabitSchedule.Weekly(1)), "schedule"))
        history.changeSettings(id, listOf(HabitSettingChange.Schedule(HabitSchedule.Weekly(2))))
        history.changeSettings(id, listOf(HabitSettingChange.Schedule(HabitSchedule.Weekly(1))))
        assertEquals(CoachUndoResult.CONFLICT, coach.undo(schedule.id))
        assertEquals(BigDecimal("2"), (history.record(id)!!.editableDraft().settings.tracking as TrackingMode.Quantity).target)
        assertEquals(HabitSchedule.Weekly(1), history.record(id)!!.editableDraft().settings.schedule)
    }
    @Test fun targetUndoKeepsALaterPendingScheduleAndAdviceNeverReportsAGoalChange() = runBlocking {
        val id = habit(quantity = true)
        val target = apply(id, exchange(id, CoachAction.Target("2", "pages"), "target"))
        history.changeSettings(id, listOf(HabitSettingChange.Schedule(HabitSchedule.Weekly(3))))
        assertEquals(CoachUndoResult.UNDONE, coach.undo(target.id))
        assertEquals(HabitSchedule.Weekly(3), history.record(id)!!.editableDraft().settings.schedule)
        assertEquals(BigDecimal("5"), (history.record(id)!!.editableDraft().settings.tracking as TrackingMode.Quantity).target)
        val before = history.record(id)
        val advice = apply(id, exchange(id, CoachAction.AdviceOnly, "advice"))
        assertEquals("ADVICE", advice.status); assertFalse(advice.canUndo(clock.millis()))
        assertEquals(CoachUndoResult.NO_CHANGE, coach.undo(advice.id)); assertEquals(before, history.record(id))
        assertTrue(advice.confirmation.contains("no habit setting changed"))
        val same = apply(id, exchange(id, CoachAction.Plan("old plan"), "same"))
        assertEquals("UNCHANGED", same.status); assertEquals(CoachUndoResult.NO_CHANGE, coach.undo(same.id)); assertEquals(before, history.record(id))
    }
    @Test fun untouchedCueAnchorFieldsAndLaterReminderArePreserved() = runBlocking {
        val id = habit(); val receipt = apply(id, exchange(id, CoachAction.CueAnchor("new", "old anchor")))
        val h = history.record(id)!!.habit
        history.updateMetadata(id, HabitMetadata(h.name, h.iconKey, h.colorKey, h.cue, "manual anchor", h.planNote))
        history.setReminder(id, true, 1200, null, null)
        assertEquals(CoachUndoResult.UNDONE, coach.undo(receipt.id)); val after = history.record(id)!!.habit
        assertEquals("old cue", after.cue); assertEquals("manual anchor", after.anchor); assertEquals(1200, after.reminderMinute)
    }
    @Test fun duplicateConcurrentApplyUndoAndRepositoryRecreationKeepIdentityAndDeadline() = runBlocking {
        val id = habit(); val x = exchange(id, CoachAction.Plan("new"))
        val receipts = coroutineScope { (1..4).map { async(Dispatchers.Default) { apply(id, x) } }.awaitAll() }
        assertEquals(1, receipts.distinct().size); assertEquals(1, db.coachDao().allActions().size)
        assertEquals(1, db.coachDao().allMessages().count { it.role == "APPLIED" })
        now = now.plusMillis(9_999); coach = repository()
        assertTrue(receipts.first().canUndo(clock.millis())); assertEquals(CoachUndoResult.UNDONE, coach.undo(receipts.first().id))
        assertEquals(CoachUndoResult.ALREADY_UNDONE, coach.undo(receipts.first().id)); assertEquals("UNDONE", apply(id, x).status)
    }
    @Test fun exactDeadlineRollbackAndMidnightProtectHistoricalMeaning() = runBlocking {
        val id = habit(); val receipt = apply(id, exchange(id, CoachAction.Plan("new")))
        now = now.plusMillis(10_000); assertEquals(CoachUndoResult.EXPIRED, coach.undo(receipt.id))
        val other = apply(id, exchange(id, CoachAction.CueAnchor("new", ""), "rollback")); now = now.minusMillis(1)
        assertEquals(CoachUndoResult.EXPIRED, coach.undo(other.id))
        val q = habit(quantity = true); now = day.atTime(23, 59, 59).toInstant(ZoneOffset.UTC)
        val target = apply(q, exchange(q, CoachAction.Target("2", "pages"), "midnight"))
        now = now.plusMillis(2_000)
        history.logToday(q, day.plusDays(1), CompletionValue.Quantity(BigDecimal("2"), "pages"))
        assertEquals(CoachUndoResult.CONFLICT, coach.undo(target.id)); assertEquals("2", history.record(q)!!.completions.single().quantityAmount)
    }
    @Test fun applyAndUndoFailureRollBackBothSettingsAndReceiptsAndRetryIsSafe() = runBlocking {
        val id = habit(); val before = history.record(id)!!; val x = exchange(id, CoachAction.Plan("new"))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_action BEFORE INSERT ON coach_actions BEGIN SELECT RAISE(ABORT, 'test'); END")
        assertTrue(runCatching { apply(id, x) }.isFailure); assertEquals(before, history.record(id)); assertTrue(db.coachDao().allActions().isEmpty())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_action")
        val receipt = apply(id, x)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_undo BEFORE INSERT ON coach_messages WHEN NEW.role = 'UNDONE' BEGIN SELECT RAISE(ABORT, 'test'); END")
        assertTrue(runCatching { coach.undo(receipt.id) }.isFailure); assertEquals("new", history.record(id)!!.habit.planNote)
        assertEquals("APPLIED", db.coachDao().actionById(receipt.id)!!.status)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_undo")
        assertEquals(CoachUndoResult.UNDONE, coach.undo(receipt.id)); assertEquals(before, history.record(id))
    }
    @Test fun lateSameFieldEditAfterReservationRejectsApplyAndClearInvalidatesLateResponse() = runBlocking {
        val id = habit(); val r = request(id); coach.beginInteraction(id, "reserved", r)
        val h = history.record(id)!!.habit
        history.updateMetadata(id, HabitMetadata(h.name, h.iconKey, h.colorKey, h.cue, h.anchor, "manual"))
        coach.saveExchange(id, "reserved", r, response(r, CoachAction.Plan("new")))
        assertTrue(runCatching { coach.apply(id, "reserved", 0, r) }.isFailure); assertEquals("manual", history.record(id)!!.habit.planNote)
        coach.beginInteraction(id, "late", r); coach.clearHistory()
        assertTrue(runCatching { coach.saveExchange(id, "late", r, response(r, CoachAction.AdviceOnly)) }.isFailure)
        assertTrue(db.coachDao().allMessages().isEmpty()); assertTrue(db.coachDao().allCaches().isEmpty())
    }
    @Test fun unknownIncompatibleUnsupportedAndWrongHabitResponsesCannotMutate() = runBlocking {
        val id = habit(); val other = habit(); val before = history.record(id)
        val r = request(id); coach.beginInteraction(id, "bad", r)
        val good = response(r, CoachAction.AdviceOnly)
        listOf(good.replace("ADVICE_ONLY", "EXECUTE_PROSE"), good.replace("card_1", "unknown"),
            response(r, CoachAction.Target("2", "pages")), response(r, CoachAction.NewHabitCount(1)),
            response(r, CoachAction.Reminder(ReminderSetting.At(1440)))).forEach {
            assertTrue(runCatching { coach.saveExchange(id, "bad", r, it) }.isFailure)
        }
        coach.saveExchange(id, "bad", r, good)
        assertTrue(runCatching { coach.apply(other, "bad", 0, r) }.isFailure)
        assertTrue(runCatching { coach.apply(id, "bad", 3, r) }.isFailure)
        assertEquals(before, history.record(id)); assertTrue(db.coachDao().allActions().isEmpty())
    }
    @Test fun disabledArchivedDeletedAndClearedActionsCannotReturnOrMutate() = runBlocking {
        val id = habit(); val x = exchange(id, CoachAction.Plan("new")); enabled = false
        assertTrue(runCatching { apply(id, x) }.isFailure)
        enabled = true; val receipt = apply(id, x); history.archive(id)
        assertEquals(CoachUndoResult.UNAVAILABLE, coach.undo(receipt.id))
        history.delete(id); assertEquals(CoachUndoResult.UNAVAILABLE, coach.undo(receipt.id))
        assertTrue(db.coachDao().allMessages().isEmpty()); assertTrue(db.coachDao().allCaches().isEmpty()); assertTrue(db.coachDao().allActions().isEmpty())
        db.openHelper.readableDatabase.query("SELECT count(*) FROM habit_field_versions").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
    }
    @Test fun threadCapCacheProvenanceAndClearPreserveAppliedSettingsAndHabitHistory() = runBlocking {
        val id = habit(); val x = exchange(id, CoachAction.Plan("new")); val receipt = apply(id, x)
        repeat(30) { exchange(id, CoachAction.AdviceOnly, "followup_$it") }
        assertEquals(50, coach.messages(id).first().size)
        val cache = coach.cache(id).first()!!; assertEquals(catalog.sha256, cache.catalogSha256)
        assertTrue(cache.request.contains("required_occurrences")); assertFalse(cache.request.contains("Test habit")); assertTrue(cache.response.isNotBlank())
        history.logToday(id, day, CompletionValue.Binary(true)); val before = history.record(id)
        coach.clearHistory(); assertEquals(before, history.record(id)); assertEquals(CoachUndoResult.UNAVAILABLE, coach.undo(receipt.id))
        assertTrue(coach.messages(id).first().isEmpty()); assertNull(coach.cache(id).first()); assertTrue(coach.actions(id).first().isEmpty())
    }
    @Test fun cacheAndActionSurviveActualDatabaseCloseAndReopen() = runBlocking {
        db.close(); val name = "chunk10-reopen-${System.nanoTime()}.db"
        try {
            db = Room.databaseBuilder(context, HabitDatabase::class.java, name).build()
            history = HabitHistoryRepository(db, clock, gate); coach = repository()
            val id = habit(); val x = exchange(id, CoachAction.Plan("new")); val receipt = apply(id, x)
            db.close(); db = Room.databaseBuilder(context, HabitDatabase::class.java, name).build()
            history = HabitHistoryRepository(db, clock, gate); coach = repository(); now = now.plusMillis(1000)
            assertEquals(x.first, coach.cache(id).first()!!.id); assertEquals(3, coach.messages(id).first().size)
            assertEquals(CoachUndoResult.UNDONE, coach.undo(receipt.id)); assertEquals("old plan", history.record(id)!!.habit.planNote)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun schemaV2MigrationPreservesEveryRawFactAndAddsEmptyCoachStores() = runBlocking {
        val name = "chunk10-migration-${System.nanoTime()}.db"
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.example.habit.data.local.HabitDatabase/2.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        try {
            val path = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
            SQLiteDatabase.openOrCreateDatabase(path, null).use { raw ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) { val e = entities.getJSONObject(i)
                    raw.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                    val indices = e.optJSONArray("indices") ?: JSONArray()
                    for (j in 0 until indices.length()) raw.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) raw.execSQL(setup.getString(i))
                val day = this@CoachActionsPersistenceTest.day.minusDays(2).toEpochDay()
                raw.execSQL("INSERT INTO habits (id,name,icon_key,color_key,frequency,scheduled_days,goal,created_at,created_epoch_day,cue,anchor,plan_note,reminder_enabled,reminder_minute) VALUES (42,'Original','book','blue','WEEKLY',127,9,1000,?,'cue','anchor','plan',1,900)", arrayOf<Any>(day))
                raw.execSQL("INSERT INTO schedule_history VALUES (42, ?, 'WEEKLY', 127, 3)", arrayOf<Any>(day))
                raw.execSQL("INSERT INTO tracking_history VALUES (42, ?, 'QUANTITY', '5.00', 'pages')", arrayOf<Any>(day))
                raw.execSQL("INSERT INTO tracking_history VALUES (42, ?, 'QUANTITY', '4.0', 'pages')", arrayOf<Any>(day + 3))
                raw.execSQL("INSERT INTO completions VALUES (42, ?, 7, 1234, 'QUANTITY', '2.50', 'pages')", arrayOf<Any>(day))
                raw.version = 2
            }
            val migrated = Room.databaseBuilder(context, HabitDatabase::class.java, name).addMigrations(HabitMigrations.MIGRATION_2_3).build()
            try {
                val r = migrated.historyDao().record(42)!!
                assertEquals(3, migrated.openHelper.readableDatabase.version); assertEquals("Original", r.habit.name)
                assertEquals(9, r.habit.goal); assertEquals(1000L, r.habit.createdAt); assertEquals("cue", r.habit.cue); assertEquals(900, r.habit.reminderMinute)
                assertEquals(2, r.tracking.size); assertEquals("4.0", r.tracking.maxBy { it.effectiveDay }.target)
                assertEquals("2.50", r.completions.single().quantityAmount); assertEquals(7, r.completions.single().count); r.toHistory()
                assertTrue(migrated.coachDao().allMessages().isEmpty()); assertTrue(migrated.coachDao().allCaches().isEmpty()); assertTrue(migrated.coachDao().allActions().isEmpty())
            } finally { migrated.close() }
        } finally { context.deleteDatabase(name) }
    }
}
