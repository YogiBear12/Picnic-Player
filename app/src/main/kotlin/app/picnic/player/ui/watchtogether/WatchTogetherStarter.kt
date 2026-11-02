package app.picnic.player.ui.watchtogether

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.socket.ServerMessageBus
import app.picnic.player.data.socket.ServerNotice
import app.picnic.player.data.syncplay.SyncPlayRepository
import app.picnic.player.data.syncplay.SyncPlaySession
import app.picnic.player.ui.common.ContextMenuAction
import app.picnic.player.ui.common.resumeTicks
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.SyncPlayUserAccessType

val LocalWatchTogether = staticCompositionLocalOf<WatchTogetherStarter> {
    error("LocalWatchTogether not provided")
}

@HiltViewModel
class WatchTogetherStarter @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncPlayRepository: SyncPlayRepository,
    private val syncPlaySession: SyncPlaySession,
    private val serverMessageBus: ServerMessageBus
) : ViewModel() {
    val canCreate: StateFlow<Boolean> = syncPlayRepository.currentAccess
        .map { it == SyncPlayUserAccessType.CREATE_AND_JOIN_GROUPS }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _opened = Channel<UUID>(Channel.BUFFERED)
    val opened = _opened.receiveAsFlow()

    fun start(movie: BaseItemDto, startTicks: Long) {
        viewModelScope.launch {
            try {
                val username = authRepository.activeSession()?.username.orEmpty()
                val group = syncPlayRepository.create(username + WATCH_PARTY_SUFFIX)
                syncPlayRepository.setNewQueue(movie.id, startTicks)
                syncPlayRepository.pause()
                check(syncPlaySession.awaitJoined(group.groupId))
                _opened.send(group.groupId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                runCatching { syncPlayRepository.leave() }
                serverMessageBus.emit(ServerNotice(text = "Couldn't start a watch party"))
            }
        }
    }
}

/** Null when this item or user can't start a room, so menus simply leave the row out. */
@Composable
fun rememberWatchTogether(item: BaseItemDto): WatchTogetherStarter? {
    val starter = LocalWatchTogether.current
    val canCreate by starter.canCreate.collectAsStateWithLifecycle()
    return starter.takeIf { canCreate && item.type == BaseItemKind.MOVIE }
}

fun watchTogetherAction(item: BaseItemDto, starter: WatchTogetherStarter, onDone: () -> Unit): ContextMenuAction = ContextMenuAction(WatchTogetherLabel, Icons.Default.Groups) {
    starter.start(item, item.resumeTicks() ?: 0L)
    onDone()
}

private const val WatchTogetherLabel = "Start a watch party"
