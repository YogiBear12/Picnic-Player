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

private const val StepMs = 50L
private const val RepeatIntervalMs = 140L
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
        while (currentCoroutineContext().isActive) {
            val next = (delayMs() + heldDirection * StepMs).coerceIn(-DelayLimitMs, DelayLimitMs)
            if (next != delayMs()) onDelayChange(next)
            delay(RepeatIntervalMs)
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
        if (active) adjust.focusRequester.requestFocus()
    }
    return adjust
}
