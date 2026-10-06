package com.example.habit.coach

import android.util.JsonReader
import android.util.JsonToken
import com.example.habit.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.DayOfWeek

/** Strict Android JSON boundary using the platform reader; no lenient JSONObject input parsing. */
object CoachJson {
    private data class NumberToken(val text: String)
    private fun parse(text: String, maxBytes: Int): Any? {
        require(text.toByteArray(Charsets.UTF_8).size <= maxBytes)
        var values = 0
        JsonReader(StringReader(text)).use { reader ->
            reader.isLenient = false
            fun read(depth: Int): Any? {
                require(depth <= 12 && ++values <= 10_000)
                return when (reader.peek()) {
                    JsonToken.BEGIN_OBJECT -> {
                        val result = linkedMapOf<String, Any?>()
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val key = reader.nextName()
                            require(!result.containsKey(key)) { "Duplicate field" }
                            result[key] = read(depth + 1)
                        }
                        reader.endObject(); result
                    }
                    JsonToken.BEGIN_ARRAY -> {
                        val result = mutableListOf<Any?>()
                        reader.beginArray()
                        while (reader.hasNext()) result.add(read(depth + 1))
                        reader.endArray(); result
                    }
                    JsonToken.STRING -> reader.nextString()
                    JsonToken.NUMBER -> NumberToken(reader.nextString())
                    JsonToken.BOOLEAN -> reader.nextBoolean()
                    JsonToken.NULL -> { reader.nextNull(); null }
                    else -> error("Unexpected JSON token")
                }
            }
            val result = read(0)
            require(reader.peek() == JsonToken.END_DOCUMENT)
            return result
        }
    }
    private fun Any?.obj(vararg keys: String): Map<String, Any?> {
        require(this is Map<*, *> && this.keys == keys.toSet()) { "Unexpected/missing fields" }
        @Suppress("UNCHECKED_CAST") return this as Map<String, Any?>
    }
    private fun Any?.list(): List<Any?> { require(this is List<*>); return this }
    private fun Any?.text(): String { require(this is String); return this }
    private fun Any?.integer(): Int {
        require(this is NumberToken && text.matches(Regex("-?(?:0|[1-9][0-9]*)")))
        return text.toInt()
    }
    private inline fun <reified E : Enum<E>> Any?.enum(): E = enumValueOf(text())

    fun catalog(bytes: ByteArray): StrategyCatalog {
        require(bytes.size <= 262_144)
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        val cards = parse(text, 262_144).list().map {
            val c = it.obj("id", "title", "principle", "action", "use_when", "tags", "source")
            StrategyCard(c["id"].text(), c["title"].text(), c["principle"].text(), c["action"].text(),
                c["use_when"].text(), c["tags"].list().map { tag -> tag.text() }, c["source"].text())
        }
        return StrategyCatalog.validated(cards, bytes)
    }

    /** Explicit whitelist, never reflection or serialization of a record/draft/navigation object. */
    fun payload(request: CoachRequest): String {
        fun json(vararg pairs: Pair<String, Any?>) = JSONObject().apply {
            pairs.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
        }
        fun schedule(value: HabitSchedule): JSONObject = when (value) {
            HabitSchedule.Daily -> json("kind" to "DAILY")
            is HabitSchedule.Weekly -> json("kind" to "WEEKLY", "quota" to value.completions)
            is HabitSchedule.Custom -> json("kind" to "CUSTOM", "weekdays" to JSONArray(value.weekdays.map { it.value }.sorted()))
        }
        fun tracking(value: TrackingMode): JSONObject = when (value) {
            TrackingMode.Binary -> json("mode" to "BINARY")
            is TrackingMode.Quantity -> json("mode" to "QUANTITY", "target" to value.target.toPlainString(), "unit" to value.unit)
        }
        val context = when (val c = request.context) {
            is CoachContext.Planning -> json("draftName" to c.name, "schedule" to schedule(c.schedule),
                "tracking" to tracking(c.tracking), "activeHabitCount" to c.activeHabitCount)
            is CoachContext.Existing -> c.summary.let { s -> json("windowDays" to 30, "observedDays" to s.observedDays,
                "schedule" to schedule(s.schedule), "tracking" to tracking(s.tracking), "metricUnit" to "required_occurrences",
                "completed" to s.completed, "missed" to s.missed, "pending" to s.pending,
                "partialQuantityDays" to s.partialQuantityDays,
                "recentSettledOutcomes" to JSONArray(s.recentSettled.map { it.name }), "attention" to s.attention.name) }
        }
        return json("contractVersion" to 1, "mode" to if (request.context is CoachContext.Planning) "PLANNING" else "EXISTING",
            "context" to context, "question" to request.question,
            "strategies" to JSONArray(request.strategies.map { admitted -> admitted.card.let { c ->
                json("id" to c.id, "title" to c.title, "principle" to c.principle, "action" to c.action,
                    "use_when" to c.useWhen, "tags" to JSONArray(c.tags), "source" to c.source,
                    "applicability" to admitted.applicability.name)
            } })).toString()
    }

    /** Rehydrate only our whitelisted local context; never reinterpret a cache with today's history. */
    fun storedRequest(text: String, digest: String, catalog: StrategyCatalog): CoachRequest? = runCatching {
        require(digest == catalog.sha256)
        val root = parse(text, 65_536).obj("contractVersion", "mode", "context", "question", "strategies")
        require(root["contractVersion"].integer() == 1)
        fun tracking(value: Any?): TrackingMode {
            require(value is Map<*, *>)
            return when (value["mode"].text()) {
                "BINARY" -> { value.obj("mode"); TrackingMode.Binary }
                "QUANTITY" -> value.obj("mode", "target", "unit").let {
                    TrackingMode.Quantity(CoachLimits.amount(it["target"].text()), CoachLimits.text(it["unit"].text(), 40))
                }
                else -> error("Unknown mode")
            }
        }
        val context = when (root["mode"].text()) {
            "PLANNING" -> root["context"].obj("draftName", "schedule", "tracking", "activeHabitCount").let {
                val count = it["activeHabitCount"].integer(); require(count >= 0)
                CoachContext.Planning(CoachLimits.text(it["draftName"].text(), 80, true), schedule(it["schedule"]), tracking(it["tracking"]), count)
            }
            "EXISTING" -> root["context"].obj("windowDays", "observedDays", "schedule", "tracking", "metricUnit",
                "completed", "missed", "pending", "partialQuantityDays", "recentSettledOutcomes", "attention").let {
                require(it["windowDays"].integer() == 30 && it["metricUnit"].text() == "required_occurrences")
                val days = it["observedDays"].integer(); require(days in 1..30)
                val done = it["completed"].integer(); val missed = it["missed"].integer(); val pending = it["pending"].integer()
                val partial = it["partialQuantityDays"].integer()
                require(done in 0..42 && missed in 0..42 && pending in 0..42 && partial in 0..days)
                require(done + missed + pending in 0..42)
                val recent = it["recentSettledOutcomes"].list().map { o -> o.enum<OccurrenceOutcome>() }
                require(recent.size <= 7 && recent.none { o -> o == OccurrenceOutcome.PENDING })
                CoachContext.Existing(MeasuredSummary(schedule(it["schedule"]), tracking(it["tracking"]), days,
                    done, missed, pending, partial, recent, it["attention"].enum<HabitAttention>()))
            }
            else -> error("Unknown context")
        }
        val cards = root["strategies"].list().map { value ->
            val c = value.obj("id", "title", "principle", "action", "use_when", "tags", "source", "applicability")
            val card = StrategyCard(c["id"].text(), c["title"].text(), c["principle"].text(), c["action"].text(),
                c["use_when"].text(), c["tags"].list().map { it.text() }, c["source"].text())
            require(catalog.byId[card.id] == card)
            AdmittedStrategy(card, c["applicability"].enum<Applicability>())
        }
        require(cards.size == 3 && cards.map { it.card.id }.distinct().size == 3)
        val question = CoachLimits.text(root["question"].text(), CoachLimits.QUESTION, true)
        require((StrategyRetriever.retrieve(catalog, context, question) as? RetrievalResult.Ready)?.strategies == cards)
        CoachRequest(context, question, cards, digest)
    }.getOrNull()

    fun response(text: String, request: CoachRequest, catalog: StrategyCatalog): ResponseResult = try {
        val root = parse(text, CoachLimits.RESPONSE_BYTES).obj("reading", "suggestions")
        val reading = root["reading"].obj("facts", "possibleBarrier")
        val value = CoachResponse(CoachReading(reading["facts"].list().map { it.enum<ReadingFact>() },
            reading["possibleBarrier"]?.enum<PossibleBarrier>()), root["suggestions"].list().map {
            val s = it.obj("strategyId", "title", "advice", "action")
            CoachSuggestion(s["strategyId"].text(), s["title"].text(), s["advice"].text(), action(s["action"]))
        })
        CoachResponseValidator.validate(value, request, catalog)
    } catch (_: Exception) { ResponseResult.Invalid }

    private fun action(value: Any?): CoachAction {
        require(value is Map<*, *>)
        return when (value["type"].text()) {
            "ADVICE_ONLY" -> { value.obj("type"); CoachAction.AdviceOnly }
            "TARGET" -> value.obj("type", "amount", "unit").let { CoachAction.Target(it["amount"].text(), it["unit"].text()) }
            "SCHEDULE" -> value.obj("type", "schedule").let { CoachAction.Schedule(schedule(it["schedule"])) }
            "CUE_ANCHOR" -> value.obj("type", "cue", "anchor").let { CoachAction.CueAnchor(it["cue"].text(), it["anchor"].text()) }
            "PLAN" -> value.obj("type", "note").let { CoachAction.Plan(it["note"].text()) }
            "REMINDER" -> value.obj("type", "setting").let { CoachAction.Reminder(reminder(it["setting"])) }
            "NEW_HABIT_COUNT" -> value.obj("type", "count").let { CoachAction.NewHabitCount(it["count"].integer()) }
            else -> error("Unsupported action")
        }
    }
    private fun schedule(value: Any?): HabitSchedule {
        require(value is Map<*, *>)
        return when (value["kind"].text()) {
            "DAILY" -> { value.obj("kind"); HabitSchedule.Daily }
            "WEEKLY" -> value.obj("kind", "quota").let { HabitSchedule.Weekly(it["quota"].integer()) }
            "CUSTOM" -> value.obj("kind", "weekdays").let {
                val days = it["weekdays"].list().map { day -> DayOfWeek.of(day.integer()) }
                require(days.distinct().size == days.size)
                HabitSchedule.Custom(days.toSet())
            }
            else -> error("Unsupported frequency")
        }
    }
    private fun reminder(value: Any?): ReminderSetting {
        require(value is Map<*, *>)
        return when (value["mode"].text()) {
            "INHERIT" -> { value.obj("mode"); ReminderSetting.Inherit }
            "OFF" -> { value.obj("mode"); ReminderSetting.Off }
            "AT" -> value.obj("mode", "minuteOfDay").let { ReminderSetting.At(it["minuteOfDay"].integer()) }
            else -> error("Unsupported reminder")
        }
    }
}
