package app.picnic.player.ui.browse

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

internal const val BrowseTabSlideDurationMs = 280

internal fun AnimatedContentTransitionScope<BrowseDest>.browseTabSlideTransition(
    order: List<String>
): ContentTransform {
    val forward = order.indexOf(targetState.key) >= order.indexOf(initialState.key)
    return if (forward) {
        slideInHorizontally(animationSpec = tween(BrowseTabSlideDurationMs)) { it } togetherWith
            slideOutHorizontally(animationSpec = tween(BrowseTabSlideDurationMs)) { -it }
    } else {
        slideInHorizontally(animationSpec = tween(BrowseTabSlideDurationMs)) { -it } togetherWith
            slideOutHorizontally(animationSpec = tween(BrowseTabSlideDurationMs)) { it }
    }
}
