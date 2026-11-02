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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.browse.DeclarePaneEntry
import app.picnic.player.ui.common.ActionButton
import app.picnic.player.ui.common.CenteredMessage
import app.picnic.player.ui.common.GlassRow
import app.picnic.player.ui.common.LoadFailedState
import app.picnic.player.ui.common.LoadingFocusTarget
import app.picnic.player.ui.common.rememberKeyedFocusRequesters
import app.picnic.player.ui.common.rememberLoadingFocusHolder
import app.picnic.player.ui.common.rememberOneShotFocus
import app.picnic.player.ui.common.rememberRetrySeed
import app.picnic.player.ui.common.requestFocusWhenAttached
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
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.poll() }
    }
    val joined by rememberUpdatedState(onJoined)
    LaunchedEffect(viewModel) { viewModel.joined.collect { joined(it) } }
    val latest by viewModel.state.collectAsStateWithLifecycle()
    var state by remember { mutableStateOf(latest) }
    val rowFocus = rememberKeyedFocusRequesters()
    var focusedRoom by remember { mutableStateOf<UUID?>(null) }
    var focusedSlot by remember { mutableIntStateOf(0) }
    var paneFocused by remember { mutableStateOf(false) }
    val park = rememberLoadingFocusHolder()
    var parking by remember { mutableStateOf(false) }
    val retrySeed = rememberRetrySeed()
    val onSeeded = {
        retrySeed.consume()
        onContentFocusSeeded()
    }

    LaunchedEffect(latest) {
        if (!paneFocused || !state.dropsFocus(latest, focusedRoom)) {
            state = latest
            return@LaunchedEffect
        }
        parking = true
        try {
            park.requester.requestFocusWhenAttached()
            if (state.content != latest.content) {
                focusedRoom = null
                focusedSlot = 0
            } else {
                focusedSlot = state.rooms.indexOfFirst { it.groupId == focusedRoom }.coerceAtLeast(0)
            }
            retrySeed.arm()
            state = latest
        } finally {
            parking = false
        }
    }

    Box(Modifier.fillMaxSize().onFocusChanged { paneFocused = it.hasFocus }.focusGroup()) {
        if (park.holds(parking)) LoadingFocusTarget(park)
        when (state.content) {
            PaneContent.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = PicnicColors.Accent)
            PaneContent.Error -> {
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
            PaneContent.Empty -> {
                val refreshFocus = rememberOneShotFocus(seedContentFocus || retrySeed.pending, onSeeded)
                DeclarePaneEntry { refreshFocus }
                CenteredMessage(message = "No watch parties found") {
                    ActionButton(
                        label = "Refresh",
                        onActivate = {
                            retrySeed.arm()
                            viewModel.load()
                        },
                        focusRequester = refreshFocus
                    )
                }
            }
            PaneContent.Rooms -> {
                val rooms = state.rooms
                val listState = rememberLazyListState()
                var listHasFocus by remember { mutableStateOf(false) }
                DeclarePaneEntry {
                    (rooms.firstOrNull { it.groupId == focusedRoom } ?: rooms.firstOrNull())?.let { rowFocus[it.groupId] } ?: FocusRequester.Default
                }
                LaunchedEffect(seedContentFocus, retrySeed.pending, rooms, state.joining) {
                    if (!seedContentFocus && !retrySeed.pending || state.joining != null) return@LaunchedEffect
                    val index = rooms.indexOfFirst { it.groupId == focusedRoom }
                        .takeIf { it >= 0 } ?: focusedSlot.coerceIn(0, rooms.lastIndex)
                    rowFocus[rooms[index].groupId].requestFocusWhenVisible(listState, index) { listHasFocus }
                    onSeeded()
                }
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(start = horizontalInset, end = 40.dp, top = 28.dp, bottom = 16.dp),
                    modifier = Modifier.fillMaxSize().onFocusChanged { listHasFocus = it.hasFocus }.focusGroup()
                ) {
                    items(rooms, key = { it.groupId }) { room ->
                        RoomRow(
                            room = room,
                            joining = state.joining == room.groupId,
                            onJoin = {
                                retrySeed.arm()
                                viewModel.join(room)
                            },
                            modifier = Modifier
                                .focusRequester(rowFocus[room.groupId])
                                .onFocusChanged { if (it.isFocused) focusedRoom = room.groupId }
                        )
                    }
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
