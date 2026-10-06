package com.example.habit.ui.screens.newhabit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.habit.R
import com.example.habit.ui.components.*
import com.example.habit.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.example.habit.coach.CoachApplyReceipt
import com.example.habit.coach.CoachUndoResult
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** SCR-05: one saved New/Edit form. Coach entry owns no database row or permanent screen. */
@Composable
fun NewHabitScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    onOpenCoach: ((FormCoachEntry) -> Unit)? = null,
    planningResult: ArrayList<String>? = null,
    onPlanningResultConsumed: () -> Unit = {},
    viewModel: NewHabitViewModel = viewModel(factory = NewHabitViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val savedActionId by viewModel.coachActionId.collectAsStateWithLifecycle()
    val actionId = savedActionId?.takeIf { it.isNotBlank() }
    var receipt by remember { mutableStateOf<CoachApplyReceipt?>(null) }
    var actionNow by remember { mutableLongStateOf(viewModel.coachNow()) }
    LaunchedEffect(actionId, state.savedHabitId) {
        while (actionId != null && state.savedHabitId == null) {
            receipt = viewModel.coachReceipt(actionId); actionNow = viewModel.coachNow()
            delay(250)
        }
        if (state.savedHabitId != null) receipt = null
    }
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notConnected = stringResource(R.string.form_coach_not_connected)
    val disabled = stringResource(R.string.form_coach_disabled)
    fun back() { if (viewModel.requestBack()) onBack() }
    fun coach() {
        val entry = viewModel.coachEntry()
        if (entry != null && onOpenCoach != null) onOpenCoach(entry)
        else scope.launchSnackbar(snackbars, if (entry == null) disabled else notConnected)
    }
    BackHandler { back() }
    LaunchedEffect(state.savedHabitId) { if (state.savedHabitId != null) onCreated() }
    LaunchedEffect(planningResult, state.coachReady, state.loading, state.loadError) {
        if (planningResult != null && state.coachReady && !state.loading && state.loadError == null) {
            PlanningDraftContract.decode(planningResult)?.let { viewModel.applyPlanning(it) }
            onPlanningResultConsumed()
        }
    }
    LaunchedEffect(state.automaticCoach, state.coachReady, state.loading, state.loadError) {
        if (state.automaticCoach && state.coachReady && !state.loading && state.loadError == null) {
            viewModel.consumeAutomaticCoach()
            coach()
        }
    }
    HabitFormContent(state, snackbars, ::back, viewModel::change, viewModel::toggleDay,
        { viewModel.save() }, ::coach, viewModel::retryLoad, viewModel::requestReload,
        receipt?.takeIf { it.canUndo(actionNow) && state.savedHabitId == null }, {
            val result = receipt?.let { viewModel.undoCoach(it.id) }
            receipt = actionId?.let(viewModel::coachReceipt)
            scope.launchSnackbar(snackbars, when (result) {
                CoachUndoResult.UNDONE, CoachUndoResult.ALREADY_UNDONE -> "Draft change undone."
                CoachUndoResult.CONFLICT -> "A later draft edit prevents Undo. Your later change is kept."
                else -> "This draft change is no longer available to undo."
            })
        })
    if (state.confirmDiscard) FormConfirmation(R.string.form_discard_title, R.string.form_discard_body,
        R.string.form_discard, onDismiss = viewModel::dismissDiscard, onConfirm = onBack)
    if (state.confirmDuplicate) FormConfirmation(R.string.form_duplicate_title, R.string.form_duplicate_body,
        if (state.editing) R.string.form_save_anyway else R.string.form_create_anyway,
        onDismiss = viewModel::dismissDuplicate, onConfirm = { viewModel.save(allowDuplicate = true) })
    if (state.confirmReload) FormConfirmation(R.string.form_reload_title, R.string.form_reload_body,
        R.string.form_reload, onDismiss = viewModel::dismissReload, onConfirm = viewModel::reload)
}

