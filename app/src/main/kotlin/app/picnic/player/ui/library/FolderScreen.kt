package app.picnic.player.ui.library

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
import app.picnic.player.data.media.FolderContext
import app.picnic.player.ui.ambient.PublishBackdrop
import app.picnic.player.ui.browse.NavGutterWidth
import app.picnic.player.ui.browse.browseLayoutMetrics
import app.picnic.player.ui.grid.GridStartInset
import app.picnic.player.ui.navigation.FolderKey
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto

@Composable
fun FolderScreen(
    key: FolderKey,
    onOpen: (BaseItemDto, FolderContext) -> Unit,
    onBack: () -> Unit,
    onSessionExpired: (String) -> Unit,
    pathViewModel: FolderPathViewModel = hiltViewModel()
) {
    PublishBackdrop(null)
    BackHandler(onBack = onBack)
    val path by pathViewModel.path.collectAsStateWithLifecycle()
    LaunchedEffect(key) { pathViewModel.bind(key) }
    var seedContentFocus by remember { mutableStateOf(true) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        FolderGridPane(
            library = key.library,
            folderId = UUID.fromString(key.folderId),
            title = path.joinToString(" / ").ifEmpty { key.folderName },
            metrics = browseLayoutMetrics(maxWidth, maxHeight),
            seedContentFocus = seedContentFocus,
            onContentFocusSeeded = { seedContentFocus = false },
            onOpen = onOpen,
            onSessionExpired = onSessionExpired,
            startInset = NavGutterWidth + GridStartInset
        )
    }
}
