@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.browse

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
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
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
import app.picnic.player.ui.common.rememberReorderBringIntoView
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
    chrome: NavChromeState,
    chromeFocus: NavChromeFocus,
    actions: NavChromeActions,
    onOpenActions: (BrowseDest) -> Unit,
    drawerState: DrawerState,
    drawerDim: State<Float>,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val browseContentWidth = maxWidth - DrawerCollapsedWidth
        NavigationDrawer(
            drawerState = drawerState,
            drawerContent = { _ ->
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .onFocusChanged { actions.onChromeFocusedChange(it.hasFocus) }
                        .navReorderKeys(chrome.reorderKey, actions.onMoveReorder, actions.onExitReorder)
                        .focusProperties {
                            onEnter = {
                                chrome.itemFocusRequesters[chrome.selectedKey]
                                    ?.let { runCatching { it.requestFocus() } }
                            }
                            exit = { direction ->
                                when (direction) {
                                    FocusDirection.Right -> actions.paneEntryFocus()
                                    else -> FocusRequester.Default
                                }
                            }
                        }
                        .focusGroup()
                        .padding(horizontal = DrawerHPad, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(DrawerRowSpacing)
                ) {
                    when (chrome.drawerPage) {
                        NavDrawerPage.Primary -> {
                            PicnicDrawerItem(
                                selected = false,
                                onClick = actions.onSwapUser,
                                label = chrome.session.username,
                                modifier = Modifier.lockedDuringReorder(chrome.reorderKey),
                                leadingContent = { NavAvatar(chrome.session, chrome.avatarUrl, DrawerAvatarSize) },
                                height = DrawerChromeRowHeight
                            )
                            Spacer(Modifier.height(6.dp))

                            ScrollingRows(Modifier.weight(1f)) {
                                chrome.destinations.forEachIndexed { index, dest ->
                                    key(dest.key) {
                                        CustomizableDrawerRow(
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
                                    PicnicDrawerItem(
                                        selected = false,
                                        onClick = actions.onOpenMore,
                                        label = "More",
                                        modifier = Modifier
                                            .lockedDuringReorder(chrome.reorderKey)
                                            .focusRequester(chromeFocus.moreItem),
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
                                onClick = actions.onSettings,
                                label = "Settings",
                                modifier = Modifier.lockedDuringReorder(chrome.reorderKey),
                                height = DrawerChromeRowHeight,
                                leadingContent = {
                                    Box {
                                        Icon(
                                            imageVector = Icons.Outlined.Settings,
                                            contentDescription = null,
                                            modifier = Modifier.size(DrawerIconSize)
                                        )
                                        if (chrome.settingsBadge) {
                                            UpdateBadgeDot(Modifier.align(Alignment.TopEnd))
                                        }
                                    }
                                }
                            )
                        }
                        NavDrawerPage.More -> {
                            PicnicDrawerItem(
                                selected = false,
                                onClick = actions.onBackFromMore,
                                label = "Back",
                                modifier = Modifier
                                    .lockedDuringReorder(chrome.reorderKey)
                                    .focusRequester(chromeFocus.moreBack),
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
                                chrome.destinations.forEachIndexed { index, dest ->
                                    key(dest.key) {
                                        CustomizableDrawerRow(
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
        PicnicDrawerItem(
            selected = active,
            onClick = onSelect,
            onLongClick = if (dest.isCustomizable()) onOpenActions else null,
            label = navLabelFor(dest),
            modifier = Modifier
                .bringIntoViewRequester(bringIntoView)
                .then(itemFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .focusableDuringReorder(reorderKey, dest.key),
            leadingContent = {
                Icon(
                    imageVector = if (active) filled else outlined,
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
