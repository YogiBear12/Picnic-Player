@file:OptIn(ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.focus.FocusRequester
import androidx.tv.material3.DrawerState
import androidx.tv.material3.ExperimentalTvMaterial3Api
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.nav.NavLayout

@Composable
internal fun NavShell(
    alternate: Boolean,
    session: UserSession?,
    avatarUrl: String?,
    destinations: List<BrowseDest>,
    selectedKey: String,
    selectedDest: BrowseDest,
    itemFocusRequesters: Map<String, FocusRequester>,
    contentFocusOnRight: () -> FocusRequester,
    drawerState: DrawerState,
    drawerDim: State<Float>,
    drawerPage: NavDrawerPage,
    moreVisible: Boolean,
    layout: NavLayout,
    reorderKey: String?,
    onSelect: (BrowseDest) -> Unit,
    onOpenMore: () -> Unit,
    onBackFromMore: () -> Unit,
    onSwapUser: () -> Unit,
    onSettings: () -> Unit,
    onChromeFocusedChange: (Boolean) -> Unit,
    onPin: (BrowseDest) -> Unit,
    onUnpin: (BrowseDest) -> Unit,
    onEnterReorder: (BrowseDest) -> Unit,
    onExitReorder: () -> Unit,
    onMoveReorder: (Int) -> Unit,
    settingsBadge: Boolean = false,
    content: @Composable () -> Unit
) {
    if (alternate) {
        BrowseFloatingNav(
            session = session,
            avatarUrl = avatarUrl,
            destinations = destinations,
            selectedKey = selectedKey,
            selectedDest = selectedDest,
            itemFocusRequesters = itemFocusRequesters,
            contentFocusOnRight = contentFocusOnRight,
            drawerState = drawerState,
            drawerDim = drawerDim,
            drawerPage = drawerPage,
            moreVisible = moreVisible,
            layout = layout,
            reorderKey = reorderKey,
            onSelect = onSelect,
            onOpenMore = onOpenMore,
            onBackFromMore = onBackFromMore,
            onSwapUser = onSwapUser,
            onSettings = onSettings,
            onChromeFocusedChange = onChromeFocusedChange,
            onPin = onPin,
            onUnpin = onUnpin,
            onEnterReorder = onEnterReorder,
            onExitReorder = onExitReorder,
            onMoveReorder = onMoveReorder,
            settingsBadge = settingsBadge,
            content = content
        )
    } else {
        BrowseSideNavDrawer(
            session = session,
            avatarUrl = avatarUrl,
            destinations = destinations,
            selectedKey = selectedKey,
            itemFocusRequesters = itemFocusRequesters,
            contentFocusOnRight = contentFocusOnRight,
            drawerState = drawerState,
            drawerDim = drawerDim,
            drawerPage = drawerPage,
            moreVisible = moreVisible,
            layout = layout,
            reorderKey = reorderKey,
            onSelect = onSelect,
            onOpenMore = onOpenMore,
            onBackFromMore = onBackFromMore,
            onSwapUser = onSwapUser,
            onSettings = onSettings,
            onChromeFocusedChange = onChromeFocusedChange,
            onPin = onPin,
            onUnpin = onUnpin,
            onEnterReorder = onEnterReorder,
            onExitReorder = onExitReorder,
            onMoveReorder = onMoveReorder,
            settingsBadge = settingsBadge,
            content = content
        )
    }
}
