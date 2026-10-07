package com.example.habit.coach

import com.example.habit.domain.*
import com.example.habit.ui.screens.newhabit.HabitFormDraft
import java.math.BigDecimal
import java.time.LocalDate

object CoachLimits {
    const val QUESTION = 1000
    const val TEXT = 240
    const val RESPONSE_BYTES = 32_768
    fun text(value: String, max: Int, allowBlank: Boolean = false): String {
        val trimmed = value.trim()
        require((allowBlank || trimmed.isNotEmpty()) && trimmed.length <= max)
        require(trimmed.none { it.isISOControl() && it != '\n' })
        return trimmed
    }
    fun amount(value: String): BigDecimal {
        require(value.length in 1..64 && value.matches(Regex("[0-9]+(?:\\.[0-9]+)?")))
        return value.toBigDecimal().also { require(it.signum() > 0) }
    }
}

/** The only automatic existing-habit fields. No name, ID, dates, logs, cue or conversation. */
@ConsistentCopyVisibility
data class MeasuredSummary internal constructor(
    val schedule: HabitSchedule, val tracking: TrackingMode,
    val observedDays: Int, val completed: Int, val missed: Int, val pending: Int,
    val partialQuantityDays: Int, val recentSettled: List<OccurrenceOutcome>,
    val attention: HabitAttention,
) {
    companion object {
        fun from(history: HabitHistory, today: LocalDate): MeasuredSummary {
            require(history.isActiveOn(today))
            val evaluation = StatsAggregator.evaluate(history, today)
            val start = maxOf(history.createdOn, today.minusDays(29))
            val occurrences = evaluation.occurrences.filter { it.date >= start }
            val totals = StatsAggregator.totals(occurrences)
            val partial = history.logs.count { log ->
                log.date in start..today && log.value is CompletionValue.Quantity &&
                    log.value.amount.signum() > 0 &&
                    !CompletionRules.isComplete(requireNotNull(history.settingsOn(log.date)).tracking, log.value)
            }
            val settings = requireNotNull(evaluation.settingsToday)
            return MeasuredSummary(settings.schedule, settings.tracking,
                java.time.temporal.ChronoUnit.DAYS.between(start, today).toInt() + 1,
                totals.completed.toInt(), totals.missed.toInt(), totals.pending.toInt(), partial,
                occurrences.filter { it.outcome != OccurrenceOutcome.PENDING }.takeLast(7).map { it.outcome },
                evaluation.metrics.attention)
        }
    }
}

sealed interface CoachContext {
    data class Planning(val name: String, val schedule: HabitSchedule, override val tracking: TrackingMode,
        val activeHabitCount: Int) : CoachContext
    data class Existing(val summary: MeasuredSummary) : CoachContext
    val tracking: TrackingMode get() = when (this) {
        is Planning -> tracking
        is Existing -> summary.tracking
    }
}

enum class Applicability { PLANNING_OPTION, MEASURED_PATTERN_OPTION, QUESTION_OPTION }
data class AdmittedStrategy(val card: StrategyCard, val applicability: Applicability)

/** Constructed only after enabled/context/retrieval validation. Local route/draft tokens stay outside. */
class CoachRequest internal constructor(val context: CoachContext, val question: String,
    val strategies: List<AdmittedStrategy>, val catalogSha256: String)

sealed interface RequestResult {
    data class Ready(val request: CoachRequest) : RequestResult
    data class Unavailable(val failure: CoachFailure) : RequestResult
}

object CoachRequestBuilder {
    fun planning(catalog: StrategyCatalog, draft: HabitFormDraft, count: Int, question: String = "",
        enabled: Boolean): RequestResult = build(catalog, question, enabled) {
        require(count >= 0)
        // An unnamed draft is valid for the empty-Home planning flow. Appearance/cues are local.
        val valid = draft.validate()
        require(!valid.schedule && !valid.target && !valid.unit)
        val name = CoachLimits.text(draft.name, 80, allowBlank = true)
        val settings = draft.copy(name = "Draft").toHabitDraft().settings
        validateTracking(settings.tracking)
        CoachContext.Planning(name, settings.schedule, settings.tracking, count)
    }
    fun existing(catalog: StrategyCatalog, history: HabitHistory, today: LocalDate, question: String = "",
        enabled: Boolean): RequestResult = build(catalog, question, enabled) {
        CoachContext.Existing(MeasuredSummary.from(history, today).also { validateTracking(it.tracking) })
    }
    private fun validateTracking(mode: TrackingMode) {
        if (mode is TrackingMode.Quantity) {
            CoachLimits.amount(mode.target.toPlainString())
            CoachLimits.text(mode.unit, 40)
        }
    }
    private fun build(catalog: StrategyCatalog, question: String, enabled: Boolean,
        context: () -> CoachContext): RequestResult {
        if (!enabled) return RequestResult.Unavailable(CoachFailure.Disabled)
        return try {
            val bounded = CoachLimits.text(question, CoachLimits.QUESTION, allowBlank = true)
            val value = context()
            when (val found = StrategyRetriever.retrieve(catalog, value, bounded)) {
                is RetrievalResult.Insufficient -> RequestResult.Unavailable(CoachFailure.InsufficientContext)
                is RetrievalResult.Ready -> RequestResult.Ready(CoachRequest(value, bounded, found.strategies, catalog.sha256))
            }
        } catch (_: IllegalArgumentException) { RequestResult.Unavailable(CoachFailure.InvalidInput) }
    }
}

