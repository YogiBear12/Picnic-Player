@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerState
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.Text
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.nav.NavLayout
import app.picnic.player.ui.common.rememberIdentityBrush
import app.picnic.player.ui.theme.PicnicColors
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import org.jellyfin.sdk.model.api.BaseItemKind

private val DrawerHPad = 12.dp

/** Closed sheet footprint: collapsed item width + container padding. Content starts here. */
internal val DrawerCollapsedWidth = 56.dp + DrawerHPad * 2 // 80dp

private val DrawerIconSize = 22.dp
private val DrawerAvatarSize = 28.dp

/**
 * Vertical TV navigation drawer on tv-material [NavigationDrawer]. Supports
 * pin/unpin/reorder of customisable destinations and a More page for unpinned
 * items (#88). Long-press a customisable row ([NavigationDrawerItem]'s
 * [onLongClick]) to open Actions — drawer width stays unchanged.
 */
@Composable
internal fun BrowseSideNavDrawer(
    session: UserSession?,
    destinations: List<BrowseDest>,
    selectedKey: String,
    itemFocusRequesters: Map<String, FocusRequester>,
    contentFocusOnRight: () -> FocusRequester,
    drawerState: DrawerState,
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

    // Page swaps dispose the focused row (More / Back). Re-seed inside the drawer on the
    // next frame so NavigationDrawer never sees an empty focus owner and closes itself.
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

    // Keep focus glued to the row being reordered across list moves (same idea as
    // EditablePickerRow after ←/→ swap).
    LaunchedEffect(reorderKey, destinations) {
        val key = reorderKey ?: return@LaunchedEffect
        repeat(2) { withFrameNanos { } }
        itemFocusRequesters[key]?.let { runCatching { it.requestFocus() } }
    }

    actionsDest?.let { dest ->
        NavDestActionsDialog(
            dest = dest,
            pinned = layout.isPinned(dest.key),
            onPin = { onPin(dest) },
            onUnpin = { onUnpin(dest) },
            onReorder = { onEnterReorder(dest) },
            onDismiss = { actionsDest = null }
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Fixed against the SCREEN, not the drawer row: expanding the sheet must push the
        // content off screen, never squeeze it into the remaining width.
        val browseContentWidth = maxWidth - DrawerCollapsedWidth
        NavigationDrawer(
            drawerState = drawerState,
            drawerContent = { _ ->
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .onFocusChanged { onChromeFocusedChange(it.hasFocus) }
                        // Redirect binds to the GROUP (properties before focusGroup): entering
                        // the drawer resolves to the ACTIVE destination during the focus search
                        // itself, never a spatial winner. Right back into the pane returns the
                        // saved content requester — Settings sits at the bottom of the sheet,
                        // so a spatial Right would otherwise land on a lower Home row (#90).
                        .focusProperties {
                            onEnter = {
                                itemFocusRequesters[selectedKey]
                                    ?.let { runCatching { it.requestFocus() } }
                            }
                            exit = { direction ->
                                when (direction) {
                                    FocusDirection.Right -> contentFocusOnRight()
                                    else -> FocusRequester.Default
                                }
                            }
                        }
                        .focusGroup()
                        .padding(horizontal = DrawerHPad, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when (drawerPage) {
                        NavDrawerPage.Primary -> {
                            PicnicDrawerItem(
                                selected = false,
                                onClick = onSwapUser,
                                label = session.username,
                                leadingContent = { DrawerAvatar(session) }
                            )
                            Spacer(Modifier.height(10.dp))

                            destinations.forEach { dest ->
                                key(dest.key) {
                                    CustomisableDrawerRow(
                                        dest = dest,
                                        selected = dest.key == selectedKey,
                                        customisable = dest.isCustomisable(),
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

                            Spacer(Modifier.weight(1f))

                            PicnicDrawerItem(
                                selected = false,
                                onClick = onSettings,
                                label = "Settings",
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

                            destinations.forEach { dest ->
                                key(dest.key) {
                                    CustomisableDrawerRow(
                                        dest = dest,
                                        selected = dest.key == selectedKey,
                                        customisable = true,
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
        ) {
            // Anchored at Start with unbounded measurement: the block keeps its width when the
            // Row hands it less space, so the drawer pushes it right and off screen (no reflow).
            Box(
                Modifier
                    .fillMaxHeight()
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .width(browseContentWidth)
            ) { content() }
        }
    }
}

/**
 * Same footprint as a plain [PicnicDrawerItem] when idle. In reorder mode: Plex-style
 * accent chevrons are drawn *over* the row (no extra layout height) so neighbours do
 * not shift. Up/Down swap; Select confirms via KeyUp consumption.
 */
@Composable
private fun androidx.tv.material3.NavigationDrawerScope.CustomisableDrawerRow(
    dest: BrowseDest,
    selected: Boolean,
    customisable: Boolean,
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
                customisable -> onOpenActions
                else -> null
            },
            label = drawerLabelFor(dest),
            modifier = Modifier
                .then(itemFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .then(
                    if (inReorder) {
                        Modifier
                            .focusProperties {
                                up = FocusRequester.Cancel
                                down = FocusRequester.Cancel
                            }
                            .onPreviewKeyEvent { event ->
                                when (event.key) {
                                    Key.DirectionUp -> {
                                        if (event.type == KeyEventType.KeyDown) onMoveReorder(-1)
                                        true
                                    }
                                    Key.DirectionDown -> {
                                        if (event.type == KeyEventType.KeyDown) onMoveReorder(1)
                                        true
                                    }
                                    Key.DirectionCenter, Key.Enter, Key.Back -> {
                                        if (event.type == KeyEventType.KeyUp) onExitReorder()
                                        true
                                    }
                                    else -> false
                                }
                            }
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
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = PicnicColors.Cyan,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-16).dp)
                    .size(18.dp)
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = PicnicColors.Cyan,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 16.dp)
                    .size(18.dp)
            )
        }
    }
}

/** One drawer entry: tv-material item with the Picnic focus palette (white pill on focus). */
@Composable
private fun androidx.tv.material3.NavigationDrawerScope.PicnicDrawerItem(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    leadingContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    NavigationDrawerItem(
        selected = selected,
        onClick = onClick,
        leadingContent = leadingContent,
        onLongClick = onLongClick,
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
        modifier = modifier
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/** Small cyan dot marking "update available" on chrome items (#111). */
@Composable
internal fun UpdateBadgeDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(PicnicColors.Cyan)
    )
}

/** Passive circular avatar (the enclosing drawer item owns focus + click). */
@Composable
private fun DrawerAvatar(session: UserSession) {
    val imageUrl = "${session.server.baseUrl.trimEnd('/')}/Users/${session.userId}/Images/Primary?fillWidth=80&quality=90"
    // Same layering as the Who's watching? picker: gradient underlay always, initial
    // only when the load fails — so transparent PNG avatars do not show a letter (#131).
    var avatarFailed by remember(session.userId) { mutableStateOf(false) }
    Box(
        Modifier
            .size(DrawerAvatarSize)
            .clip(CircleShape)
            .background(rememberIdentityBrush(session.username.ifBlank { "?" })),
        contentAlignment = Alignment.Center
    ) {
        if (avatarFailed) {
            Text(
                session.username.firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium
            )
        }
        AsyncImage(
            model = imageUrl,
            contentDescription = session.username,
            contentScale = ContentScale.Crop,
            onState = { avatarFailed = it is AsyncImagePainter.State.Error },
            modifier = Modifier.fillMaxSize().clip(CircleShape)
        )
    }
}

internal fun drawerLabelFor(dest: BrowseDest): String = when (dest) {
    BrowseDest.Search -> "Search"
    BrowseDest.Home -> "Home"
    BrowseDest.Discover -> "Discover"
    is BrowseDest.Library -> dest.title
}

private fun BrowseDest.isCustomisable(): Boolean = this is BrowseDest.Library || this == BrowseDest.Discover

/** Filled + outlined icon pair for a destination. */
private fun iconsFor(dest: BrowseDest): Pair<ImageVector, ImageVector> = when (dest) {
    BrowseDest.Search -> Icons.Filled.Search to Icons.Outlined.Search
    BrowseDest.Home -> Icons.Filled.Home to Icons.Outlined.Home
    BrowseDest.Discover -> Icons.Filled.Explore to Icons.Outlined.Explore
    is BrowseDest.Library -> when {
        dest.kinds == listOf(BaseItemKind.MOVIE) -> Icons.Filled.Movie to Icons.Outlined.Movie
        dest.kinds == listOf(BaseItemKind.SERIES) -> Icons.Filled.Tv to Icons.Outlined.Tv
        else -> Icons.Filled.VideoLibrary to Icons.Outlined.VideoLibrary
    }
}
