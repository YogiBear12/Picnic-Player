@file:OptIn(ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.browse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Movie
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.jellyfin.JellyfinImages
import app.picnic.player.ui.ambient.CardFocusBorderWidth
import app.picnic.player.ui.ambient.LocalCapBadgeCount
import app.picnic.player.ui.ambient.rememberCardFocusAccent
import app.picnic.player.ui.ambient.rememberCardFocusGlow
import app.picnic.player.ui.common.ArtworkImage
import app.picnic.player.ui.common.ArtworkLogTag
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Poster (or landscape) card with the shared Picnic focus chrome — border + glow accent
 * pulled from the artwork palette, scale 1.1 on focus. Sized exactly [BrowseCardStyle.width]
 * x [BrowseCardStyle.height]; the focus scale/glow overflow the bounds and must be given
 * breathing room by the caller (a slot box for rows, row spacing for the grid).
 */
@Composable
internal fun BrowsePosterCard(
    item: BaseItemDto,
    session: UserSession,
    style: BrowseCardStyle,
    focusRequester: FocusRequester?,
    upFocus: FocusRequester?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    leftFocus: FocusRequester? = null,
    overrideImageUrl: String? = null
) {
    // Request artwork at the card's pixel width (server-resized) so the hardware bitmaps stay
    // small — large posters blow GPU memory in a big grid and posters go blank.
    val widthPx = with(LocalDensity.current) { style.width.roundToPx() }
    // Callers with their own image policy (search's episode stills) pass [overrideImageUrl];
    // it also suppresses the logo overlay — an override caller labels the card itself.
    val thumbUrl = if (style.landscape && overrideImageUrl == null) {
        JellyfinImages.thumb(session, item, fillWidth = widthPx)
    } else {
        null
    }
    val imageUrl = overrideImageUrl ?: cardArtworkUrl(session, item, style.landscape, widthPx)
    val showOverlay = style.landscape && overrideImageUrl == null && thumbUrl == null
    val progress = (item.userData?.playedPercentage ?: 0.0).toFloat() / 100f
    val shape = RoundedCornerShape(12.dp)
    // Extract the palette as the card composes so the accent is ready when focus lands — no white
    // flash. The same artwork at accent size: a fraction of the displayed image's bytes, and a URL
    // of its own so the two fetches can never contend over one cache entry.
    val accentUrl = overrideImageUrl ?: cardArtworkUrl(session, item, style.landscape, AccentSourceWidth)
    val focusAccent = rememberCardFocusAccent(accentUrl)
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
        onLongClick = onLongClick,
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
            // Placeholder is an UNDERLAY, always composed: visible while the image loads,
            // through every retry, and permanently when there is no artwork. The loaded
            // image is opaque and simply covers it — the card is never blank.
            PosterPlaceholder(item.name)
            if (imageUrl == null) {
                // Diagnostic: a permanently blank card with artwork visible on the server
                // means URL RESOLUTION failed, not loading — log what tags the item carried.
                androidx.compose.runtime.LaunchedEffect(item.id) {
                    android.util.Log.w(
                        ArtworkLogTag,
                        "no image URL resolved: item='${item.name}' type=${item.type} id=${item.id} " +
                            "landscape=${style.landscape} imageTags=${item.imageTags?.keys} " +
                            "seriesId=${item.seriesId} seriesPrimaryTag=${item.seriesPrimaryImageTag}"
                    )
                }
            } else {
                ArtworkImage(
                    url = imageUrl,
                    contentDescription = item.name,
                    label = "item='${item.name}' type=${item.type}",
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (showOverlay) {
                val logoUrl = JellyfinImages.logo(session, item, fillWidth = 220)
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.85f)
                            )
                        )
                        .padding(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 8.dp)
                ) {
                    if (logoUrl != null) {
                        ArtworkImage(
                            url = logoUrl,
                            contentDescription = item.seriesName ?: item.name,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.BottomStart,
                            label = "logo item='${item.name}'",
                            modifier = Modifier.height(style.height * 0.35f)
                        )
                    } else {
                        Text(
                            text = item.seriesName ?: item.name.orEmpty(),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 2
                        )
                    }
                }
            }

            if (progress > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 8.dp, end = 8.dp, bottom = 6.dp)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.Black.copy(alpha = 0.50f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(Color.White)
                    )
                }
            }

            val unwatchedCount = item.userData?.unplayedItemCount ?: 0
            val played = item.userData?.played ?: false
            val remaining = minutesLeft(item)
            if (remaining != null && progress > 0f) {
                CardTimeLeftBadge(remaining)
            } else if (unwatchedCount > 0) {
                CardCountBadge(unwatchedCount)
            } else if (played && progress < 0.01f) {
                CardWatchedBadge()
            }
        }
    }
}

/** Source width for the focus-accent fetch — enough pixels to pick a colour, nothing more. */
private const val AccentSourceWidth = 48

/**
 * A card's artwork at [fillWidth] px: landscape cards prefer a thumb, then a backdrop, then the
 * poster; portrait cards take the row poster. Card-sized throughout — the full-size backdrop is
 * the hero's, not a card's. Resolving per width lets the accent read the same picture the card
 * shows without re-fetching the displayed image.
 */
private fun cardArtworkUrl(
    session: UserSession,
    item: BaseItemDto,
    landscape: Boolean,
    fillWidth: Int
): String? = if (landscape) {
    JellyfinImages.thumb(session, item, fillWidth = fillWidth)
        ?: JellyfinImages.backdrop(session, item, fillWidth = fillWidth)
        ?: JellyfinImages.primary(session, item, fillWidth = fillWidth)
} else {
    JellyfinImages.rowPoster(session, item, fillWidth = fillWidth)
}

/** Shown when an item has no artwork (or it fails to load) — icon + title, never a blank card. */
@Composable
private fun PosterPlaceholder(name: String?) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF232A31)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
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
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 3,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
internal fun BoxScope.CardWatchedBadge() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(5.dp)
            .size(20.dp)
            .background(Color.Black.copy(alpha = 0.70f), CircleShape)
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(12.dp)
        )
    }
}

/** "42m left" on an in-progress card — same chrome as the other corner badges. */
@Composable
internal fun BoxScope.CardTimeLeftBadge(minutes: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(5.dp)
            .background(Color.Black.copy(alpha = 0.70f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text = "${minutes}m left",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}

@Composable
internal fun BoxScope.CardCountBadge(count: Int) {
    val cap = LocalCapBadgeCount.current
    val label = if (cap && count > 99) "99+" else count.toString()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(5.dp)
            .background(Color.Black.copy(alpha = 0.70f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}

/**
 * Row variant: wraps [BrowsePosterCard] in a slot box that reserves vertical room for the
 * focus scale + glow, so a focused card never clips against neighbouring rows.
 */
@Composable
internal fun BrowseMediaCard(
    item: BaseItemDto,
    session: UserSession,
    style: BrowseCardStyle,
    focusRequester: FocusRequester?,
    upFocus: FocusRequester? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onFocused: () -> Unit
) {
    Box(
        Modifier.width(style.width).height(style.slotHeight),
        contentAlignment = Alignment.TopCenter
    ) {
        BrowsePosterCard(
            item = item,
            session = session,
            style = style,
            focusRequester = focusRequester,
            upFocus = upFocus,
            onClick = onClick,
            onLongClick = onLongClick,
            onFocused = onFocused,
            modifier = Modifier.padding(top = style.topInset)
        )
    }
}
