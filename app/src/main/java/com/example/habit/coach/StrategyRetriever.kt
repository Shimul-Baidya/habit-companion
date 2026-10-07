package com.example.habit.coach

import java.util.Locale

sealed interface RetrievalResult {
    data class Ready(val strategies: List<AdmittedStrategy>) : RetrievalResult
    data class Insufficient(val relevantCount: Int) : RetrievalResult
}

/** Stable local lexical ranking. Matches propose options; they never diagnose motives/life events. */
object StrategyRetriever {
    private val stop = setOf("when", "you", "your", "the", "and", "for", "with", "that", "this", "from",
        "have", "habit", "habits", "what", "help", "how", "can", "want", "more", "are", "but", "while", "into")
    private fun words(text: String) = Regex("[a-z0-9]+").findAll(text.lowercase(Locale.ROOT))
        .map { it.value }.filter { it.length >= 3 && it !in stop }.toSet()
    // Narrow vocabulary bridges to the supplied tags, not inferred personal circumstances.
    private fun questionWords(question: String, expanded: Boolean = true): Set<String> = words(question).toMutableSet().apply {
        val original = toSet()
        fun bridge(tokens: Set<String>, tags: Set<String>) { if (original.any { it in tokens }) addAll(tags) }
        bridge(setOf("forget", "forgetting", "forgot", "remember", "remembering", "reminder", "reminders"), setOf("cues", "triggers"))
        bridge(setOf("start", "starting", "begin", "beginning", "smaller", "simpler", "overwhelming", "overwhelmed"), setOf("starting", "simplicity"))
        bridge(setOf("schedule", "scheduling", "time", "busy"), setOf("scheduling", "timeboxing", "planning"))
        bridge(setOf("distracted", "distractions", "distracting", "concentrate", "concentration"), setOf("focus", "distraction", "control"))
        if (expanded) {
            bridge(setOf("consistent", "consistently", "consistency", "regular", "regularly", "stick", "sticking", "sustain", "sustaining"),
                setOf("consistency", "routines", "tracking"))
            bridge(setOf("miss", "missed", "missing", "skip", "skipped", "skipping", "relapse", "restart", "recover"),
                setOf("recovery", "consistency"))
            bridge(setOf("struggle", "struggling", "difficult", "difficulty", "hard", "effort"),
                setOf("friction", "ease", "simplicity"))
            bridge(setOf("unmotivated", "motivate", "motivated", "motivation", "boring", "bored", "tedious"),
                setOf("motivation", "rewards", "engagement"))
            bridge(setOf("strategize", "strategy", "strategies", "plan"), setOf("planning", "clarity"))
        }
    }
    fun retrieve(catalog: StrategyCatalog, context: CoachContext, question: String): RetrievalResult {
        return rank(catalog, context, questionWords(question), questionFirst = true, allowFewer = question.isNotBlank())
    }
    /** Exact previous keyword gate retained only for already saved v1 exchanges/receipts. */
    internal fun previousRetrieve(catalog: StrategyCatalog, context: CoachContext, question: String): RetrievalResult =
        rank(catalog, context, questionWords(question, expanded = false), questionFirst = true)
    /** Exact original ranking retained solely to validate saved pre-fix requests/Apply receipts. */
    internal fun legacyRetrieve(catalog: StrategyCatalog, context: CoachContext, question: String): RetrievalResult =
        rank(catalog, context, words(question), questionFirst = false)

    private fun rank(catalog: StrategyCatalog, context: CoachContext, query: Set<String>, questionFirst: Boolean,
        allowFewer: Boolean = false): RetrievalResult {
        val contextTags = when (context) {
            is CoachContext.Planning -> setOf("planning", "cues", "starting", "simplicity")
            is CoachContext.Existing -> when {
                context.summary.completed + context.summary.missed < 2 -> emptySet()
                context.summary.missed > 0 -> setOf("recovery", "consistency", "starting", "simplicity", "friction")
                context.summary.completed >= 7 -> setOf("consistency", "tracking", "progression", "habit-shaping")
                else -> setOf("tracking", "consistency", "cues")
            }
        }
        data class Ranked(val card: StrategyCard, val score: Int, val questionScore: Int, val applicability: Applicability)
        val ranked = catalog.cards.mapNotNull { card ->
            val tagMatches = card.tags.count { it in contextTags }
            val questionTags = card.tags.flatMap { words(it) }.toSet().intersect(query).size
            val conditions = words(card.useWhen).intersect(query).size
            val questionScore = questionTags * 4 + conditions * 2
            // A single generic condition word is too weak to admit a card. Question matches
            // remain conditional even for negated/ambiguous/user-supplied phrases.
            if (tagMatches == 0 && questionScore < 4) return@mapNotNull null
            Ranked(card, tagMatches * 5 + questionScore, questionScore,
                if (questionScore >= 4) Applicability.QUESTION_OPTION else if (context is CoachContext.Planning)
                    Applicability.PLANNING_OPTION else Applicability.MEASURED_PATTERN_OPTION)
        }.sortedWith(if (questionFirst) compareByDescending<Ranked> { it.questionScore >= 4 }
            .thenByDescending { it.questionScore }.thenByDescending { it.score }.thenBy { it.card.id }
            else compareByDescending<Ranked> { it.score }.thenBy { it.card.id })
        if (ranked.size < 3 && !allowFewer) return RetrievalResult.Insufficient(ranked.size)
        return RetrievalResult.Ready(ranked.take(3).map { AdmittedStrategy(it.card, it.applicability) })
    }
}
