package app.picnic.player.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import app.picnic.player.ui.browse.BrowseHeroSkeleton
import app.picnic.player.ui.browse.browseLayoutMetrics
import app.picnic.player.ui.browse.posterCardStyle
import app.picnic.player.ui.common.SkeletonCardRow
import app.picnic.player.ui.grid.GridCardSkeleton

@Composable
internal fun DetailSkeleton() = BoxWithConstraints(Modifier.fillMaxSize()) {
    val metrics = browseLayoutMetrics(maxWidth, maxHeight)
    val cardStyle = posterCardStyle(sy = metrics.sy)
    BrowseHeroSkeleton(
        logoHeight = metrics.logoHeight,
        width = metrics.heroContentWidth,
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = metrics.hInset, bottom = metrics.sy(RESTING_ACTIONS_BOTTOM))
    )
    Box(
        Modifier
            .align(Alignment.BottomStart)
            .height(metrics.sy(RESTING_ROW_PEEK))
            .clipToBounds()
    ) {
        Box(Modifier.wrapContentHeight(Alignment.Top, unbounded = true)) {
            SkeletonCardRow(
                cardWidth = cardStyle.width,
                spacing = metrics.cardSpacing,
                startInset = metrics.hInset
            ) {
                GridCardSkeleton(cardStyle)
            }
        }
    }
}
