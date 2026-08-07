package app.picnic.player.ui.common

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.delay

private val SelectKeyCodes = setOf(
    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
    AndroidKeyEvent.KEYCODE_ENTER,
    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
)

private const val HoldSettleMs = 2_000L

@Stable
class LongPressGuard internal constructor() {
    internal var armed by mutableStateOf(false)
    internal var holdTicks by mutableIntStateOf(0)
}

@Composable
fun rememberLongPressGuard(): LongPressGuard {
    val guard = remember { LongPressGuard() }
    LaunchedEffect(guard.holdTicks) {
        delay(HoldSettleMs)
        guard.armed = true
    }
    return guard
}

fun Modifier.longPressGuard(guard: LongPressGuard): Modifier = onPreviewKeyEvent { event ->
    if (guard.armed || event.nativeKeyEvent.keyCode !in SelectKeyCodes) {
        return@onPreviewKeyEvent false
    }
    when (event.type) {
        KeyEventType.KeyUp -> guard.armed = true
        KeyEventType.KeyDown -> guard.holdTicks++
        else -> Unit
    }
    true
}
