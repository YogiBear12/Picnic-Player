package app.picnic.player.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.seerr.SeerrImages
import app.picnic.player.data.seerr.SeerrMediaStatus
import app.picnic.player.data.seerr.SeerrMediaType
import app.picnic.player.data.seerr.SeerrRequestDisplay
import app.picnic.player.data.seerr.SeerrRequestStatus
import app.picnic.player.data.seerr.formatRequestedSeasonsLabel
import app.picnic.player.data.seerr.resolvedMediaType
import app.picnic.player.ui.ambient.CardFocusBorderWidth
import app.picnic.player.ui.ambient.rememberCardFocusAccent
import app.picnic.player.ui.ambient.rememberCardFocusGlow
import app.picnic.player.ui.browse.BrowseCardStyle
import app.picnic.player.ui.common.ArtworkImage
import app.picnic.player.ui.common.ArtworkPlaceholder
import app.picnic.player.ui.common.PosterPlaceholderLabel
import app.picnic.player.ui.grid.gridCellHeight
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

private val LabelGap = 6.dp
private val LabelGapFocused = 14.dp

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
    var focused by remember { mutableStateOf(false) }
    val labelGap by animateDpAsState(
        if (focused) LabelGapFocused else LabelGap,
        label = "requestLabelGap"
    )
    val badge = requestBadge(row)
    val subtitle = requestSubtitle(row)
    val imageUrl = remember(row.posterPath, seerrBaseUrl, cacheImages) {
        SeerrImages.poster(seerrBaseUrl, row.posterPath, cacheImages)
    }
    val accentUrl = remember(row.posterPath, seerrBaseUrl, cacheImages) {
        SeerrImages.poster(seerrBaseUrl, row.posterPath, cacheImages, size = "w92")
    }
    val focusAccent = rememberCardFocusAccent(accentUrl)
    val focusedGlow = rememberCardFocusGlow(focusAccent.glowColor, focused)
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .requiredWidth(style.width)
            .height(gridCellHeight(style))
            .onFocusChanged { focused = it.hasFocus }
    ) {
        var cardModifier = Modifier
            .width(style.width)
            .height(style.height)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
        if (focusRequester != null) cardModifier = cardModifier.focusRequester(focusRequester)
        cardModifier = cardModifier.focusProperties {
            upFocus?.let { up = it() }
            downFocus?.let { down = it() }
            leftFocus?.let { left = it }
            rightFocus?.let { right = it }
        }

        Card(
            onClick = onClick,
            shape = CardDefaults.shape(shape),
            colors = CardDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            ),
            scale = CardDefaults.scale(focusedScale = 1.1f),
            border = CardDefaults.border(
                border = Border(BorderStroke(0.dp, Color.Transparent), shape = shape),
                focusedBorder = Border(
                    BorderStroke(CardFocusBorderWidth, focusAccent.borderColor),
                    shape = shape
                )
            ),
            glow = CardDefaults.glow(focusedGlow = focusedGlow),
            modifier = cardModifier
        ) {
            Box(Modifier.fillMaxSize()) {
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
                        .padding(6.dp)
                        .size(12.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .padding(2.dp)
                        .background(badge.color, CircleShape)
                        .semantics { contentDescription = badge.label }
                )
            }
        }
        Spacer(Modifier.height(labelGap))
        Text(
            text = row.title,
            style = MaterialTheme.typography.labelMedium,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = subtitle ?: badge.label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.55f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
