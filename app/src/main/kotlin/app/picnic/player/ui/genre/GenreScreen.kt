@file:OptIn(ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.genre

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import app.picnic.player.ui.ambient.PublishBackdrop
import app.picnic.player.ui.browse.DrawerCollapsedWidth
import app.picnic.player.ui.browse.browseLayoutMetrics
import app.picnic.player.ui.grid.GridStartInset
import app.picnic.player.ui.grid.LibraryGridViewModel
import app.picnic.player.ui.grid.MediaGridPane
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto

@Composable
fun GenreScreen(
    genreId: String,
    genreName: String?,
    onItem: (BaseItemDto, String?, String?) -> Unit,
    onBack: () -> Unit,
    onSessionExpired: (String) -> Unit,
    libraryId: String? = null,
    libraryName: String? = null,
    viewModel: LibraryGridViewModel = hiltViewModel(key = "grid:genre:$genreId:${libraryId.orEmpty()}")
) {
    PublishBackdrop(null)
    BackHandler(onBack = onBack)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val title = genreName ?: "Genre"

    LaunchedEffect(genreId) {
        viewModel.bindGenre(
            genreId = UUID.fromString(genreId),
            title = title,
            libraryId = libraryId?.let(UUID::fromString)
        )
    }
    LaunchedEffect(state.sessionExpiredServerId) {
        state.sessionExpiredServerId?.let {
            viewModel.consumeSessionExpired()
            onSessionExpired(it)
        }
    }

    // Full-screen destination (no live drawer). Match Library Grid geometry: drawer
    // collapsed footprint + the shell's tiny breathing inset. The global variant shows
    // no title (matches the old behaviour); the library-scoped variant disambiguates
    // with "Genre · Library" — the same genre name lists fewer items here than the
    // search tab's global grid.
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
            title = libraryName?.let { "$title · $it" },
            startInset = DrawerCollapsedWidth + GridStartInset
        )
    }
}
