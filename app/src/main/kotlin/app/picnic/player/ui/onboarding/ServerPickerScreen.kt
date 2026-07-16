@file:OptIn(ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import app.picnic.player.ui.common.ExitOnBack

/**
 * Return-user Server Picker. A row of onboarded server tiles plus
 * "Add server"; no footer. Selecting a server opens its Profile Picker.
 */
@Composable
fun ServerPickerScreen(
    onServerSelected: (String) -> Unit,
    onAddServer: () -> Unit,
    onNoServersLeft: () -> Unit,
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

    PickerScaffold(title = "Select server") {
        EditablePickerRow(
            items = state.servers.map {
                PickerEntry(id = it.id, label = it.name, fallbackInitial = it.name.firstOrNull()?.toString())
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
