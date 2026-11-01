package app.picnic.player.ui.watchtogether

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.browse.DeclarePaneEntry
import app.picnic.player.ui.common.CenteredMessage
import app.picnic.player.ui.common.GlassRow
import app.picnic.player.ui.common.LoadFailedState
import app.picnic.player.ui.common.rememberKeyedFocusRequesters
import app.picnic.player.ui.common.rememberOneShotFocus
import app.picnic.player.ui.common.rememberRetrySeed
import app.picnic.player.ui.common.requestFocusWhenVisible
import app.picnic.player.ui.theme.PicnicColors
import java.util.UUID
import org.jellyfin.sdk.model.api.GroupInfoDto

@Composable
internal fun WatchTogetherPane(
    horizontalInset: Dp,
    seedContentFocus: Boolean,
    onContentFocusSeeded: () -> Unit,
    onJoined: (UUID) -> Unit,
    viewModel: WatchTogetherPaneViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val joined by rememberUpdatedState(onJoined)
    LaunchedEffect(viewModel) { viewModel.joined.collect { joined(it) } }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rowFocus = rememberKeyedFocusRequesters()
    var focusedKey by remember { mutableStateOf<String?>(null) }
    val retrySeed = rememberRetrySeed()
    val onSeeded = {
        retrySeed.consume()
        onContentFocusSeeded()
    }

    when {
        state.loading && state.rooms.isEmpty() -> Box(Modifier.fillMaxSize()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = PicnicColors.Accent)
        }
        state.error != null -> {
            val retryFocus = rememberOneShotFocus(seedContentFocus || (retrySeed.pending && state.joining == null), onSeeded)
            DeclarePaneEntry { retryFocus }
            LoadFailedState(
                message = state.error.orEmpty(),
                retryFocus = retryFocus,
                onRetry = {
                    retrySeed.arm()
                    viewModel.retry()
                },
                retrying = state.joining != null
            )
        }
        state.rooms.isEmpty() -> CenteredMessage(
            message = "No rooms right now",
            detail = "Start one from a movie's menu, or from the web."
        )
        else -> {
            val rooms = state.rooms
            val listState = rememberLazyListState()
            var listHasFocus by remember { mutableStateOf(false) }
            DeclarePaneEntry {
                val keys = rooms.map { it.groupId.toString() }
                (focusedKey?.takeIf { it in keys } ?: keys.firstOrNull())?.let { rowFocus[it] } ?: FocusRequester.Default
            }
            LaunchedEffect(seedContentFocus, retrySeed.pending, rooms, state.joining) {
                if (!seedContentFocus && !retrySeed.pending || state.joining != null) return@LaunchedEffect
                val index = rooms.indexOfFirst { it.groupId.toString() == focusedKey }.coerceAtLeast(0)
                rowFocus[rooms[index].groupId.toString()].requestFocusWhenVisible(listState, index) { listHasFocus }
                onSeeded()
            }
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = horizontalInset, end = 40.dp, top = 28.dp, bottom = 16.dp),
                modifier = Modifier.fillMaxSize().onFocusChanged { listHasFocus = it.hasFocus }.focusGroup()
            ) {
                items(rooms, key = { it.groupId.toString() }) { room ->
                    val key = room.groupId.toString()
                    RoomRow(
                        room = room,
                        joining = state.joining == room.groupId,
                        onJoin = {
                            retrySeed.arm()
                            viewModel.join(room)
                        },
                        modifier = Modifier
                            .focusRequester(rowFocus[key])
                            .onFocusChanged { if (it.isFocused) focusedKey = key }
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomRow(room: GroupInfoDto, joining: Boolean, onJoin: () -> Unit, modifier: Modifier = Modifier) {
    GlassRow(onClick = onJoin, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                watchPartyName(room.groupName.orEmpty()),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (joining) "Joining…" else roomDetail(room),
                style = MaterialTheme.typography.bodySmall,
                color = PicnicColors.OnDarkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun roomDetail(room: GroupInfoDto): String = (listOf(room.state.serialName) + room.participants).joinToString(" · ")
