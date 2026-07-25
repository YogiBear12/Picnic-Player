@file:OptIn(ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.seerr

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.seerr.SeerrCatalogItem
import app.picnic.player.data.seerr.SeerrImages
import app.picnic.player.data.seerr.personCreditDetailLine
import app.picnic.player.data.seerr.personCreditRoleLine
import app.picnic.player.ui.ambient.CardFocusBorderWidth
import app.picnic.player.ui.ambient.rememberCardFocusAccent
import app.picnic.player.ui.ambient.rememberCardFocusGlow
import app.picnic.player.ui.browse.BrowseCardStyle
import app.picnic.player.ui.common.ArtworkImage
import app.picnic.player.ui.grid.gridCellHeight

private val LabelGap = 6.dp
private val LabelGapFocused = 14.dp

/** Extra label reserve when Known-for shows role + year/episodes (third text line). */
private val PersonCreditLabelExtra = 14.dp

/**
 * Immersive-row Seerr poster: same focus chrome + slot headroom as [app.picnic.player.ui.browse.BrowseMediaCard].
 * No under-card labels — hero carries the title (Home immersive pattern).
 */
@Composable
internal fun SeerrMediaCard(
    item: SeerrCatalogItem,
    seerrBaseUrl: String?,
    cacheImages: Boolean,
    style: BrowseCardStyle,
    focusRequester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    Box(
        Modifier.width(style.width).height(style.slotHeight),
        contentAlignment = Alignment.TopCenter
    ) {
        SeerrPosterFace(
            item = item,
            seerrBaseUrl = seerrBaseUrl,
            cacheImages = cacheImages,
            style = style,
            focusRequester = focusRequester,
            onFocused = onFocused,
            onClick = onClick,
            modifier = Modifier.padding(top = style.topInset)
        )
    }
}

/**
 * Search/grid Seerr card: poster + title/subtitle under, matching [app.picnic.player.ui.grid.MediaGridCard].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SeerrLabeledCard(
    item: SeerrCatalogItem,
    seerrBaseUrl: String?,
    cacheImages: Boolean,
    style: BrowseCardStyle,
    focusRequester: FocusRequester?,
    upFocus: FocusRequester? = null,
    leftFocus: FocusRequester? = null,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val labelGap by animateDpAsState(
        if (focused) LabelGapFocused else LabelGap,
        label = "seerrLabelGap"
    )
    val personMetaline = item.creditRole != null
    val roleLine = if (personMetaline) personCreditRoleLine(item.creditRole) else null
    val detailLine = if (personMetaline) {
        personCreditDetailLine(item.mediaType, item.releaseDate, item.episodeCount)
    } else {
        null
    }
    val subtitle = if (!personMetaline) {
        item.releaseDate?.take(4)?.takeIf { it.length == 4 }
    } else {
        null
    }
    val labelExtra = if (personMetaline) PersonCreditLabelExtra else 0.dp

    Column(
        modifier = modifier
            .requiredWidth(style.width)
            .height(gridCellHeight(style) + labelExtra)
            .onFocusChanged { focused = it.hasFocus }
    ) {
        SeerrPosterFace(
            item = item,
            seerrBaseUrl = seerrBaseUrl,
            cacheImages = cacheImages,
            style = style,
            focusRequester = focusRequester,
            upFocus = upFocus,
            leftFocus = leftFocus,
            onFocused = onFocused,
            onClick = onClick
        )
        Spacer(Modifier.height(labelGap))
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelMedium,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focused) Modifier.basicMarquee() else Modifier)
        )
        if (personMetaline) {
            roleLine?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            detailLine?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** Poster face only — shared focus chrome with [app.picnic.player.ui.browse.BrowsePosterCard]. */
@Composable
private fun SeerrPosterFace(
    item: SeerrCatalogItem,
    seerrBaseUrl: String?,
    cacheImages: Boolean,
    style: BrowseCardStyle,
    focusRequester: FocusRequester?,
    upFocus: FocusRequester? = null,
    leftFocus: FocusRequester? = null,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val imageUrl = SeerrImages.poster(seerrBaseUrl, item.posterPath, cacheImages)
    // Accent comes from the smallest poster size so prefetching one per card stays cheap, and so
    // its URL differs from the displayed poster's — a shared cache entry can be read mid-write.
    val accentUrl = SeerrImages.poster(seerrBaseUrl, item.posterPath, cacheImages, size = AccentPosterSize)
    val focusAccent = rememberCardFocusAccent(accentUrl)
    val shape = RoundedCornerShape(12.dp)
    var focused by remember { mutableStateOf(false) }
    val focusedGlow = rememberCardFocusGlow(focusAccent.glowColor, focused)

    var cardModifier = modifier
        .width(style.width)
        .height(style.height)
        .onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) onFocused()
        }
    if (focusRequester != null) cardModifier = cardModifier.focusRequester(focusRequester)
    if (upFocus != null) cardModifier = cardModifier.focusProperties { up = upFocus }
    if (leftFocus != null) cardModifier = cardModifier.focusProperties { left = leftFocus }

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
            // Same underlay pattern as BrowseMediaCard — never a blank/transparent card.
            SeerrPosterPlaceholder(item.title)
            if (imageUrl != null) {
                ArtworkImage(
                    url = imageUrl,
                    contentDescription = item.title,
                    label = "seerr='${item.title}'",
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Poster only — no media-status overlay (Available / Pending / …).
            // Availability is clear on Detail after OK.
        }
    }
}

/** Smallest TMDB poster rendition — plenty for picking a focus accent colour. */
private const val AccentPosterSize = "w92"

@Composable
private fun SeerrPosterPlaceholder(name: String?) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF232A31)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(32.dp)
            )
            if (!name.isNullOrBlank()) {
                Text(
                    text = name,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/** Total height when wrapping [SeerrLabeledCard] in a focus-scale slot (Search rows). */
internal fun seerrLabeledSlotHeight(
    style: BrowseCardStyle,
    personCreditMetaline: Boolean = false
): Dp = style.topInset + gridCellHeight(style) +
    if (personCreditMetaline) PersonCreditLabelExtra else 0.dp
