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
import app.picnic.player.data.jellyfin.JellyfinImages
import app.picnic.player.ui.ambient.CardFocusBorderWidth
import app.picnic.player.ui.ambient.LocalCapBadgeCount
import app.picnic.player.ui.ambient.cardFocusGlow
import app.picnic.player.ui.ambient.rememberCardFocusAccent
import app.picnic.player.ui.ambient.rememberCardFocusGlow
import app.picnic.player.ui.common.ArtworkImage
import app.picnic.player.ui.common.ArtworkLogTag
import app.picnic.player.ui.common.ArtworkPlaceholder
import app.picnic.player.ui.common.ImageUrls
import app.picnic.player.ui.common.LocalImageUrls
import app.picnic.player.ui.common.LogoOrFallback
import app.picnic.player.ui.common.PosterPlaceholderLabel
import app.picnic.player.ui.common.watchProgress
import org.jellyfin.sdk.model.api.BaseItemDto

internal val CardCornerRadius = 12.dp
private const val CardFocusedScale = 1.1f

@Composable
internal fun BrowsePosterCard(
    item: BaseItemDto,
    style: BrowseCardStyle,
    focusRequester: FocusRequester?,
    upFocus: (() -> FocusRequester)?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    leftFocus: FocusRequester? = null,
    downFocus: (() -> FocusRequester)? = null,
    rightFocus: FocusRequester? = null,
    overrideImageUrl: String? = null,
    showStatus: Boolean = true
) {
    val images = LocalImageUrls.current
    val widthPx = with(LocalDensity.current) { style.width.roundToPx() }
    val thumbUrl = if (style.landscape && overrideImageUrl == null) {
        images.thumb(item, fillWidth = widthPx)
    } else {
        null
    }
    val imageUrl = overrideImageUrl ?: cardArtworkUrl(images, item, style.landscape, widthPx)
    val showOverlay = style.landscape && overrideImageUrl == null && thumbUrl == null
    val progress = item.watchProgress()
    val accentUrl = overrideImageUrl ?: cardArtworkUrl(images, item, style.landscape, AccentSourceWidth)
    val accentBlurHash = if (overrideImageUrl == null) cardArtworkBlurHash(item, style.landscape) else null

    PosterCardFrame(
        style = style,
        accentUrl = accentUrl,
        accentBlurHash = accentBlurHash,
        focusRequester = focusRequester,
        upFocus = upFocus,
        downFocus = downFocus,
        leftFocus = leftFocus,
        rightFocus = rightFocus,
        onClick = onClick,
        onLongClick = onLongClick,
        onFocused = onFocused,
        modifier = modifier
    ) {
        var artworkFailed by remember(imageUrl) { mutableStateOf(false) }
        ArtworkPlaceholder()
        if (imageUrl == null) {
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
                onSettled = { failed -> artworkFailed = failed },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (imageUrl == null || artworkFailed) {
            PosterPlaceholderLabel(item.name)
        }

        if (showOverlay) {
            val logoUrl = images.logo(item, fillWidth = 220)
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
                LogoOrFallback(
                    url = logoUrl,
                    contentDescription = item.seriesName ?: item.name,
                    alignment = Alignment.BottomStart,
                    label = "logo item='${item.name}'",
                    modifier = Modifier.height(style.height * 0.35f)
                ) {
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

        if (showStatus && progress > 0f) {
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

        if (showStatus) CardBadge(item, progress)
    }
}

@Composable
internal fun PosterCardFrame(
    style: BrowseCardStyle,
    accentUrl: String?,
    accentBlurHash: String?,
    focusRequester: FocusRequester?,
    upFocus: (() -> FocusRequester)?,
    downFocus: (() -> FocusRequester)?,
    leftFocus: FocusRequester?,
    rightFocus: FocusRequester?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val focusAccent = rememberCardFocusAccent(accentUrl, accentBlurHash)
    val shape = RoundedCornerShape(CardCornerRadius)
    val focusGlow = rememberCardFocusGlow(focusAccent.glowColor, shape, CardFocusedScale)

    var cardModifier = modifier
        .width(style.width)
        .height(style.height)
        .onFocusChanged { if (it.isFocused) onFocused() }
    if (focusRequester != null) cardModifier = cardModifier.focusRequester(focusRequester)
    cardModifier = cardModifier.focusProperties {
        upFocus?.let { up = it() }
        downFocus?.let { down = it() }
        leftFocus?.let { left = it }
        rightFocus?.let { right = it }
    }
    cardModifier = cardModifier.cardFocusGlow(focusGlow)

    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = CardDefaults.shape(shape),
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        scale = CardDefaults.scale(focusedScale = focusGlow.focusedScale),
        border = CardDefaults.border(
            border = Border(BorderStroke(0.dp, Color.Transparent), shape = shape),
            focusedBorder = Border(
                BorderStroke(CardFocusBorderWidth, focusAccent.borderColor),
                shape = shape
            )
        ),
        interactionSource = focusGlow.interactionSource,
        modifier = cardModifier,
        content = { Box(Modifier.fillMaxSize(), content = content) }
    )
}

private const val AccentSourceWidth = 48

private fun cardArtworkUrl(
    images: ImageUrls,
    item: BaseItemDto,
    landscape: Boolean,
    fillWidth: Int
): String? = if (landscape) {
    images.thumb(item, fillWidth = fillWidth)
        ?: images.backdrop(item, fillWidth = fillWidth)
        ?: images.primary(item, fillWidth = fillWidth)
} else {
    images.rowPoster(item, fillWidth = fillWidth)
}

private fun cardArtworkBlurHash(item: BaseItemDto, landscape: Boolean): String? = if (landscape) {
    JellyfinImages.thumbBlurHash(item)
        ?: JellyfinImages.backdropBlurHash(item)
        ?: JellyfinImages.rowPosterBlurHash(item)
} else {
    JellyfinImages.rowPosterBlurHash(item)
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
private fun BoxScope.CardBadge(item: BaseItemDto, progress: Float) {
    val unwatchedCount = item.userData?.unplayedItemCount ?: 0
    val versionCount = item.mediaSourceCount ?: 0
    val played = item.userData?.played ?: false
    val remaining = minutesLeft(item)
    when {
        remaining != null && progress > 0f -> CardTimeLeftBadge(remaining)
        unwatchedCount > 0 -> CardCountBadge(unwatchedCount)
        played && progress < 0.01f -> CardWatchedBadge()
        versionCount > 1 -> CardCountBadge(versionCount)
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

@Composable
internal fun BrowseMediaCard(
    item: BaseItemDto,
    style: BrowseCardStyle,
    focusRequester: FocusRequester?,
    upFocus: (() -> FocusRequester)? = null,
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
