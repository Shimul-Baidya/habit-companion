package com.example.habit.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.habit.R
import com.example.habit.ui.theme.Elevation
import com.example.habit.ui.theme.HabitTheme
import com.example.habit.ui.theme.Sizes

/** The four nav roots. Order is fixed; these are the tabs on SCR-04, SCR-12/13 and SCR-15. */
enum class HomeTab(val labelRes: Int, val icon: ImageVector) {
    HOME(R.string.tab_home, Icons.Outlined.Home),
    PROGRESS(R.string.tab_progress, Icons.Outlined.BarChart),
    COACH(R.string.tab_coach, Icons.Outlined.ChatBubbleOutline),
    PROFILE(R.string.tab_profile, Icons.Outlined.Person),
}

/**
 * C-09 — bottom nav. 72dp, four items, primary when selected and on.surface.faint when
 * not. Tab switching is a fade, never a slide (SCR-04 element 13).
 *
 * SCR-03 passes a reduced [enabled] set: Progress and Coach are visible but inert until
 * an active habit exists, and tapping one explains why rather than doing nothing.
 * Progress remains available when archived records preserve a historical collection.
 */
@Composable
fun BottomNav(
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Set<HomeTab> = HomeTab.entries.toSet(),
) {
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        HorizontalDivider(
            thickness = Elevation.cardOutline,
            color = HabitTheme.colors.outline,
        )
        NavigationBar(
            containerColor = HabitTheme.colors.surfaceCard,
            tonalElevation = 0.dp,
            modifier = Modifier.height(Sizes.bottomNavHeight),
        ) {
            HomeTab.entries.forEach { tab ->
                val isEnabled = tab in enabled
                NavigationBarItem(
                    selected = tab == selected,
                    // Disabled tabs stay tappable so the explanation can be surfaced;
                    // the colours are what communicate that they are not available.
                    onClick = { onSelect(tab) },
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            modifier = Modifier.size(Sizes.iconNav),
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(tab.labelRes),
                            style = HabitTheme.type.micro,
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = HabitTheme.colors.primary,
                        selectedTextColor = HabitTheme.colors.primary,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = if (isEnabled) {
                            HabitTheme.colors.onSurfaceFaint
                        } else {
                            HabitTheme.colors.onSurfaceFaint.copy(alpha = 0.45f)
                        },
                        unselectedTextColor = if (isEnabled) {
                            HabitTheme.colors.onSurfaceFaint
                        } else {
                            HabitTheme.colors.onSurfaceFaint.copy(alpha = 0.45f)
                        },
                    ),
                )
            }
        }
    }
}
