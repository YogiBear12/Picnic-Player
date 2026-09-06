package app.picnic.player.ui.common

import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val PicnicDialogProperties = DialogProperties(
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = false
)

val DialogCornerRadius = 20.dp

object PanelWidth {
    val Rail = 300.dp

    val Picker = 360.dp

    val Panel = 380.dp

    val Form = 420.dp

    val Reading = 600.dp
}

private val SelectKeyCodes = setOf(
    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
    AndroidKeyEvent.KEYCODE_ENTER,
    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
)

@Composable
fun PicnicDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = PicnicDialogProperties) {
        val openedAt = remember { SystemClock.uptimeMillis() }
        Box(
            Modifier.onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                native.keyCode in SelectKeyCodes && native.downTime < openedAt
            }
        ) {
            content()
        }
    }
}
