@file:OptIn(ExperimentalFoundationApi::class)

package app.picnic.player.ui.browse

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

class ScrollToTopBringIntoView(
    private val spaceAbovePx: Float,
    private val animated: Boolean = true
) : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float>
        get() = if (animated) super.scrollAnimationSpec else snap()

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - spaceAbovePx
}
