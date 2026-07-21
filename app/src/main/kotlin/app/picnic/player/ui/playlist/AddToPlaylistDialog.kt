@file:OptIn(ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.onboarding.OnboardingTextField
import app.picnic.player.ui.theme.PicnicColors
import org.jellyfin.sdk.model.api.BaseItemDto

/** Adds [item] to a chosen playlist, or a newly created one. */
@Composable
fun AddToPlaylistDialog(
    item: BaseItemDto,
    onDismiss: () -> Unit,
    viewModel: AddToPlaylistViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val itemId = item.id.toString()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .width(420.dp)
                .shadow(8.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(PicnicColors.GlassFill)
                .padding(24.dp)
        ) {
            Text(
                "Add to playlist",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (creating) {
                val nameFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) { nameFocus.requestFocusWhenAttached() }
                OnboardingTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = "Playlist name",
                    onImeAction = {
                        if (newName.isNotBlank()) {
                            viewModel.createAndAdd(newName, itemId)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocus)
                )
            } else {
                val firstFocus = remember { FocusRequester() }
                LaunchedEffect(state.loading) {
                    if (!state.loading) firstFocus.requestFocusWhenAttached()
                }
                ListItem(
                    selected = false,
                    onClick = { creating = true },
                    leadingContent = { Icon(Icons.Default.Add, contentDescription = null, tint = Color.White.copy(alpha = 0.8f)) },
                    headlineContent = { Text("New playlist…", color = Color.White) },
                    colors = playlistPickerColors(),
                    modifier = Modifier.fillMaxWidth().focusRequester(firstFocus)
                )
                state.playlists.forEach { playlist ->
                    ListItem(
                        selected = false,
                        onClick = {
                            viewModel.addTo(playlist.id.toString(), itemId)
                            onDismiss()
                        },
                        headlineContent = { Text(playlist.name.orEmpty(), color = Color.White) },
                        supportingContent = {
                            playlist.childCount?.let {
                                Text(
                                    if (it == 1) "1 item" else "$it items",
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        },
                        colors = playlistPickerColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (state.playlists.isEmpty() && !state.loading) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "No playlists yet — create one above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PicnicColors.OnDarkMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun playlistPickerColors() = ListItemDefaults.colors(
    containerColor = Color.Transparent,
    focusedContainerColor = Color.White.copy(alpha = 0.15f),
    contentColor = Color.White
)
