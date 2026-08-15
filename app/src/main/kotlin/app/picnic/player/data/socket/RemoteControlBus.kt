package app.picnic.player.data.socket

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A remote-control action that must be applied at the **app/UI** level rather than to a
 * player: launching playback as a cast target, jumping to a
 * screen, or proxying a phone's D-pad. The sole collector is `MainActivity`, which holds both
 * the navigation stack and the activity window needed to service these.
 *
 * D-pad proxying is modelled as a semantic [RemoteKey] rather than an Android key-code so the
 * data layer stays free of `android.view`; `MainActivity` maps each key to a `KeyEvent` it
 * dispatches through the activity window, driving Compose-TV focus exactly as a real remote does.
 */
sealed interface RemoteControlAction {
    /** Cast target: start playback of [itemId] on this TV at [startPositionMs] (PlayNow). */
    data class Play(val itemId: String, val startPositionMs: Long?) : RemoteControlAction

    /** Open an item's detail screen (`DisplayContent`). */
    data class ShowItem(val itemId: String) : RemoteControlAction

    /** Return to the browse home (`GoHome`). */
    data object GoHome : RemoteControlAction

    /** Open settings (`GoToSettings`). */
    data object GoToSettings : RemoteControlAction

    /** Inject a navigation key press (phone-as-remote D-pad proxy). */
    data class DispatchKey(val key: RemoteKey) : RemoteControlAction
}

/** The navigation keys a remote can proxy; mapped to Android key-codes by the UI collector. */
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

/**
 * Singleton fan-in for [RemoteControlAction]s, mirroring
 * [LibraryChangeBus][app.picnic.player.data.media.LibraryChangeBus].
 */
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
