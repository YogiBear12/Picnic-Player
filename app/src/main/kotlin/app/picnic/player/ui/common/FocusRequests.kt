package app.picnic.player.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester

suspend fun FocusRequester.requestFocusWhenAttached(maxFrames: Int = 10): Boolean {
    repeat(maxFrames) {
        if (runCatching { requestFocus() }.isSuccess) return true
        withFrameNanos { }
    }
    return false
}

@Stable
class FocusSeed internal constructor() {
    val requester = FocusRequester()
    internal var ready by mutableStateOf(false)
}

@Composable
fun rememberFocusSeed(key: Any?, enabled: Boolean): FocusSeed {
    val seed = remember { FocusSeed() }
    LaunchedEffect(key, enabled) {
        seed.ready = false
        if (!enabled) return@LaunchedEffect
        withFrameNanos { }
        seed.ready = true
        seed.requester.requestFocusWhenAttached()
    }
    return seed
}

fun Modifier.focusSeed(seed: FocusSeed): Modifier = focusProperties { canFocus = seed.ready }.focusRequester(seed.requester)

@Composable
fun rememberRowFocusRequesters(rowCount: Int): List<FocusRequester> = remember {
    mutableListOf<FocusRequester>()
}.growTo(rowCount) { FocusRequester() }

fun <T> MutableList<T>.growTo(count: Int, create: () -> T): List<T> {
    while (size < count) add(create())
    return this
}

@Composable
fun rememberOneShotFocus(seed: Boolean, onSeeded: () -> Unit): FocusRequester {
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
