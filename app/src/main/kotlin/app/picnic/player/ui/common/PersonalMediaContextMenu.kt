package app.picnic.player.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.graphics.vector.ImageVector
import app.picnic.player.data.media.FolderContext
import app.picnic.player.data.media.PersonalLibrary
import app.picnic.player.data.media.isFolderContainer
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

class PersonalMenuRequest(
    val item: BaseItemDto,
    val library: PersonalLibrary?,
    val from: FolderContext?,
    val fromContinueWatching: Boolean = false
)

@Composable
fun PersonalMediaContextMenu(
    item: BaseItemDto,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onPlay: (startTicks: Long?) -> Unit,
    onMarkWatched: (Boolean) -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
    extraActions: List<ContextMenuAction> = emptyList()
) {
    val menuFocus = rememberContextMenuFocus()
    var mediaInfo by remember { mutableStateOf(false) }
    val favorite = item.userData?.isFavorite == true
    val played = item.userData?.played == true
    val resume = item.resumeTicks()
    fun action(label: String, icon: ImageVector, run: () -> Unit) = ContextMenuAction(label, icon) {
        run()
        onDismiss()
    }
    val actions = buildList {
        when {
            item.isFolderContainer() -> add(action("Open", Icons.AutoMirrored.Filled.ArrowForward, onOpen))
            item.type == BaseItemKind.PHOTO -> add(action("View", Icons.Default.Visibility, onOpen))
            item.type == BaseItemKind.VIDEO -> {
                if (resume != null) {
                    add(action("Resume", Icons.Default.PlayArrow) { onPlay(resume) })
                    add(action(PlayFromStartLabel, Icons.Default.Replay) { onPlay(null) })
                } else {
                    add(action("Play", Icons.Default.PlayArrow) { onPlay(null) })
                }
                add(
                    action(
                        if (played) "Mark unwatched" else "Mark watched",
                        if (played) Icons.Default.VisibilityOff else Icons.Default.Visibility
                    ) { onMarkWatched(!played) }
                )
            }
        }
        add(
            action(
                if (favorite) "Remove from favorites" else "Add to favorites",
                if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder
            ) { onToggleFavorite(!favorite) }
        )
        if (item.type == BaseItemKind.VIDEO || item.type == BaseItemKind.PHOTO) {
            add(ContextMenuAction("View media info", Icons.Default.Info) { mediaInfo = true })
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
    PicnicDialog(onDismiss = onDismiss) {
        BackHandler(enabled = mediaInfo) { mediaInfo = false }
        if (mediaInfo) MediaInfoPanel(item = item) else ContextMenuPanel(actions = actions, focus = menuFocus)
    }
}
