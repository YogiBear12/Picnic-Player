package app.picnic.player.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val CoarseAfterMs = 600L
private const val FineStepMs = 100L
private const val CoarseStepMs = 1_000L
private const val FineIntervalMs = 140L
private const val CoarseIntervalMs = 90L
private const val DelayLimitMs = 60_000L

@Stable
class SubtitleDelayAdjust {
    val focusRequester = FocusRequester()

    var heldDirection by mutableIntStateOf(0)
        private set

    fun holdEarlier() {
        heldDirection = -1
    }

    fun holdLater() {
        heldDirection = 1
    }

    fun release() {
        heldDirection = 0
    }

    internal suspend fun ramp(delayMs: () -> Long, onDelayChange: (Long) -> Unit) {
        if (heldDirection == 0) return
        var elapsed = 0L
        while (currentCoroutineContext().isActive) {
            val coarse = elapsed >= CoarseAfterMs
            val step = if (coarse) CoarseStepMs else FineStepMs
            val next = (delayMs() + heldDirection * step).coerceIn(-DelayLimitMs, DelayLimitMs)
            if (next != delayMs()) onDelayChange(next)
            val wait = if (coarse) CoarseIntervalMs else FineIntervalMs
            delay(wait)
            elapsed += wait
        }
    }
}

@Composable
fun rememberSubtitleDelayAdjust(
    active: Boolean,
    delayMs: Long,
    onDelayChange: (Long) -> Unit
): SubtitleDelayAdjust {
    val adjust = remember { SubtitleDelayAdjust() }
    val currentDelayMs = rememberUpdatedState(delayMs)
    val currentOnDelayChange = rememberUpdatedState(onDelayChange)

    LaunchedEffect(adjust.heldDirection) {
        adjust.ramp({ currentDelayMs.value }, { currentOnDelayChange.value(it) })
    }
    LaunchedEffect(active) {
        if (active) runCatching { adjust.focusRequester.requestFocus() }
    }
    return adjust
}
