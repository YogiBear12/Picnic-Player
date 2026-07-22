@file:OptIn(ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.common.ExitOnBack
import app.picnic.player.ui.theme.PicnicColors

/**
 * Return-user Server Picker. A row of onboarded server tiles plus
 * "Add server"; no footer. Selecting a server opens its Profile Picker.
 */
@Composable
fun ServerPickerScreen(
    onServerSelected: (String) -> Unit,
    onAddServer: () -> Unit,
    onNoServersLeft: () -> Unit,
    /** A server that just failed to load, flagged with an error on its tile; null on a normal visit. */
    unreachableServerId: String? = null,
    errorText: String? = null,
    viewModel: ServerPickerViewModel = hiltViewModel()
) {
    // Onboarding shows the plain ocean wash — drop any backdrop left by a media screen.
    app.picnic.player.ui.ambient.PublishBackdrop(null)
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Forgetting the last server replaces this screen with server entry.
    LaunchedEffect(state.goAddServer) {
        if (state.goAddServer) {
            onNoServersLeft()
            viewModel.consumeNav()
        }
    }
    // Entry point: Back always exits the app.
    ExitOnBack()

    PickerScaffold(
        title = "Select server",
        subtitle = if (errorText != null) {
            {
                Text(
                    errorText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = PicnicColors.Error,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            null
        }
    ) {
        EditablePickerRow(
            items = state.servers.map {
                PickerEntry(
                    id = it.id,
                    label = it.name,
                    fallbackInitial = it.name.firstOrNull()?.toString(),
                    // Only the server that failed carries the error visual.
                    errorText = errorText?.takeIf { _ -> it.id == unreachableServerId }
                )
            },
            addTile = PickerEntry(
                id = ADD_TILE_ID,
                label = "Add server",
                icon = Icons.Rounded.Add,
                editable = false
            ),
            onActivate = { id -> if (id == ADD_TILE_ID) onAddServer() else onServerSelected(id) },
            onReorder = viewModel::reorder,
            onForget = viewModel::forget,
            confirmText = { "Remove ${it.label}?" }
        )
    }
}

private const val ADD_TILE_ID = "__add__"
