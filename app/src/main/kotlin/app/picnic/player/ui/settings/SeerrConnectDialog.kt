package app.picnic.player.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.common.ActionButton
import app.picnic.player.ui.common.DialogTextField
import app.picnic.player.ui.common.PanelWidth
import app.picnic.player.ui.common.PicnicDialog
import app.picnic.player.ui.common.panelSurface
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.theme.PicnicColors

private val ContentInset = 28.dp

@Composable
internal fun SeerrConnectDialog(
    initialUrl: String,
    connecting: Boolean,
    error: String?,
    onConnect: (url: String, password: String) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var password by remember { mutableStateOf("") }
    val urlFieldFr = remember { FocusRequester() }

    PicnicDialog(onDismiss = onDismiss) {
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .panelSurface(width = PanelWidth.Form)
                    .padding(horizontal = ContentInset)
            ) {
                Text(
                    "Connect to Seerr",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                DialogTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = "https://requests.example.com",
                    label = "Seerr URL",
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                    modifier = Modifier.focusRequester(urlFieldFr)
                )
                DialogTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "Password",
                    label = "Jellyfin password",
                    password = true
                )
                if (error != null) {
                    Text(error, color = PicnicColors.Error, style = MaterialTheme.typography.bodyMedium)
                }
                ActionButton(
                    label = "Connect",
                    onActivate = { onConnect(url, password) },
                    enabled = url.isNotBlank() && password.isNotBlank(),
                    busy = connecting,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    LaunchedEffect(Unit) {
        urlFieldFr.requestFocusWhenAttached(maxFrames = 20)
    }
}
