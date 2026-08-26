@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package app.picnic.player.ui.detail

import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import app.picnic.player.ui.browse.BrowseLayoutMetrics
import app.picnic.player.ui.browse.DetailMediaRow
import app.picnic.player.ui.common.CircularPersonCard
import app.picnic.player.ui.common.LocalImageUrls
import app.picnic.player.ui.common.PageAction
import app.picnic.player.ui.common.RowFocusState
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson

@Immutable
internal data class DetailButtonActions(
    val onPlay: () -> Unit,
    val onEpisodes: () -> Unit,
    val onToggleWatched: () -> Unit,
    val onTrailers: () -> Unit,
    val onMore: () -> Unit
)

internal fun detailActions(
    item: BaseItemDto,
    playTitle: String,
    trailerCount: Int,
    actions: DetailButtonActions
): List<PageAction> = buildList {
    add(PageAction(playTitle, Icons.Default.PlayArrow, actions.onPlay))
    if (item.type == BaseItemKind.SERIES) {
        add(PageAction("Episodes", Icons.Default.List, actions.onEpisodes))
    }
    add(
        PageAction(
            if (item.userData?.played == true) "Mark Unwatched" else "Mark Watched",
            Icons.Default.Check,
            actions.onToggleWatched
        )
    )
    if (trailerCount > 0) {
        add(
            PageAction(
                if (trailerCount == 1) "Play trailer" else "Trailers",
                Icons.Default.Movie,
                actions.onTrailers
            )
        )
    }
    add(PageAction("More", Icons.Default.MoreVert, actions.onMore))
}

@Composable
internal fun DetailCastRow(
    people: List<BaseItemPerson>,
    metrics: BrowseLayoutMetrics,
    horizontalRowSpec: BringIntoViewSpec,
    rowFocus: RowFocusState,
    upFocus: () -> FocusRequester,
    onFocused: (Int, BaseItemPerson) -> Unit,
    onPersonClick: (BaseItemPerson) -> Unit
) {
    DetailMediaRow(
        title = "Cast & crew",
        items = people,
        endInset = metrics.hInset,
        cardSpacing = metrics.cardSpacing,
        horizontalRowSpec = horizontalRowSpec,
        rowFocus = rowFocus.rowModifier()
    ) { index, person ->
        CircularPersonCard(
            imageUrl = LocalImageUrls.current.personPrimary(
                person.id.toString(),
                person.primaryImageTag
            ),
            name = person.name,
            subtitle = person.role,
            imageSize = metrics.sy(96f),
            onClick = { onPersonClick(person) },
            modifier = Modifier
                .focusRequester(rowFocus.requesterAt(index))
                .onFocusChanged { if (it.isFocused) onFocused(index, person) }
                .focusProperties {
                    up = upFocus()
                    if (index == 0) left = FocusRequester.Cancel
                }
        )
    }
}
