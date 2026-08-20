package app.picnic.player.data.socket

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface RemoteControlAction {
    data class Play(val itemId: String, val startPositionMs: Long?) : RemoteControlAction

    data class ShowItem(val itemId: String) : RemoteControlAction

    data object GoHome : RemoteControlAction

    data object GoToSettings : RemoteControlAction

    data class DispatchKey(val key: RemoteKey) : RemoteControlAction
}

enum class RemoteKey {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    SELECT,
    BACK,
    PAGE_UP,
    PAGE_DOWN
}

@Singleton
class RemoteControlBus @Inject constructor() {
    private val _actions = MutableSharedFlow<RemoteControlAction>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val actions: SharedFlow<RemoteControlAction> = _actions.asSharedFlow()

    fun emit(action: RemoteControlAction) {
        _actions.tryEmit(action)
    }
}
