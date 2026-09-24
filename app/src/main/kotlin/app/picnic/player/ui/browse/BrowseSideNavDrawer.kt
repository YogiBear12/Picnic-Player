@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerState
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.NavigationDrawerItemScale
import androidx.tv.material3.Text
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.nav.NavLayout
import app.picnic.player.ui.common.verticalFadingEdges

private val DrawerHPad = 12.dp

internal val DrawerCollapsedWidth = 56.dp + DrawerHPad * 2

private val DrawerIconSize = 22.dp
private val DrawerAvatarSize = 28.dp

private val DrawerRowHeight = 40.dp

private val DrawerChromeRowHeight = 34.dp
private val DrawerRowSpacing = 4.dp

@Composable
internal fun BrowseSideNavDrawer(
    session: UserSession?,
    avatarUrl: String?,
    destinations: List<BrowseDest>,
    selectedKey: String,
    itemFocusRequesters: Map<String, FocusRequester>,
    paneEntryFocus: () -> FocusRequester,
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
    if (session == null) {
        Box(Modifier.fillMaxSize()) { content() }
        return
    }

    var actionsDest by remember { mutableStateOf<BrowseDest?>(null) }
    val inReorder = reorderKey != null
    val moreItemFocus = remember { FocusRequester() }
    val moreBackFocus = remember { FocusRequester() }
    var previousPage by remember { mutableStateOf(drawerPage) }

    if (inReorder) {
        BackHandler { onExitReorder() }
    }

    LaunchedEffect(drawerPage) {
        val from = previousPage
        previousPage = drawerPage
        if (from == drawerPage) return@LaunchedEffect
        kotlinx.coroutines.yield()
        when (drawerPage) {
            NavDrawerPage.More -> runCatching { moreBackFocus.requestFocus() }
            NavDrawerPage.Primary -> {
                if (moreVisible) {
                    runCatching { moreItemFocus.requestFocus() }
                } else {
                    itemFocusRequesters[selectedKey]?.let { runCatching { it.requestFocus() } }
                }
            }
        }
    }

    LaunchedEffect(reorderKey, destinations) {
        val key = reorderKey ?: return@LaunchedEffect
        repeat(2) { withFrameNanos { } }
        itemFocusRequesters[key]?.let { runCatching { it.requestFocus() } }
    }

    val refocusNeighbor = latchNeighborRefocus(destinations, layout, itemFocusRequesters)

    actionsDest?.let { dest ->
        NavDestActionsDialog(
            dest = dest,
            pinned = layout.isPinned(dest.key),
            onPin = {
                refocusNeighbor(dest)
                onPin(dest)
            },
            onUnpin = {
                refocusNeighbor(dest)
                onUnpin(dest)
            },
            onReorder = { onEnterReorder(dest) },
            onDismiss = { actionsDest = null }
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val browseContentWidth = maxWidth - DrawerCollapsedWidth
        NavigationDrawer(
            drawerState = drawerState,
            drawerContent = { _ ->
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .onFocusChanged { onChromeFocusedChange(it.hasFocus) }
                        .focusProperties {
                            onEnter = {
                                itemFocusRequesters[selectedKey]
                                    ?.let { runCatching { it.requestFocus() } }
                            }
                            exit = { direction ->
                                when (direction) {
                                    FocusDirection.Right -> paneEntryFocus()
                                    else -> FocusRequester.Default
                                }
                            }
                        }
                        .focusGroup()
                        .padding(horizontal = DrawerHPad, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(DrawerRowSpacing)
                ) {
                    when (drawerPage) {
                        NavDrawerPage.Primary -> {
                            PicnicDrawerItem(
                                selected = false,
                                onClick = onSwapUser,
                                label = session.username,
                                leadingContent = { NavAvatar(session, avatarUrl, DrawerAvatarSize) },
                                height = DrawerChromeRowHeight
                            )
                            Spacer(Modifier.height(6.dp))

                            ScrollingRows(Modifier.weight(1f)) {
                                destinations.forEach { dest ->
                                    key(dest.key) {
                                        CustomizableDrawerRow(
                                            dest = dest,
                                            selected = dest.key == selectedKey,
                                            customizable = dest.isCustomizable(),
                                            reorderKey = reorderKey,
                                            itemFocusRequester = itemFocusRequesters[dest.key],
                                            onSelect = { onSelect(dest) },
                                            onOpenActions = { actionsDest = dest },
                                            onMoveReorder = onMoveReorder,
                                            onExitReorder = onExitReorder
                                        )
                                    }
                                }

                                if (moreVisible) {
                                    PicnicDrawerItem(
                                        selected = false,
                                        onClick = onOpenMore,
                                        label = "More",
                                        modifier = Modifier.focusRequester(moreItemFocus),
                                        leadingContent = {
                                            Icon(
                                                imageVector = Icons.Outlined.MoreHoriz,
                                                contentDescription = null,
                                                modifier = Modifier.size(DrawerIconSize)
                                            )
                                        }
                                    )
                                }
                            }

                            Spacer(Modifier.height(DrawerRowSpacing))

                            PicnicDrawerItem(
                                selected = false,
                                onClick = onSettings,
                                label = "Settings",
                                height = DrawerChromeRowHeight,
                                leadingContent = {
                                    Box {
                                        Icon(
                                            imageVector = Icons.Outlined.Settings,
                                            contentDescription = null,
                                            modifier = Modifier.size(DrawerIconSize)
                                        )
                                        if (settingsBadge) {
                                            UpdateBadgeDot(Modifier.align(Alignment.TopEnd))
                                        }
                                    }
                                }
                            )
                        }
                        NavDrawerPage.More -> {
                            PicnicDrawerItem(
                                selected = false,
                                onClick = onBackFromMore,
                                label = "Back",
                                modifier = Modifier.focusRequester(moreBackFocus),
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null,
                                        modifier = Modifier.size(DrawerIconSize)
                                    )
                                }
                            )
                            Spacer(Modifier.height(10.dp))

                            ScrollingRows(Modifier.weight(1f)) {
                                destinations.forEach { dest ->
                                    key(dest.key) {
                                        CustomizableDrawerRow(
                                            dest = dest,
                                            selected = dest.key == selectedKey,
                                            customizable = true,
                                            reorderKey = reorderKey,
                                            itemFocusRequester = itemFocusRequesters[dest.key],
                                            onSelect = { onSelect(dest) },
                                            onOpenActions = { actionsDest = dest },
                                            onMoveReorder = onMoveReorder,
                                            onExitReorder = onExitReorder
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .width(browseContentWidth)
                    .graphicsLayer { alpha = drawerDim.value }
            ) { content() }
        }
    }
}

@Composable
private fun ScrollingRows(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .verticalFadingEdges(
                topFade = scrollState.canScrollBackward,
                bottomFade = scrollState.canScrollForward
            )
            .verticalScroll(scrollState)
            .padding(vertical = DrawerRowSpacing),
        verticalArrangement = Arrangement.spacedBy(DrawerRowSpacing),
        content = content
    )
}

@Composable
private fun androidx.tv.material3.NavigationDrawerScope.CustomizableDrawerRow(
    dest: BrowseDest,
    selected: Boolean,
    customizable: Boolean,
    reorderKey: String?,
    itemFocusRequester: FocusRequester?,
    onSelect: () -> Unit,
    onOpenActions: () -> Unit,
    onMoveReorder: (Int) -> Unit,
    onExitReorder: () -> Unit
) {
    val (filled, outlined) = iconsFor(dest)
    val inReorder = reorderKey == dest.key

    Box(contentAlignment = Alignment.Center) {
        PicnicDrawerItem(
            selected = selected || inReorder,
            onClick = if (inReorder) {
                {}
            } else {
                onSelect
            },
            onLongClick = when {
                inReorder -> null
                customizable -> onOpenActions
                else -> null
            },
            label = navLabelFor(dest),
            modifier = Modifier
                .then(itemFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .then(
                    if (inReorder) {
                        Modifier.navReorderKeys(onMoveReorder, onExitReorder)
                    } else {
                        Modifier
                    }
                ),
            leadingContent = {
                Icon(
                    imageVector = if (selected || inReorder) filled else outlined,
                    contentDescription = null,
                    modifier = Modifier.size(DrawerIconSize)
                )
            }
        )
        if (inReorder) {
            ReorderArrows(offset = 16.dp)
        }
    }
}

@Composable
private fun androidx.tv.material3.NavigationDrawerScope.PicnicDrawerItem(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    leadingContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    height: Dp = DrawerRowHeight
) {
    val rowShape = RoundedCornerShape(10.dp)
    NavigationDrawerItem(
        selected = selected,
        onClick = onClick,
        leadingContent = leadingContent,
        onLongClick = onLongClick,
        shape = NavigationDrawerItemDefaults.shape(
            shape = rowShape,
            focusedShape = rowShape,
            pressedShape = rowShape,
            selectedShape = rowShape,
            disabledShape = rowShape,
            focusedSelectedShape = rowShape,
            focusedDisabledShape = rowShape,
            pressedSelectedShape = rowShape
        ),
        scale = NavigationDrawerItemScale.None,
        colors = NavigationDrawerItemDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White.copy(alpha = 0.6f),
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
            selectedContainerColor = Color.Transparent,
            selectedContentColor = Color.White,
            focusedSelectedContainerColor = Color.White,
            focusedSelectedContentColor = Color.Black
        ),
        modifier = modifier.height(height)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}
