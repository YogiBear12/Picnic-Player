package app.picnic.player.ui.seerr

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.picnic.player.data.seerr.SeerrCatalogItem
import app.picnic.player.data.seerr.SeerrMediaType
import app.picnic.player.ui.common.ContextMenuAction
import app.picnic.player.ui.common.ContextMenuDialog
import app.picnic.player.ui.detail.launchRemoteTrailer

@Composable
fun SeerrCardContextMenu(
    item: SeerrCatalogItem,
    onDismiss: () -> Unit
) {
    val viewModel = hiltViewModel<SeerrDetailViewModel, SeerrDetailViewModel.Factory>(
        key = "seerr-card-menu-${item.mediaType}-${item.tmdbId}",
        creationCallback = { factory -> factory.create(item.tmdbId, item.mediaType) }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (state.showSeasonPicker) {
        SeasonRequestDialog(
            seasons = viewModel.seasonPickItems(),
            onConfirm = { seasons ->
                viewModel.requestSeasons(seasons)
                onDismiss()
            },
            onDismiss = {
                viewModel.dismissSeasonPicker()
                onDismiss()
            }
        )
        return
    }

    val actions = if (state.loading) {
        listOf(ContextMenuAction("Loading…", Icons.Default.HourglassEmpty) {})
    } else {
        val actionRow = viewModel.actionRow()
        val trailerUrl = state.catalog?.trailerUrl
        buildList {
            when (actionRow.primary) {
                SeerrPrimaryAction.Request,
                SeerrPrimaryAction.RequestMore
                -> add(
                    ContextMenuAction(
                        label = actionRow.primaryLabel,
                        icon = primaryIcon(actionRow.primary),
                        enabled = !state.busy,
                        onClick = {
                            viewModel.onRequestClicked()
                            if (item.mediaType == SeerrMediaType.MOVIE) onDismiss()
                        }
                    )
                )
                SeerrPrimaryAction.Pending -> add(
                    ContextMenuAction(
                        label = actionRow.primaryLabel,
                        icon = primaryIcon(actionRow.primary),
                        onClick = {}
                    )
                )
                SeerrPrimaryAction.Play,
                SeerrPrimaryAction.Unavailable
                -> Unit
            }
            if (actionRow.showCancel) {
                add(
                    ContextMenuAction(
                        label = actionRow.cancelLabel,
                        icon = Icons.Default.Close,
                        enabled = !state.busy,
                        onClick = {
                            viewModel.cancelRequest()
                            onDismiss()
                        }
                    )
                )
            }
            if (!trailerUrl.isNullOrBlank()) {
                add(
                    ContextMenuAction(
                        label = "Watch trailer",
                        icon = Icons.Default.Movie,
                        onClick = {
                            context.launchRemoteTrailer(trailerUrl, state.trailerYouTubePackage)
                            onDismiss()
                        }
                    )
                )
            }
            if (isEmpty()) {
                add(
                    ContextMenuAction(
                        label = state.error ?: "No actions available",
                        icon = Icons.Default.Info,
                        onClick = onDismiss
                    )
                )
            }
        }
    }

    ContextMenuDialog(actions = actions, onDismiss = onDismiss)
}
