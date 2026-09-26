@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)

package app.picnic.player.ui.browse

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.auth.UserSession
import app.picnic.player.ui.common.PanelContentInset
import app.picnic.player.ui.common.PanelDividerColor
import app.picnic.player.ui.common.PanelEdgeInset
import app.picnic.player.ui.common.PanelFadeLength
import app.picnic.player.ui.common.PanelFloatingHeight
import app.picnic.player.ui.common.PanelRowMetrics
import app.picnic.player.ui.common.PanelRowSpacing
import app.picnic.player.ui.common.PanelWidth
import app.picnic.player.ui.common.PicnicListRow
import app.picnic.player.ui.common.panelGlass
import app.picnic.player.ui.common.rememberReorderBringIntoView
import app.picnic.player.ui.common.rowPrimaryColor
import app.picnic.player.ui.common.verticalFadingEdges
import app.picnic.player.ui.theme.PicnicColors

private val DockIconSize = 22.dp
private val DockAvatarSize = 28.dp
private val DockSlotSpacing = 18.dp
private val PanelIconSize = 18.dp
private val PanelAvatarSize = 18.dp
private val NavRowMetrics = PanelRowMetrics(innerPadding = 10.dp, verticalPadding = 7.dp)
private const val PanelAnimMs = 220

@Composable
internal fun BrowseFloatingNav(
    chrome: NavChromeState,
    chromeFocus: NavChromeFocus,
    actions: NavChromeActions,
    onOpenActions: (BrowseDest) -> Unit,
    drawerState: DrawerState,
    drawerDim: State<Float>,
    content: @Composable () -> Unit
) {
    var panelFocused by remember { mutableStateOf(false) }

    LaunchedEffect(panelFocused) {
        drawerState.setValue(if (panelFocused) DrawerValue.Open else DrawerValue.Closed)
    }

    val onPrimaryPage = chrome.drawerPage == NavDrawerPage.Primary

    val openProgress by animateFloatAsState(
        targetValue = if (panelFocused) 1f else 0f,
        animationSpec = tween(PanelAnimMs),
        label = "floatingNavOpen"
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelTravel = with(LocalDensity.current) { (PanelWidth.Floating + PanelEdgeInset).toPx() }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(maxWidth - DrawerCollapsedWidth)
                .graphicsLayer { alpha = drawerDim.value }
        ) { content() }

        NavDock(
            session = chrome.session,
            avatarUrl = chrome.avatarUrl,
            selected = chrome.selectedDest,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(DrawerCollapsedWidth)
                .graphicsLayer { alpha = 1f - openProgress }
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(PanelEdgeInset)
                .height(PanelFloatingHeight.coerceAtMost(maxHeight - PanelEdgeInset * 2))
                .graphicsLayer {
                    translationX = (openProgress - 1f) * panelTravel
                    alpha = openProgress
                }
                .width(PanelWidth.Floating)
                .panelGlass()
                .padding(PanelContentInset)
                .onFocusChanged {
                    panelFocused = it.hasFocus
                    actions.onChromeFocusedChange(it.hasFocus)
                }
                .navReorderKeys(chrome.reorderKey, actions.onMoveReorder, actions.onExitReorder)
                .onKeyEvent { event ->
                    val sideways = event.key == Key.DirectionLeft || event.key == Key.DirectionRight
                    if (sideways && event.type == KeyEventType.KeyDown && onPrimaryPage) {
                        runCatching { actions.paneEntryFocus().requestFocus() }
                    }
                    sideways
                }
                .focusProperties {
                    onEnter = {
                        chrome.itemFocusRequesters[chrome.selectedKey]
                            ?.let { runCatching { it.requestFocus() } }
                    }
                }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(PanelRowSpacing)
        ) {
            when (chrome.drawerPage) {
                NavDrawerPage.Primary -> {
                    val fixed = chrome.destinations.filterNot { it.isCustomizable() }
                    val rearrangeable = chrome.destinations.filter { it.isCustomizable() }
                    val libraryScroll = rememberLibraryScroll(panelFocused, chrome.selectedDest)

                    NavPanelRow(
                        label = chrome.session.username,
                        onActivate = actions.onSwapUser,
                        modifier = Modifier.lockedDuringReorder(chrome.reorderKey),
                        leading = { NavAvatar(chrome.session, chrome.avatarUrl, PanelAvatarSize) }
                    )

                    fixed.forEachIndexed { index, dest ->
                        key(dest.key) {
                            FloatingNavDestRow(
                                dest = dest,
                                index = index,
                                selected = dest.key == chrome.selectedKey,
                                reorderKey = chrome.reorderKey,
                                itemFocusRequester = chrome.itemFocusRequesters[dest.key],
                                onSelect = { actions.onSelect(dest) },
                                onOpenActions = { onOpenActions(dest) }
                            )
                        }
                    }

                    NavPanelDivider()

                    PanelScrollColumn(Modifier.weight(1f), libraryScroll) {
                        rearrangeable.forEachIndexed { index, dest ->
                            key(dest.key) {
                                FloatingNavDestRow(
                                    dest = dest,
                                    index = index,
                                    selected = dest.key == chrome.selectedKey,
                                    reorderKey = chrome.reorderKey,
                                    itemFocusRequester = chrome.itemFocusRequesters[dest.key],
                                    onSelect = { actions.onSelect(dest) },
                                    onOpenActions = { onOpenActions(dest) }
                                )
                            }
                        }

                        if (chrome.moreVisible) {
                            NavPanelRow(
                                label = "More",
                                onActivate = actions.onOpenMore,
                                modifier = Modifier.lockedDuringReorder(chrome.reorderKey),
                                focusRequester = chromeFocus.moreItem,
                                leading = { NavPanelIcon(Icons.Outlined.MoreHoriz, it) }
                            )
                        }
                    }

                    NavPanelDivider()

                    NavPanelRow(
                        label = "Settings",
                        onActivate = actions.onSettings,
                        modifier = Modifier.lockedDuringReorder(chrome.reorderKey),
                        leading = { focused ->
                            Box {
                                NavPanelIcon(Icons.Outlined.Settings, focused)
                                if (chrome.settingsBadge) {
                                    UpdateBadgeDot(Modifier.align(Alignment.TopEnd))
                                }
                            }
                        }
                    )
                }
                NavDrawerPage.More -> {
                    NavPanelRow(
                        label = "Back",
                        onActivate = actions.onBackFromMore,
                        modifier = Modifier.lockedDuringReorder(chrome.reorderKey),
                        focusRequester = chromeFocus.moreBack,
                        leading = { NavPanelIcon(Icons.AutoMirrored.Filled.ArrowBack, it) }
                    )
                    NavPanelDivider()

                    PanelScrollColumn(Modifier.weight(1f)) {
                        chrome.destinations.forEachIndexed { index, dest ->
                            key(dest.key) {
                                FloatingNavDestRow(
                                    dest = dest,
                                    index = index,
                                    selected = dest.key == chrome.selectedKey,
                                    reorderKey = chrome.reorderKey,
                                    itemFocusRequester = chrome.itemFocusRequesters[dest.key],
                                    onSelect = { actions.onSelect(dest) },
                                    onOpenActions = { onOpenActions(dest) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavDock(
    session: UserSession,
    avatarUrl: String?,
    selected: BrowseDest,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DockSlotSpacing)
    ) {
        navDockSlots(selected).forEach { slot ->
            when (slot) {
                NavDockSlot.Avatar -> NavAvatar(session, avatarUrl, DockAvatarSize)
                NavDockSlot.Menu -> Icon(
                    imageVector = Icons.Outlined.MoreHoriz,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(DockIconSize)
                )
                is NavDockSlot.Destination -> {
                    val (filled, outlined) = iconsFor(slot.dest)
                    Icon(
                        imageVector = if (slot.selected) filled else outlined,
                        contentDescription = null,
                        tint = if (slot.selected) Color.White else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(DockIconSize)
                    )
                }
            }
        }
    }
}

@Composable
private fun NavPanelDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .height(1.dp)
            .background(PanelDividerColor)
    )
}

@Composable
private fun rememberLibraryScroll(panelFocused: Boolean, selectedDest: BrowseDest): ScrollState {
    val scrollState = rememberScrollState()
    val resetToTop = panelFocused && !selectedDest.isCustomizable()
    LaunchedEffect(resetToTop) {
        if (resetToTop) scrollState.scrollTo(0)
    }
    return scrollState
}

@Composable
private fun PanelScrollColumn(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .verticalFadingEdges(
                topFade = scrollState.canScrollBackward,
                bottomFade = scrollState.canScrollForward,
                length = PanelFadeLength
            )
            .verticalScroll(scrollState)
            .padding(vertical = PanelRowSpacing),
        verticalArrangement = Arrangement.spacedBy(PanelRowSpacing),
        content = content
    )
}

@Composable
private fun FloatingNavDestRow(
    dest: BrowseDest,
    index: Int,
    selected: Boolean,
    reorderKey: String?,
    itemFocusRequester: FocusRequester?,
    onSelect: () -> Unit,
    onOpenActions: () -> Unit
) {
    val (filled, outlined) = iconsFor(dest)
    val inReorder = reorderKey == dest.key
    val active = selected || inReorder
    val bringIntoView = rememberReorderBringIntoView(inReorder, index)

    Box(contentAlignment = Alignment.Center) {
        NavPanelRow(
            label = navLabelFor(dest),
            onActivate = onSelect,
            onLongActivate = if (dest.isCustomizable()) onOpenActions else null,
            focusRequester = itemFocusRequester,
            selected = active,
            modifier = Modifier
                .bringIntoViewRequester(bringIntoView)
                .focusableDuringReorder(reorderKey, dest.key),
            leading = { focused ->
                Icon(
                    imageVector = if (active) filled else outlined,
                    contentDescription = null,
                    tint = navRowIconTint(focused, active),
                    modifier = Modifier.size(PanelIconSize)
                )
            }
        )
        if (inReorder) {
            ReorderArrows(offset = 14.dp)
        }
    }
}

@Composable
private fun NavPanelRow(
    label: String,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onLongActivate: (() -> Unit)? = null,
    selected: Boolean = false,
    leading: @Composable (focused: Boolean) -> Unit
) {
    PicnicListRow(
        modifier = modifier,
        focusRequester = focusRequester,
        metrics = NavRowMetrics,
        onActivate = onActivate,
        onLongActivate = onLongActivate
    ) { focused ->
        leading(focused)
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (focused) rowPrimaryColor(true) else navRowLabelColor(selected),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun NavPanelIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    focused: Boolean
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = navRowIconTint(focused, active = false),
        modifier = Modifier.size(PanelIconSize)
    )
}

private fun navRowLabelColor(selected: Boolean): Color = if (selected) Color.White else Color.White.copy(alpha = 0.6f)

private fun navRowIconTint(focused: Boolean, active: Boolean): Color = when {
    focused -> Color.Black
    active -> PicnicColors.Accent
    else -> Color.White.copy(alpha = 0.6f)
}
