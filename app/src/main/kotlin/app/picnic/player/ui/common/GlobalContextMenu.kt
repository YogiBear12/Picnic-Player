package app.picnic.player.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
fun GlobalContextMenuDialog(
    item: BaseItemDto,
    onDismiss: () -> Unit,
    onPlay: (itemId: String, startTicks: Long?) -> Unit,
    onMarkWatched: (Boolean) -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
    onGoToSeries: ((String) -> Unit)?,
    onAddToPlaylist: (() -> Unit)? = null,
    extraActions: List<ContextMenuAction> = emptyList()
) {
    val played = item.userData?.played ?: false
    val isFavorite = item.userData?.isFavorite ?: false
    val resumeTicks = item.userData?.playbackPositionTicks?.takeIf { it > 0L }

    var showSynopsis by remember { mutableStateOf(false) }
    var showMediaInfo by remember { mutableStateOf(false) }

    val actions = buildList {
        if (item.type in PlayableKinds) {
            if (resumeTicks != null) {
                add(
                    ContextMenuAction("Resume", Icons.Default.PlayArrow) {
                        onPlay(item.id.toString(), resumeTicks)
                        onDismiss()
                    }
                )
                add(
                    ContextMenuAction("Restart", Icons.Default.Replay) {
                        onPlay(item.id.toString(), 1L)
                        onDismiss()
                    }
                )
            } else {
                add(
                    ContextMenuAction("Play", Icons.Default.PlayArrow) {
                        onPlay(item.id.toString(), null)
                        onDismiss()
                    }
                )
            }
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

        if (item.overview?.isNotBlank() == true) {
            add(ContextMenuAction("View synopsis", Icons.Default.Article) { showSynopsis = true })
        }

        if (item.type == BaseItemKind.EPISODE && item.seriesId != null && onGoToSeries != null) {
            add(
                ContextMenuAction("Go to series", Icons.Default.ArrowForward) {
                    onGoToSeries(item.seriesId.toString())
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

        if (onAddToPlaylist != null) {
            add(
                ContextMenuAction("Add to playlist", Icons.AutoMirrored.Filled.PlaylistAdd) {
                    onAddToPlaylist()
                    onDismiss()
                }
            )
        }

        if (item.type == BaseItemKind.MOVIE || item.type == BaseItemKind.EPISODE) {
            add(ContextMenuAction("View media info", Icons.Default.Info) { showMediaInfo = true })
        }

        extraActions.forEach { extra ->
            add(
                extra.copy(onClick = {
                    extra.onClick()
                    onDismiss()
                })
            )
        }
    }

    ContextMenuDialog(actions = actions, onDismiss = onDismiss)

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
