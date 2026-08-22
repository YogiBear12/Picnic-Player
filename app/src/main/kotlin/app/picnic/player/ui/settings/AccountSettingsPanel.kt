package app.picnic.player.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.seerr.SeerrLinkState
import app.picnic.player.ui.common.ActionButton
import app.picnic.player.ui.common.DialogTextField
import app.picnic.player.ui.common.rememberIdentityBrush
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.theme.PicnicColors
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter

@Composable
internal fun AccountSettingsPanel(
    viewModel: SettingsViewModel,
    onSignedOut: () -> Unit,
    enterFr: FocusRequester,
    leftFocus: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val seerr by viewModel.seerrState.collectAsStateWithLifecycle()
    val connecting by viewModel.seerrConnecting.collectAsStateWithLifecycle()
    val connectError by viewModel.seerrConnectError.collectAsStateWithLifecycle()
    val username by viewModel.activeUsername.collectAsStateWithLifecycle()
    val avatarUrl by viewModel.activeUserImageUrl.collectAsStateWithLifecycle()
    val linked = seerr.linkState == SeerrLinkState.Linked
    var showConnectDialog by remember { mutableStateOf(false) }

    LaunchedEffect(linked) {
        if (linked && showConnectDialog) {
            showConnectDialog = false
            enterFr.requestFocusWhenAttached(maxFrames = 20)
        }
    }

    Column(
        modifier = modifier.onFocusChanged { onFocusChanged(it.hasFocus) },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(bottom = 24.dp)
        ) {
            ProfileAvatar(name = username, imageUrl = avatarUrl)
            Spacer(Modifier.height(16.dp))
            Text(
                username,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = PicnicColors.OnDark
            )
        }
        ActionRow(
            label = if (linked) "Disconnect Seerr" else "Connect to Seerr",
            leftFocus = leftFocus,
            focusRequester = enterFr,
            blockUp = true,
            onActivate = {
                if (linked) viewModel.disconnectSeerr() else showConnectDialog = true
            }
        )
        ActionRow(
            label = "Sign out",
            leftFocus = leftFocus,
            blockDown = true,
            onActivate = { viewModel.signOut(onSignedOut) }
        )
    }

    if (showConnectDialog) {
        SeerrConnectDialog(
            initialUrl = seerr.serverUrl.orEmpty(),
            connecting = connecting,
            error = connectError,
            onConnect = viewModel::connectSeerr,
            onDismiss = { showConnectDialog = false }
        )
    }
}

@Composable
private fun ProfileAvatar(name: String, imageUrl: String?) {
    var avatarFailed by remember(imageUrl) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(140.dp)
            .clip(CircleShape)
            .background(rememberIdentityBrush(name.ifBlank { "?" })),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl == null || avatarFailed) {
            Text(
                name.firstOrNull()?.uppercase().orEmpty(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = PicnicColors.OnDark
            )
        }
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                onState = { avatarFailed = it is AsyncImagePainter.State.Error },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private val DialogGlassFill = Color(0xEA181E24)

@Composable
private fun SeerrConnectDialog(
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .width(440.dp)
                .shadow(8.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(DialogGlassFill)
                .padding(28.dp)
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
            if (connecting) {
                CircularProgressIndicator(color = PicnicColors.Accent, modifier = Modifier.padding(4.dp))
            }
        }
    }
    LaunchedEffect(Unit) {
        urlFieldFr.requestFocusWhenAttached(maxFrames = 20)
    }
}
