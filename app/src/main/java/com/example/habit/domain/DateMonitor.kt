package com.example.habit.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

interface DateProvider {
    val dates: Flow<LocalDate>
    fun today(): LocalDate
    fun refresh()
}

/** The zone follows device settings rather than being frozen at application creation. */
class DeviceClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun instant(): Instant = Instant.now()
    override fun withZone(zone: ZoneId): Clock = Clock.system(zone)
}

class DateMonitor(private val clock: Clock, scope: CoroutineScope) : DateProvider {
    private val refreshes = MutableStateFlow(0L)
    override fun today(): LocalDate = LocalDate.now(clock)
    override fun refresh() { refreshes.update { it + 1 } }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val dates: StateFlow<LocalDate> = refreshes.flatMapLatest {
        flow {
            while (true) {
                val now = clock.instant()
                val date = now.atZone(clock.zone).toLocalDate()
                emit(date)
                val midnight = date.plusDays(1).atStartOfDay(clock.zone).toInstant()
                delay(Duration.between(now, midnight).toMillis().coerceAtLeast(1))
            }
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), today())
}
