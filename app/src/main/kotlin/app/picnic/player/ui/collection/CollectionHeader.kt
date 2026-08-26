package app.picnic.player.ui.collection

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import app.picnic.player.ui.common.PageAction
import org.jellyfin.sdk.model.api.BaseItemDto

internal fun collectionActions(
    item: BaseItemDto,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onToggleWatched: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMore: () -> Unit
): List<PageAction> {
    val favorite = item.userData?.isFavorite ?: false
    return buildList {
        add(PageAction("Play", Icons.Default.PlayArrow, onPlay))
        add(PageAction("Shuffle", Icons.Default.Shuffle, onShuffle))
        add(
            PageAction(
                if (item.userData?.played == true) "Mark Unwatched" else "Mark Watched",
                Icons.Default.Check,
                onToggleWatched
            )
        )
        add(
            PageAction(
                if (favorite) "Remove from favorites" else "Add to favorites",
                if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                onToggleFavorite
            )
        )
        add(PageAction("More", Icons.Default.MoreVert, onMore))
    }
}
