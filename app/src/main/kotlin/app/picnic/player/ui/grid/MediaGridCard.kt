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
import app.picnic.player.text.countLabel
import app.picnic.player.ui.browse.BrowseCardStyle
import app.picnic.player.ui.browse.BrowsePosterCard
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

private val GridLabelGap = 6.dp
private val GridLabelGapFocused = 14.dp

private val GridLabelReserve = 38.dp

internal fun gridCellHeight(style: BrowseCardStyle): Dp = style.height + GridLabelGap + GridLabelReserve

internal fun Modifier.gridCellSlot(style: BrowseCardStyle): Modifier = width(style.width).height(style.topInset + gridCellHeight(style))

@Composable
internal fun MediaGridCard(
    item: BaseItemDto,
    style: BrowseCardStyle,
    focusRequester: FocusRequester?,
    upFocus: (() -> FocusRequester)?,
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
    val title = titleOverride ?: gridTitle(item)
    val subtitle = subtitleOverride ?: gridSubtitle(item)

    Column(
        modifier = modifier
            .requiredWidth(style.width)
            .height(gridCellHeight(style))
            .onFocusChanged { focused = it.hasFocus }
    ) {
        BrowsePosterCard(
            item = item,
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

private fun gridTitle(item: BaseItemDto): String = when (item.type) {
    BaseItemKind.SEASON -> item.seriesName ?: item.name.orEmpty()
    else -> item.name.orEmpty()
}

private fun gridSubtitle(item: BaseItemDto): String? = when (item.type) {
    BaseItemKind.SERIES -> item.childCount?.let { countLabel(it, "season") } ?: item.productionYear?.toString()
    BaseItemKind.SEASON -> item.name?.takeIf { it.isNotBlank() } ?: item.indexNumber?.let { "Season $it" }
    else -> item.productionYear?.toString()
}
