package app.picnic.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.theme.PicnicColors
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

private val ContextMenuGlassFill = PicnicColors.GlassFill

/** An extra, caller-supplied context-menu row (e.g. Reorder / Remove from playlist). */
data class ContextMenuExtra(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GlobalContextMenuDialog(
    item: BaseItemDto,
    onDismiss: () -> Unit,
    onPlay: (itemId: String, startTicks: Long?) -> Unit,
    onMarkWatched: (Boolean) -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
    onGoToSeries: ((String) -> Unit)?,
    onAddToPlaylist: (() -> Unit)? = null,
    extraActions: List<ContextMenuExtra> = emptyList()
) {
    val played = item.userData?.played ?: false
    val isFavorite = item.userData?.isFavorite ?: false
    val resumeTicks = item.userData?.playbackPositionTicks?.takeIf { it > 0L }

    // Disable items for ~1s after opening from long-press so the held key can't fire the first item.
    var showSynopsis by remember { mutableStateOf(false) }
    var showMediaInfo by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1000L)
        enabled = true
    }

    data class MenuItem(val label: String, val icon: ImageVector, val onClick: () -> Unit)

    val items = buildList {
        if (resumeTicks != null) {
            add(
                MenuItem("Resume", Icons.Default.PlayArrow) {
                    onPlay(item.id.toString(), resumeTicks)
                    onDismiss()
                }
            )
            add(
                MenuItem("Restart", Icons.Default.Replay) {
                    onPlay(item.id.toString(), 1L)
                    onDismiss()
                }
            )
        } else {
            add(
                MenuItem("Play", Icons.Default.PlayArrow) {
                    onPlay(item.id.toString(), null)
                    onDismiss()
                }
            )
        }

        if (item.overview?.isNotBlank() == true) {
            add(MenuItem("View synopsis", Icons.Default.Article) { showSynopsis = true })
        }

        add(
            MenuItem(
                if (played) "Mark unwatched" else "Mark watched",
                if (played) Icons.Default.VisibilityOff else Icons.Default.Visibility
            ) {
                onMarkWatched(!played)
                onDismiss()
            }
        )

        add(
            MenuItem(
                if (isFavorite) "Remove favorite" else "Add to favorites",
                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder
            ) {
                onToggleFavorite(!isFavorite)
                onDismiss()
            }
        )

        if (item.type == BaseItemKind.EPISODE && item.seriesId != null && onGoToSeries != null) {
            add(
                MenuItem("Go to Series", Icons.Default.ArrowForward) {
                    onGoToSeries(item.seriesId.toString())
                    onDismiss()
                }
            )
        }

        if (item.type == BaseItemKind.MOVIE || item.type == BaseItemKind.EPISODE) {
            add(MenuItem("View media info", Icons.Default.Info) { showMediaInfo = true })
        }

        if (onAddToPlaylist != null) {
            add(
                MenuItem("Add to playlist", Icons.AutoMirrored.Filled.PlaylistAdd) {
                    onAddToPlaylist()
                    onDismiss()
                }
            )
        }

        extraActions.forEach { extra ->
            add(
                MenuItem(extra.label, extra.icon) {
                    extra.onClick()
                    onDismiss()
                }
            )
        }
    }

    val focusRequesters = remember(items.size) { List(items.size) { FocusRequester() } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .width(380.dp)
                .heightIn(max = 460.dp)
                .shadow(8.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(ContextMenuGlassFill)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                // Release the held key so it can't immediately click the first enabled item.
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp &&
                        event.nativeKeyEvent.keyCode in setOf(
                            android.view.KeyEvent.KEYCODE_ENTER,
                            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
                        )
                    ) {
                        enabled = true
                    }
                    false
                }
        ) {
            items.forEachIndexed { index, menuItem ->
                ListItem(
                    selected = false,
                    enabled = enabled,
                    onClick = menuItem.onClick,
                    headlineContent = {
                        Text(menuItem.label, color = Color.White)
                    },
                    leadingContent = {
                        Icon(menuItem.icon, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Color.White.copy(alpha = 0.15f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequesters[index])
                )
            }
        }

        LaunchedEffect(Unit) {
            focusRequesters.firstOrNull()?.requestFocusWhenAttached()
        }
    }

    if (showSynopsis) {
        Dialog(
            onDismissRequest = { showSynopsis = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            androidx.tv.material3.Surface(
                shape = MaterialTheme.shapes.medium,
                colors = androidx.tv.material3.SurfaceDefaults.colors(
                    containerColor = app.picnic.player.ui.theme.PicnicColors.Surface,
                    contentColor = Color.White
                ),
                modifier = Modifier.width(600.dp).padding(32.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = item.overview ?: "No synopsis available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }

    if (showMediaInfo) {
        MediaInfoDialog(item = item, onDismiss = { showMediaInfo = false })
    }
}
