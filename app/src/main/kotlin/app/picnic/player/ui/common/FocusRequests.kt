package app.picnic.player.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
fun rememberRowFocusRequesters(rowCount: Int): List<FocusRequester> = remember {
    mutableListOf<FocusRequester>()
}.growTo(rowCount) { FocusRequester() }

fun <T> MutableList<T>.growTo(count: Int, create: () -> T): List<T> {
    while (size < count) add(create())
    return this
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

@Stable
class KeyedFocusRequesters {
    private val requesters = mutableMapOf<String, FocusRequester>()

    operator fun get(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }
}

@Composable
fun rememberKeyedFocusRequesters(): KeyedFocusRequesters = remember { KeyedFocusRequesters() }
