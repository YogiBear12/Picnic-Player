@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)

package app.picnic.player.ui.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.picnic.player.ui.theme.PicnicColors

// "#" first — numbers sort before letters.
internal val GridAlphabetLetters = listOf("#") + ('A'..'Z').map { it.toString() }

/**
 * Vertical jump rail: a sort/filter button above the A–Z letters.
 *
 * Entry is driven entirely by the GRID's Right-exit (see MediaGridPane), which imperatively
 * focuses the current letter's permanently-attached requester — enter/onEnter redirects and
 * migrating requesters were both verified broken on-device (redirects never fire; a moving
 * requester loses to the list's previously-focused-child restoration).
 *
 * A plain [Column] — NOT a LazyColumn — on purpose: a lazy list restores its previously
 * focused child when focus re-enters, which overrode the grid-exit's returned requester and
 * left entry on the stale letter. A non-lazy column has no such restoration, so the
 * grid-exit target wins. The 27 letters + filter are tiny (15dp) and fit the full height.
 * With no group boundary, the top/bottom/right edges are caged on the edge items themselves.
 */
@Composable
internal fun GridAlphabetRail(
    activeLetter: String?,
    enabled: Boolean,
    onLetter: (String) -> Unit,
    onOpenFilter: () -> Unit,
    filterActive: Boolean,
    /** Owned by the pane: the filter panel returns focus here when it closes. */
    filterFocusRequester: FocusRequester,
    /** Reports the filter button's focus so the pane can stop re-requesting after a popup close. */
    onFilterFocusChanged: (Boolean) -> Unit,
    /** Owned by the pane: one PERMANENT requester per letter cell (grid Right-exit targets). */
    letterFocusRequesters: List<FocusRequester>,
    modifier: Modifier = Modifier
) {
    val last = GridAlphabetLetters.lastIndex
    val activeIndex = GridAlphabetLetters.indexOf(activeLetter).coerceIn(0, last)

    Column(
        modifier = modifier.width(28.dp).fillMaxHeight().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        FilterRailButton(
            active = filterActive,
            focusRequester = filterFocusRequester,
            onFocusChanged = onFilterFocusChanged,
            onClick = onOpenFilter
        )
        GridAlphabetLetters.forEachIndexed { index, letter ->
            AlphabetLetter(
                letter = letter,
                active = index == activeIndex,
                enabled = enabled,
                isLast = index == last,
                focusRequester = letterFocusRequesters[index],
                onClick = { onLetter(letter) }
            )
        }
    }
}

/** Sort/filter entry point atop the alphabet rail; the dot marks an active filter/sort. */
@Composable
private fun FilterRailButton(
    active: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Box(Modifier.padding(bottom = 6.dp)) {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = PicnicColors.Accent,
                contentColor = Color.White.copy(alpha = if (active) 0.95f else 0.6f),
                focusedContentColor = Color.Black
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.25f),
            modifier = Modifier
                .size(22.dp)
                .onFocusChanged { onFocusChanged(it.isFocused) }
                .focusRequester(focusRequester)
                // No group boundary above — cage the rail's top edge on the item itself.
                .focusProperties {
                    right = FocusRequester.Cancel
                    up = FocusRequester.Cancel
                }
        ) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = "Sort and filter",
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        if (active) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(PicnicColors.Cyan)
            )
        }
    }
}

@Composable
private fun AlphabetLetter(
    letter: String,
    active: Boolean,
    enabled: Boolean,
    isLast: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit
) {
    if (!enabled) {
        Box(Modifier.size(width = 24.dp, height = 15.dp), Alignment.Center) {
            Text(
                letter,
                fontSize = 9.sp,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.3f)
            )
        }
        return
    }
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = PicnicColors.Accent,
            contentColor = if (active) Color.White else Color.White.copy(alpha = 0.7f),
            focusedContentColor = Color.Black
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.25f),
        modifier = Modifier
            .size(width = 24.dp, height = 15.dp)
            .focusRequester(focusRequester)
            // Cage the right edge everywhere and the bottom edge on the final letter
            // (no group boundary to do it for us).
            .focusProperties {
                right = FocusRequester.Cancel
                if (isLast) down = FocusRequester.Cancel
            }
    ) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(
                letter,
                fontSize = 9.sp,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
