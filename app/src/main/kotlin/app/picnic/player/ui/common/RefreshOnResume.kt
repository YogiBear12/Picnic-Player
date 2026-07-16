package app.picnic.player.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Invokes [onResume] each time the composition's lifecycle re-enters RESUMED (e.g. app
 * foreground), skipping the very first RESUME when [skipInitial] (the initial load already
 * happened). Covers server-side changes this app didn't cause; intra-app changes are covered by
 * [app.picnic.player.data.media.LibraryChangeBus].
 */
@Composable
fun RefreshOnResume(skipInitial: Boolean = true, onResume: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val cb by rememberUpdatedState(onResume)
    DisposableEffect(owner) {
        var seenFirst = !skipInitial
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (seenFirst) cb() else seenFirst = true
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
