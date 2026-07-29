package app.picnic.player.data.socket

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the app process is foregrounded (RESUMED). [WebSocketManager] gates its socket
 * subscriptions on this so we only react to server pushes while the user is present.
 * An interface so tests can drive it directly.
 */
interface AppForegroundState {
    val isResumed: StateFlow<Boolean>

    /**
     * Whether the app is on screen at all (STARTED). Distinct from [isResumed]: a
     * Picture-in-Picture window is started but not resumed, so playback outlives losing focus
     * and ends only once the app is really gone.
     */
    val isVisible: StateFlow<Boolean>
}

/**
 * Production [AppForegroundState] backed by [ProcessLifecycleOwner]. Constructed on the
 * main thread (via `PicnicApp.onCreate` → [WebSocketManager]) so the observer registers
 * on the main dispatcher as the lifecycle API requires.
 */
@Singleton
class ProcessAppForegroundState @Inject constructor() : AppForegroundState {
    private val _isResumed = MutableStateFlow(false)
    override val isResumed: StateFlow<Boolean> = _isResumed.asStateFlow()

    private val _isVisible = MutableStateFlow(false)
    override val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    _isVisible.value = true
                }

                override fun onResume(owner: LifecycleOwner) {
                    _isResumed.value = true
                }

                override fun onPause(owner: LifecycleOwner) {
                    _isResumed.value = false
                }

                override fun onStop(owner: LifecycleOwner) {
                    _isVisible.value = false
                }
            }
        )
    }
}
