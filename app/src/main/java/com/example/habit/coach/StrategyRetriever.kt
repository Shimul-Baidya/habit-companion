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
    fun retrieve(catalog: StrategyCatalog, context: CoachContext, question: String): RetrievalResult {
        val query = words(question)
        val contextTags = when (context) {
            is CoachContext.Planning -> setOf("planning", "cues", "starting", "simplicity")
            is CoachContext.Existing -> when {
                context.summary.completed + context.summary.missed < 2 -> emptySet()
                context.summary.missed > 0 -> setOf("recovery", "consistency", "starting", "simplicity", "friction")
                context.summary.completed >= 7 -> setOf("consistency", "tracking", "progression", "habit-shaping")
                else -> setOf("tracking", "consistency", "cues")
            }
        }
        data class Ranked(val card: StrategyCard, val score: Int, val applicability: Applicability)
        val ranked = catalog.cards.mapNotNull { card ->
            val tagMatches = card.tags.count { it in contextTags }
            val questionTags = card.tags.flatMap { words(it) }.toSet().intersect(query).size
            val conditions = words(card.useWhen).intersect(query).size
            val questionScore = questionTags * 4 + conditions * 2
            // A single generic condition word is too weak to admit a card. Question matches
            // remain conditional even for negated/ambiguous/user-supplied phrases.
            if (tagMatches == 0 && questionScore < 4) return@mapNotNull null
            Ranked(card, tagMatches * 5 + questionScore,
                if (questionScore >= 4) Applicability.QUESTION_OPTION else if (context is CoachContext.Planning)
                    Applicability.PLANNING_OPTION else Applicability.MEASURED_PATTERN_OPTION)
        }.sortedWith(compareByDescending<Ranked> { it.score }.thenBy { it.card.id })
        if (ranked.size < 3) return RetrievalResult.Insufficient(ranked.size)
        return RetrievalResult.Ready(ranked.take(3).map { AdmittedStrategy(it.card, it.applicability) })
    }
}
