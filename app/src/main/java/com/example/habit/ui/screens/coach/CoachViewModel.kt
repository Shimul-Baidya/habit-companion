package com.example.habit.ui.screens.coach

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.habit.ui.container
import com.example.habit.coach.*
import com.example.habit.data.local.*
import com.example.habit.domain.*
import com.example.habit.ui.screens.newhabit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Clock
import java.util.UUID

data class CoachBubble(val id: Long, val user: Boolean, val text: String, val details: String? = null)
data class CoachUiState(
    val name: String = "Habit", val subtitle: String = "", val planning: Boolean = false,
    val ready: Boolean = false, val enabled: Boolean = false, val missing: Boolean = false,
    val loading: Boolean = false, val busy: Boolean = false, val localError: Boolean = false,
    val failure: CoachFailure? = null, val operationError: String? = null,
    val response: ValidatedCoachResponse? = null, val strategies: List<AdmittedStrategy> = emptyList(),
    val cachedAt: Long? = null, val question: String = "", val messages: List<CoachBubble> = emptyList(),
    val receipt: CoachApplyReceipt? = null, val now: Long = 0, val retryAt: Long = 0,
) {
    val canRequest get() = ready && enabled && !missing && !localError && !loading && !busy && now >= retryAt
    val canApply get() = ready && enabled && !missing && !localError && !loading && !busy && response != null && failure == null
}

