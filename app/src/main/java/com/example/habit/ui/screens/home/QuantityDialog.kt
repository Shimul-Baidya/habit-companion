package com.example.habit.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.example.habit.R
import com.example.habit.ui.theme.HabitTheme
import com.example.habit.ui.theme.Spacing

/** A single amount field, with explicit clear. Back dismisses this dialog before Home. */
@Composable
internal fun QuantityDialog(entry: QuantityEntry, canWrite: Boolean, onAmount: (String) -> Unit,
    onSave: (Boolean) -> Unit, onDismiss: () -> Unit, onRetry: () -> Unit) {
    val available = canWrite && !entry.saving && !entry.stale
    val inputLabel = stringResource(R.string.quantity_input)
    AlertDialog(onDismissRequest = { if (!entry.saving) onDismiss() },
        containerColor = HabitTheme.colors.surfaceCard,
        title = { Text(stringResource(R.string.quantity_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.quantity_target, entry.target, entry.unit))
                OutlinedTextField(value = entry.amount, onValueChange = onAmount, enabled = available,
                    label = { Text(inputLabel) }, singleLine = true, isError = entry.invalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (available) onSave(false) }),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = inputLabel })
                val error = when {
                    entry.stale -> R.string.quantity_stale
                    entry.failed -> R.string.quantity_failed
                    entry.invalid -> R.string.quantity_invalid
                    else -> null
                }
                error?.let { Text(stringResource(it), color = HabitTheme.colors.danger) }
                if (!canWrite && !entry.saving) {
                    Text(stringResource(R.string.home_db_error), color = HabitTheme.colors.onSurfaceMuted)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.home_retry)) }
                }
                TextButton(onClick = { onSave(true) }, enabled = available) { Text(stringResource(R.string.quantity_clear)) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(false) }, enabled = available && entry.valid) {
            Text(if (entry.saving) stringResource(R.string.form_saving) else stringResource(R.string.quantity_save))
        } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !entry.saving) { Text(stringResource(R.string.quantity_cancel)) } })
}
