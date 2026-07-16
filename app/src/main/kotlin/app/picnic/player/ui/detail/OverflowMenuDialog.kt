package app.picnic.player.ui.detail

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
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
import app.picnic.player.ui.common.MediaInfoDialog
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.theme.PicnicColors
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

private val ContextMenuGlassFill = Color(0xEA181E24)
private val SynopsisBackground = Color(0xFF181E24)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeasonContextMenu(
    season: BaseItemDto,
    onDismiss: () -> Unit,
    onMarkWatched: (Boolean) -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
    onGoToSeries: (() -> Unit)?
) {
    val played = season.userData?.played ?: false
    val isFavorite = season.userData?.isFavorite ?: false

    // Disable items for ~1s after opening from long-press so the held key can't fire the first item.
    var enabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1000L)
        enabled = true
    }

    data class MenuItem(val label: String, val icon: ImageVector, val onClick: () -> Unit)

    val items = buildList {
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
                if (isFavorite) "Remove favourite" else "Add to favourites",
                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder
            ) {
                onToggleFavorite(!isFavorite)
                onDismiss()
            }
        )
        if (onGoToSeries != null) {
            add(
                MenuItem("Go to Series", Icons.Default.ArrowForward) {
                    onGoToSeries()
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
                .shadow(8.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(ContextMenuGlassFill)
                .padding(24.dp)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp &&
                        event.nativeKeyEvent.keyCode in setOf(
                            AndroidKeyEvent.KEYCODE_ENTER,
                            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
                        )
                    ) {
                        enabled = true
                    }
                    false
                }
        ) {
            items.forEachIndexed { index, item ->
                ListItem(
                    selected = false,
                    enabled = enabled,
                    onClick = item.onClick,
                    headlineContent = {
                        Text(item.label, color = Color.White)
                    },
                    leadingContent = {
                        Icon(item.icon, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
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
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EpisodeContextMenu(
    episode: BaseItemDto,
    onDismiss: () -> Unit,
    onPlay: (startTicks: Long?) -> Unit,
    onMarkWatched: (Boolean) -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
    onGoToSeries: ((String) -> Unit)? = null
) {
    val played = episode.userData?.played ?: false
    val isFavorite = episode.userData?.isFavorite ?: false
    val resumeTicks = episode.userData?.playbackPositionTicks?.takeIf { it > 0L }

    var showSynopsis by remember { mutableStateOf(false) }
    var showMediaInfo by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(1000L)
        enabled = true
    }

    data class MenuItem(val label: String, val icon: ImageVector, val onClick: () -> Unit)

    val items = buildList<MenuItem> {
        if (resumeTicks != null) {
            add(
                MenuItem("Resume", Icons.Default.PlayArrow) {
                    onPlay(resumeTicks)
                    onDismiss()
                }
            )
            add(
                MenuItem("Restart", Icons.Default.Replay) {
                    onPlay(1L)
                    onDismiss()
                }
            )
        } else {
            add(
                MenuItem("Play", Icons.Default.PlayArrow) {
                    onPlay(null)
                    onDismiss()
                }
            )
        }
        add(MenuItem("View synopsis", Icons.Default.Article) { showSynopsis = true })
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
                if (isFavorite) "Remove favourite" else "Add to favourites",
                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder
            ) {
                onToggleFavorite(!isFavorite)
                onDismiss()
            }
        )

        if (episode.seriesId != null && onGoToSeries != null) {
            add(
                MenuItem("Go to Series", Icons.Default.ArrowForward) {
                    onGoToSeries(episode.seriesId.toString())
                    onDismiss()
                }
            )
        }

        add(MenuItem("View media info", Icons.Default.Info) { showMediaInfo = true })
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
                .shadow(8.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(ContextMenuGlassFill)
                .padding(24.dp)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp &&
                        event.nativeKeyEvent.keyCode in setOf(
                            AndroidKeyEvent.KEYCODE_ENTER,
                            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
                        )
                    ) {
                        enabled = true
                    }
                    false
                }
        ) {
            items.forEachIndexed { index, item ->
                ListItem(
                    selected = false,
                    enabled = enabled,
                    onClick = item.onClick,
                    headlineContent = {
                        Text(item.label, color = Color.White)
                    },
                    leadingContent = {
                        Icon(item.icon, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
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
        EpisodeSynopsisDialog(episode = episode, onDismiss = { showSynopsis = false })
    }

    if (showMediaInfo) {
        MediaInfoDialog(item = episode, onDismiss = { showMediaInfo = false })
    }
}

@Composable
fun OverflowMenuDialog(
    item: BaseItemDto,
    onPlayVersion: (String) -> Unit,
    onDismiss: () -> Unit,
    showRequestMore: Boolean = false,
    requestMoreBusy: Boolean = false,
    onRequestMore: () -> Unit = {}
) {
    var showVersions by remember { mutableStateOf(false) }
    var showMediaInfo by remember { mutableStateOf(false) }
    val mediaSources = item.mediaSources.orEmpty()
    val hasVersions = mediaSources.size > 1

    if (showMediaInfo) {
        MediaInfoDialog(item = item, onDismiss = { showMediaInfo = false })
        return
    }

    if (showVersions) {
        val versionItems = mediaSources.mapIndexed { index, source ->
            GlassMenuItem(
                label = source.name ?: "Version ${index + 1}",
                icon = Icons.Default.PlayArrow,
                onClick = {
                    onPlayVersion(source.id ?: "")
                    onDismiss()
                }
            )
        }
        GlassContextMenu(
            items = versionItems,
            onDismissRequest = { showVersions = false }
        )
        return
    }

    val mainItems = buildList {
        if (showRequestMore) {
            add(
                GlassMenuItem(
                    label = if (requestMoreBusy) "Requesting…" else "Request more",
                    icon = Icons.Default.Add,
                    enabled = !requestMoreBusy,
                    onClick = {
                        onDismiss()
                        if (!requestMoreBusy) onRequestMore()
                    }
                )
            )
        }
        if (hasVersions) {
            add(
                GlassMenuItem(
                    label = "Choose Version",
                    icon = Icons.Default.Layers,
                    onClick = { showVersions = true }
                )
            )
        }
        if (item.type == BaseItemKind.MOVIE || item.type == BaseItemKind.EPISODE) {
            add(
                GlassMenuItem(
                    label = "View media info",
                    icon = Icons.Default.Info,
                    onClick = { showMediaInfo = true }
                )
            )
        }
    }

    GlassContextMenu(
        items = mainItems,
        onDismissRequest = onDismiss
    )
}

private data class GlassMenuItem(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GlassContextMenu(
    items: List<GlassMenuItem>,
    onDismissRequest: () -> Unit
) {
    val focusRequesters = remember(items.size) { List(items.size) { FocusRequester() } }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .width(380.dp)
                .shadow(8.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(ContextMenuGlassFill)
                .padding(24.dp)
        ) {
            items.forEachIndexed { index, item ->
                ListItem(
                    selected = false,
                    enabled = item.enabled,
                    onClick = item.onClick,
                    headlineContent = {
                        Text(item.label, color = Color.White)
                    },
                    leadingContent = {
                        Icon(item.icon, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
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
}

@Composable
private fun EpisodeSynopsisDialog(
    episode: BaseItemDto,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.tv.material3.Surface(
            shape = MaterialTheme.shapes.medium,
            colors = androidx.tv.material3.SurfaceDefaults.colors(
                containerColor = PicnicColors.Surface,
                contentColor = Color.White
            ),
            modifier = Modifier.width(600.dp).padding(32.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = episode.overview?.takeIf { it.isNotBlank() } ?: "No synopsis available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}