/** Only these values can ever be offered to local handlers. Prose remains advice. */
sealed interface CoachAction {
    data object AdviceOnly : CoachAction
    data class Target(val amount: String, val unit: String) : CoachAction
    data class Schedule(val value: HabitSchedule) : CoachAction
    data class CueAnchor(val cue: String, val anchor: String) : CoachAction
    data class Plan(val note: String) : CoachAction
    data class Reminder(val setting: ReminderSetting) : CoachAction
    data class NewHabitCount(val count: Int) : CoachAction
}
sealed interface ReminderSetting {
    data object Inherit : ReminderSetting
    data object Off : ReminderSetting
    data class At(val minuteOfDay: Int) : ReminderSetting
}

/** Free-form causal readings are excluded. All hypotheses render using fixed conditional copy. */
enum class ReadingFact { PLANNING_DRAFT, RECENT_COMPLETIONS, RECENT_MISSES, OPEN_EXPECTATIONS, PARTIAL_QUANTITIES, ATTENTION, NO_SETTLED_HISTORY }
enum class PossibleBarrier(val conditionalText: String) {
    STARTING_SIZE("If starting feels too large, a smaller first step may help."),
    UNCLEAR_CUE("If it is hard to remember when to start, a clear cue may help."),
    SETUP_FRICTION("If setup gets in the way, preparing the environment may help."),
    TOO_MANY_NEW_HABITS("If several new habits feel difficult to sustain, start with fewer."),
}
data class CoachReading(val facts: List<ReadingFact>, val possibleBarrier: PossibleBarrier?)
data class CoachSuggestion(val strategyId: String, val title: String, val advice: String, val action: CoachAction)
data class CoachResponse(val reading: CoachReading, val suggestions: List<CoachSuggestion>)

class ValidatedCoachResponse internal constructor(val value: CoachResponse, private val context: CoachContext) {
    fun readingText(): String = buildList {
        value.reading.facts.forEach { fact ->
            val summary = (context as? CoachContext.Existing)?.summary
            add(when (fact) {
                ReadingFact.PLANNING_DRAFT -> "Unsaved plan; no completion history yet."
                ReadingFact.RECENT_COMPLETIONS -> "${requireNotNull(summary).completed} required occurrences completed over ${summary.observedDays} days."
                ReadingFact.RECENT_MISSES -> "${requireNotNull(summary).missed} required occurrences missed over ${summary.observedDays} days."
                ReadingFact.OPEN_EXPECTATIONS -> "${requireNotNull(summary).pending} required occurrences are still pending."
                ReadingFact.PARTIAL_QUANTITIES -> "Dates with amounts below their historical targets: ${requireNotNull(summary).partialQuantityDays}."
                ReadingFact.ATTENTION -> "The occurrence history needs attention."
                ReadingFact.NO_SETTLED_HISTORY -> "No settled required occurrences in this summary yet."
            })
        }
        value.reading.possibleBarrier?.let { add(it.conditionalText) }
    }.joinToString(" ")

    /** A follow-up must show the generated advice, rather than repeating fixed measured copy. */
    fun conversationText(question: String): String = if (question.isBlank()) readingText()
        else value.suggestions.joinToString("\n\n") { "${it.title}: ${it.advice}" }
}
sealed interface ResponseResult {
    data class Valid(val response: ValidatedCoachResponse) : ResponseResult
    data object Invalid : ResponseResult
}

object CoachResponseValidator {
    fun validate(response: CoachResponse, request: CoachRequest, catalog: StrategyCatalog): ResponseResult = try {
        require(request.catalogSha256 == catalog.sha256)
        require(response.suggestions.size == 3 && response.suggestions.map { it.strategyId }.distinct().size == 3)
        require(request.strategies.size == 3 && request.strategies.all { catalog.byId[it.card.id] == it.card })
        require(response.reading.facts.size in 1..2 && response.reading.facts.distinct().size == response.reading.facts.size)
        val allowed = allowedFacts(request.context)
        require(response.reading.facts.all { it in allowed })
        if (response.reading.possibleBarrier == PossibleBarrier.TOO_MANY_NEW_HABITS) require(request.context is CoachContext.Planning)
        response.suggestions.forEach { s ->
            require(s.strategyId in catalog.byId && request.strategies.any { it.card.id == s.strategyId })
            CoachLimits.text(s.title, 80); CoachLimits.text(s.advice, CoachLimits.TEXT)
            validateAction(s.action, request.context)
            validateStrategyAction(s.action, requireNotNull(catalog.byId[s.strategyId]))
        }
        ResponseResult.Valid(ValidatedCoachResponse(response, request.context))
    } catch (_: IllegalArgumentException) { ResponseResult.Invalid }

