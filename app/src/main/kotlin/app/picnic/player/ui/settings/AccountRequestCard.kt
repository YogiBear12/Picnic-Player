package app.picnic.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.picnic.player.data.seerr.SeerrImages
import app.picnic.player.data.seerr.SeerrMediaStatus
import app.picnic.player.data.seerr.SeerrMediaType
import app.picnic.player.data.seerr.SeerrRequestDisplay
import app.picnic.player.data.seerr.SeerrRequestStatus
import app.picnic.player.data.seerr.formatRequestedSeasonsLabel
import app.picnic.player.data.seerr.resolvedMediaType
import app.picnic.player.ui.browse.BrowseCardStyle
import app.picnic.player.ui.browse.PosterCardFrame
import app.picnic.player.ui.common.ArtworkImage
import app.picnic.player.ui.common.ArtworkPlaceholder
import app.picnic.player.ui.common.PosterPlaceholderLabel
import app.picnic.player.ui.grid.MediaCardCell
import app.picnic.player.ui.theme.PicnicColors

internal enum class RequestBadge(val color: Color, val label: String) {
    PENDING(PicnicColors.Warning, "Pending"),
    AVAILABLE(PicnicColors.Success, "Available"),
    REJECTED(PicnicColors.Error, "Not available")
}

internal fun requestBadge(row: SeerrRequestDisplay): RequestBadge = when (row.request.status) {
    SeerrRequestStatus.DECLINED, SeerrRequestStatus.FAILED -> RequestBadge.REJECTED
    else -> when (row.request.media?.status) {
        SeerrMediaStatus.AVAILABLE, SeerrMediaStatus.PARTIALLY_AVAILABLE -> RequestBadge.AVAILABLE
        else -> RequestBadge.PENDING
    }
}

internal fun requestSubtitle(row: SeerrRequestDisplay): String? = if (row.request.resolvedMediaType() == SeerrMediaType.TV) {
    formatRequestedSeasonsLabel(row.request.seasons.mapNotNull { it.seasonNumber })
        ?: row.yearLabel
} else {
    row.yearLabel
}

private val BadgeDotSize = 12.dp
private val BadgeDotInset = 6.dp
private val BadgeDotRing = 2.dp
private val BadgeDotBacking = Color.Black.copy(alpha = 0.55f)

@Composable
internal fun AccountRequestCard(
    row: SeerrRequestDisplay,
    seerrBaseUrl: String?,
    cacheImages: Boolean,
    style: BrowseCardStyle,
    focusRequester: FocusRequester?,
    upFocus: (() -> FocusRequester)?,
    downFocus: (() -> FocusRequester)?,
    leftFocus: FocusRequester?,
    rightFocus: FocusRequester? = null,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val badge = requestBadge(row)
    val imageUrl = remember(row.posterPath, seerrBaseUrl, cacheImages) {
        SeerrImages.poster(seerrBaseUrl, row.posterPath, cacheImages)
    }
    val accentUrl = remember(row.posterPath, seerrBaseUrl, cacheImages) {
        SeerrImages.poster(seerrBaseUrl, row.posterPath, cacheImages, size = "w92")
    }

    MediaCardCell(
        style = style,
        title = row.title,
        subtitle = requestSubtitle(row) ?: badge.label,
        modifier = modifier
    ) {
        PosterCardFrame(
            style = style,
            accentUrl = accentUrl,
            accentBlurHash = null,
            focusRequester = focusRequester,
            upFocus = upFocus,
            downFocus = downFocus,
            leftFocus = leftFocus,
            rightFocus = rightFocus,
            onClick = onClick,
            onLongClick = null,
            onFocused = onFocused
        ) {
            var artworkFailed by remember(imageUrl) { mutableStateOf(false) }
            ArtworkPlaceholder()
            if (imageUrl != null) {
                ArtworkImage(
                    url = imageUrl,
                    contentDescription = row.title,
                    label = "seerr request '${row.title}'",
                    onSettled = { failed -> artworkFailed = failed },
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (imageUrl == null || artworkFailed) {
                PosterPlaceholderLabel(row.title)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(BadgeDotInset)
                    .size(BadgeDotSize)
                    .background(BadgeDotBacking, CircleShape)
                    .padding(BadgeDotRing)
                    .background(badge.color, CircleShape)
                    .semantics { contentDescription = badge.label }
            )
        }
    }
}
