package app.picnic.player.ui.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.picnic.player.ui.browse.BrowseCardStyle
import app.picnic.player.ui.common.SkeletonBar
import app.picnic.player.ui.common.SkeletonBlockCorner
import app.picnic.player.ui.common.SkeletonTextBar

private val SkeletonCardTextGap = 6.dp
private const val CardTitleBarWidth = 0.8f
private const val CardSubtitleBarWidth = 0.45f

@Composable
internal fun GridCardSkeleton(style: BrowseCardStyle) {
    Column(
        Modifier.gridCellSlot(style).padding(top = style.topInset),
        verticalArrangement = Arrangement.spacedBy(SkeletonCardTextGap)
    ) {
        SkeletonBar(width = style.width, height = style.height, corner = SkeletonBlockCorner)
        SkeletonTextBar(width = style.width * CardTitleBarWidth)
        SkeletonTextBar(width = style.width * CardSubtitleBarWidth)
    }
}
