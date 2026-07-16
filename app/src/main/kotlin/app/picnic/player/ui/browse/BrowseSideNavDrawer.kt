@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.browse

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.picnic.player.ui.common.rememberIdentityBrush
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import org.jellyfin.sdk.model.api.BaseItemKind

private val DrawerHPad = 12.dp

/** Closed sheet footprint: collapsed item width + container padding. Content starts here. */
internal val DrawerCollapsedWidth = 56.dp + DrawerHPad * 2 // 80dp

private val DrawerIconSize = 22.dp
private val DrawerAvatarSize = 28.dp

/**
 * Vertical TV navigation drawer on tv-material [NavigationDrawer]. The
 * component owns open/close — it opens while any drawer item has focus and closes when
 * focus leaves. Standard (non-modal) variant per the TV component guidance: expanding
 * pushes the content right and off screen; the content keeps its size (no reflow).
 *
 * @param contentFocusOnRight Resolved at exit time for D-pad Right — must be the
 *  destination pane's saved card/row requester (same contract as [MediaGridPane] rail
 *  exits). Returning [FocusRequester.Default] keeps spatial search.
 * @param drawerState Hoisted above the NavHost so the open/closed value survives back-nav:
 *  returning to the shell restores the last state (resting Closed) instead of the component
 *  reconstructing a default state and replaying its open→close animation (#106).
 */
@Composable
internal fun BrowseSideNavDrawer(
    session: UserSession?,
    destinations: List<BrowseDest>,
    selectedKey: String,
    itemFocusRequesters: Map<String, FocusRequester>,
    contentFocusOnRight: () -> FocusRequester,
    drawerState: DrawerState,
    onSelect: (BrowseDest) -> Unit,
    onSwapUser: () -> Unit,
    onSettings: () -> Unit,
    onChromeFocusedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    if (session == null) {
        Box(Modifier.fillMaxSize()) { content() }
        return
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
                    PicnicDrawerItem(
                        selected = false,
                        onClick = onSwapUser,
                        label = session.username,
                        leadingContent = { DrawerAvatar(session) }
                    )
                    Spacer(Modifier.height(10.dp))

                    destinations.forEach { dest ->
                        val (filled, outlined) = iconsFor(dest)
                        val selected = dest.key == selectedKey
                        PicnicDrawerItem(
                            selected = selected,
                            onClick = { onSelect(dest) },
                            label = labelFor(dest),
                            modifier = itemFocusRequesters[dest.key]
                                ?.let { Modifier.focusRequester(it) } ?: Modifier,
                            leadingContent = {
                                Icon(
                                    imageVector = if (selected) filled else outlined,
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
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(DrawerIconSize)
                            )
                        }
                    )
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

/** One drawer entry: tv-material item with the Picnic focus palette (white pill on focus). */
@Composable
private fun androidx.tv.material3.NavigationDrawerScope.PicnicDrawerItem(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    leadingContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationDrawerItem(
        selected = selected,
        onClick = onClick,
        leadingContent = leadingContent,
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

private fun labelFor(dest: BrowseDest): String = when (dest) {
    BrowseDest.Search -> "Search"
    BrowseDest.Home -> "Home"
    BrowseDest.Discover -> "Discover"
    is BrowseDest.Library -> dest.title
}

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
