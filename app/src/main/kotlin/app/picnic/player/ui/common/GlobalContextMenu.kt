package app.picnic.player.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.playlist.AddToPlaylistPanel
import app.picnic.player.ui.seerr.ReportIssuePanel
import app.picnic.player.ui.seerr.rememberIssueReporter
import app.picnic.player.ui.theme.PicnicColors
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
    extraActions: List<ContextMenuAction> = emptyList(),
    showResumePosition: Boolean = true
) {
    val played = item.userData?.played ?: false
    val isFavorite = item.userData?.isFavorite ?: false
    val resumeTicks = item.resumeTicks()

    var panel by remember { mutableStateOf<GlobalMenuPanel?>(null) }
    val menuFocus = rememberContextMenuFocus()
    val reportTarget = rememberIssueReporter(item)

    val actions = buildList {
        if (item.type in PlayableKinds) {
            if (resumeTicks != null) {
                add(
                    ContextMenuAction(
                        if (showResumePosition) resumeLabel(resumeTicks) else "Resume",
                        Icons.Default.PlayArrow
                    ) {
                        onPlay(item.id.toString(), resumeTicks)
                        onDismiss()
                    }
                )
                add(
                    ContextMenuAction(PlayFromStartLabel, Icons.Default.Replay) {
                        onPlay(item.id.toString(), null)
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
            add(ContextMenuAction("View synopsis", Icons.Default.Article) { panel = GlobalMenuPanel.SYNOPSIS })
        }

        if (item.type == BaseItemKind.EPISODE && item.seriesId != null && onGoToSeries != null) {
            add(
                ContextMenuAction("Go to show", Icons.Default.ArrowForward) {
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

        add(
            ContextMenuAction("Add to playlist", Icons.AutoMirrored.Filled.PlaylistAdd) {
                panel = GlobalMenuPanel.PLAYLIST
            }
        )

        extraActions.forEach { extra ->
            add(
                extra.copy(onClick = {
                    extra.onClick()
                    onDismiss()
                })
            )
        }

        if (reportTarget != null) {
            add(ContextMenuAction("Report an issue", Icons.Default.ReportProblem) { panel = GlobalMenuPanel.REPORT })
        }

        if (item.type == BaseItemKind.MOVIE || item.type == BaseItemKind.EPISODE) {
            add(ContextMenuAction("View media info", Icons.Default.Info) { panel = GlobalMenuPanel.MEDIA_INFO })
        }
    }

    ContextMenuHost(onDismiss = onDismiss) {
        BackHandler(enabled = panel != null) { panel = null }
        when (panel) {
            GlobalMenuPanel.SYNOPSIS -> SynopsisPanel(item.overview)
            GlobalMenuPanel.PLAYLIST -> AddToPlaylistPanel(item = item, onDone = onDismiss)
            GlobalMenuPanel.MEDIA_INFO -> MediaInfoPanel(item = item)
            GlobalMenuPanel.REPORT -> if (reportTarget != null) {
                ReportIssuePanel(
                    item = item,
                    target = reportTarget,
                    onDone = onDismiss,
                    onCancel = { panel = null }
                )
            }
            null -> ContextMenuPanel(actions = actions, focus = menuFocus)
        }
    }
}

private enum class GlobalMenuPanel {
    SYNOPSIS,
    PLAYLIST,
    MEDIA_INFO,
    REPORT
}

@Composable
private fun SynopsisPanel(overview: String?) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .panelSurface(width = PanelWidth.Reading, corner = DialogCornerRadius)
            .padding(horizontal = PanelContentInset + PanelRowInnerPadding)
    ) {
        Text(
            text = overview ?: "No synopsis available.",
            style = MaterialTheme.typography.bodyMedium,
            color = PicnicColors.OnDarkMuted
        )
    }
}
