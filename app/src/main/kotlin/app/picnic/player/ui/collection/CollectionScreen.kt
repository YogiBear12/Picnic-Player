package app.picnic.player.ui.collection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.picnic.player.ui.ambient.PublishBackdrop
import app.picnic.player.ui.browse.DrawerCollapsedWidth
import app.picnic.player.ui.browse.browseLayoutMetrics
import app.picnic.player.ui.grid.GridStartInset
import app.picnic.player.ui.grid.LibraryGridViewModel
import app.picnic.player.ui.grid.MediaGridPane
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * One collection (box set): a grid of its children. Same full-screen geometry and grid
 * machinery as [app.picnic.player.ui.genre.GenreScreen]; the collection name is the
 * on-screen title.
 */
@Composable
fun CollectionScreen(
    collectionId: String,
    collectionName: String?,
    onItem: (BaseItemDto, String?, String?) -> Unit,
    onBack: () -> Unit,
    onSessionExpired: (String) -> Unit,
    viewModel: LibraryGridViewModel = hiltViewModel(key = "grid:collection:$collectionId")
) {
    PublishBackdrop(null)
    BackHandler(onBack = onBack)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val title = collectionName ?: "Collection"

    LaunchedEffect(collectionId) {
        viewModel.bindCollection(UUID.fromString(collectionId), title)
    }
    LaunchedEffect(state.sessionExpiredServerId) {
        state.sessionExpiredServerId?.let {
            viewModel.consumeSessionExpired()
            onSessionExpired(it)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        MediaGridPane(
            state = state,
            viewModel = viewModel,
            metrics = browseLayoutMetrics(maxWidth, maxHeight),
            seedContentFocus = true,
            onContentFocusSeeded = {},
            onItem = onItem,
            onChromeVisibleChange = {},
            showGenres = false,
            showContentType = true,
            title = title,
            startInset = DrawerCollapsedWidth + GridStartInset
        )
    }
}
