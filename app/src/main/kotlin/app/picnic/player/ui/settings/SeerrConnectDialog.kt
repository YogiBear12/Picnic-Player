package app.picnic.player.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.seerr.SeerrAuthMethod
import app.picnic.player.data.seerr.SeerrCredentials
import app.picnic.player.ui.common.ActionButton
import app.picnic.player.ui.common.ContextMenuAction
import app.picnic.player.ui.common.ContextMenuFocus
import app.picnic.player.ui.common.ContextMenuPanel
import app.picnic.player.ui.common.DialogTextField
import app.picnic.player.ui.common.PanelWidth
import app.picnic.player.ui.common.PicnicDialog
import app.picnic.player.ui.common.focusSeed
import app.picnic.player.ui.common.panelSurface
import app.picnic.player.ui.common.rememberFocusSeed
import app.picnic.player.ui.theme.PicnicColors

private val ContentInset = 28.dp

private enum class ConnectStep {
    CHOOSE,
    JELLYFIN,
    LOCAL
}

private val ChooserRows = listOf(
    ConnectStep.JELLYFIN to "Login with Jellyfin user",
    ConnectStep.LOCAL to "Login with Seerr user"
)

@Composable
internal fun SeerrConnectDialog(
    prompt: SeerrConnectPrompt,
    initialUrl: String,
    connecting: Boolean,
    error: String?,
    onConnect: (url: String, credentials: SeerrCredentials) -> Unit,
    onStepChange: () -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    var step by remember { mutableStateOf(if (prompt.offerLocalLogin) ConnectStep.CHOOSE else ConnectStep.JELLYFIN) }
    val chooserFocus = remember {
        val lastStep = if (prompt.lastLogin.method == SeerrAuthMethod.LOCAL) ConnectStep.LOCAL else ConnectStep.JELLYFIN
        ContextMenuFocus().apply { lastIndex = ChooserRows.indexOfFirst { it.first == lastStep } }
    }
    val goTo: (ConnectStep) -> Unit = {
        step = it
        onStepChange()
    }

    PicnicDialog(onDismiss = onDismiss) {
        BackHandler {
            when (step) {
                ConnectStep.CHOOSE -> onDismiss()
                ConnectStep.JELLYFIN -> if (prompt.offerLocalLogin) goTo(ConnectStep.CHOOSE) else onDismiss()
                ConnectStep.LOCAL -> goTo(ConnectStep.CHOOSE)
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
                contentAlignment = Alignment.Center
            ) {
                when (step) {
                    ConnectStep.CHOOSE -> ContextMenuPanel(
                        actions = ChooserRows.map { (target, label) -> ContextMenuAction(label) { goTo(target) } },
                        focus = chooserFocus
                    )
                    ConnectStep.JELLYFIN -> JellyfinConnectForm(
                        url = url,
                        onUrlChange = { url = it },
                        connecting = connecting,
                        error = error,
                        onConnect = onConnect
                    )
                    ConnectStep.LOCAL -> LocalConnectForm(
                        url = url,
                        onUrlChange = { url = it },
                        initialEmail = prompt.lastLogin.email.orEmpty(),
                        connecting = connecting,
                        error = error,
                        onConnect = onConnect
                    )
                }
            }
        }
    }
}

@Composable
private fun JellyfinConnectForm(
    url: String,
    onUrlChange: (String) -> Unit,
    connecting: Boolean,
    error: String?,
    onConnect: (url: String, credentials: SeerrCredentials) -> Unit
) {
    var password by remember { mutableStateOf("") }
    ConnectFormPanel(
        url = url,
        onUrlChange = onUrlChange,
        connecting = connecting,
        error = error,
        canConnect = url.isNotBlank() && password.isNotBlank(),
        onConnect = { onConnect(url, SeerrCredentials.Jellyfin(password)) }
    ) {
        DialogTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = "Password",
            label = "Jellyfin password",
            password = true,
            modifier = Modifier.keepInViewWhileFocused()
        )
    }
}

@Composable
private fun LocalConnectForm(
    url: String,
    onUrlChange: (String) -> Unit,
    initialEmail: String,
    connecting: Boolean,
    error: String?,
    onConnect: (url: String, credentials: SeerrCredentials) -> Unit
) {
    var email by remember { mutableStateOf(initialEmail) }
    var password by remember { mutableStateOf("") }
    ConnectFormPanel(
        url = url,
        onUrlChange = onUrlChange,
        connecting = connecting,
        error = error,
        canConnect = url.isNotBlank() && email.isNotBlank() && password.isNotBlank(),
        onConnect = { onConnect(url, SeerrCredentials.Local(email.trim(), password)) }
    ) {
        DialogTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = "name@example.com",
            label = "Seerr email",
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            modifier = Modifier.keepInViewWhileFocused()
        )
        DialogTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = "Password",
            label = "Seerr password",
            password = true,
            modifier = Modifier.keepInViewWhileFocused()
        )
    }
}

@Composable
private fun ConnectFormPanel(
    url: String,
    onUrlChange: (String) -> Unit,
    connecting: Boolean,
    error: String?,
    canConnect: Boolean,
    onConnect: () -> Unit,
    credentialFields: @Composable () -> Unit
) {
    val urlFieldSeed = rememberFocusSeed(key = Unit, enabled = true)
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
            onValueChange = onUrlChange,
            placeholder = "https://requests.example.com",
            label = "Seerr URL",
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Next,
            modifier = Modifier.focusSeed(urlFieldSeed).keepInViewWhileFocused()
        )
        credentialFields()
        if (error != null) {
            Text(error, color = PicnicColors.Error, style = MaterialTheme.typography.bodyMedium)
        }
        ActionButton(
            label = "Connect",
            onActivate = onConnect,
            enabled = canConnect,
            busy = connecting,
            fillWidth = true
        )
    }
}

/**
 * A text field only scrolls its cursor line into view, which leaves the field's box and label
 * under the keyboard. Re-running on every IME inset change keeps the whole field visible as the
 * keyboard animates in.
 */
@Composable
private fun Modifier.keepInViewWhileFocused(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    LaunchedEffect(focused, density) {
        if (focused) snapshotFlow { ime.getBottom(density) }.collect { requester.bringIntoView() }
    }
    return bringIntoViewRequester(requester).onFocusEvent { focused = it.hasFocus }
}
