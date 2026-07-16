@file:OptIn(ExperimentalFoundationApi::class)

package app.picnic.player.ui.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * Disables LazyColumn vertical bring-into-view. Row alignment is handled explicitly
 * via [androidx.compose.foundation.lazy.LazyListState.animateScrollToItem] so every
 * row title lands at the same viewport top regardless of card height.
 */
internal object SuppressVerticalBringIntoView : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

/**
 * Pins the focused item's top a fixed distance ([spaceAbovePx]) below the viewport top. Drives
 * smooth, framework-managed vertical scrolling and lets geometric up/down focus search work
 * (the row above is scrolled in and composed on demand).
 */
class ScrollToTopBringIntoView(private val spaceAbovePx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - spaceAbovePx
}
