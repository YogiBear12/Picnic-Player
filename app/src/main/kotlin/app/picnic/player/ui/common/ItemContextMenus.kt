package app.picnic.player.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.tv.material3.ExperimentalTvMaterial3Api
import app.picnic.player.data.seerr.SeerrSeasonPickItem
import app.picnic.player.ui.seerr.ReportIssuePanel
import app.picnic.player.ui.seerr.SeasonRequestPanel
import app.picnic.player.ui.seerr.rememberIssueReporter
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

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
                ContextMenuAction("Go to show", Icons.Default.ArrowForward) {
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

    ContextMenuHost(onDismiss = onDismiss) { ContextMenuPanel(actions = actions) }
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
    val addToPlaylist = LocalAddToPlaylist.current
    GlobalContextMenuDialog(
        item = episode,
        onDismiss = onDismiss,
        onPlay = { _, startTicks -> onPlay(startTicks) },
        onMarkWatched = onMarkWatched,
        onToggleFavorite = onToggleFavorite,
        onGoToSeries = onGoToSeries,
        onAddToPlaylist = { addToPlaylist(episode) }
    )
}

@Composable
fun OverflowMenuDialog(
    item: BaseItemDto,
    onPlayVersion: (String) -> Unit,
    onDismiss: () -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
    extraActions: List<ContextMenuAction> = emptyList(),
    requestMore: RequestMoreSeasons? = null
) {
    var panel by remember { mutableStateOf<OverflowPanel?>(null) }
    val menuFocus = rememberContextMenuFocus()
    val versionFocus = rememberContextMenuFocus()
    val reportTarget = rememberIssueReporter(item)
    val mediaSources = item.mediaSources.orEmpty()
    val hasVersions = mediaSources.size > 1
    val isFavorite = item.userData?.isFavorite ?: false
    val addToPlaylist = LocalAddToPlaylist.current

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

    val mainItems = buildList {
        addAll(extraActions)
        if (requestMore != null) {
            add(
                ContextMenuAction(
                    label = if (requestMore.busy) "Requesting…" else "Request more",
                    icon = Icons.Default.Add,
                    enabled = !requestMore.busy,
                    onClick = { panel = OverflowPanel.REQUEST_MORE }
                )
            )
        }
        if (hasVersions) {
            add(
                ContextMenuAction(
                    label = "Choose version",
                    icon = Icons.Default.Layers,
                    onClick = { panel = OverflowPanel.VERSIONS }
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
        if (reportTarget != null) {
            add(
                ContextMenuAction(
                    label = "Report an issue",
                    icon = Icons.Default.ReportProblem,
                    onClick = { panel = OverflowPanel.REPORT }
                )
            )
        }
        if (item.type == BaseItemKind.MOVIE || item.type == BaseItemKind.EPISODE) {
            add(
                ContextMenuAction(
                    label = "View media info",
                    icon = Icons.Default.Info,
                    onClick = { panel = OverflowPanel.MEDIA_INFO }
                )
            )
        }
    }

    ContextMenuHost(onDismiss = onDismiss) {
        BackHandler(enabled = panel != null) { panel = null }
        when (panel) {
            OverflowPanel.MEDIA_INFO -> MediaInfoPanel(item = item)
            OverflowPanel.REPORT -> if (reportTarget != null) {
                ReportIssuePanel(
                    item = item,
                    target = reportTarget,
                    onDone = onDismiss,
                    onCancel = { panel = null }
                )
            }
            OverflowPanel.REQUEST_MORE -> if (requestMore != null) {
                SeasonRequestPanel(
                    seasons = requestMore.seasons,
                    onConfirm = {
                        requestMore.onConfirm(it)
                        onDismiss()
                    },
                    onDismiss = { panel = null }
                )
            }
            OverflowPanel.VERSIONS ->
                ContextMenuPanel(actions = versionItems, openedByLongPress = false, focus = versionFocus)
            null -> ContextMenuPanel(actions = mainItems, openedByLongPress = false, focus = menuFocus)
        }
    }
}

data class RequestMoreSeasons(
    val busy: Boolean,
    val seasons: List<SeerrSeasonPickItem>,
    val onConfirm: (List<Int>) -> Unit
)

private enum class OverflowPanel {
    REQUEST_MORE,
    VERSIONS,
    MEDIA_INFO,
    REPORT
}