    internal fun allowedFacts(context: CoachContext): Set<ReadingFact> = when (val c = context) {
            is CoachContext.Planning -> setOf(ReadingFact.PLANNING_DRAFT)
            is CoachContext.Existing -> buildSet {
                if (c.summary.completed == 0 && c.summary.missed == 0) add(ReadingFact.NO_SETTLED_HISTORY)
                if (c.summary.completed > 0) add(ReadingFact.RECENT_COMPLETIONS)
                if (c.summary.missed > 0) add(ReadingFact.RECENT_MISSES)
                if (c.summary.pending > 0) add(ReadingFact.OPEN_EXPECTATIONS)
                if (c.summary.partialQuantityDays > 0) add(ReadingFact.PARTIAL_QUANTITIES)
                if (c.summary.attention == HabitAttention.AT_RISK) add(ReadingFact.ATTENTION)
            }
        }

    private fun validateAction(action: CoachAction, context: CoachContext) {
        when (action) {
            CoachAction.AdviceOnly -> Unit
            is CoachAction.Target -> {
                val tracking = context.tracking
                require(tracking is TrackingMode.Quantity && action.unit == tracking.unit)
                CoachLimits.amount(action.amount); CoachLimits.text(action.unit, 40)
            }
            is CoachAction.Schedule -> when (val schedule = action.value) {
                HabitSchedule.Daily -> Unit
                is HabitSchedule.Weekly -> require(schedule.completions in 1..7)
                is HabitSchedule.Custom -> require(schedule.weekdays.isNotEmpty())
            }
            is CoachAction.CueAnchor -> {
                CoachLimits.text(action.cue, CoachLimits.TEXT, allowBlank = true)
                CoachLimits.text(action.anchor, CoachLimits.TEXT, allowBlank = true)
                require(action.cue.isNotBlank() || action.anchor.isNotBlank())
            }
            is CoachAction.Plan -> CoachLimits.text(action.note, CoachLimits.TEXT)
            is CoachAction.Reminder -> {
                require(context is CoachContext.Existing)
                if (action.setting is ReminderSetting.At) require(action.setting.minuteOfDay in 0..1439)
            }
            is CoachAction.NewHabitCount -> require(context is CoachContext.Planning && action.count in 1..7)
        }
    }
    private fun validateStrategyAction(action: CoachAction, card: StrategyCard) {
        require(strategySupports(action, card))
    }
    internal fun strategySupports(action: CoachAction, card: StrategyCard): Boolean {
        val tags = card.tags.toSet()
        return when (action) {
            CoachAction.AdviceOnly, is CoachAction.Plan -> true
            is CoachAction.Target -> tags.any { it in setOf("simplicity", "starting", "scaling", "habit-shaping", "two-minute-rule") }
            is CoachAction.Schedule -> tags.any { it in setOf("scheduling", "timeboxing", "routines", "recovery", "consistency", "planning") }
            is CoachAction.CueAnchor -> tags.any { it in setOf("cues", "triggers", "routines", "planning", "starting", "friction", "environment") }
            is CoachAction.Reminder -> tags.any { it in setOf("reminders", "cues", "triggers", "scheduling") }
            is CoachAction.NewHabitCount -> tags.any { it in setOf("planning", "simplicity") }
        }
    }
}

sealed interface CoachFailure {
    data object Disabled : CoachFailure
    data object Unconfigured : CoachFailure
    data object CatalogMissing : CoachFailure
    data object CatalogInvalid : CoachFailure
    data object InsufficientContext : CoachFailure
    data object InvalidInput : CoachFailure
    data object MalformedResponse : CoachFailure
    data object Offline : CoachFailure
    data object Timeout : CoachFailure
    data class RateLimited(val retryAfterSeconds: Int) : CoachFailure {
        init { require(retryAfterSeconds in 1..3600) }
    }
    data object ServerError : CoachFailure
}

sealed interface CoachServiceResult {
    /** Untrusted transport text must pass CoachJson.response before presentation or Apply. */
    data class RawResponse(val json: String) : CoachServiceResult
    data class Failure(val reason: CoachFailure) : CoachServiceResult
}
/** Provider-neutral, one call per explicit interaction; cancellation is propagated by implementations. */
interface CoachService { suspend fun request(request: CoachRequest): CoachServiceResult }
class UnconfiguredCoachService : CoachService {
    override suspend fun request(request: CoachRequest) = CoachServiceResult.Failure(CoachFailure.Unconfigured)
}
