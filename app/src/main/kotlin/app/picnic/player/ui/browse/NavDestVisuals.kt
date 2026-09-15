package app.picnic.player.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.nav.NavLayout
import app.picnic.player.ui.common.rememberIdentityBrush
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.theme.PicnicColors
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import org.jellyfin.sdk.model.api.BaseItemKind

internal sealed interface NavDockSlot {
    data object Avatar : NavDockSlot

    data class Destination(val dest: BrowseDest, val selected: Boolean) : NavDockSlot

    data object Menu : NavDockSlot
}

/**
 * Search owns a fixed slot, so when search is the selected destination the third slot falls back to
 * Home rather than drawing the search icon twice.
 */
internal fun navDockSlots(selected: BrowseDest): List<NavDockSlot> {
    val onSearch = selected == BrowseDest.Search
    return listOf(
        NavDockSlot.Avatar,
        NavDockSlot.Destination(BrowseDest.Search, onSearch),
        NavDockSlot.Destination(if (onSearch) BrowseDest.Home else selected, !onSearch),
        NavDockSlot.Menu
    )
}

internal fun navLabelFor(dest: BrowseDest): String = when (dest) {
    BrowseDest.Search -> "Search"
    BrowseDest.Home -> "Home"
    BrowseDest.Discover -> "Discover"
    BrowseDest.Playlists -> "Playlists"
    is BrowseDest.Library -> dest.title
}

internal fun BrowseDest.isCustomizable(): Boolean = this is BrowseDest.Library || this == BrowseDest.Discover || this == BrowseDest.Playlists

/** Filled first, outlined second. */
internal fun iconsFor(dest: BrowseDest): Pair<ImageVector, ImageVector> = when (dest) {
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

@Composable
internal fun UpdateBadgeDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(PicnicColors.Cyan)
    )
}

@Composable
internal fun NavAvatar(session: UserSession, imageUrl: String?, size: Dp) {
    var avatarFailed by remember(session.userId) { mutableStateOf(false) }
    Box(
        Modifier
            .size(size)
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

/**
 * While a row is held in reorder, up and down move it instead of moving focus, so the row must
 * swallow those keys before the row's own click handling sees them.
 */
internal fun Modifier.navReorderKeys(
    onMoveReorder: (Int) -> Unit,
    onExitReorder: () -> Unit
): Modifier = focusProperties {
    up = FocusRequester.Cancel
    down = FocusRequester.Cancel
}.onPreviewKeyEvent { event ->
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

@Composable
internal fun BoxScope.ReorderArrows(offset: Dp) {
    Icon(
        imageVector = Icons.Filled.KeyboardArrowUp,
        contentDescription = null,
        tint = PicnicColors.Cyan,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = -offset)
            .size(18.dp)
    )
    Icon(
        imageVector = Icons.Filled.KeyboardArrowDown,
        contentDescription = null,
        tint = PicnicColors.Cyan,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .offset(y = offset)
            .size(18.dp)
    )
}

private data class PendingRefocus(val key: String, val capturedLayout: NavLayout)

/**
 * Pinning or unpinning drops the row out of the list it was focused in. Focus must land on its
 * neighbor, but only once the settings store has echoed the new [NavLayout] back, so the target is
 * latched against the layout it was captured under and fires on the first layout that differs.
 */
@Composable
internal fun latchNeighborRefocus(
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
