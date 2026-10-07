package com.example.habit.coach

import androidx.lifecycle.SavedStateHandle
import com.example.habit.domain.*
import com.example.habit.ui.screens.newhabit.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class DraftCoachActionTest {
    private class MovingClock(var at: Instant = Instant.parse("2026-10-07T10:00:00Z")) : Clock() {
        override fun instant() = at
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: ZoneId) = Clock.fixed(at, zone)
    }
    private val catalog = StrategyCatalog.validated((1..3).map { StrategyCard("card_$it", "Card", "Principle", "Prose", "Starting",
        listOf("simplicity", "cues", "planning", "scheduling"), "Attribution") }, byteArrayOf(1))
    private var draft = HabitFormDraft()
    private val saved = SavedStateHandle()
    private val clock = MovingClock()
    private lateinit var handler: DraftCoachActions
    private fun start(value: HabitFormDraft = HabitFormDraft()) {
        draft = value
        handler = DraftCoachActions(saved, "draft-token", clock, { draft }) { next -> handler.changed(draft, next); draft = next }
    }
    private fun interaction(action: CoachAction, id: String = "interaction"): Triple<CoachRequest, ValidatedCoachResponse, String> {
        val request = (CoachRequestBuilder.planning(catalog, draft, 2, enabled = true) as RequestResult.Ready).request
        val response = CoachResponse(CoachReading(listOf(ReadingFact.PLANNING_DRAFT), null), request.strategies.mapIndexed { i, it ->
            CoachSuggestion(it.card.id, "Suggestion", "Conditional advice", if (i == 0) action else CoachAction.AdviceOnly) })
        val valid = (CoachResponseValidator.validate(response, request, catalog) as ResponseResult.Valid).response
        handler.beginInteraction("draft-token", id, request)
        return Triple(request, valid, id)
    }
    private fun apply(value: Triple<CoachRequest, ValidatedCoachResponse, String>) = handler.apply("draft-token", value.third, 0, value.first, value.second, catalog)
    private fun change(next: HabitFormDraft) { handler.changed(draft, next); draft = next }
    @Test fun conversationalReplyCannotApplyANonexistentDraftSuggestion() {
        start()
        val before = draft
        val request = (CoachRequestBuilder.planning(catalog, draft, 2, "Hello", true) as RequestResult.Ready).request
        val raw = CoachResponse(CoachReading(listOf(ReadingFact.PLANNING_DRAFT), null), emptyList(), "What would you like help with?")
        val valid = (CoachResponseValidator.validate(raw, request, catalog) as ResponseResult.Valid).response
        handler.beginInteraction("draft-token", "conversation", request)
        assertTrue(runCatching { handler.apply("draft-token", "conversation", 0, request, valid, catalog) }.isFailure)
        assertEquals(before, draft); assertNull(handler.receipt("conversation:0"))
    }

    @Test fun eachAdmittedDraftActionUpdatesOnlyItsScopeAndUndoRestoresIt() {
        listOf(CoachAction.Schedule(HabitSchedule.Weekly(2)), CoachAction.Schedule(HabitSchedule.Custom(setOf(DayOfWeek.FRIDAY))),
            CoachAction.CueAnchor("after coffee", "breakfast"), CoachAction.Plan("Keep it small"), CoachAction.NewHabitCount(2),
            CoachAction.Target("2.50", "pages")).forEachIndexed { i, action ->
            start(HabitFormDraft(name = "Keep", tracking = "QUANTITY", target = "5", unit = "pages"))
            val before = draft
            val receipt = apply(interaction(action, "action_$i"))
            assertEquals("APPLIED", receipt.status); assertNotEquals(before, draft)
            assertEquals(before.name, draft.name); assertEquals(before.iconKey, draft.iconKey)
            assertEquals(CoachUndoResult.UNDONE, handler.undo(receipt.id)); assertEquals(before, draft)
        }
    }
    @Test fun adviceAndNoChangeDoNotFabricateAMutation() {
        start(); val before = draft
        val receipt = apply(interaction(CoachAction.AdviceOnly))
        assertEquals("ADVICE", receipt.status); assertEquals(before, draft); assertFalse(receipt.canUndo(clock.millis()))
        assertTrue(receipt.confirmation.contains("no habit setting changed"))
        val same = apply(interaction(CoachAction.Schedule(HabitSchedule.Daily), "same"))
        assertEquals("UNCHANGED", same.status); assertEquals(CoachUndoResult.NO_CHANGE, handler.undo(same.id))
    }
    @Test fun repeatedApplyAndUndoUseTheSameIdentity() {
        start(); val value = interaction(CoachAction.Plan("small")); val receipt = apply(value)
        assertEquals(receipt, apply(value)); assertEquals(CoachUndoResult.UNDONE, handler.undo(receipt.id))
        assertEquals(CoachUndoResult.ALREADY_UNDONE, handler.undo(receipt.id))
        assertEquals("UNDONE", apply(value).status); assertEquals("", draft.planNote)
    }
    @Test fun unrelatedLaterEditAndUntouchedAnchorSurviveUndo() {
        start(HabitFormDraft(anchor = "coffee"))
        val receipt = apply(interaction(CoachAction.CueAnchor("morning", "coffee")))
        change(draft.copy(name = "New name", anchor = "tea", planNote = "Later plan"))
        assertEquals(CoachUndoResult.UNDONE, handler.undo(receipt.id)); assertEquals("", draft.cue)
        assertEquals("tea", draft.anchor); assertEquals("Later plan", draft.planNote); assertEquals("New name", draft.name)
    }
    @Test fun sameFieldEditIncludingChangeBackBlocksUndo() {
        start(); val receipt = apply(interaction(CoachAction.Plan("one")))
        change(draft.copy(planNote = "two")); change(draft.copy(planNote = "one"))
        assertEquals(CoachUndoResult.CONFLICT, handler.undo(receipt.id)); assertEquals("one", draft.planNote)
    }
    @Test fun editsDuringRequestRejectOnlyAffectedFields() {
        start(); val value = interaction(CoachAction.CueAnchor("new", ""))
        change(draft.copy(cue = "manual"))
        assertTrue(runCatching { apply(value) }.isFailure); assertEquals("manual", draft.cue)
    }
    @Test fun tenSecondDeadlineAndClockRollbackCannotExtendUndo() {
        start(); val receipt = apply(interaction(CoachAction.Plan("one")))
        clock.at = clock.at.plusMillis(10_000)
        assertEquals(CoachUndoResult.EXPIRED, handler.undo(receipt.id))
        start(); val next = apply(interaction(CoachAction.Plan("two"), "rollback"))
        clock.at = clock.at.minusMillis(1)
        assertEquals(CoachUndoResult.EXPIRED, handler.undo(next.id))
    }
    @Test fun recreationKeepsOriginalDeadlineAndPriorFieldPrimitives() {
        start(); val receipt = apply(interaction(CoachAction.Plan("one")))
        clock.at = clock.at.plusMillis(9_999)
        val restored = SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) })
        handler = DraftCoachActions(restored, "draft-token", clock, { draft }) { next -> handler.changed(draft, next); draft = next }
        assertTrue(handler.receipt(receipt.id)!!.canUndo(clock.millis()))
        assertEquals(CoachUndoResult.UNDONE, handler.undo(receipt.id)); assertEquals("", draft.planNote)
    }
    @Test fun wrongDraftAndUnreservedInteractionRejectWithoutChanges() {
        start(); val value = interaction(CoachAction.Plan("one")); val before = draft
        assertTrue(runCatching { handler.apply("wrong", value.third, 0, value.first, value.second, catalog) }.isFailure)
        assertTrue(runCatching { handler.apply("draft-token", "missing", 0, value.first, value.second, catalog) }.isFailure)
        assertEquals(before, draft)
    }
    @Test fun actionFeedbackMatchesActualScopeAndExpectationDate() {
        val date = LocalDate.of(2026, 10, 8)
        assertTrue(CoachActionFeedback.text(CoachAction.Target("2", "pages"), date).contains("from 2026-10-08"))
        assertTrue(CoachActionFeedback.text(CoachAction.Schedule(HabitSchedule.Weekly(3)), date).contains("3 completions per week"))
        assertTrue(CoachActionFeedback.text(CoachAction.Reminder(ReminderSetting.Off)).contains("Reminder turned off"))
        assertFalse(CoachActionFeedback.text(CoachAction.Plan("small")).contains("Goal"))
        assertTrue(CoachActionFeedback.text(CoachAction.NewHabitCount(1)).contains("none created"))
    }
}
