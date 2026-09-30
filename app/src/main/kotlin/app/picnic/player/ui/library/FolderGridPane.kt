package app.picnic.player.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.picnic.player.data.media.FolderContext
import app.picnic.player.data.media.PersonalLibrary
import app.picnic.player.ui.browse.BrowseLayoutMetrics
import app.picnic.player.ui.browse.landscapeCardStyle
import app.picnic.player.ui.common.ContextMenuHandler
import app.picnic.player.ui.common.LocalContextMenuHandler
import app.picnic.player.ui.common.PersonalMenuRequest
import app.picnic.player.ui.grid.GridStartInset
import app.picnic.player.ui.grid.LibraryGridViewModel
import app.picnic.player.ui.grid.MediaGridPane
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto

@Composable
internal fun FolderGridPane(
    library: PersonalLibrary,
    folderId: UUID,
    title: String,
    metrics: BrowseLayoutMetrics,
    seedContentFocus: Boolean,
    onContentFocusSeeded: () -> Unit,
    onOpen: (BaseItemDto, FolderContext) -> Unit,
    onSessionExpired: (String) -> Unit,
    startInset: Dp = GridStartInset,
    viewModel: LibraryGridViewModel = hiltViewModel(key = "grid:folder:$folderId")
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val from = FolderContext(folderId, state.sort)

    LaunchedEffect(folderId) { viewModel.bindFolder(library.id, folderId, title.ifEmpty { library.name }) }
    LaunchedEffect(state.sessionExpiredServerId) {
        state.sessionExpiredServerId?.let {
            viewModel.consumeSessionExpired()
            onSessionExpired(it)
        }
    }

    val menus = LocalContextMenuHandler.current
    val personalMenus = remember(menus, library, from) {
        object : ContextMenuHandler by menus {
            override fun show(item: BaseItemDto, fromContinueWatching: Boolean) = menus.showPersonal(PersonalMenuRequest(item, library, from))
        }
    }
    CompositionLocalProvider(LocalContextMenuHandler provides personalMenus) {
        MediaGridPane(
            state = state,
            viewModel = viewModel,
            metrics = metrics,
            seedContentFocus = seedContentFocus,
            onContentFocusSeeded = onContentFocusSeeded,
            onItem = { item, _, _ -> onOpen(item, from) },
            onChromeVisibleChange = {},
            offeredFilters = emptySet(),
            title = title,
            startInset = startInset,
            cardStyle = landscapeCardStyle(sy = metrics.sy)
        )
    }
}
