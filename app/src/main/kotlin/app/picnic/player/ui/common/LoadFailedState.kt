package app.picnic.player.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties

@Composable
fun LoadFailedState(
    message: String,
    retryFocus: FocusRequester,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    retrying: Boolean = false,
    upExitFocus: FocusRequester? = null
) {
    CenteredMessage(message = message, modifier = modifier, detail = detail) {
        MessageActionButton(
            label = "Retry",
            focusRequester = retryFocus,
            onActivate = onRetry,
            busy = retrying,
            modifier = Modifier.focusProperties { up = upExitFocus ?: FocusRequester.Default }
        )
    }
}
