package app.picnic.player.ui.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import app.picnic.player.ui.common.LoadingFocusHolder
import app.picnic.player.ui.common.LoadingFocusTarget
import app.picnic.player.ui.common.rememberLoadingFocusHolder
import app.picnic.player.ui.common.requestFocusWhenAttached
import kotlinx.coroutines.delay

private const val DETAIL_SKELETON_DELAY_MS = 300L
private const val DETAIL_CONTENT_FADE_MS = 500

/** How far a card-origin open has got; null on every other open. */
enum class CardOpenPhase { Growing, Settled }

@Stable
internal data class CardOpenReveal(
    private val fade: Animatable<Float, AnimationVector1D>,
    val skeletonShown: Boolean,
    val focusHold: LoadingFocusHolder?
) {
    fun alpha(): Float = fade.value
}

@Composable
internal fun rememberCardOpenReveal(cardOpen: CardOpenPhase?, loading: Boolean): CardOpenReveal {
    val contentReady = cardOpen != CardOpenPhase.Growing && !loading
    var skeletonShown by remember { mutableStateOf(false) }
    LaunchedEffect(cardOpen, loading) {
        if (cardOpen == CardOpenPhase.Settled && loading) {
            delay(DETAIL_SKELETON_DELAY_MS)
            skeletonShown = true
        }
    }
    val fade = remember { Animatable(if (cardOpen == null) 1f else 0f) }
    LaunchedEffect(contentReady) {
        if (!contentReady) return@LaunchedEffect
        if (skeletonShown) {
            fade.snapTo(1f)
        } else {
            fade.animateTo(1f, tween(DETAIL_CONTENT_FADE_MS, easing = EaseOut))
        }
    }
    val loadingFocus = rememberLoadingFocusHolder()
    val holdFocus = cardOpen != null && loadingFocus.holds(!contentReady)
    LaunchedEffect(holdFocus) {
        if (holdFocus) loadingFocus.requester.requestFocusWhenAttached()
    }
    return CardOpenReveal(fade, skeletonShown, loadingFocus.takeIf { holdFocus })
}

@Composable
internal fun CardOpenFocusHold(reveal: CardOpenReveal) {
    val holder = reveal.focusHold ?: return
    LoadingFocusTarget(
        holder,
        Modifier.focusProperties {
            left = FocusRequester.Cancel
            right = FocusRequester.Cancel
            up = FocusRequester.Cancel
            down = FocusRequester.Cancel
        }
    )
}
