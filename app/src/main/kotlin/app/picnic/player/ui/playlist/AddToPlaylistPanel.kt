@file:OptIn(ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.playlist

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.common.ActionButton
import app.picnic.player.ui.common.DialogTextField
import app.picnic.player.ui.common.PanelRowCornerRadius
import app.picnic.player.ui.common.PanelWidth
import app.picnic.player.ui.common.focusSeed
import app.picnic.player.ui.common.marqueeWhenFocused
import app.picnic.player.ui.common.panelSurface
import app.picnic.player.ui.common.rememberFocusSeed
import app.picnic.player.ui.common.requestFocusWhenAttached
import org.jellyfin.sdk.model.api.BaseItemDto

private val PanelMaxHeight = 460.dp

@Composable
fun AddToPlaylistPanel(
    item: BaseItemDto,
    onDone: () -> Unit,
    viewModel: AddToPlaylistViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val itemId = item.id.toString()

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .panelSurface(width = PanelWidth.Panel)
            .heightIn(max = PanelMaxHeight)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .focusGroup()
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
            val create: () -> Unit = {
                if (newName.isNotBlank()) {
                    viewModel.createAndAdd(newName, itemId)
                    onDone()
                }
            }
            DialogTextField(
                value = newName,
                onValueChange = { newName = it },
                placeholder = "Playlist name",
                onImeAction = create,
                modifier = Modifier.focusRequester(nameFocus)
            )
            Spacer(Modifier.height(8.dp))
            ActionButton(
                label = "Create playlist",
                onActivate = create,
                enabled = newName.isNotBlank(),
                fillWidth = true
            )
        } else {
            val seed = rememberFocusSeed(state.loading, enabled = !state.loading)
            ListItem(
                selected = false,
                onClick = { creating = true },
                leadingContent = { Icon(Icons.Default.Add, contentDescription = null, tint = Color.White.copy(alpha = 0.8f)) },
                headlineContent = { Text("New playlist…", color = Color.White) },
                colors = playlistPickerColors(),
                shape = playlistPickerShape(),
                scale = ListItemDefaults.scale(focusedScale = 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusSeed(seed)
            )
            state.playlists.forEach { playlist ->
                var focused by remember(playlist.id) { mutableStateOf(false) }
                ListItem(
                    selected = false,
                    onClick = {
                        viewModel.addTo(playlist.id.toString(), itemId)
                        onDone()
                    },
                    headlineContent = {
                        Text(
                            text = playlist.name.orEmpty(),
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.marqueeWhenFocused(focused)
                        )
                    },
                    supportingContent = {
                        playlist.childCount?.let {
                            Text(
                                if (it == 1) "1 item" else "$it items",
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    },
                    colors = playlistPickerColors(),
                    shape = playlistPickerShape(),
                    scale = ListItemDefaults.scale(focusedScale = 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused }
                )
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

@Composable
private fun playlistPickerShape() = ListItemDefaults.shape(
    shape = RoundedCornerShape(PanelRowCornerRadius)
)
