package com.example.habit.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
class DateMonitorTest {
    private class MovingClock(val scheduler: TestCoroutineScheduler, var base: Instant, var zoneId: ZoneId) : Clock() {
        override fun instant(): Instant = base.plusMillis(scheduler.currentTime)
        override fun getZone(): ZoneId = zoneId
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(instant(), zone)
    }

    @Test fun refreshesAtMidnightWithoutReopeningHome() = runTest {
        val clock = MovingClock(testScheduler, Instant.parse("2026-10-05T23:59:59Z"), ZoneOffset.UTC)
        val monitor = DateMonitor(clock, backgroundScope)
        val seen = mutableListOf<LocalDate>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { monitor.dates.collect { seen += it } }
        runCurrent()
        advanceTimeBy(999); runCurrent()
        assertEquals(LocalDate.of(2026, 10, 5), seen.last())
        advanceTimeBy(1); runCurrent()
        assertEquals(LocalDate.of(2026, 10, 6), seen.last())
    }

    @Test fun resumeRefreshPicksUpManualClockJumpAndRearmsTimer() = runTest {
        val clock = MovingClock(testScheduler, Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC)
        val monitor = DateMonitor(clock, backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { monitor.dates.collect {} }
        runCurrent()
        clock.base = Instant.parse("2026-10-08T23:59:59Z")
        monitor.refresh(); runCurrent()
        assertEquals(LocalDate.of(2026, 10, 8), monitor.dates.value)
        advanceTimeBy(1000); runCurrent()
        assertEquals(LocalDate.of(2026, 10, 9), monitor.dates.value)
    }

    @Test fun timezoneChangeRefreshesDateAndUsesNewMidnight() = runTest {
        val clock = MovingClock(testScheduler, Instant.parse("2026-10-05T23:59:59Z"), ZoneOffset.UTC)
        val monitor = DateMonitor(clock, backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { monitor.dates.collect {} }
        runCurrent()
        clock.zoneId = ZoneId.of("Asia/Dhaka")
        monitor.refresh(); runCurrent()
        assertEquals(LocalDate.of(2026, 10, 6), monitor.dates.value)
        advanceTimeBy(1000); runCurrent()
        assertEquals(LocalDate.of(2026, 10, 6), monitor.dates.value) // UTC midnight is no longer the deadline
        advanceTimeBy(Duration.ofHours(18).toMillis()); runCurrent()
        assertEquals(LocalDate.of(2026, 10, 7), monitor.dates.value)
    }

    @Test fun midnightUsesActualDstDayLength() = runTest {
        val zone = ZoneId.of("America/New_York")
        for ((date, hours) in listOf(LocalDate.of(2026, 3, 8) to 23L, LocalDate.of(2026, 11, 1) to 25L)) {
            val clock = MovingClock(testScheduler, date.atStartOfDay(zone).toInstant().minusMillis(testScheduler.currentTime), zone)
            val monitor = DateMonitor(clock, backgroundScope)
            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { monitor.dates.collect {} }
            runCurrent()
            advanceTimeBy(Duration.ofHours(hours).toMillis() - 1); runCurrent()
            assertEquals(date, monitor.dates.value)
            advanceTimeBy(1); runCurrent()
            assertEquals(date.plusDays(1), monitor.dates.value)
            job.cancel()
        }
    }
}
