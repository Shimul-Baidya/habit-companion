package com.example.habit

import androidx.room.Room
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.habit.data.*
import com.example.habit.data.local.*
import com.example.habit.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.*
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class StorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val opened = mutableListOf<HabitDatabase>()
    private val monday = LocalDate.of(2026, 10, 5)
    private fun clock(day: Int) = Clock.fixed(monday.plusDays(day.toLong()).atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private fun memory(): HabitDatabase = Room.inMemoryDatabaseBuilder(context, HabitDatabase::class.java).build().also { opened += it }
    @After fun close() { opened.forEach { it.close() } }

    @Test fun v1MigrationPreservesOriginalDataAndHistoricalMeaning() = runBlocking {
        val name = "test-migration-v1-v2.db"
        context.deleteDatabase(name)
        val created = monday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val archived = monday.plusDays(2).atTime(18, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.example.habit.data.local.HabitDatabase/1.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val path = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).apply {
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (i in 0 until indices.length())
                    execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) execSQL(setup.getString(index))
            version = 1
            execSQL("INSERT INTO habits VALUES (11, 'Daily original', 'book', 'teal', 'DAILY', 127, 9, ?, NULL)", arrayOf<Any>(created))
            execSQL("INSERT INTO habits VALUES (22, 'Legacy weekly', 'walk', 'green', 'WEEKLY', 2, 5, ?, NULL)", arrayOf<Any>(created))
            execSQL("INSERT INTO habits VALUES (33, 'Archived', 'default', 'primary', 'CUSTOM', 127, 1, ?, ?)", arrayOf<Any>(created, archived))
            execSQL("INSERT INTO habits VALUES (44, 'Invalid legacy mask retained', 'default', 'primary', 'CUSTOM', 0, 1, ?, NULL)", arrayOf<Any>(created))
            execSQL("INSERT INTO completions VALUES (11, ?, 5, ?)", arrayOf<Any>(monday.toEpochDay(), created + 1000))
            execSQL("INSERT INTO completions VALUES (22, ?, 1, ?)", arrayOf<Any>(monday.plusDays(1).toEpochDay(), created + 2000))
            execSQL("INSERT INTO completions VALUES (33, ?, 1, ?)", arrayOf<Any>(monday.plusDays(2).toEpochDay(), archived - 1000))
            close()
        }
        // The production Room open path performs migration and generated v3 schema validation.
        val db = Room.databaseBuilder(context, HabitDatabase::class.java, name).addMigrations(HabitMigrations.MIGRATION_1_2, HabitMigrations.MIGRATION_2_3).build().also { opened += it }
        val daily = db.historyDao().record(11)!!
        assertEquals(3, db.openHelper.readableDatabase.version)
        assertEquals("Daily original", daily.habit.name)
        assertEquals("book", daily.habit.iconKey)
        assertEquals("teal", daily.habit.colorKey)
        assertEquals(created, daily.habit.createdAt)
        assertEquals(9, daily.habit.goal)
        assertEquals(5, daily.completions.single().count)
        assertEquals(created + 1000, daily.completions.single().completedAt)
        assertEquals(TrackingMode.Binary, daily.toHistory().settingsOn(monday)!!.tracking)
        assertEquals(CompletionValue.Binary(true), daily.toHistory().logs.single().value)
        val weekly = db.historyDao().record(22)!!
        assertEquals(Frequency.WEEKLY, weekly.habit.frequency) // retained legacy metadata
        assertEquals(HabitSchedule.Custom(setOf(DayOfWeek.TUESDAY)), weekly.toHistory().settingsOn(monday)!!.schedule)
        assertNull(weekly.schedules.single().quota)
        val oldArchived = db.historyDao().record(33)!!
        assertEquals(archived, oldArchived.habit.archivedAt)
        assertEquals(1, oldArchived.toHistory().logs.size)
        assertEquals(monday.plusDays(2), oldArchived.toHistory().archivedOn)
        val invalid = db.historyDao().record(44)!!
        assertEquals(0, invalid.habit.scheduledDays)
        assertFails { invalid.toHistory() }
        assertEquals(4, db.historyDao().observeRecords().first().size)
        assertEquals(3, db.habitDao().count())
    }

    @Test fun binaryQuantityAndHistoricalChangesRoundTripAcrossReopen() = runBlocking {
        val name = "test-reopen-v2.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, HabitDatabase::class.java, name).build()
        val repo = HabitHistoryRepository(db, clock(0))
        val id = repo.create(HabitDraft("Reading", HabitSettings(HabitSchedule.Daily, TrackingMode.Quantity(BigDecimal("0.3"), "pages"))))
        repo.correct(id, monday, CompletionValue.Quantity(BigDecimal("0.30"), "pages"))
        repo.correct(id, monday, CompletionValue.Quantity(BigDecimal("0.300"), "pages"))
        repo.changeSettings(id, listOf(HabitSettingChange.Tracking(TrackingMode.Quantity(BigDecimal("1.5"), "pages"))))
        repo.updateMetadata(id, HabitMetadata("Read", "book", "teal", "after breakfast", "coffee", "start small", true, 1200))
        db.close()
        db = Room.databaseBuilder(context, HabitDatabase::class.java, name).build().also { opened += it }
        val stored = db.historyDao().record(id)!!
        assertEquals(1, stored.completions.size)
        assertEquals("0.300", stored.completions.single().quantityAmount)
        assertEquals("after breakfast", stored.habit.cue)
        assertEquals("coffee", stored.habit.anchor)
        assertEquals("start small", stored.habit.planNote)
        assertEquals(true, stored.habit.reminderEnabled)
        assertEquals(1200, stored.habit.reminderMinute)
        val history = stored.toHistory()
        assertEquals(BigDecimal("0.3"), (history.settingsOn(monday)!!.tracking as TrackingMode.Quantity).target)
        assertEquals(BigDecimal("1.5"), (history.settingsOn(monday.plusDays(1))!!.tracking as TrackingMode.Quantity).target)
        assertEquals(1, HistoryCalculator.calculate(history, monday).completed)
    }

    @Test fun pendingScheduleAndTrackingChangesDoNotOverwriteEachOther() = runBlocking {
        val db = memory()
        val repo = HabitHistoryRepository(db, clock(1))
        val id = repo.create(HabitDraft("Walk", HabitSettings(HabitSchedule.Weekly(3))))
        repo.changeSettings(id, listOf(HabitSettingChange.Schedule(HabitSchedule.Weekly(1))))
        repo.changeSettings(id, listOf(HabitSettingChange.Tracking(TrackingMode.Quantity(BigDecimal("2"), "km"))))
        val history = repo.record(id)!!.toHistory()
        assertEquals(HabitSchedule.Weekly(3), history.settingsOn(monday.plusDays(2))!!.schedule)
        assertEquals(HabitSchedule.Weekly(1), history.settingsOn(monday.plusDays(7))!!.schedule)
        assertEquals(TrackingMode.Quantity(BigDecimal("2"), "km"), history.settingsOn(monday.plusDays(7))!!.tracking)
        assertEquals(TrackingMode.Binary, history.settingsOn(monday.plusDays(1))!!.tracking)
    }

    @Test fun pendingWeeklyTransitionCanBeReplacedWithoutCreatingInvalidMidweekTimeline() = runBlocking {
        val repo = HabitHistoryRepository(memory(), clock(1))
        val id = repo.create(HabitDraft("Walk"))
        repo.changeSettings(id, listOf(HabitSettingChange.Schedule(HabitSchedule.Weekly(3))))
        repo.changeSettings(id, listOf(HabitSettingChange.Schedule(HabitSchedule.Custom(setOf(DayOfWeek.FRIDAY)))))
        val history = repo.record(id)!!.toHistory()
        assertEquals(HabitSchedule.Daily, history.settingsOn(monday.plusDays(2))!!.schedule)
        assertEquals(HabitSchedule.Custom(setOf(DayOfWeek.FRIDAY)), history.settingsOn(monday.plusDays(7))!!.schedule)
    }

    @Test fun creationTransactionRollsBackWhenChildInsertFails() = runBlocking {
        val db = memory()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_tracking BEFORE INSERT ON tracking_history BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        val repo = HabitHistoryRepository(db, clock(0))
        assertFails { repo.create(HabitDraft("No partial habit")) }
        assertEquals(0, db.habitDao().count())
        assertEquals(0, db.historyDao().observeRecords().first().size)
    }

    @Test fun invalidDatesAndUnitsCannotWriteAndRepeatedCorrectionsReplaceOnlyTheirDate() = runBlocking {
        val repo = HabitHistoryRepository(memory(), clock(2))
        val id = repo.create(HabitDraft("Only Wednesday", HabitSettings(HabitSchedule.Custom(setOf(DayOfWeek.WEDNESDAY)))))
        assertFails { repo.correct(id, monday, CompletionValue.Binary(true)) }
        assertFails { repo.correct(id, monday.plusDays(3), CompletionValue.Binary(true)) }
        assertFails { repo.correct(id, monday.plusDays(2), CompletionValue.Quantity(BigDecimal.ONE, "km")) }
        repo.correct(id, monday.plusDays(2), CompletionValue.Binary(true))
        repo.correct(id, monday.plusDays(2), CompletionValue.Binary(true))
        assertEquals(1, repo.record(id)!!.completions.size)
        repo.correct(id, monday.plusDays(2), CompletionValue.Binary(false))
        assertEquals(0, repo.record(id)!!.completions.size)
    }

    @Test fun archiveKeepsAchievementsAndUndoDoesNotReplaceLaterMetadataThenDeleteCascades() = runBlocking {
        val db = memory()
        val repo = HabitHistoryRepository(db, clock(0))
        val id = repo.create(HabitDraft("Walk"))
        repo.correct(id, monday, CompletionValue.Binary(true))
        val archive = repo.archive(id)
        assertEquals(0, db.habitDao().count())
        assertEquals(1, repo.record(id)!!.toHistory().logs.size)
        assertFails { repo.correct(id, monday, CompletionValue.Binary(true)) }
        repo.updateMetadata(id, HabitMetadata("Renamed", "default", "primary"))
        assertTrue(repo.undoArchive(archive))
        assertFalse(repo.undoArchive(archive))
        assertEquals("Renamed", repo.record(id)!!.habit.name)
        assertEquals(1, db.habitDao().count())
        repo.delete(id)
        assertNull(repo.record(id))
        listOf("schedule_history", "tracking_history", "completions").forEach { table ->
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test fun newWeeklyIsQuotaAndCompatibilityFormCreatesCompleteHistory() = runBlocking {
        val repo = HabitHistoryRepository(memory(), clock(0))
        val id = repo.create(HabitDraft("Quota", HabitSettings(HabitSchedule.Weekly(3))))
        assertEquals(HabitSchedule.Weekly(3), repo.record(id)!!.toHistory().settingsOn(monday)!!.schedule)
        val old = repo.createLegacy(HabitEntity(name = "  Legacy form  ", createdAt = clock(0).millis(), createdEpochDay = monday.toEpochDay()))
        assertEquals("Legacy form", repo.record(old)!!.habit.name)
        assertEquals(TrackingMode.Binary, repo.record(old)!!.toHistory().settingsOn(monday)!!.tracking)
    }

    @Test fun archiveUndoExpiresAndPastQuantityCorrectionsUseOldUnit() = runBlocking {
        val db = memory()
        val first = HabitHistoryRepository(db, clock(0))
        val id = first.create(HabitDraft("Read", HabitSettings(HabitSchedule.Daily, TrackingMode.Quantity(BigDecimal.TEN, "pages"))))
        first.correct(id, monday, CompletionValue.Quantity(BigDecimal.TEN, "pages"))
        first.changeSettings(id, listOf(HabitSettingChange.Tracking(TrackingMode.Binary)))
        val later = HabitHistoryRepository(db, clock(1))
        later.correct(id, monday.plusDays(1), CompletionValue.Binary(true))
        later.correct(id, monday, CompletionValue.Quantity(BigDecimal("12"), "pages"))
        assertFails { later.correct(id, monday, CompletionValue.Binary(true)) }
        assertEquals(2, later.record(id)!!.toHistory().logs.size)
        val archive = later.archive(id)
        assertFalse(HabitHistoryRepository(db, Clock.offset(clock(1), Duration.ofSeconds(5))).undoArchive(archive))
        assertNotNull(later.record(id)!!.habit.archivedAt)
    }

    @Test fun creationCrossingMidnightUsesOneInstantForBothTimestampAndLocalDate() = runBlocking {
        val before = Instant.parse("2026-10-05T23:59:59.999Z")
        val ticking = object : Clock() {
            var reads = 0
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = Clock.fixed(before, zone)
            override fun instant(): Instant = before.plusMillis((reads++).toLong())
        }
        val repo = HabitHistoryRepository(memory(), ticking)
        val row = repo.record(repo.create(HabitDraft("Midnight")))!!.habit
        assertEquals(before.toEpochMilli(), row.createdAt)
        assertEquals(monday.toEpochDay(), row.createdEpochDay)
    }

    @Test fun observationReceivesAtomicHistoryAndRetainsArchivedRecords() = runBlocking {
        val repo = HabitHistoryRepository(memory(), clock(0))
        val channel = repo.records.produceIn(this)
        try {
            assertTrue(withTimeout(10_000) { channel.receive() }.isEmpty())
            val id = repo.create(HabitDraft("Observable"))
            val created = withTimeout(10_000) { channel.receive() }.single()
            assertEquals(id, created.habit.id)
            assertEquals(1, created.schedules.size)
            assertEquals(1, created.tracking.size)
            created.toHistory()
            repo.correct(id, monday, CompletionValue.Binary(true))
            val corrected = withTimeout(10_000) { channel.receive() }.single()
            assertEquals(1, corrected.completions.size)
            repo.archive(id)
            val archived = withTimeout(10_000) { channel.receive() }.single()
            assertNotNull(archived.habit.archivedAt)
            assertEquals(1, archived.toHistory().logs.size)
            repo.delete(id)
            assertTrue(withTimeout(10_000) { channel.receive() }.isEmpty())
        } finally { channel.cancel() }
    }

    private suspend fun assertFails(block: suspend () -> Unit) {
        var failure: Throwable? = null
        try { block() } catch (e: Exception) { failure = e }
        assertNotNull("Expected write rejection", failure)
    }
}