private fun kotlinx.coroutines.CoroutineScope.launchSnackbar(host: SnackbarHostState, message: String) =
    launch { host.showSnackbar(message) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HabitFormContent(
    state: HabitFormUiState,
    snackbars: SnackbarHostState = remember { SnackbarHostState() },
    onBack: () -> Unit,
    onChange: (HabitFormDraft) -> Unit,
    onDay: (DayOfWeek) -> Unit,
    onSave: () -> Unit,
    onCoach: () -> Unit,
    onRetry: () -> Unit,
    onReload: () -> Unit,
    coachReceipt: CoachApplyReceipt? = null,
    onCoachUndo: () -> Unit = {},
) {
    val draft = state.draft
    val appBarHeight = maxOf(Sizes.appBarHeight, with(LocalDensity.current) { HabitTheme.type.headline.lineHeight.toDp() } + Spacing.lg)
    val enabled = !state.saving && state.savedHabitId == null && (!state.editing || (!state.loading && state.loadError == null))
    Scaffold(
        containerColor = HabitTheme.colors.surface,
        snackbarHost = {
            if (coachReceipt != null) Snackbar(action = { TextButton(onClick = onCoachUndo, enabled = !state.saving) { Text("Undo", color = HabitTheme.colors.onPrimaryContainer) } },
                shape = Radius.card, containerColor = HabitTheme.colors.primaryContainer, contentColor = HabitTheme.colors.onPrimaryContainer,
                modifier = Modifier.padding(16.dp)) { Text(coachReceipt.confirmation) }
            else SnackbarHost(snackbars)
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.editing) R.string.form_edit_title else R.string.new_habit_title),
                    style = HabitTheme.type.headline, color = HabitTheme.colors.onSurface) },
                expandedHeight = appBarHeight,
                navigationIcon = { IconButton(onClick = onBack, enabled = !state.saving) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = HabitTheme.colors.onSurface)
                } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HabitTheme.colors.surface),
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                .padding(horizontal = Spacing.gutter).padding(top = Spacing.sm, bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                state.saveError?.let { error ->
                    ErrorText(stringResource(if (error == FormSaveError.CONFLICT) R.string.form_save_conflict else R.string.form_save_failed))
                    if (error == FormSaveError.CONFLICT) TextButton(onClick = onReload) { Text(stringResource(R.string.form_reload)) }
                }
                PrimaryButton(text = stringResource(when {
                    state.saving -> R.string.form_saving
                    state.editing -> R.string.form_save
                    else -> R.string.new_habit_create
                }), onClick = onSave, enabled = state.canSave)
            }
        },
    ) { padding ->
        if (state.editing && (state.loading || state.loadError != null)) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.loading) CircularProgressIndicator(color = HabitTheme.colors.primary)
                else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(Spacing.gutter)) {
                    ErrorText(stringResource(if (state.loadError == FormLoadError.UNAVAILABLE) R.string.form_unavailable else R.string.form_load_failed))
                    if (state.loadError == FormLoadError.READ_FAILED) TextButton(onClick = onRetry) { Text(stringResource(R.string.home_retry)) }
                }
            }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
            .verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter).padding(top = Spacing.lg, bottom = Spacing.lg)) {
            if (!state.editing && state.loadError != null) {
                ErrorText(stringResource(R.string.form_load_failed))
                TextButton(onClick = onRetry) { Text(stringResource(R.string.home_retry)) }
            }
            FormLabel(R.string.new_habit_name_label)
            FormField(draft.name, { onChange(draft.copy(name = it)) }, stringResource(R.string.form_name_accessibility),
                enabled, state.showValidation && state.validation.name,
                keyboard = KeyboardOptions(imeAction = ImeAction.Done, capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences), onDone = onSave)
            if (state.showValidation && state.validation.name) ErrorText(stringResource(R.string.form_name_required))
            if (state.duplicateName) Text(stringResource(R.string.form_duplicate_warning), style = HabitTheme.type.caption, color = HabitTheme.colors.onSurfaceMuted)
            Spacer(Modifier.height(Spacing.xxl))
            FormLabel(R.string.form_appearance)
            AppearancePicker(draft, enabled, onChange)
            Spacer(Modifier.height(Spacing.xxl))
            FormLabel(R.string.form_frequency)
            FormSegments(listOf("DAILY" to stringResource(R.string.form_daily), "WEEKLY" to stringResource(R.string.form_weekly),
                "CUSTOM" to stringResource(R.string.form_custom)), draft.frequency, enabled) { onChange(draft.copy(frequency = it)) }
            Spacer(Modifier.height(Spacing.sm))
            if (draft.frequency == "WEEKLY") {
                FormLabel(R.string.form_weekly_quota)
                FormField(draft.quota, { onChange(draft.copy(quota = it)) }, stringResource(R.string.form_weekly_quota), enabled,
                    state.showValidation && state.validation.schedule, keyboard = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done), onDone = onSave)
                Text(stringResource(R.string.form_weekly_help), style = HabitTheme.type.caption, color = HabitTheme.colors.onSurfaceMuted)
            } else {
                WeekdayPicker(draft, enabled && draft.frequency == "CUSTOM", onDay)
            }
            if (state.showValidation && state.validation.schedule) ErrorText(stringResource(if (draft.frequency == "WEEKLY") R.string.form_quota_error else R.string.form_days_error))
            Spacer(Modifier.height(Spacing.xxl))
            FormLabel(R.string.form_tracking)
            FormSegments(listOf("BINARY" to stringResource(R.string.form_binary), "QUANTITY" to stringResource(R.string.form_quantity)), draft.tracking, enabled) {
                onChange(draft.copy(tracking = it))
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(stringResource(if (draft.tracking == "BINARY") R.string.form_binary_help else R.string.form_quantity_help),
                style = HabitTheme.type.caption, color = HabitTheme.colors.onSurfaceMuted)
            if (draft.tracking == "QUANTITY") {
                Spacer(Modifier.height(Spacing.lg))
                FormLabel(R.string.form_target)
                FormField(draft.target, { onChange(draft.copy(target = it)) }, stringResource(R.string.form_target), enabled,
                    state.showValidation && state.validation.target, keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next))
                if (state.showValidation && state.validation.target) ErrorText(stringResource(R.string.form_target_error))
                Spacer(Modifier.height(Spacing.lg))
                FormLabel(R.string.form_unit)
                FormField(draft.unit, { onChange(draft.copy(unit = it)) }, stringResource(R.string.form_unit_accessibility), enabled,
                    state.showValidation && state.validation.unit, keyboard = KeyboardOptions(imeAction = ImeAction.Done), onDone = onSave, placeholder = stringResource(R.string.form_unit_example))
                if (state.showValidation && state.validation.unit) ErrorText(stringResource(R.string.form_unit_error))
            }
            if (state.editing) {
                Spacer(Modifier.height(Spacing.lg))
                Text(stringResource(R.string.form_history_help), style = HabitTheme.type.caption, color = HabitTheme.colors.onSurfaceMuted)
            }
            if (draft.cue.isNotBlank() || draft.anchor.isNotBlank() || draft.planNote.isNotBlank()) {
                Spacer(Modifier.height(Spacing.lg))
                Text(listOf(draft.cue, draft.anchor, draft.planNote).filter { it.isNotBlank() }.joinToString("\n"),
                    style = HabitTheme.type.caption, color = HabitTheme.colors.onSurfaceMuted)
            }
            if (state.coachEnabled) {
                Spacer(Modifier.height(Spacing.xxl))
                CoachPlanningCard(enabled && !state.loading && state.loadError == null, onCoach)
            }
        }
    }
}

