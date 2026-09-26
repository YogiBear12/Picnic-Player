@file:OptIn(ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.tv.material3.DrawerState
import androidx.tv.material3.ExperimentalTvMaterial3Api
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.nav.NavLayout
import app.picnic.player.ui.common.requestFocusWhenAttached
import kotlinx.coroutines.yield

@Immutable
internal data class NavChromeState(
    val session: UserSession,
    val avatarUrl: String?,
    val destinations: List<BrowseDest>,
    val selectedKey: String,
    val selectedDest: BrowseDest,
    val itemFocusRequesters: Map<String, FocusRequester>,
    val drawerPage: NavDrawerPage,
    val moreVisible: Boolean,
    val reorderKey: String?,
    val layout: NavLayout,
    val settingsBadge: Boolean
)

internal class NavChromeActions(
    val paneEntryFocus: () -> FocusRequester,
    val onSelect: (BrowseDest) -> Unit,
    val onOpenMore: () -> Unit,
    val onBackFromMore: () -> Unit,
    val onSwapUser: () -> Unit,
    val onSettings: () -> Unit,
    val onChromeFocusedChange: (Boolean) -> Unit,
    val onPin: (BrowseDest) -> Unit,
    val onUnpin: (BrowseDest) -> Unit,
    val onEnterReorder: (BrowseDest) -> Unit,
    val onExitReorder: () -> Unit,
    val onMoveReorder: (Int) -> Unit
)

internal class NavChromeFocus {
    val moreItem = FocusRequester()
    val moreBack = FocusRequester()
}

@Composable
internal fun NavShell(
    alternate: Boolean,
    chrome: NavChromeState,
    actions: NavChromeActions,
    drawerState: DrawerState,
    drawerDim: State<Float>,
    content: @Composable () -> Unit
) {
    var actionsDest by remember { mutableStateOf<BrowseDest?>(null) }
    val chromeFocus = remember { NavChromeFocus() }
    var previousPage by remember { mutableStateOf(chrome.drawerPage) }

    if (chrome.reorderKey != null) {
        BackHandler { actions.onExitReorder() }
    }

    LaunchedEffect(chrome.drawerPage) {
        val from = previousPage
        previousPage = chrome.drawerPage
        if (from == chrome.drawerPage) return@LaunchedEffect
        yield()
        when (chrome.drawerPage) {
            NavDrawerPage.More -> runCatching { chromeFocus.moreBack.requestFocus() }
            NavDrawerPage.Primary -> {
                if (chrome.moreVisible) {
                    runCatching { chromeFocus.moreItem.requestFocus() }
                } else {
                    chrome.itemFocusRequesters[chrome.selectedKey]?.let { runCatching { it.requestFocus() } }
                }
            }
        }
    }

    LaunchedEffect(chrome.reorderKey, chrome.destinations) {
        val key = chrome.reorderKey ?: return@LaunchedEffect
        repeat(2) { withFrameNanos { } }
        chrome.itemFocusRequesters[key]?.let { runCatching { it.requestFocus() } }
    }

    val refocusNeighbor = latchNeighborRefocus(chrome.destinations, chrome.layout, chrome.itemFocusRequesters)

    actionsDest?.let { dest ->
        NavDestActionsDialog(
            dest = dest,
            pinned = chrome.layout.isPinned(dest.key),
            onPin = {
                refocusNeighbor(dest)
                actions.onPin(dest)
            },
            onUnpin = {
                refocusNeighbor(dest)
                actions.onUnpin(dest)
            },
            onReorder = { actions.onEnterReorder(dest) },
            onDismiss = { actionsDest = null }
        )
    }

    if (alternate) {
        BrowseFloatingNav(
            chrome = chrome,
            chromeFocus = chromeFocus,
            actions = actions,
            onOpenActions = { actionsDest = it },
            drawerState = drawerState,
            drawerDim = drawerDim,
            content = content
        )
    } else {
        BrowseSideNavDrawer(
            chrome = chrome,
            chromeFocus = chromeFocus,
            actions = actions,
            onOpenActions = { actionsDest = it },
            drawerState = drawerState,
            drawerDim = drawerDim,
            content = content
        )
    }
}

private data class PendingRefocus(val key: String, val capturedLayout: NavLayout)

/**
 * Pinning or unpinning drops the row out of the list it was focused in. Focus must land on its
 * neighbor, but only once the settings store has echoed the new [NavLayout] back, so the target is
 * latched against the layout it was captured under and fires on the first layout that differs.
 */
@Composable
private fun latchNeighborRefocus(
    destinations: List<BrowseDest>,
    layout: NavLayout,
    itemFocusRequesters: Map<String, FocusRequester>
): (BrowseDest) -> Unit {
    var pending by remember { mutableStateOf<PendingRefocus?>(null) }
    LaunchedEffect(layout) {
        val target = pending ?: return@LaunchedEffect
        if (layout == target.capturedLayout) return@LaunchedEffect
        pending = null
        itemFocusRequesters[target.key]?.requestFocusWhenAttached(maxFrames = 20)
    }
    return { dest ->
        val index = destinations.indexOfFirst { it.key == dest.key }
        val neighbor = if (index < 0) {
            null
        } else {
            destinations.getOrNull(index - 1) ?: destinations.getOrNull(index + 1)
        }
        pending = neighbor?.let { PendingRefocus(it.key, layout) }
    }
}
