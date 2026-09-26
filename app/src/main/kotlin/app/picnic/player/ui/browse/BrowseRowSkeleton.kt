package app.picnic.player.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.common.SkeletonBar
import app.picnic.player.ui.common.SkeletonCardRow
import app.picnic.player.ui.common.SkeletonTextCorner
import app.picnic.player.ui.common.SkeletonTitleHeight
import app.picnic.player.ui.common.rememberSkeletonPulse
import app.picnic.player.ui.common.skeletonPulse

private const val TitleBarCards = 2
private const val UntitledSkeletonRowCount = 3

/** [key] matches the key of the row that replaces it, so the swap happens in place. */
@Immutable
internal data class SkeletonRow(val key: String, val title: String?, val landscape: Boolean)

internal fun untitledSkeletonRows(landscapeFirst: Boolean): List<SkeletonRow> = List(UntitledSkeletonRowCount) { index ->
    SkeletonRow(key = "skeleton-$index", title = null, landscape = landscapeFirst && index == 0)
}

@Composable
internal fun BrowseRowSkeleton(
    row: SkeletonRow,
    style: BrowseCardStyle,
    hInset: Dp,
    spacing: Dp,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Box(Modifier.padding(start = hInset, bottom = RowTitleBottomGap), contentAlignment = Alignment.CenterStart) {
            Text(
                text = row.title.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
            if (row.title == null) {
                SkeletonBar(
                    width = style.width * TitleBarCards,
                    height = SkeletonTitleHeight,
                    corner = SkeletonTextCorner,
                    modifier = Modifier.skeletonPulse(rememberSkeletonPulse())
                )
            }
        }
        SkeletonCardRow(cardWidth = style.width, spacing = spacing, startInset = hInset) {
            Box(Modifier.width(style.width).height(style.slotHeight), contentAlignment = Alignment.TopCenter) {
                SkeletonBar(
                    width = style.width,
                    height = style.height,
                    corner = CardCornerRadius,
                    modifier = Modifier.padding(top = style.topInset)
                )
            }
        }
    }
}