@Composable
private fun FormLabel(label: Int) {
    SectionLabel(stringResource(label)); Spacer(Modifier.height(Spacing.sm))
}
@Composable
private fun ErrorText(message: String) { Text(message, color = HabitTheme.colors.danger, style = HabitTheme.type.caption) }

@Composable
private fun FormField(value: String, onChange: (String) -> Unit, description: String, enabled: Boolean, error: Boolean,
    keyboard: KeyboardOptions = KeyboardOptions.Default, onDone: () -> Unit = {}, placeholder: String? = null) {
    OutlinedTextField(value, onChange, singleLine = true, enabled = enabled, isError = error, shape = Radius.md,
        placeholder = placeholder?.let { { Text(it, style = HabitTheme.type.title, color = HabitTheme.colors.onSurfaceFaint) } },
        textStyle = HabitTheme.type.title, keyboardOptions = keyboard, keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HabitTheme.colors.primary, unfocusedBorderColor = HabitTheme.colors.outline,
            focusedContainerColor = HabitTheme.colors.surfaceCard, unfocusedContainerColor = HabitTheme.colors.surfaceCard,
            focusedTextColor = HabitTheme.colors.onSurface, unfocusedTextColor = HabitTheme.colors.onSurface, cursorColor = HabitTheme.colors.primary,
            errorBorderColor = HabitTheme.colors.danger, errorContainerColor = HabitTheme.colors.surfaceCard),
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).semantics { contentDescription = description })
}

