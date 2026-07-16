package app.picnic.player.ui.common

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/**
 * Requests focus, retrying frame-aligned while the requester's node is still entering
 * composition ([FocusRequester.requestFocus] throws until it attaches — e.g. a freshly
 * scrolled-to lazy item or a panel mid slide-in). Bounded: gives up after [maxFrames]
 * frames instead of guessing with wall-clock delays.
 *
 * @return true when the request was dispatched to an attached node.
 */
suspend fun FocusRequester.requestFocusWhenAttached(maxFrames: Int = 10): Boolean {
    repeat(maxFrames) {
        if (runCatching { requestFocus() }.isSuccess) return true
        withFrameNanos { }
    }
    return false
}
