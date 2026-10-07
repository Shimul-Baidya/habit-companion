package com.example.habit.ui.screens.coach

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.habit.coach.*
import com.example.habit.ui.theme.*

@Composable
fun CoachScreen(viewModel: CoachViewModel, onBack: () -> Unit, onAppliedDraft: () -> Unit = onBack,
    bottomNavigation: @Composable () -> Unit = {}, onChooseHabit: (() -> Unit)? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) { onDispose { viewModel.pause() } }
    BackHandler { onBack() }
    LaunchedEffect(state.missing) { if (state.missing) onBack() }
    CoachContent(state, onBack, { viewModel.apply(it, onAppliedDraft) }, viewModel::undo,
        viewModel::question, viewModel::send, viewModel::retry, viewModel::cached, viewModel::fresh,
        bottomNavigation, onChooseHabit)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CoachContent(state: CoachUiState, onBack: () -> Unit, onApply: (Int) -> Unit, onUndo: () -> Unit,
    onQuestion: (String) -> Unit, onSend: () -> Unit, onRetry: () -> Unit, onCached: () -> Unit,
    onFresh: () -> Unit, bottomNavigation: @Composable () -> Unit = {}, onChooseHabit: (() -> Unit)? = null) {
    val colors = HabitTheme.colors
    val list = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    var privacy by rememberSaveable { mutableStateOf(false) }
    val applied = state.receipt != null
    var shownReceipt by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.receipt?.id, state.messages.lastOrNull()?.id, state.loading, state.failure, state.localError) {
        if (state.failure != null || state.localError) {
            list.animateScrollToItem(1)
        } else if (applied && shownReceipt != state.receipt.id) {
            shownReceipt = state.receipt.id
            list.animateScrollToItem(2 + (if (state.cachedAt != null) 1 else 0) + (if (state.operationError != null) 1 else 0))
        } else if (state.messages.any { it.user } && !state.loading) {
            withFrameNanos { }; withFrameNanos { }
            list.animateScrollToItem((list.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        }
    }
    Scaffold(containerColor = colors.surface,
        topBar = { TopAppBar(title = { Text("Coach", style = HabitTheme.type.headline, color = colors.onSurface) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.onSurface) } },
            actions = { IconButton(onClick = { privacy = true }) { Icon(Icons.Default.Info, "Coach privacy and strategy sources", tint = colors.onSurfaceMuted) }
                if (onChooseHabit != null) TextButton(onClick = onChooseHabit, enabled = !state.busy && !state.loading) { Text("Change habit") } },
            expandedHeight = maxOf(56.dp, with(LocalDensity.current) { HabitTheme.type.headline.lineHeight.toDp() } + 16.dp),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)) },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().imePadding()) {
                if (state.ready && !state.missing) {
                    Text(state.inputStatus(), style = HabitTheme.type.caption, color = colors.onSurfaceMuted,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).padding(top = 8.dp)
                            .testTag("coach-input-status"))
                    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).padding(top = 8.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(state.question, onQuestion, enabled = state.enabled && !state.busy,
                            label = { Text("Your question", style = HabitTheme.type.caption) },
                            placeholder = { Text("Type your question…", style = HabitTheme.type.caption) },
                            textStyle = HabitTheme.type.body, shape = Radius.pill, maxLines = 4,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { if (state.canRequest && state.question.isNotBlank()) { keyboard?.hide(); onSend() } }),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = colors.surfaceCard,
                                focusedContainerColor = colors.surfaceCard, unfocusedBorderColor = colors.outline,
                                focusedBorderColor = colors.primary, focusedTextColor = colors.onSurface, unfocusedTextColor = colors.onSurface),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("coach-question").semantics { contentDescription = "Question for Coach" })
                        FilledIconButton(onClick = { keyboard?.hide(); onSend() }, enabled = state.canRequest && state.question.isNotBlank(),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                            modifier = Modifier.size(48.dp).semantics { contentDescription = "Send question" }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (state.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.onSurfaceMuted)
                                else Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                                Text("Send", style = HabitTheme.type.micro.copy(letterSpacing = 0.sp))
                            }
                        }
                    }
                }
                if (!WindowInsets.isImeVisible) bottomNavigation()
            }
        }) { padding ->
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("coach-list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item("context") { Surface(shape = Radius.card, color = colors.surfaceCard, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.width(4.dp).height(44.dp).clip(Radius.pill).background(colors.primary))
                    Column { Text(state.name, style = HabitTheme.type.title, color = colors.onSurface)
                        Text(when (state.receipt?.status) {
                            "UNDONE" -> "Change undone · ${state.subtitle}"
                            "CONFLICT" -> "Later edits kept · ${state.subtitle}"
                            null -> state.subtitle
                            else -> state.receipt.confirmation
                        }, style = HabitTheme.type.caption, color = colors.onSurfaceMuted)
                        Text("Google Gemini receives Coach context and questions. Free-tier data may be reviewed.",
                            style = HabitTheme.type.caption, color = colors.onSurfaceMuted, modifier = Modifier.padding(top = 8.dp).testTag("coach-transmission")) }
                }
            } }
            item("status") {
                Crossfade(targetState = state.failure, animationSpec = tween(200), label = "Coach error") { failure ->
                    when {
                        state.localError -> CoachFailureCard(CoachFailure.ServerError, "Couldn't read local Coach data. Local habits and history remain available.", true, onRetry)
                        failure != null -> CoachFailureCard(failure, failure.explanation(), state.canRequest,
                            if (failure == CoachFailure.InsufficientContext) {
                                { onQuestion("How can I make starting this habit easier?"); onSend() }
                            } else onRetry)
                        state.loading || !state.ready -> ReadingShimmer()
                        state.response == null -> Column {
                            Text("Ready when you are. Local habits and history remain available.", style = HabitTheme.type.body, color = colors.onSurfaceMuted)
                            TextButton(onClick = onFresh, enabled = state.canRequest) { Text("Get suggestions") }
                        }
                        !applied -> Column(Modifier.padding(top = 8.dp)) {
                            Text(if (state.planning) "BEFORE YOU COMMIT" else "WHAT I'M SEEING", style = HabitTheme.type.label, color = colors.onSurfaceMuted)
                            Text(state.response.readingText(), style = HabitTheme.type.body.copy(lineHeight = 24.sp), color = colors.onSurface,
                                modifier = Modifier.padding(top = 8.dp).testTag("coach-reading"))
                        }
                    }
                }
            }
            if (state.response != null && state.cachedAt != null) item("cache") {
                Surface(shape = Radius.card, border = BorderStroke(1.dp, colors.outline), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Saved suggestions · ${java.time.Instant.ofEpochMilli(state.cachedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}", style = HabitTheme.type.caption, color = colors.onSurface)
                        Text("Based on the original context, not a new response.", style = HabitTheme.type.caption, color = colors.onSurfaceMuted)
                        if (state.failure != null) TextButton(onClick = onCached) { Text("View saved suggestions") }
                        else TextButton(onClick = onFresh, enabled = state.canRequest) { Text("Get new suggestions") }
                    }
                }
            }
            state.operationError?.let { notice -> item("notice") {
                Text(notice, style = HabitTheme.type.caption, color = colors.onSurfaceMuted,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            } }
            if (applied) item("applied") {
                AnimatedVisibility(visible = true, enter = expandVertically(tween(250)) + fadeIn(tween(250))) {
                    CoachConfirmation(requireNotNull(state.receipt), state.now, state.busy, onUndo)
                }
            }
            if (state.response != null && !applied && state.failure == null && !state.loading) {
                item("heading") { Text(if (state.planning) "Three ways to start" else "Three things to try", style = HabitTheme.type.title,
                    color = colors.onSurface, modifier = Modifier.padding(top = 12.dp)) }
                itemsIndexed(state.response.value.suggestions, key = { _, s -> s.strategyId }) { index, suggestion ->
                    val strategy = state.strategies.single { it.card.id == suggestion.strategyId }.card
                    Surface(onClick = { onApply(index) }, enabled = state.canApply, shape = Radius.card, color = colors.surfaceCard,
                        border = BorderStroke(1.dp, colors.outline), modifier = Modifier.fillMaxWidth().testTag("coach-card-$index")) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(suggestion.title, style = HabitTheme.type.title, color = colors.onSurface, modifier = Modifier.weight(1f))
                                TextButton(onClick = { onApply(index) }, enabled = state.canApply, modifier = Modifier.widthIn(min = 78.dp).heightIn(min = 48.dp)) {
                                    Box(Modifier.clip(Radius.pill).background(colors.primaryContainer).padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        Text("Apply", style = HabitTheme.type.micro.copy(letterSpacing = 0.sp), color = colors.onPrimaryContainer)
                                    }
                                }
                            }
                            Text(suggestion.advice, style = HabitTheme.type.caption, color = colors.onSurfaceMuted)
                            Text(strategy.title.uppercase(), style = HabitTheme.type.micro.copy(letterSpacing = 0.5.sp), color = colors.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        }
                    }
                }
            }
            if (state.messages.isNotEmpty() && (applied || state.messages.any { it.user })) {
                item("thread-label") { Text("Conversation · saved on this device", style = HabitTheme.type.caption, color = colors.onSurfaceMuted) }
                items(state.messages, key = { "message-${it.id}" }) { message ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = if (message.user) Alignment.CenterEnd else Alignment.CenterStart) {
                        Surface(shape = Radius.card, color = if (message.user) colors.primary else colors.surfaceCard,
                            border = if (message.user) null else BorderStroke(1.dp, colors.outline), modifier = Modifier.fillMaxWidth(0.76f)) {
                            Column(Modifier.padding(16.dp)) {
                                Text(message.text, style = HabitTheme.type.body, color = if (message.user) colors.onPrimary else colors.onSurface)
                                message.details?.let { details ->
                                    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
                                    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide suggestions" else "Saved suggestions") }
                                    if (expanded) Text(details, style = HabitTheme.type.caption, color = colors.onSurfaceMuted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (privacy) AlertDialog(onDismissRequest = { privacy = false }, title = { Text("Coach privacy") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (state.planning) "Google Gemini receives this unsaved draft's name, schedule, tracking target/unit, current habit count, your question and matched strategies. It never creates a habit automatically. Questions may include personal details you choose to enter."
                else "Google Gemini receives only this habit's measured summary, your question and matched strategies. Habit names, local IDs, other habits and the full conversation are excluded. Questions may include personal details you choose to enter.")
            Text("This academic demo uses Google’s free Gemini API tier. Google may use inputs and replies to improve its products; human reviewers may read them. Avoid sensitive, confidential or personal information in draft names, units and questions. Habit records and Coach history stay on this device; each interaction sends only the permitted context above. No full conversation is sent automatically.")
            if (state.strategies.isNotEmpty()) Text("Strategy sources", style = HabitTheme.type.title)
            state.strategies.forEach { admitted ->
                Text(admitted.card.title, style = HabitTheme.type.body)
                Text("Supplied attribution: ${admitted.card.source}", style = HabitTheme.type.caption)
            }
        } }, confirmButton = { TextButton(onClick = { privacy = false }) { Text("Close") } })
}

@Composable internal fun CoachConfirmation(receipt: CoachApplyReceipt, now: Long, busy: Boolean, onUndo: () -> Unit) {
    val colors = HabitTheme.colors
    Surface(shape = Radius.card, color = colors.primaryContainer, modifier = Modifier.fillMaxWidth().heightIn(min = 104.dp).testTag("coach-confirmation")) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).background(colors.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, null, Modifier.size(24.dp), tint = colors.onPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text(if (receipt.status == "UNDONE") "Change undone" else receipt.confirmation, style = HabitTheme.type.body, color = colors.onPrimaryContainer,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (receipt.canUndo(now)) TextButton(onClick = onUndo, enabled = !busy) { Text("Undo", color = colors.onPrimaryContainer) }
                else Text(if (receipt.status == "APPLIED") "Undo window expired" else when (receipt.status) {
                    "CONFLICT" -> "Later edits are kept"
                    "ADVICE", "UNCHANGED" -> "No habit setting changed"
                    else -> "History and completions are kept"
                }, style = HabitTheme.type.caption, color = colors.onPrimaryContainer)
            }
        }
    }
}
@Composable private fun CoachFailureCard(failure: CoachFailure, explanation: String, retry: Boolean, onRetry: () -> Unit) {
    val colors = HabitTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(148.dp).background(colors.surfaceSunken, CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (failure == CoachFailure.Offline) Icons.Default.CloudOff else Icons.Default.Explore, null, Modifier.size(56.dp), tint = colors.onSurfaceMuted)
        }
        Text(failure.headline(), style = HabitTheme.type.titleLg, color = colors.onSurface, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 24.dp))
        Text(explanation, style = HabitTheme.type.body, color = colors.onSurfaceMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        if (failure != CoachFailure.Disabled && failure != CoachFailure.Unconfigured) OutlinedButton(onClick = onRetry, enabled = retry,
            shape = Radius.pill, border = BorderStroke(2.dp, colors.primary), modifier = Modifier.padding(top = 24.dp).heightIn(min = 52.dp)) {
            Text(if (!retry) "Please wait…" else if (failure == CoachFailure.InsufficientContext) "Help me get started" else "Try again", color = colors.primary)
        }
    }
}
@Composable private fun ReadingShimmer() {
    val transition = rememberInfiniteTransition(label = "Reading shimmer")
    val alpha by transition.animateFloat(0.3f, 0.8f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "Shimmer alpha")
    Column(Modifier.fillMaxWidth().padding(top = 16.dp).semantics { contentDescription = "Loading Coach suggestions" }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) { Box(Modifier.fillMaxWidth(if (it == 2) 0.7f else 1f).height(16.dp).clip(Radius.sm).background(HabitTheme.colors.outline.copy(alpha = alpha))) }
    }
}
