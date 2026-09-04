package app.picnic.player.ui.seerr

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.picnic.player.data.seerr.SeerrCatalogItem
import app.picnic.player.data.seerr.SeerrMediaType
import app.picnic.player.data.seerr.jellyfinDetailIdOrNull
import app.picnic.player.ui.common.ContextMenuAction
import app.picnic.player.ui.common.ContextMenuHost
import app.picnic.player.ui.common.ContextMenuPanel
import app.picnic.player.ui.common.GlobalContextMenuViewModel
import app.picnic.player.ui.detail.launchRemoteTrailer
import org.jellyfin.sdk.model.api.BaseItemDto

@Composable
fun SeerrCardContextMenu(
    item: SeerrCatalogItem,
    onLibraryItem: (BaseItemDto) -> Unit,
    onDismiss: () -> Unit
) {
    val jellyfinItemId = jellyfinDetailIdOrNull(item.jellyfinMediaId, item.jellyfinMediaId4k)
    if (jellyfinItemId != null) {
        LibraryItemHandoff(jellyfinItemId, onLibraryItem, onDismiss)
        return
    }

    val viewModel = hiltViewModel<SeerrDetailViewModel, SeerrDetailViewModel.Factory>(
        key = "seerr-card-menu-${item.mediaType}-${item.tmdbId}",
        creationCallback = { factory -> factory.create(item.tmdbId, item.mediaType) }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val actions = if (state.loading) {
        listOf(LoadingAction)
    } else {
        val actionRow = viewModel.actionRow()
        val trailerUrl = state.catalog?.trailerUrl
        buildList {
            when (actionRow.primary) {
                SeerrPrimaryAction.Request,
                SeerrPrimaryAction.RequestMore,
                SeerrPrimaryAction.Pending
                -> add(
                    ContextMenuAction(
                        label = actionRow.primaryLabel,
                        icon = primaryIcon(actionRow.primary),
                        enabled = !state.busy,
                        onClick = {
                            val requesting = viewModel.canRequest()
                            viewModel.onRequestClicked()
                            if (requesting && item.mediaType == SeerrMediaType.MOVIE) onDismiss()
                        }
                    )
                )
                SeerrPrimaryAction.Unavailable -> actionRow.infoMessage?.let { reason ->
                    add(ContextMenuAction(reason, Icons.Default.Info, enabled = false) {})
                }
                SeerrPrimaryAction.Play -> Unit
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
                        enabled = false,
                        onClick = {}
                    )
                )
            }
        }
    }

    ContextMenuHost(onDismiss = onDismiss) {
        if (state.showSeasonPicker) {
            SeasonRequestPanel(
                seasons = viewModel.seasonPickItems(),
                onConfirm = { seasons ->
                    viewModel.requestSeasons(seasons)
                    onDismiss()
                },
                onDismiss = { viewModel.dismissSeasonPicker() }
            )
        } else {
            ContextMenuPanel(actions = actions)
        }
    }
}

@Composable
private fun LibraryItemHandoff(
    jellyfinItemId: String,
    onLibraryItem: (BaseItemDto) -> Unit,
    onDismiss: () -> Unit
) {
    val menuViewModel: GlobalContextMenuViewModel = hiltViewModel()
    var lookupFailed by remember(jellyfinItemId) { mutableStateOf(false) }

    LaunchedEffect(jellyfinItemId) {
        val libraryItem = menuViewModel.libraryItem(jellyfinItemId)
        if (libraryItem == null) lookupFailed = true else onLibraryItem(libraryItem)
    }

    if (!lookupFailed) return
    ContextMenuHost(onDismiss = onDismiss) {
        ContextMenuPanel(
            actions = listOf(
                ContextMenuAction("Couldn't load this title", Icons.Default.Info, enabled = false) {}
            )
        )
    }
}

private val LoadingAction =
    ContextMenuAction("Loading…", Icons.Default.HourglassEmpty, enabled = false) {}
