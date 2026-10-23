@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.picnic.player.ui.detail

import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.relocation.BringIntoViewModifierNode
import androidx.compose.ui.relocation.bringIntoView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.browse.BrowseLayoutMetrics
import app.picnic.player.ui.browse.ScrollToTopBringIntoView
import app.picnic.player.ui.browse.posterCardStyle
import app.picnic.player.ui.common.ActionButtonRow
import app.picnic.player.ui.common.PageAction
import app.picnic.player.ui.common.RowPageFocus
import app.picnic.player.ui.grid.gridCellHeight
import app.picnic.player.ui.theme.PicnicColors

// Design-canvas units above the bottom edge at rest; derivation in docs/guides/browse-backdrop.md.
internal const val RESTING_ACTIONS_BOTTOM = 112f
internal const val RESTING_ROW_PEEK = 41f

// Design-canvas units of the next row left showing when a poster row is focused; derivation in docs/guides/browse-backdrop.md.
internal const val FOCUSED_ROW_PEEK = 50f

@Composable
internal fun DetailFullscreenScaffold(
    focus: RowPageFocus,
    metrics: BrowseLayoutMetrics,
    screenHeight: Dp,
    restoring: Boolean,
    actions: List<PageAction>,
    notice: String?,
    hero: @Composable (Modifier) -> Unit,
    rows: LazyListScope.(BringIntoViewSpec, Dp) -> Unit
) {
    val rowSpacing = metrics.rowSpacing + metrics.sy(12f)
    val rowPinTop = screenHeight - metrics.sy(FOCUSED_ROW_PEEK) - gridCellHeight(posterCardStyle(metrics.sy)) - rowSpacing
    val pinSpec = with(LocalDensity.current) {
        remember(rowPinTop, restoring) { ScrollToTopBringIntoView(rowPinTop.toPx(), animated = !restoring) }
    }
    val horizontalRowSpec = LocalBringIntoViewSpec.current
    val viewportHeightPx = with(LocalDensity.current) { screenHeight.toPx() }
    val heroMinHeight = screenHeight - metrics.sy(RESTING_ROW_PEEK) - rowSpacing
    val heroBottomGap = (metrics.sy(RESTING_ACTIONS_BOTTOM - RESTING_ROW_PEEK) - rowSpacing).coerceAtLeast(0.dp)

    CompositionLocalProvider(LocalBringIntoViewSpec provides pinSpec) {
        LazyColumn(
            state = focus.listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = metrics.bottomInset),
            verticalArrangement = Arrangement.spacedBy(rowSpacing)
        ) {
            item(key = "hero") {
                // heightIn, not height: a taller hero (font scale, notice) grows the item so its top stays reachable.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = heroMinHeight)
                        .then(HeroTopBringIntoViewElement(viewportHeightPx))
                        .padding(start = metrics.hInset, end = metrics.hInset, bottom = heroBottomGap),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    hero(Modifier.width(metrics.heroContentWidth))
                    Spacer(Modifier.height(metrics.heroGap))
                    ActionButtonRow(
                        actions = actions,
                        focus = focus.buttons,
                        onFocused = focus::onButtonFocused
                    )
                    notice?.let { message ->
                        Text(
                            message,
                            color = PicnicColors.Accent,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }
            rows(horizontalRowSpec, metrics.hInset)
        }
    }
}

// The resting hero sits below the row pin, so a focus request inside it is widened to the
// item's top and the pin turns that into "scroll to 0". A hero taller than the viewport keeps
// the child's rect so its actions stay reachable. See docs/guides/compose-tv-traps.md.
private data class HeroTopBringIntoViewElement(val viewportHeightPx: Float) : ModifierNodeElement<HeroTopBringIntoViewNode>() {
    override fun create() = HeroTopBringIntoViewNode(viewportHeightPx)

    override fun update(node: HeroTopBringIntoViewNode) {
        node.viewportHeightPx = viewportHeightPx
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "heroTopBringIntoView"
    }
}

private class HeroTopBringIntoViewNode(var viewportHeightPx: Float) :
    Modifier.Node(),
    BringIntoViewModifierNode {
    override suspend fun bringIntoView(childCoordinates: LayoutCoordinates, boundsProvider: () -> Rect?) {
        bringIntoView {
            if (!isAttached || !childCoordinates.isAttached) return@bringIntoView null
            val coordinates = requireLayoutCoordinates()
            val bounds = boundsProvider()
            val rect = if (bounds == null) {
                coordinates.localBoundingBoxOf(childCoordinates, clipBounds = false)
            } else {
                Rect(
                    coordinates.localPositionOf(childCoordinates, bounds.topLeft),
                    coordinates.localPositionOf(childCoordinates, bounds.bottomRight)
                )
            }
            if (rect.bottom <= viewportHeightPx) rect.copy(top = 0f) else rect
        }
    }
}
