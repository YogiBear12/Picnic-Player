@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.browse

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
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
import app.picnic.player.ui.common.rememberIdentityBrush
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.common.verticalFadingEdges
import app.picnic.player.ui.theme.PicnicColors
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import org.jellyfin.sdk.model.api.BaseItemKind

private val DrawerHPad = 12.dp

/** Closed sheet footprint: collapsed item width + container padding. Content starts here. */
internal val DrawerCollapsedWidth = 56.dp + DrawerHPad * 2 // 80dp

private val DrawerIconSize = 22.dp
private val DrawerAvatarSize = 28.dp

/** Compact rows: overrides tv-material's 56dp one-line item height so more rows fit on screen. */
private val DrawerRowHeight = 40.dp

/** Header (profile) + footer (Settings) chrome rows sit a little shorter than the nav rows. */
private val DrawerChromeRowHeight = 34.dp
private val DrawerRowSpacing = 4.dp

/**
 * Vertical TV navigation drawer on tv-material [NavigationDrawer]. Supports
 * pin/unpin/reorder of customisable destinations and a More page for unpinned
 * items (#88). Long-press a customisable row ([NavigationDrawerItem]'s
 * [onLongClick]) to open Actions — drawer width stays unchanged.
 */
@Composable
internal fun BrowseSideNavDrawer(
    session: UserSession?,
    avatarUrl: String?,
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

    // Moving focus before the row leaves would show two steps, so this waits for the layout the
    // pin produced and lands on the neighbour in the same frame the row goes.
    var refocusAfter by remember { mutableStateOf<Pair<String, NavLayout>?>(null) }
    LaunchedEffect(layout) {
        val (key, before) = refocusAfter ?: return@LaunchedEffect
        if (layout == before) return@LaunchedEffect
        refocusAfter = null
        itemFocusRequesters[key]?.requestFocusWhenAttached(maxFrames = 20)
    }

    fun neighbourOf(dest: BrowseDest): Pair<String, NavLayout>? {
        val index = destinations.indexOfFirst { it.key == dest.key }
        if (index < 0) return null
        val neighbour = destinations.getOrNull(index - 1) ?: destinations.getOrNull(index + 1)
        return neighbour?.let { it.key to layout }
    }

    actionsDest?.let { dest ->
        NavDestActionsDialog(
            dest = dest,
            pinned = layout.isPinned(dest.key),
            onPin = {
                refocusAfter = neighbourOf(dest)
                onPin(dest)
            },
            onUnpin = {
                refocusAfter = neighbourOf(dest)
                onUnpin(dest)
            },
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
                    verticalArrangement = Arrangement.spacedBy(DrawerRowSpacing)
                ) {
                    when (drawerPage) {
                        NavDrawerPage.Primary -> {
                            // Avatar pinned at the top; destinations scroll between it and the
                            // Settings row pinned at the bottom, so both chrome rows stay visible.
                            PicnicDrawerItem(
                                selected = false,
                                onClick = onSwapUser,
                                label = session.username,
                                leadingContent = { DrawerAvatar(session, avatarUrl) },
                                height = DrawerChromeRowHeight
                            )
                            Spacer(Modifier.height(6.dp))

                            ScrollingRows(Modifier.weight(1f)) {
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
 * Vertically scrolling list of drawer rows with faded top/bottom edges. Native [verticalScroll]:
 * focus-driven scrolling (bringIntoView) is automatic for the focusable rows, so D-pad moving onto
 * an off-screen row scrolls it into view. The fade signals more rows above/below.
 */
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
            // Content padding (inside the scroll): keeps an edge row's highlight off the viewport
            // clip when bringIntoView aligns it flush, so its rounded corners stay whole and it
            // never touches the pinned header/footer.
            .padding(vertical = DrawerRowSpacing),
        verticalArrangement = Arrangement.spacedBy(DrawerRowSpacing),
        content = content
    )
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
    onLongClick: (() -> Unit)? = null,
    height: Dp = DrawerRowHeight
) {
    // Rounded rectangle instead of tv-material's default pill; applied to every state so the
    // focus highlight, selection and press all share the same corner radius.
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
        // No focus scale: the flat rounded-rect highlight is the focus cue, and an overscaled
        // pill would be clipped by the scroll viewport / pinned header + footer edges.
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
        // Outer height constraint wins over the item's internal 56dp one-line height.
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
private fun DrawerAvatar(session: UserSession, imageUrl: String?) {
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
    BrowseDest.Playlists -> "Playlists"
    is BrowseDest.Library -> dest.title
}

private fun BrowseDest.isCustomisable(): Boolean = this is BrowseDest.Library || this == BrowseDest.Discover || this == BrowseDest.Playlists

/** Filled + outlined icon pair for a destination. */
private fun iconsFor(dest: BrowseDest): Pair<ImageVector, ImageVector> = when (dest) {
    BrowseDest.Search -> Icons.Filled.Search to Icons.Outlined.Search
    BrowseDest.Home -> Icons.Filled.Home to Icons.Outlined.Home
    BrowseDest.Discover -> Icons.Filled.Explore to Icons.Outlined.Explore
    BrowseDest.Playlists -> Icons.AutoMirrored.Filled.PlaylistPlay to Icons.AutoMirrored.Outlined.PlaylistPlay
    is BrowseDest.Library -> when {
        dest.kinds == listOf(BaseItemKind.MOVIE) -> Icons.Filled.Movie to Icons.Outlined.Movie
        dest.kinds == listOf(BaseItemKind.SERIES) -> Icons.Filled.Tv to Icons.Outlined.Tv
        else -> Icons.Filled.VideoLibrary to Icons.Outlined.VideoLibrary
    }
}
