package app.picnic.player.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.common.ContextMenuAction
import app.picnic.player.ui.common.ContextMenuDialog
import app.picnic.player.ui.common.LocalAddToPlaylist
import app.picnic.player.ui.common.MediaInfoDialog
import app.picnic.player.ui.theme.PicnicColors
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

private val ContextMenuGlassFill = PicnicColors.GlassFill
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
    val addToPlaylist = LocalAddToPlaylist.current

    val actions = buildList {
        add(
            ContextMenuAction(
                if (played) "Mark unwatched" else "Mark watched",
                if (played) Icons.Default.VisibilityOff else Icons.Default.Visibility
            ) {
                onMarkWatched(!played)
                onDismiss()
            }
        )
        if (onGoToSeries != null) {
            add(
                ContextMenuAction("Go to series", Icons.Default.ArrowForward) {
                    onGoToSeries()
                    onDismiss()
                }
            )
        }
        add(
            ContextMenuAction(
                if (isFavorite) "Remove from favorites" else "Add to favorites",
                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder
            ) {
                onToggleFavorite(!isFavorite)
                onDismiss()
            }
        )
        add(
            ContextMenuAction("Add to playlist", Icons.AutoMirrored.Filled.PlaylistAdd) {
                addToPlaylist(season)
                onDismiss()
            }
        )
    }

    ContextMenuDialog(actions = actions, onDismiss = onDismiss)
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
    val addToPlaylist = LocalAddToPlaylist.current

    var showSynopsis by remember { mutableStateOf(false) }
    var showMediaInfo by remember { mutableStateOf(false) }

    val actions = buildList {
        if (resumeTicks != null) {
            add(
                ContextMenuAction("Resume", Icons.Default.PlayArrow) {
                    onPlay(resumeTicks)
                    onDismiss()
                }
            )
            add(
                ContextMenuAction("Restart", Icons.Default.Replay) {
                    onPlay(1L)
                    onDismiss()
                }
            )
        } else {
            add(
                ContextMenuAction("Play", Icons.Default.PlayArrow) {
                    onPlay(null)
                    onDismiss()
                }
            )
        }
        add(
            ContextMenuAction(
                if (played) "Mark unwatched" else "Mark watched",
                if (played) Icons.Default.VisibilityOff else Icons.Default.Visibility
            ) {
                onMarkWatched(!played)
                onDismiss()
            }
        )
        add(ContextMenuAction("View synopsis", Icons.Default.Article) { showSynopsis = true })

        if (episode.seriesId != null && onGoToSeries != null) {
            add(
                ContextMenuAction("Go to series", Icons.Default.ArrowForward) {
                    onGoToSeries(episode.seriesId.toString())
                    onDismiss()
                }
            )
        }

        add(
            ContextMenuAction(
                if (isFavorite) "Remove from favorites" else "Add to favorites",
                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder
            ) {
                onToggleFavorite(!isFavorite)
                onDismiss()
            }
        )

        add(
            ContextMenuAction("Add to playlist", Icons.AutoMirrored.Filled.PlaylistAdd) {
                addToPlaylist(episode)
                onDismiss()
            }
        )

        add(ContextMenuAction("View media info", Icons.Default.Info) { showMediaInfo = true })
    }

    ContextMenuDialog(actions = actions, onDismiss = onDismiss)

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
    onToggleFavorite: (Boolean) -> Unit,
    showRequestMore: Boolean = false,
    requestMoreBusy: Boolean = false,
    onRequestMore: () -> Unit = {}
) {
    var showVersions by remember { mutableStateOf(false) }
    var showMediaInfo by remember { mutableStateOf(false) }
    val mediaSources = item.mediaSources.orEmpty()
    val hasVersions = mediaSources.size > 1
    val isFavorite = item.userData?.isFavorite ?: false
    val addToPlaylist = LocalAddToPlaylist.current

    if (showMediaInfo) {
        MediaInfoDialog(item = item, onDismiss = { showMediaInfo = false })
        return
    }

    if (showVersions) {
        val versionItems = mediaSources.mapIndexed { index, source ->
            ContextMenuAction(
                label = source.name ?: "Version ${index + 1}",
                icon = Icons.Default.PlayArrow,
                onClick = {
                    onPlayVersion(source.id ?: "")
                    onDismiss()
                }
            )
        }
        ContextMenuDialog(
            actions = versionItems,
            onDismiss = { showVersions = false },
            openedByLongPress = false
        )
        return
    }

    val mainItems = buildList {
        if (showRequestMore) {
            add(
                ContextMenuAction(
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
                ContextMenuAction(
                    label = "Choose version",
                    icon = Icons.Default.Layers,
                    onClick = { showVersions = true }
                )
            )
        }
        add(
            ContextMenuAction(
                label = if (isFavorite) "Remove from favorites" else "Add to favorites",
                icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                onClick = {
                    onToggleFavorite(!isFavorite)
                    onDismiss()
                }
            )
        )
        add(
            ContextMenuAction(
                label = "Add to playlist",
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                onClick = {
                    addToPlaylist(item)
                    onDismiss()
                }
            )
        )
        if (item.type == BaseItemKind.MOVIE || item.type == BaseItemKind.EPISODE) {
            add(
                ContextMenuAction(
                    label = "View media info",
                    icon = Icons.Default.Info,
                    onClick = { showMediaInfo = true }
                )
            )
        }
    }

    ContextMenuDialog(actions = mainItems, onDismiss = onDismiss, openedByLongPress = false)
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
