@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)

package app.picnic.player.ui.grid

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.auth.UserSession
import app.picnic.player.ui.browse.BrowseCardStyle
import app.picnic.player.ui.browse.BrowsePosterCard
import org.jellyfin.sdk.model.api.BaseItemDto

private val GridLabelGap = 6.dp
private val GridLabelGapFocused = 14.dp

/** Reserved height for title + year so every cell is the same size (stable scroll math). */
private val GridLabelReserve = 38.dp

/** Total cell height — used by both the card and the loading placeholder so rows never jump. */
internal fun gridCellHeight(style: BrowseCardStyle): Dp = style.height + GridLabelGap + GridLabelReserve

/**
 * Grid poster card with title + year. Poster reuses [BrowsePosterCard] (shared focus
 * chrome — the TV Card scales itself on focus). The labels shift down on focus via an animated
 * gap (padding), NOT a graphicsLayer — a per-card render layer is compositing overhead that
 * janks the grid scroll once many cards are on screen. Title marquee-scrolls only while focused.
 */
@Composable
internal fun MediaGridCard(
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
    overrideImageUrl: String? = null,
    titleOverride: String? = null,
    subtitleOverride: String? = null
) {
    var focused by remember { mutableStateOf(false) }
    val labelGap by animateDpAsState(
        if (focused) GridLabelGapFocused else GridLabelGap,
        label = "gridLabelGap"
    )
    val title = titleOverride ?: item.name.orEmpty()
    val subtitle = subtitleOverride ?: gridSubtitle(item)

    Column(
        // requiredWidth: LazyVerticalGrid measures items at the CELL width (which can exceed
        // the poster when columns don't divide evenly) — the labels then outgrow the poster.
        // Forcing the card's own width keeps poster and labels aligned; the grid centres the
        // card in its cell.
        modifier = modifier
            .requiredWidth(style.width)
            .height(gridCellHeight(style))
            .onFocusChanged { focused = it.hasFocus }
    ) {
        BrowsePosterCard(
            item = item,
            session = session,
            style = style,
            focusRequester = focusRequester,
            upFocus = upFocus,
            leftFocus = leftFocus,
            onClick = onClick,
            onLongClick = onLongClick,
            onFocused = onFocused,
            overrideImageUrl = overrideImageUrl
        )
        Spacer(Modifier.height(labelGap))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focused) Modifier.basicMarquee() else Modifier)
        )
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

/** Default second label line: series show a season count, everything else its year. */
private fun gridSubtitle(item: BaseItemDto): String? = when (item.type) {
    org.jellyfin.sdk.model.api.BaseItemKind.SERIES ->
        item.childCount?.let { seasons -> if (seasons == 1) "1 season" else "$seasons seasons" }
            ?: item.productionYear?.toString()
    else -> item.productionYear?.toString()
}
