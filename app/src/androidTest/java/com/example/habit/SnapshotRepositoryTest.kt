package com.example.habit

import androidx.room.Room
import androidx.room.withTransaction
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

@RunWith(AndroidJUnit4::class)
class SnapshotRepositoryTest {
    private val monday = LocalDate.of(2026, 10, 5)
    private lateinit var db: HabitDatabase
    private fun clock(day: Long) = Clock.fixed(monday.plusDays(day).atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    @Before fun open() { db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, HabitDatabase::class.java).build() }
    @After fun close() { db.close() }

    @Test fun repositoryHasNoHistoryCapAndArchiveDeleteUpdateWholeSnapshot() = runBlocking {
        val initial = HabitHistoryRepository(db, clock(0))
        val old = initial.create(HabitDraft("Isolated long history"))
        db.withTransaction {
            for (day in 0L..999L) db.completionDao().upsert(CompletionEntity(old, monday.plusDays(day).toEpochDay(), completedAt = clock(day).millis()))
        }
        val history = HabitHistoryRepository(db, clock(1000))
        val current = history.create(HabitDraft("Isolated active"))
        history.logToday(current, monday.plusDays(1000), CompletionValue.Binary(true))
        val repo = HabitRepository(history)
        val before = repo.observeSnapshot(monday.plusDays(1000), DayOfWeek.MONDAY).first()
        assertEquals(1000, before.allTimeBest)
        assertEquals(1001L, before.lifetime.completed)
        assertEquals(2, before.active.size)
        history.archive(old)
        val archived = repo.observeSnapshot(monday.plusDays(1000), DayOfWeek.MONDAY).first()
        assertEquals(listOf(current), archived.active.map { it.habit.id })
        assertEquals(listOf(current), archived.topStreaks.map { it.habit.id })
        assertEquals(1000, archived.allTimeBest)
        assertEquals(1001L, archived.lifetime.completed)
        history.delete(old)
        val deleted = repo.observeSnapshot(monday.plusDays(1000), DayOfWeek.MONDAY).first()
        assertEquals(1, deleted.allTimeBest)
        assertEquals(CompletionTotals(1), deleted.lifetime)
        assertEquals(1, deleted.records.size)
    }

    @Test fun liveCorrectionsUpdateRowRiskStreakAndAggregatesTogether() = runBlocking {
        val id = HabitHistoryRepository(db, clock(0)).create(HabitDraft("Isolated correction"))
        val history = HabitHistoryRepository(db, clock(2))
        history.logToday(id, monday.plusDays(2), CompletionValue.Binary(true))
        val stream = HabitRepository(history).observeSnapshot(monday.plusDays(2), DayOfWeek.MONDAY).produceIn(this)
        try {
            val first = withTimeout(5000) { stream.receive() }
            assertTrue(first.active.single().atRisk)
            assertEquals(1, first.active.single().currentStreak)
            history.correct(id, monday.plusDays(1), CompletionValue.Binary(true))
            val updated = withTimeout(5000) { var item = stream.receive(); while (item.lifetime.completed != 2L) item = stream.receive(); item }
            assertFalse(updated.active.single().atRisk)
            assertEquals(2, updated.allTimeBest)
            assertEquals(2, updated.active.single().currentStreak)
            assertEquals(CompletionTotals(2, 1), updated.lifetime)
            assertEquals(updated.lifetime, updated.week.fold(CompletionTotals()) { a, b -> a + b.totals })
        } finally { stream.cancel() }
    }

    @Test fun quantityHistoricalTargetAndCurrentPartialStayCoherent() = runBlocking {
        val small = TrackingMode.Quantity(BigDecimal("5"), "pages")
        val initial = HabitHistoryRepository(db, clock(0))
        val id = initial.create(HabitDraft("Isolated quantity", HabitSettings(HabitSchedule.Daily, small)))
        initial.logToday(id, monday, CompletionValue.Quantity(BigDecimal("5"), "pages"))
        initial.changeSettings(id, listOf(HabitSettingChange.Tracking(TrackingMode.Quantity(BigDecimal("10"), "pages"))))
        val next = HabitHistoryRepository(db, clock(1))
        next.logToday(id, monday.plusDays(1), CompletionValue.Quantity(BigDecimal("5"), "pages"))
        val snapshot = HabitRepository(next).observeSnapshot(monday.plusDays(1), DayOfWeek.MONDAY).first()
        assertEquals(CompletionTotals(1, 0, 1), snapshot.lifetime)
        assertFalse(snapshot.active.single().doneToday)
        assertEquals(0.5f, snapshot.active.single().progressToday, 0.0f)
        assertEquals(1, snapshot.active.single().currentStreak)
        next.correct(id, monday, CompletionValue.Quantity(BigDecimal("4"), "pages"))
        val corrected = HabitRepository(next).observeSnapshot(monday.plusDays(1), DayOfWeek.MONDAY).first()
        assertEquals(CompletionTotals(0, 1, 1), corrected.lifetime)
        assertEquals(0, corrected.allTimeBest)
    }

    @Test fun invalidStoredHistoryPropagatesFailureRatherThanEmptySnapshot() = runBlocking {
        val history = HabitHistoryRepository(db, clock(0))
        val id = history.create(HabitDraft("Isolated invalid"))
        db.historyDao().putSchedule(ScheduleHistoryEntity(id, monday.toEpochDay(), "CUSTOM", 0, null))
        try {
            HabitRepository(history).observeSnapshot(monday, DayOfWeek.MONDAY).first()
            fail("Invalid history must not become an empty Home")
        } catch (_: IllegalArgumentException) { }
        assertNotNull(history.record(id)) // raw record is retained
    }

    @Test fun staleTodayWriteFailsWhileIntentionalPastCorrectionStillWorks() = runBlocking {
        val id = HabitHistoryRepository(db, clock(0)).create(HabitDraft("Isolated midnight"))
        val next = HabitHistoryRepository(db, clock(1))
        try {
            next.logToday(id, monday, CompletionValue.Binary(true))
            fail("Stale Home write must be rejected transactionally")
        } catch (_: IllegalArgumentException) { }
        assertTrue(next.record(id)!!.completions.isEmpty())
        next.correct(id, monday, CompletionValue.Binary(true))
        assertEquals(1, next.record(id)!!.completions.size)
    }
}
