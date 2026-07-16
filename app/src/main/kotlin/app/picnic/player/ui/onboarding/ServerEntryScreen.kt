@file:OptIn(ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.picnic.player.data.auth.ServerConnection

/**
 * Onboarding step 1 — server entry. Centered panel (same shell as before the
 * visual pass). [imePadding] shrinks the viewport when the keyboard opens so the
 * panel shifts up; if the stack is taller than the remaining space it scrolls
 * so the address field can stay above the IME.
 */
@Composable
fun ServerEntryScreen(
    onServerResolved: () -> Unit,
    viewModel: ServerEntryViewModel = hiltViewModel()
) {
    app.picnic.player.ui.ambient.PublishBackdrop(null)
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.resolved) {
        if (state.resolved) {
            onServerResolved()
            viewModel.consumeResolved()
        }
    }

    val cardFocus = remember { FocusRequester() }
    val continueFocus = remember { FocusRequester() }
    val addressFocus = remember { FocusRequester() }
    var addressEditing by remember { mutableStateOf(false) }
    val hasDiscovered = state.discovered.isNotEmpty()
    LaunchedEffect(hasDiscovered, addressEditing) {
        if (addressEditing) return@LaunchedEffect
        runCatching { (if (hasDiscovered) cardFocus else continueFocus).requestFocus() }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        val scrollState = rememberScrollState()
        // When the address field opens, scroll so the field + Connect sit in the
        // IME-shrunk viewport (same “shift up” feel as a short centered panel).
        LaunchedEffect(addressEditing, maxHeight) {
            if (addressEditing) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Connect to Jellyfin",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))

            when {
                state.discovered.isNotEmpty() -> {
                    SectionLabel("Discovered servers")
                    Spacer(Modifier.height(10.dp))
                    state.discovered.forEachIndexed { index, server ->
                        DiscoveredServerRow(
                            server = server,
                            onClick = { viewModel.selectDiscovered(server) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .then(if (index == 0) Modifier.focusRequester(cardFocus) else Modifier)
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
                state.discovering -> {
                    SectionLabel("Discovered servers")
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Looking for servers…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(24.dp))
                }
                else -> {
                    Text(
                        "None found — enter your address below",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }

            SectionLabel("Enter manually")
            Spacer(Modifier.height(10.dp))
            OnboardingTextField(
                value = state.serverUrl,
                onValueChange = viewModel::setServerUrl,
                placeholder = "jellyfin.local:8096",
                imeAction = ImeAction.Done,
                onImeAction = viewModel::connectManual,
                onEditingChange = { addressEditing = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(addressFocus)
                    .focusProperties { down = continueFocus }
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = viewModel::connectManual,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .focusRequester(continueFocus)
                    .focusProperties { up = addressFocus }
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.loading) "Connecting…" else "Connect",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            state.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun DiscoveredServerRow(
    server: ServerConnection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.medium
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                shape = shape
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                server.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                server.baseUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Start
    )
}
