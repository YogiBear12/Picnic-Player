@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.picnic.player.ui.browse

import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.common.ActionButtonRow
import app.picnic.player.ui.common.PageAction
import app.picnic.player.ui.common.RowPageFocus
import app.picnic.player.ui.theme.PicnicColors

@Composable
internal fun DetailPageScaffold(
    focus: RowPageFocus,
    metrics: BrowseLayoutMetrics,
    heroRegionHeight: Dp,
    rowSpacing: Dp,
    restoring: Boolean,
    actions: List<PageAction>,
    notice: String?,
    hero: @Composable (Modifier) -> Unit,
    rows: LazyListScope.(BringIntoViewSpec) -> Unit
) {
    val buttonsToRowGap = (metrics.rowsViewportOffset + metrics.rowTitleHeight) / 3
    val pinSpec = with(LocalDensity.current) {
        remember(heroRegionHeight, buttonsToRowGap, restoring) {
            ScrollToTopBringIntoView((heroRegionHeight + buttonsToRowGap).toPx(), animated = !restoring)
        }
    }
    val horizontalRowSpec = LocalBringIntoViewSpec.current

    CompositionLocalProvider(LocalBringIntoViewSpec provides pinSpec) {
        LazyColumn(
            state = focus.listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = metrics.bottomInset),
            verticalArrangement = Arrangement.spacedBy(rowSpacing)
        ) {
            item(key = "hero") {
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(heroRegionHeight)) {
                        hero(
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(
                                    start = DetailContentStartInset,
                                    end = metrics.hInset,
                                    bottom = metrics.heroGap
                                )
                                .width(metrics.heroContentWidth)
                        )
                    }
                    ActionButtonRow(
                        actions = actions,
                        focus = focus.buttons,
                        onFocused = focus::onButtonFocused,
                        modifier = Modifier.padding(start = DetailContentStartInset, end = metrics.hInset)
                    )
                    notice?.let { message ->
                        Text(
                            message,
                            color = PicnicColors.Accent,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(
                                start = DetailContentStartInset,
                                end = metrics.hInset,
                                top = 12.dp
                            )
                        )
                    }
                    Spacer(Modifier.height(buttonsToRowGap))
                }
            }
            rows(horizontalRowSpec)
        }
    }
}
