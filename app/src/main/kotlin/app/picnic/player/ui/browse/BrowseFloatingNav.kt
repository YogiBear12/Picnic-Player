@file:OptIn(ExperimentalComposeUiApi::class)

package app.picnic.player.ui.browse

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.nav.NavLayout
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
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.common.rowPrimaryColor
import app.picnic.player.ui.common.verticalFadingEdges
import app.picnic.player.ui.theme.PicnicColors
import kotlinx.coroutines.yield

private val DockIconSize = 22.dp
private val DockAvatarSize = 28.dp
private val DockSlotSpacing = 18.dp
private val PanelIconSize = 18.dp
private val PanelAvatarSize = 18.dp
private val NavRowMetrics = PanelRowMetrics(innerPadding = 10.dp, verticalPadding = 7.dp)
private const val PanelAnimMs = 220

@Immutable
internal data class NavChromeState(
    val session: UserSession,
    val avatarUrl: String?,
    val destinations: List<BrowseDest>,
    val selectedKey: String,
    val selectedDest: BrowseDest,
    val itemFocusRequesters: Map<String, FocusRequester>,
    val panelPage: NavPanelPage,
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
    val settings = FocusRequester()
}

@Composable
internal fun BrowseFloatingNav(
    chrome: NavChromeState,
    actions: NavChromeActions,
    focusSettingsOnEntry: Boolean,
    navDim: State<Float>,
    content: @Composable () -> Unit
) {
    var actionsDest by remember { mutableStateOf<BrowseDest?>(null) }
    val chromeFocus = remember { NavChromeFocus() }
    LaunchedEffect(focusSettingsOnEntry) {
        if (focusSettingsOnEntry) chromeFocus.settings.requestFocusWhenAttached()
    }
    var previousPage by remember { mutableStateOf(chrome.panelPage) }

    if (chrome.reorderKey != null) {
        BackHandler { actions.onExitReorder() }
    }

    LaunchedEffect(chrome.panelPage) {
        val from = previousPage
        previousPage = chrome.panelPage
        if (from == chrome.panelPage) return@LaunchedEffect
        yield()
        when (chrome.panelPage) {
            NavPanelPage.More -> runCatching { chromeFocus.moreBack.requestFocus() }
            NavPanelPage.Primary -> {
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

    FloatingNavBody(
        chrome = chrome,
        chromeFocus = chromeFocus,
        actions = actions,
        onOpenActions = { actionsDest = it },
        navDim = navDim,
        content = content
    )
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

@Composable
private fun FloatingNavBody(
    chrome: NavChromeState,
    chromeFocus: NavChromeFocus,
    actions: NavChromeActions,
    onOpenActions: (BrowseDest) -> Unit,
    navDim: State<Float>,
    content: @Composable () -> Unit
) {
    var panelFocused by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { actions.onChromeFocusedChange(false) }
    }

    val onPrimaryPage = chrome.panelPage == NavPanelPage.Primary
    val libraryScroll = rememberLibraryScroll(panelFocused, chrome.selectedDest)

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
                .width(maxWidth - NavGutterWidth)
                .graphicsLayer { alpha = navDim.value }
        ) { content() }

        NavDock(
            session = chrome.session,
            avatarUrl = chrome.avatarUrl,
            selected = chrome.selectedDest,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(NavGutterWidth)
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
                    onEnter = { onEnterFocusActiveDestination(chrome.itemFocusRequesters[chrome.selectedKey]) }
                }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(PanelRowSpacing)
        ) {
            when (chrome.panelPage) {
                NavPanelPage.Primary -> {
                    val fixed = chrome.destinations.filterNot { it.isCustomizable() }
                    val rearrangeable = chrome.destinations.filter { it.isCustomizable() }

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
                        focusRequester = chromeFocus.settings,
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
                NavPanelPage.More -> {
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
