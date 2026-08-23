@file:OptIn(ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.ui.common.ExitOnBack
import app.picnic.player.ui.theme.PicnicColors

@Composable
fun ProfilePickerScreen(
    serverId: String,
    onProfileReady: () -> Unit,
    onNeedsLogin: () -> Unit,
    onChangeServer: () -> Unit,
    viewModel: ProfilePickerViewModel = hiltViewModel<ProfilePickerViewModel, ProfilePickerViewModel.Factory>(
        creationCallback = { factory -> factory.create(serverId) }
    )
) {
    app.picnic.player.ui.ambient.PublishBackdrop(null)
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Consume the one-shot nav flags after acting, so returning here (Back from
    // Login) does not immediately re-fire and bounce the user forward again.
    LaunchedEffect(state.goReady) {
        if (state.goReady) {
            onProfileReady()
            viewModel.consumeNav()
        }
    }
    LaunchedEffect(state.goLogin) {
        if (state.goLogin) {
            onNeedsLogin()
            viewModel.consumeNav()
        }
    }
    ExitOnBack()

    var rowEditing by remember { mutableStateOf(false) }
    PickerScaffold(
        title = "Who's watching?",
        footerVisible = !rowEditing,
        subtitle = if (state.profiles.any { it.authError != null }) {
            {
                Text(
                    AuthRepository.SESSION_EXPIRED_MESSAGE,
                    style = MaterialTheme.typography.bodyLarge,
                    color = PicnicColors.Error,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            null
        },
        footer = {
            OutlinedButton(
                onClick = {
                    viewModel.changeServer()
                    onChangeServer()
                }
            ) {
                Text("Change server", style = MaterialTheme.typography.titleSmall)
            }
        }
    ) {
        EditablePickerRow(
            items = state.profiles.map {
                PickerEntry(
                    id = it.userId,
                    label = it.name,
                    imageUrl = it.imageUrl,
                    errorText = it.authError
                )
            },
            addTile = if (!state.localDataPending) {
                PickerEntry(
                    id = ADD_TILE_ID,
                    label = "Add user",
                    icon = Icons.Rounded.Add,
                    editable = false
                )
            } else {
                null
            },
            onActivate = { id -> if (id == ADD_TILE_ID) viewModel.addUser() else viewModel.activate(id) },
            onReorder = viewModel::reorder,
            onForget = viewModel::forget,
            confirmText = { "Remove ${it.label}?" },
            onEditingChanged = { rowEditing = it }
        )
    }
}

private const val ADD_TILE_ID = "__add__"
