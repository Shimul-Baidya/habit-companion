package com.example.habit.ui.screens.newhabit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.habit.R
import com.example.habit.ui.components.PrimaryButton
import com.example.habit.ui.components.SectionLabel
import com.example.habit.ui.theme.HabitTheme
import com.example.habit.ui.theme.Radius
import com.example.habit.ui.theme.Spacing

/**
 * Stub for SCR-05. It exists so SCR-04 is reachable — a habit has to be creatable before
 * the populated Home can be seen at all.
 *
 * Slide 12 specifies far more than this: icon and colour swatches, a frequency segmented
 * control, the custom-day picker, the Coach card above Create, and a dirty-form back
 * confirmation. None of that is built yet, and the note on screen says so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewHabitScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: NewHabitViewModel = viewModel(factory = NewHabitViewModel.Factory),
) {
    var name by remember { mutableStateOf("") }

    Scaffold(
        containerColor = HabitTheme.colors.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.new_habit_title),
                        style = HabitTheme.type.headline,
                        color = HabitTheme.colors.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = HabitTheme.colors.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HabitTheme.colors.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.gutter)
                .imePadding(),
        ) {
            SectionLabel(text = stringResource(R.string.new_habit_name_label))

            Spacer(Modifier.height(Spacing.sm))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                shape = Radius.md,
                textStyle = HabitTheme.type.title,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
            )

            Spacer(Modifier.height(Spacing.xxl))

            // 11 — Create is disabled until the name is non-blank.
            PrimaryButton(
                text = stringResource(R.string.new_habit_create),
                onClick = { viewModel.create(name) { onCreated() } },
                enabled = name.isNotBlank(),
            )

            Spacer(Modifier.height(Spacing.lg))

            Text(
                text = stringResource(R.string.new_habit_stub_note),
                style = HabitTheme.type.caption,
                color = HabitTheme.colors.onSurfaceFaint,
            )
        }
    }
}