/** One intended entry/request; saved primitives prevent dispatch on recreation or tab re-entry. */
class CoachViewModel(
    private val habitId: Long?, private val records: Flow<List<HabitRecord>>,
    enabled: Flow<Boolean>, private val dates: DateProvider, private val strategies: StrategyRepository,
    private val service: CoachService, private val actions: CoachActionRepository,
    private val saved: SavedStateHandle, private val form: NewHabitViewModel? = null,
    private val clock: Clock = DeviceClock(), private val online: () -> Boolean = { true }, private val timeoutMillis: Long = 30_000,
) : ViewModel() {
    private val mutable = MutableStateFlow(CoachUiState(planning = habitId == null, question = saved["question"] ?: "",
        now = clock.millis(), retryAt = saved["retryAt"] ?: 0L, failure = CoachFailureState.decode(saved["failure"], saved["rateSeconds"])))
    val state = mutable.asStateFlow()
    private var catalog: StrategyCatalog? = null
    private val catalogReady = MutableStateFlow<StrategyCatalog?>(null)
    private var record: HabitRecord? = null
    private var request: CoachRequest? = null
    private var exchangeId: String? = saved["exchange"]
    private var activeJob: Job? = null
    private var cacheReady = habitId == null
    private var localCache: CoachCacheEntity? = null
    private var freshExchangeId: String? = null
    private var storedActions: List<CoachActionEntity> = emptyList()
    private var prepared = false
    private val reads = MutableStateFlow(0)
    private val interruptedOnRestore = saved.get<Boolean>("inFlight") == true

    init {
        viewModelScope.launch { enabled.catch { emit(false) }.collect { on ->
            if (!on) { activeJob?.cancel(); saved["inFlight"] = false }
            mutable.update { it.copy(enabled = on, loading = if (!on) false else it.loading,
                failure = if (!on) CoachFailure.Disabled else if (it.failure == CoachFailure.Disabled) CoachFailureState.decode(saved["failure"], saved["rateSeconds"]) else it.failure) }
        } }
        // An interrupted interaction is never silently sent again.
        if (interruptedOnRestore) {
            saved["inFlight"] = false; saved["failure"] = "SERVER"
            mutable.update { it.copy(failure = CoachFailure.ServerError) }
        }
        viewModelScope.launch {
            reads.collectLatest {
                try {
                    when (val result = strategies.load()) {
                        is CatalogResult.Ready -> { catalog = result.catalog; catalogReady.value = result.catalog }
                        is CatalogResult.Unavailable -> { mutable.update { it.copy(ready = true) }; fail(result.failure); return@collectLatest }
                    }
                    if (!prepared) { prepared = true; restore() }
                    combine(records, enabled.catch { emit(false) }, dates.dates) { list, on, day -> Triple(list, on, day) }
                        .collect { (list, on, day) ->
                            record = list.singleOrNull { it.habit.id == habitId }
                            val missing = habitId != null && (record == null || record?.habit?.archivedAt != null || record!!.habit.createdEpochDay > day.toEpochDay())
                            val draft = form?.coachEntry() as? FormCoachEntry.Planning
                            val name = if (habitId == null) form?.state?.value?.draft?.name?.ifBlank { "New habit" } ?: "New habit" else record?.habit?.name ?: state.value.name
                            val subtitle = if (habitId == null) "New habit · not started yet" else record?.let {
                                val evaluation = StatsAggregator.evaluate(it.toHistory(), day)
                                val rate = evaluation.occurrences.filter { o -> java.time.YearMonth.from(o.date) == java.time.YearMonth.from(day) }
                                    .let(StatsAggregator::totals)
                                val percent = if (rate.eligible == 0L) "no monthly rate yet" else "${rate.completed * 100 / rate.eligible}% this month"
                                val status = when (evaluation.metrics.attention) {
                                    HabitAttention.AT_RISK -> "Needs attention"
                                    HabitAttention.NEUTRAL -> "No settled history yet"
                                    else -> "${evaluation.metrics.currentStreak} occurrence streak"
                                }
                                "$status · $percent"
                            } ?: "Habit unavailable"
                            if (!on || missing) { activeJob?.cancel(); saved["inFlight"] = false }
                            mutable.update { it.copy(name = name, subtitle = subtitle, enabled = on, missing = missing,
                                ready = habitId != null || draft != null, localError = false,
                                loading = if (!on || missing) false else it.loading,
                                failure = if (!on) CoachFailure.Disabled else if (it.failure == CoachFailure.Disabled) CoachFailureState.decode(saved["failure"], saved["rateSeconds"]) else it.failure) }
                            enterOnce()
                        }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { mutable.update { it.copy(localError = true, ready = true, loading = false) } }
            }
        }
        if (habitId != null) {
            viewModelScope.launch {
                reads.collectLatest {
                    try {
                        actions.cache(habitId).collect { cache ->
                            val cleared = localCache != null && cache == null
                            localCache = cache; cacheReady = true
                            if (cleared) {
                                activeJob?.cancel(); request = null; exchangeId = null
                                saved["raw"] = null; saved["request"] = null; saved["exchange"] = null; saved["receipt"] = null
                                mutable.update { it.copy(response = null, cachedAt = null, receipt = null, loading = false,
                                    operationError = "Coach history was cleared. Applied settings are kept.") }
                            } else if (cache != null && !state.value.loading) showCache(cache)
                            enterOnce()
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { mutable.update { it.copy(localError = true) } }
                }
            }
            viewModelScope.launch {
                reads.collectLatest {
                    try { actions.actions(habitId).collect { list ->
                        storedActions = list
                        val key = saved.get<String>("receipt") ?: list.lastOrNull { it.id.substringBeforeLast(':') == exchangeId }?.id
                        val value = list.singleOrNull { it.id == key }
                        mutable.update { it.copy(receipt = value?.let { a -> CoachApplyReceipt(a.id, a.confirmation, a.appliedAt, a.status) }) }
                    } } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { mutable.update { it.copy(localError = true) } }
                }
            }
            viewModelScope.launch {
                reads.collectLatest {
                    try { combine(actions.messages(habitId), catalogReady.filterNotNull()) { messages, c -> messages to c }.collect { (messages, c) ->
                        val bubbles = messages.map { message ->
                            var details: String? = null
                            val text = if (message.role == "COACH") {
                                val cache = actions.exchange(habitId, message.exchangeId)
                                val original = cache?.let { CoachJson.storedRequest(it.request, it.catalogSha256, c) }
                                val validated = original?.let { (CoachJson.response(message.text, it, c) as? ResponseResult.Valid)?.response }
                                validated?.let {
                                    details = it.value.suggestions.joinToString("\n\n") { s ->
                                        val card = requireNotNull(c.byId[s.strategyId])
                                        "${s.title}: ${s.advice}\n${card.title}\nSupplied attribution: ${card.source}"
                                    }
                                    it.conversationText(requireNotNull(original).question)
                                } ?: "Saved Coach response is unavailable."
                            } else message.text
                            CoachBubble(message.id, message.role == "USER", text, details)
                        }
                        mutable.update { it.copy(messages = bubbles) }
                    } } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { mutable.update { it.copy(localError = true) } }
                }
            }
        }
        if (form != null) viewModelScope.launch { form.state.collect { value ->
            mutable.update { it.copy(name = value.draft.name.ifBlank { "New habit" }, ready = !value.loading && value.loadError == null && value.savedHabitId == null && value.coachReady && value.coachEnabled) }
            enterOnce()
        } }
        viewModelScope.launch { while (isActive) {
            val now = clock.millis()
            if (form != null) saved.get<String>("receipt")?.let { key -> mutable.update { it.copy(receipt = form.coachReceipt(key)) } }
            mutable.update { it.copy(now = now) }
            delay(250)
        } }
    }
    private fun restore() {
        val c = catalog ?: return
        request = saved.get<String>("request")?.let { CoachJson.storedRequest(it, c.sha256, c) }
        val raw = saved.get<String>("raw")
        if (request != null && raw != null) {
            val value = (CoachJson.response(raw, request!!, c) as? ResponseResult.Valid)?.response
            mutable.update { it.copy(response = value, strategies = request!!.strategies, cachedAt = saved["cachedAt"]) }
        }
        if (localCache != null) showCache(localCache!!)
    }
    private fun showCache(cache: CoachCacheEntity) {
        val c = catalog ?: return
        if (cache.id == freshExchangeId) return
        val original = CoachJson.storedRequest(cache.request, cache.catalogSha256, c) ?: return
        val value = (CoachJson.response(cache.response, original, c) as? ResponseResult.Valid)?.response ?: return
        request = original; exchangeId = cache.id
        saved["request"] = cache.request; saved["raw"] = cache.response; saved["exchange"] = cache.id; saved["cachedAt"] = cache.createdAt
        val restored = storedActions.lastOrNull { it.id.substringBeforeLast(':') == cache.id }?.let { CoachApplyReceipt(it.id, it.confirmation, it.appliedAt, it.status) }
        mutable.update { it.copy(response = value, strategies = original.strategies, cachedAt = cache.createdAt, receipt = it.receipt ?: restored) }
    }
    private fun enterOnce() {
        if (!state.value.ready || !state.value.enabled || state.value.missing || !cacheReady || catalog == null || saved.get<Boolean>("entered") == true) return
        if (habitId == null && form?.coachEntry() !is FormCoachEntry.Planning) return
        saved["entered"] = true
        if (state.value.response == null && state.value.failure == null) dispatch("")
    }
    fun question(value: String) {
        if (value.length <= CoachLimits.QUESTION) { saved["question"] = value; mutable.update { it.copy(question = value) } }
    }
    fun retry() {
        if (state.value.localError || catalog == null) { reads.update { it + 1 }; return }
        if (!state.value.canRequest) return
        dispatch(saved["lastQuestion"] ?: "")
    }
    fun send() { if (state.value.canRequest && state.value.question.isNotBlank()) dispatch(state.value.question) }
    fun fresh() { if (state.value.canRequest) dispatch("") }
    fun cached() { if (state.value.response != null) { saved["failure"] = null; mutable.update { it.copy(failure = null) } } }
    fun pause() {
        if (state.value.loading) {
            activeJob?.cancel(); saved["inFlight"] = false
            saved["failure"] = "SERVER"
            mutable.update { it.copy(loading = false, failure = CoachFailure.ServerError) }
        }
    }
    private fun dispatch(question: String) {
        if (!state.value.canRequest) return
        val c = catalog ?: return
        saved["lastQuestion"] = question
        val result = if (habitId == null) {
            val draft = (form?.coachEntry() as? FormCoachEntry.Planning)?.request
            if (draft == null) RequestResult.Unavailable(CoachFailure.InvalidInput)
            else CoachRequestBuilder.planning(c, draft.draft, draft.currentHabitCount, question, state.value.enabled)
        } else record?.let { CoachRequestBuilder.existing(c, it.toHistory(), dates.today(), question, state.value.enabled) }
            ?: RequestResult.Unavailable(CoachFailure.InvalidInput)
        if (result is RequestResult.Unavailable) { fail(result.failure); return }
        if (!online()) { fail(CoachFailure.Offline); return }
        val next = (result as RequestResult.Ready).request
        val key = UUID.randomUUID().toString()
        saved["inFlight"] = true; saved["pendingExchange"] = key
        mutable.update { it.copy(loading = true, failure = null, operationError = null) }
        activeJob = viewModelScope.launch {
            try {
                if (habitId != null) actions.beginInteraction(habitId, key, next)
                else {
                    val draft = requireNotNull((form?.coachEntry() as? FormCoachEntry.Planning)?.request)
                    check(form.beginCoach(draft.token, key, next))
                }
                check(state.value.enabled && !state.value.missing)
                when (val result = withTimeout(timeoutMillis) { service.request(next) }) {
                    is CoachServiceResult.Failure -> fail(result.reason)
                    is CoachServiceResult.RawResponse -> {
                        val value = (CoachJson.response(result.json, next, c) as? ResponseResult.Valid)?.response
                        if (value == null) fail(CoachFailure.MalformedResponse)
                        else {
                            check(state.value.enabled && !state.value.missing)
                            if (habitId != null) actions.saveExchange(habitId, key, next, result.json)
                            request = next; exchangeId = key
                            saved["raw"] = result.json; saved["request"] = CoachJson.payload(next); saved["exchange"] = key
                            saved["cachedAt"] = clock.millis(); saved["receipt"] = null; freshExchangeId = key; saved["failure"] = null; saved["question"] = ""; saved["attempt"] = 0
                            mutable.update { it.copy(response = value, strategies = next.strategies, cachedAt = null,
                                receipt = null, question = "", loading = false, failure = null) }
                        }
                    }
                }
            } catch (_: TimeoutCancellationException) { fail(CoachFailure.Timeout) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { fail(CoachFailure.ServerError) }
            finally { saved["inFlight"] = false; mutable.update { it.copy(loading = false) } }
        }
    }
    private fun fail(failure: CoachFailure) {
        if (state.value.response != null && state.value.cachedAt == null) {
            mutable.update { it.copy(cachedAt = localCache?.createdAt ?: saved.get<Long>("cachedAt")) }
        }
        val localInputFailure = failure == CoachFailure.InsufficientContext || failure == CoachFailure.InvalidInput
        val attempt = (saved.get<Int>("attempt") ?: 0) + if (localInputFailure) 0 else 1
        saved["failure"] = CoachFailureState.encode(failure)
        saved["rateSeconds"] = (failure as? CoachFailure.RateLimited)?.retryAfterSeconds
        saved["attempt"] = attempt
        // Local context/input checks send nothing; a useful revised question can be sent immediately.
        val deadline = clock.millis() + CoachRetryPolicy.delayMillis(attempt, failure)
        saved["retryAt"] = deadline
        mutable.update { it.copy(failure = failure, loading = false, retryAt = deadline) }
    }
    fun apply(index: Int, returned: () -> Unit = {}) {
        if (!state.value.canApply) return
        val original = request ?: return; val key = exchangeId ?: return
        mutable.update { it.copy(busy = true, operationError = null) }
        viewModelScope.launch {
            try {
                val receipt = if (habitId != null) actions.apply(habitId, key, index, original)
                    else {
                        val token = requireNotNull((form?.coachEntry() as? FormCoachEntry.Planning)?.request?.token)
                        requireNotNull(form.applyCoach(token, key, index, original, requireNotNull(state.value.response), requireNotNull(catalog)))
                    }
                saved["receipt"] = receipt.id
                mutable.update { it.copy(receipt = receipt) }
                if (habitId == null) returned()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(operationError = "Could not apply. Settings or history may have changed. Retry, or request new suggestions.") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun undo() {
        val receipt = state.value.receipt ?: return
        if (state.value.busy || !receipt.canUndo(clock.millis())) return
        mutable.update { it.copy(busy = true, operationError = null) }
        viewModelScope.launch {
            try {
                val result = if (habitId == null) requireNotNull(form).undoCoach(receipt.id) else actions.undo(receipt.id)
                mutable.update { it.copy(operationError = when (result) {
                    CoachUndoResult.UNDONE, CoachUndoResult.ALREADY_UNDONE -> "Change undone."
                    CoachUndoResult.CONFLICT -> "A later edit prevents Undo. Your later change is kept."
                    CoachUndoResult.EXPIRED -> "The Undo window has expired."
                    else -> "This change is no longer available to undo."
                }) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(operationError = "Could not undo. Try again before the original deadline.") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    companion object {
        fun factory(id: Long?, form: NewHabitViewModel? = null) = viewModelFactory { initializer {
            CoachViewModel(id, container.habitHistory.records, container.settings.coachEnabled, container.dates,
                container.strategies, container.coachService, container.coachActions, createSavedStateHandle(), form, online = {
                    if (container.coachService is UnconfiguredCoachService) true else container.coachConnection.available()
                })
        } }
    }

}
