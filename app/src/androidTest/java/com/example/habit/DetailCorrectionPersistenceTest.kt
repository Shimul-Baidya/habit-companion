package com.example.habit

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.habit.data.*
import com.example.habit.data.local.*
import com.example.habit.domain.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class DetailCorrectionPersistenceTest {
    private val start = LocalDate.of(2026, 10, 1)
    private val today = start.plusDays(5)
    private lateinit var db: HabitDatabase
    private fun repo(day: LocalDate) = HabitHistoryRepository(db, Clock.fixed(day.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC))
    @Before fun open() { db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, HabitDatabase::class.java).build() }
    @After fun close() { db.close() }
    @Test fun correctionsUseOldQuantityTargetAndAtomicStatistics() = runBlocking {
        val mode = TrackingMode.Quantity(BigDecimal.TEN, "pages")
        val id = repo(start).create(HabitDraft("Isolated old target", HabitSettings(HabitSchedule.Daily, mode)))
        repo(start).changeSettings(id, listOf(HabitSettingChange.Tracking(mode.copy(target = BigDecimal.ONE))))
        val current = repo(today)
        current.correctChecked(id, start, today, HabitSettings(HabitSchedule.Daily, mode), null, CompletionValue.Quantity(BigDecimal("2.5"), "pages"))
        assertEquals(0, HistoryCalculator.calculate(current.record(id)!!.toHistory(), today).completed)
        current.correctChecked(id, start, today, HabitSettings(HabitSchedule.Daily, mode), CompletionValue.Quantity(BigDecimal("2.5"), "pages"), CompletionValue.Quantity(BigDecimal.TEN, "pages"))
        assertEquals(1, HistoryCalculator.calculate(current.record(id)!!.toHistory(), today).completed)
        assertEquals(1, HistoryCalculator.calculate(current.record(id)!!.toHistory(), today).bestStreak)
        assertEquals(mode, current.record(id)!!.toHistory().settingsOn(start)!!.tracking)
        current.correctChecked(id, start, today, HabitSettings(HabitSchedule.Daily, mode), CompletionValue.Quantity(BigDecimal.TEN, "pages"), null)
        assertTrue(current.record(id)!!.completions.isEmpty())
    }
    @Test fun competingCorrectionsCannotOverwriteTheSameOriginalFact() = runBlocking {
        val id = repo(start).create(HabitDraft("Isolated race")); val current = repo(today)
        val results = List(2) { async(Dispatchers.IO) { runCatching {
            current.correctChecked(id, start, today, HabitSettings(HabitSchedule.Daily), null, CompletionValue.Binary(true))
        } } }.awaitAll()
        assertEquals(1, results.count { it.isSuccess }); assertEquals(1, current.record(id)!!.completions.size)
    }
    @Test fun archiveDeleteFutureRestAndMidnightRejectStaleWrites() = runBlocking {
        val id = repo(start).create(HabitDraft("Isolated guards", HabitSettings(HabitSchedule.Custom(setOf(DayOfWeek.THURSDAY)))))
        val expected = HabitSettings(HabitSchedule.Custom(setOf(DayOfWeek.THURSDAY)))
        val current = repo(today)
        assertTrue(runCatching { current.correctChecked(id, start.minusDays(1), today, expected, null, CompletionValue.Binary(true)) }.isFailure)
        assertTrue(runCatching { current.correctChecked(id, today.plusDays(2), today, expected, null, CompletionValue.Binary(true)) }.isFailure)
        assertTrue(runCatching { current.correctChecked(id, start.plusDays(1), today, expected, null, CompletionValue.Binary(true)) }.isFailure)
        assertTrue(runCatching { current.correctChecked(id, start, start, expected, null, CompletionValue.Binary(true)) }.isFailure)
        current.archive(id)
        assertTrue(runCatching { current.correctChecked(id, start, today, expected, null, CompletionValue.Binary(true)) }.isFailure)
        current.delete(id)
        assertTrue(runCatching { current.correctChecked(id, start, today, expected, null, CompletionValue.Binary(true)) }.isFailure)
        assertNull(current.record(id))
    }
    @Test fun historicalChangeMismatchAndFailedCorrectionLeaveAllRecordsIntact() = runBlocking {
        val id = repo(start).create(HabitDraft("Isolated rollback")); val current = repo(today)
        assertTrue(runCatching { current.correctChecked(id, start, today, HabitSettings(HabitSchedule.Weekly(2)), null, CompletionValue.Binary(true)) }.isFailure)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_correction BEFORE INSERT ON completions BEGIN SELECT RAISE(ABORT, 'isolated correction test'); END")
        assertTrue(runCatching { current.correctChecked(id, start, today, HabitSettings(HabitSchedule.Daily), null, CompletionValue.Binary(true)) }.isFailure)
        assertTrue(current.record(id)!!.completions.isEmpty()); assertEquals(1, current.record(id)!!.schedules.size)
    }
}
