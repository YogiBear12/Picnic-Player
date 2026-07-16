@file:OptIn(ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.picnic.player.ui.onboarding.LoginViewModel.Method

/**
 * Onboarding step 2 — split-pane sign-in (parity with the unified flow,
 * onboarding.md). Left column: two stacked methods, Quick Connect default-
 * focused; focusing a method swaps the right pane. Right pane: Quick Connect QR +
 * live code, or username/password. Back discards the in-memory server.
 */
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onBack: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    // Onboarding shows the plain ocean wash — drop any backdrop left by a media screen.
    app.picnic.player.ui.ambient.PublishBackdrop(null)
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.loggedIn) { if (state.loggedIn) onLoggedIn() }
    BackHandler(onBack = onBack)

    val quickConnectFocus = remember { FocusRequester() }
    val passwordOptionFocus = remember { FocusRequester() }
    val usernameFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    // While a TV field shell swaps to the real TextField, focus can briefly escape to
    // Quick Connect — ignore that auto-select so the Password pane stays mounted.
    var fieldsEditing by remember { mutableIntStateOf(0) }
    val suppressMethodAutoSelect = fieldsEditing > 0
    // Default focus on the Quick Connect option (method follows focus).
    LaunchedEffect(Unit) { runCatching { quickConnectFocus.requestFocus() } }

    Column(modifier = Modifier.fillMaxSize().imePadding().padding(48.dp)) {
        // Heading / subheading pinned to the top-left of the page.
        Text("Sign in", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Choose how to connect to your server",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(48.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxHeight().width(320.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // Method follows focus — moving between the two options swaps the
                // right pane automatically; explicit focus links keep traversal
                // from ever landing on the wrong option (no double-highlight).
                MethodOption(
                    icon = Icons.Rounded.Cast,
                    label = "Sign in with Quick Connect",
                    selected = state.method == Method.QuickConnect,
                    onSelect = { viewModel.selectMethod(Method.QuickConnect) },
                    suppressAutoSelect = suppressMethodAutoSelect,
                    modifier = Modifier
                        .focusRequester(quickConnectFocus)
                        .focusProperties { down = passwordOptionFocus }
                )
                Spacer(Modifier.height(12.dp))
                MethodOption(
                    icon = Icons.Rounded.Keyboard,
                    label = "Username + password",
                    selected = state.method == Method.Password,
                    onSelect = { viewModel.selectMethod(Method.Password) },
                    suppressAutoSelect = suppressMethodAutoSelect,
                    modifier = Modifier
                        .focusRequester(passwordOptionFocus)
                        .focusProperties {
                            up = quickConnectFocus
                            right = usernameFocus
                        }
                )
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                when (state.method) {
                    Method.QuickConnect -> QuickConnectPane(
                        code = state.quickConnectCode,
                        url = state.quickConnectUrl,
                        displayUrl = state.serverDisplayUrl,
                        error = state.quickConnectError
                    )
                    Method.Password -> PasswordPane(
                        username = state.username,
                        password = state.password,
                        error = state.passwordError,
                        loading = state.loading,
                        usernameFocus = usernameFocus,
                        passwordFocus = passwordFocus,
                        leftTarget = passwordOptionFocus,
                        onUsername = viewModel::setUsername,
                        onPassword = viewModel::setPassword,
                        onSubmit = viewModel::login,
                        onEditingChange = { editing ->
                            fieldsEditing = (fieldsEditing + if (editing) 1 else -1).coerceAtLeast(0)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MethodOption(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    suppressAutoSelect: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Focusing an option selects it (unless a password-field edit transition is in
    // flight — see [suppressAutoSelect]). During normal D-pad use the focused option
    // is therefore the selected (white) one.
    Surface(
        onClick = onSelect,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused && !suppressAutoSelect) onSelect() },
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor =
            if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant,
            contentColor =
            if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
            focusedContainerColor =
            if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant,
            focusedContentColor =
            if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                shape = MaterialTheme.shapes.medium
            )
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun QuickConnectPane(code: String?, url: String?, displayUrl: String?, error: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = MaterialTheme.shapes.large,
            colors = androidx.tv.material3.SurfaceDefaults.colors(containerColor = Color.White)
        ) {
            // The QR encodes the pairing URL with the Quick Connect code appended
            // as a query param (?code=NNNNNN) so scanning it auto-fills the code on
            // the phone. The code is a separate network call, so the QR waits for it
            // — a spinner shows until it arrives. The URL shown as text below stays
            // codeless (the code is displayed on its own line).
            when {
                url != null && code != null ->
                    QrCode(content = "$url?code=$code", size = 180.dp, modifier = Modifier.padding(16.dp))
                error != null ->
                    Box(Modifier.size(212.dp), contentAlignment = Alignment.Center) {
                        Text(
                            error,
                            color = Color.Black,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                else ->
                    Box(Modifier.size(212.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.Black)
                    }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Scan with your phone or visit",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        displayUrl?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Enter this code on your server",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = code ?: "— — — — — —",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 8.sp
        )
    }
}

@Composable
private fun PasswordPane(
    username: String,
    password: String,
    error: String?,
    loading: Boolean,
    usernameFocus: FocusRequester,
    passwordFocus: FocusRequester,
    leftTarget: FocusRequester,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSubmit: () -> Unit,
    onEditingChange: (Boolean) -> Unit
) {
    // Idle shells never open the IME; Select does (TV practice).
    // Right from the method option enters the username shell.
    Column(
        modifier = Modifier.width(420.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OnboardingTextField(
            value = username,
            onValueChange = onUsername,
            placeholder = "Username",
            imeAction = ImeAction.Next,
            onImeAction = { runCatching { passwordFocus.requestFocus() } },
            onEditingChange = onEditingChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(usernameFocus)
                .focusProperties {
                    left = leftTarget
                    down = passwordFocus
                }
        )
        OnboardingTextField(
            value = password,
            onValueChange = onPassword,
            placeholder = "Password",
            password = true,
            imeAction = ImeAction.Done,
            onImeAction = onSubmit,
            onEditingChange = onEditingChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(passwordFocus)
                .focusProperties {
                    left = leftTarget
                    up = usernameFocus
                }
        )
        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .focusProperties { left = leftTarget }
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (loading) "Signing in…" else "Sign in",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
