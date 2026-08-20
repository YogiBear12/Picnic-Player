@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package app.picnic.player.ui.detail

import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.jellyfin.JellyfinImages
import app.picnic.player.ui.browse.BrowseCardStyle
import app.picnic.player.ui.browse.BrowseHero
import app.picnic.player.ui.browse.BrowseLayoutMetrics
import app.picnic.player.ui.browse.DetailContentStartInset
import app.picnic.player.ui.browse.DetailMediaRow
import app.picnic.player.ui.common.CircularPersonCard
import app.picnic.player.ui.common.RowFocusState
import app.picnic.player.ui.theme.PicnicColors
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.MediaStream

@Immutable
internal data class DetailButtonActions(
    val onPlay: () -> Unit,
    val onEpisodes: () -> Unit,
    val onToggleWatched: () -> Unit,
    val onTrailers: () -> Unit,
    val onMore: () -> Unit
)

@Composable
internal fun DetailHero(
    item: BaseItemDto,
    session: UserSession,
    metrics: BrowseLayoutMetrics,
    heroRegionHeight: Dp,
    leadStreams: List<MediaStream>?,
    focus: DetailPageFocus,
    onSummaryClick: () -> Unit
) {
    // Same fixed-height block as Home, bottom-anchored heroGap above where the
    // rows start, so the logo/details line sit at the identical Y on both screens
    // (no shift navigating Home ↔ Detail). Summary length is type-driven inside
    // BrowseHero, matching Home.
    Box(Modifier.fillMaxWidth().height(heroRegionHeight)) {
        BrowseHero(
            item = item,
            session = session,
            logoHeight = metrics.logoHeight,
            continueWatching = false,
            streamsOverride = leadStreams,
            seasonCount = if (item.type == BaseItemKind.SERIES) item.childCount else null,
            onSummaryClick = onSummaryClick,
            summaryDown = { focus.lastFocusedButton },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    // Rail supplies the left gutter — align with the shell
                    // panes so Home → Detail keeps one horizontal origin.
                    start = DetailContentStartInset,
                    end = metrics.hInset,
                    bottom = metrics.heroGap
                )
                .width(metrics.heroContentWidth)
        )
    }
}

@Composable
internal fun DetailActionButtons(
    item: BaseItemDto,
    playTitle: String,
    trailerCount: Int,
    requestMoreError: String?,
    metrics: BrowseLayoutMetrics,
    focus: DetailPageFocus,
    actions: DetailButtonActions,
    buttonsToRowGap: Dp
) {
    val isSeries = item.type == BaseItemKind.SERIES
    // The overflow always shows now — it's the home for the Add-to actions
    // (favorites, playlist) plus media info / versions / request more.
    var nextButtonIndex = 0
    val playButtonIndex = nextButtonIndex++
    val episodesButtonIndex = if (isSeries) nextButtonIndex++ else -1
    val watchedButtonIndex = nextButtonIndex++
    val trailersButtonIndex = if (trailerCount > 0) nextButtonIndex++ else -1
    val moreButtonIndex = nextButtonIndex

    Row(
        modifier = Modifier
            .padding(
                start = DetailContentStartInset,
                end = metrics.hInset
            )
            .height(40.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        fun Modifier.actionButton(index: Int) = this
            .focusRequester(focus.buttons[index])
            .onFocusChanged { if (it.isFocused) focus.onButtonFocused(index) }

        ExpandableButton(
            title = playTitle,
            icon = Icons.Default.PlayArrow,
            onClick = actions.onPlay,
            modifier = Modifier.actionButton(playButtonIndex)
        )

        if (isSeries) {
            ExpandableButton(
                title = "Episodes",
                icon = Icons.Default.List,
                onClick = actions.onEpisodes,
                modifier = Modifier.actionButton(episodesButtonIndex)
            )
        }

        ExpandableButton(
            title = if (item.userData?.played == true) "Mark Unwatched" else "Mark Watched",
            icon = Icons.Default.Check,
            onClick = actions.onToggleWatched,
            modifier = Modifier.actionButton(watchedButtonIndex)
        )

        if (trailerCount > 0) {
            ExpandableButton(
                title = if (trailerCount == 1) "Play trailer" else "Trailers",
                icon = Icons.Default.Movie,
                onClick = actions.onTrailers,
                modifier = Modifier.actionButton(trailersButtonIndex)
            )
        }

        ExpandableButton(
            title = "More",
            icon = Icons.Default.MoreVert,
            onClick = actions.onMore,
            modifier = Modifier.actionButton(moreButtonIndex)
        )
    }

    requestMoreError?.let { err ->
        Text(
            err,
            color = PicnicColors.Accent,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(
                start = DetailContentStartInset,
                end = metrics.hInset,
                top = 12.dp
            )
        )
    }

    // Keeps the rows' resting position at page top aligned with where they
    // pin when focused (buttonsToRowGap below the pin line).
    Spacer(Modifier.height(buttonsToRowGap))
}

@Composable
internal fun DetailCastRow(
    people: List<BaseItemPerson>,
    session: UserSession,
    metrics: BrowseLayoutMetrics,
    horizontalRowSpec: BringIntoViewSpec,
    focus: DetailPageFocus,
    onPersonClick: (BaseItemPerson) -> Unit
) {
    DetailMediaRow(
        title = "Cast & crew",
        items = people,
        endInset = metrics.hInset,
        cardSpacing = metrics.cardSpacing,
        horizontalRowSpec = horizontalRowSpec,
        rowFocus = focus.cast.rowModifier()
    ) { index, person ->
        CircularPersonCard(
            imageUrl = JellyfinImages.personPrimary(
                session,
                person.id.toString(),
                person.primaryImageTag
            ),
            name = person.name,
            subtitle = person.role,
            imageSize = metrics.sy(96f),
            onClick = { onPersonClick(person) },
            modifier = Modifier
                .focusRequester(focus.cast.requesterAt(index))
                .onFocusChanged { if (it.isFocused) focus.onCastFocused(index) }
                // Up returns to the button we left from, not the
                // spatially nearest one (must sit on the card's own
                // focus node — a group-level `up` is not consulted
                // for searches leaving a child). First card also
                // blocks left so focus can't escape the row.
                .focusProperties {
                    up = focus.lastFocusedButton
                    if (index == 0) left = FocusRequester.Cancel
                }
        )
    }
}

@Composable
internal fun DetailPosterRow(
    title: String,
    items: List<BaseItemDto>,
    session: UserSession,
    metrics: BrowseLayoutMetrics,
    cardStyle: BrowseCardStyle,
    horizontalRowSpec: BringIntoViewSpec,
    rowFocus: RowFocusState,
    onClick: (BaseItemDto) -> Unit,
    onLongClick: (BaseItemDto) -> Unit,
    onFocused: (Int, BaseItemDto) -> Unit
) {
    DetailMediaRow(
        title = title,
        items = items,
        endInset = metrics.hInset,
        cardSpacing = metrics.cardSpacing,
        horizontalRowSpec = horizontalRowSpec,
        rowFocus = rowFocus.rowModifier(),
        key = { _, it -> it.id }
    ) { index, rowItem ->
        DetailRowCard(
            item = rowItem,
            session = session,
            style = cardStyle,
            focusRequester = rowFocus.requesters[index],
            firstInRow = index == 0,
            onClick = { onClick(rowItem) },
            onLongClick = { onLongClick(rowItem) },
            onFocused = { onFocused(index, rowItem) }
        )
    }
}
