package app.picnic.player.ui.common

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable

/**
 * Makes Back on a top-level screen exit the app rather than pop the nav stack.
 * Used by the launch roots — Select Server, Select User, Home — where there is no
 * meaningful "previous" screen to return to.
 */
@Composable
fun ExitOnBack() {
    val activity = LocalActivity.current
    BackHandler(enabled = activity != null) { activity?.finish() }
}
