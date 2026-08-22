package app.picnic.player.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

suspend fun FocusRequester.requestFocusWhenAttached(maxFrames: Int = 10): Boolean {
    repeat(maxFrames) {
        if (runCatching { requestFocus() }.isSuccess) return true
        withFrameNanos { }
    }
    return false
}

@Composable
fun rememberSeededFocus(seed: Boolean, onSeeded: () -> Unit): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(seed) {
        if (!seed) return@LaunchedEffect
        requester.requestFocusWhenAttached()
        onSeeded()
    }
    return requester
}
