@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package app.picnic.player.ui.common

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowDefaults
import androidx.tv.material3.TabRowScope
import app.picnic.player.ui.theme.PicnicColors

private val PillIndicatorActive = Color.White.copy(alpha = 0.20f)
private val PillIndicatorInactive = Color.White.copy(alpha = 0.10f)

@Composable
fun PillTabRow(
    selectedIndex: Int,
    activeTabFocus: FocusRequester?,
    modifier: Modifier = Modifier,
    tabs: @Composable TabRowScope.() -> Unit
) {
    TabRow(
        selectedTabIndex = selectedIndex,
        containerColor = Color.Transparent,
        indicator = { positions, hasFocus ->
            positions.getOrNull(selectedIndex)?.let { position ->
                TabRowDefaults.PillIndicator(
                    currentTabPosition = position,
                    doesTabRowHaveFocus = hasFocus,
                    activeColor = PillIndicatorActive,
                    inactiveColor = PillIndicatorInactive
                )
            }
        },
        modifier = modifier
            .focusGroup()
            .focusProperties {
                up = FocusRequester.Cancel
                enter = { activeTabFocus ?: FocusRequester.Default }
            },
        tabs = tabs
    )
}

@Composable
fun TabRowScope.PillTab(
    selected: Boolean,
    focusRequester: FocusRequester,
    onSelect: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    Tab(
        selected = selected,
        onFocus = onSelect,
        onClick = onSelect,
        colors = TabDefaults.pillIndicatorTabColors(
            contentColor = PicnicColors.OnDarkMuted,
            selectedContentColor = PicnicColors.OnDark,
            focusedContentColor = PicnicColors.OnDark,
            focusedSelectedContentColor = PicnicColors.OnDark
        ),
        modifier = Modifier.focusRequester(focusRequester)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            content = content
        )
    }
}
