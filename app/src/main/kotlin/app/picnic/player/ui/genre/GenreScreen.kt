@file:OptIn(ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.genre

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import app.picnic.player.ui.ambient.PublishBackdrop
import app.picnic.player.ui.browse.NavGutterWidth
import app.picnic.player.ui.browse.browseLayoutMetrics
import app.picnic.player.ui.grid.GridFilterSection
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
    var seedContentFocus by remember { mutableStateOf(true) }
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

    BoxWithConstraints(Modifier.fillMaxSize()) {
        MediaGridPane(
            state = state,
            viewModel = viewModel,
            metrics = browseLayoutMetrics(maxWidth, maxHeight),
            seedContentFocus = seedContentFocus,
            onContentFocusSeeded = { seedContentFocus = false },
            onItem = onItem,
            onChromeVisibleChange = {},
            offeredFilters = setOf(GridFilterSection.CONTENT_TYPE),
            title = libraryName?.let { "$title · $it" },
            startInset = NavGutterWidth + GridStartInset
        )
    }
}