@Composable
private fun AppearancePicker(draft: HabitFormDraft, enabled: Boolean, onChange: (HabitFormDraft) -> Unit) {
    val choices = if (HabitAppearanceOptions.any { it.iconKey == draft.iconKey && it.colorKey == draft.colorKey }) HabitAppearanceOptions else
        HabitAppearanceOptions + HabitAppearanceOption(draft.iconKey, draft.colorKey, R.string.habit_appearance_current)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        items(choices) { option ->
            val selected = option.iconKey == draft.iconKey && option.colorKey == draft.colorKey
            val label = stringResource(option.label)
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)
                .border(if (selected) 2.dp else 0.dp, if (selected) HabitTheme.colors.primary else Color.Transparent, Radius.card)
                .padding(if (selected) Spacing.xs else 0.dp).background(HabitTheme.colors.habitAccent(option.colorKey), Radius.card)
                .selectable(selected, enabled = enabled, role = Role.RadioButton,
                    onClick = { onChange(draft.copy(iconKey = option.iconKey, colorKey = option.colorKey)) })
                .semantics { contentDescription = label }) {
                Icon(habitIcon(option.iconKey), null, tint = if (HabitTheme.colors.isDark) HabitTheme.colors.surfaceSunken else Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun FormSegments(options: List<Pair<String, String>>, selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).background(HabitTheme.colors.surfaceSunken, Radius.pill).padding(horizontal = Spacing.xs, vertical = 2.dp)) {
        options.forEach { (key, label) ->
            Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).heightIn(min = Sizes.touchTarget)
                .background(if (selected == key) HabitTheme.colors.surfaceCard else Color.Transparent, Radius.pill)
                .selectable(selected == key, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(key) }).padding(Spacing.sm)) {
                Text(label, style = HabitTheme.type.caption, fontWeight = FontWeight.Bold,
                    color = if (selected == key) HabitTheme.colors.onSurface else HabitTheme.colors.onSurfaceMuted)
            }
        }
    }
}

@Composable
private fun WeekdayPicker(draft: HabitFormDraft, enabled: Boolean, onDay: (DayOfWeek) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        DayOfWeek.entries.forEach { day ->
            val selected = draft.frequency == "DAILY" || draft.weekdayMask and (1 shl (day.value - 1)) != 0
            val fullName = day.getDisplayName(TextStyle.FULL, Locale.getDefault())
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(Sizes.touchTarget)
                .toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onDay(day) })
                .semantics { contentDescription = fullName }) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp).background(
                    if (selected) HabitTheme.colors.primaryContainer else HabitTheme.colors.surfaceSunken, CircleShape)) {
                    Text(day.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = HabitTheme.type.caption, fontWeight = FontWeight.Bold,
                        color = if (selected) HabitTheme.colors.onPrimaryContainer else HabitTheme.colors.onSurfaceMuted)
                }
            }
        }
    }
}

@Composable
private fun CoachPlanningCard(enabled: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.fillMaxWidth().heightIn(min = 116.dp).background(HabitTheme.colors.primaryContainer, Radius.card)
            .border(Elevation.cardOutline, HabitTheme.colors.primary.copy(alpha = 0.18f), Radius.card)
            .clickable(enabled = enabled, onClick = onClick).padding(Spacing.lg)) {
        Box(Modifier.size(48.dp).background(HabitTheme.colors.primary, Radius.md), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Explore, null, tint = HabitTheme.colors.onPrimary, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.form_coach_title), style = HabitTheme.type.body, fontWeight = FontWeight.Bold, color = HabitTheme.colors.onPrimaryContainer)
            Text(stringResource(R.string.form_coach_body), style = HabitTheme.type.caption, color = HabitTheme.colors.onPrimaryContainer)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = HabitTheme.colors.onPrimaryContainer, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FormConfirmation(title: Int, body: Int, action: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = HabitTheme.colors.surfaceCard,
        title = { Text(stringResource(title), style = HabitTheme.type.title) },
        text = { Text(stringResource(body), style = HabitTheme.type.body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(action), color = HabitTheme.colors.primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.form_keep_editing)) } })
}

@Preview(widthDp = 393, heightDp = 832)
@Composable
private fun FormPreview() { HabitTheme { HabitFormContent(HabitFormUiState(HabitFormDraft(name = "Read 20 pages"), false, loading = false, coachEnabled = true),
    onBack = {}, onChange = {}, onDay = {}, onSave = {}, onCoach = {}, onRetry = {}, onReload = {}) } }
