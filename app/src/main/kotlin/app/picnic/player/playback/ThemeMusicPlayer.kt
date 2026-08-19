package app.picnic.player.playback

import android.content.Context
import android.util.Log
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.media.MediaRepository
import app.picnic.player.data.settings.SettingsStore
import app.picnic.player.data.settings.ThemeMusicVolume
import app.picnic.player.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class ThemeMusicPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository,
    private val settingsStore: SettingsStore,
    @ApplicationScope private val appScope: CoroutineScope
) {
    private var player: ExoPlayer? = null

    private var currentOwnerId: UUID? = null
    private var refCount = 0
    private var loadJob: Job? = null
    private var stopJob: Job? = null
    private var fadeJob: Job? = null

    private var fadeInPending = false
    private var targetVolume = 0f

    fun acquire(ownerId: UUID) {
        appScope.launch(Dispatchers.Main) {
            stopJob?.cancel()
            stopJob = null
            if (ownerId == currentOwnerId) {
                refCount++
                return@launch
            }
            loadJob?.cancel()
            currentOwnerId = ownerId
            refCount = 1
            loadJob = launch { startPlayback(ownerId) }
        }
    }

    fun release(ownerId: UUID) {
        appScope.launch(Dispatchers.Main) {
            if (ownerId != currentOwnerId) return@launch
            refCount--
            if (refCount > 0) return@launch
            stopJob = launch {
                delay(STOP_GRACE_MS)
                fadeOutAndStop()
            }
        }
    }

    fun stop() {
        appScope.launch(Dispatchers.Main) { fadeOutAndStop() }
    }

    private fun fadeOutAndStop() {
        loadJob?.cancel()
        loadJob = null
        stopJob?.cancel()
        stopJob = null
        currentOwnerId = null
        refCount = 0
        fadeInPending = false
        val current = player ?: return
        Log.d(TAG, "fading out and stopping")
        fadeJob?.cancel()
        fadeJob = appScope.launch(Dispatchers.Main) {
            rampVolume(current, 0f, FADE_OUT_STOP_MS)
            current.stop()
            current.clearMediaItems()
        }
    }

    private suspend fun startPlayback(ownerId: UUID) {
        player?.let { current ->
            fadeJob?.cancel()
            fadeInPending = false
            if (current.isPlaying) rampVolume(current, 0f, FADE_OUT_STOP_MS)
            current.stop()
            current.clearMediaItems()
        }
        val setting = settingsStore.settings.first().themeMusicVolume
        val level = setting.playerVolume
        if (level == null) {
            Log.d(TAG, "not playing for $ownerId: setting is $setting")
            return
        }
        val session = authRepository.activeSession()
        if (session == null) {
            Log.w(TAG, "not playing for $ownerId: no active session")
            return
        }
        val url = withContext(Dispatchers.IO) {
            runCatching { mediaRepository.themeSongUrl(ownerId) }
                .onFailure { Log.w(TAG, "theme lookup failed for $ownerId", it) }
                .getOrNull()
        }
        if (url == null) {
            Log.d(TAG, "no theme available for $ownerId")
            return
        }
        if (ownerId != currentOwnerId) return
        Log.d(TAG, "starting theme for $ownerId at $setting ($level)")
        targetVolume = level
        fadeInPending = true
        obtainPlayer().apply {
            volume = 0f
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            play()
        }
    }

    private fun obtainPlayer(): ExoPlayer = player ?: ExoPlayer.Builder(context).build().also { built ->
        player = built
        built.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) onReady()
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "theme playback failed", error)
                fadeInPending = false
                currentOwnerId = null
                refCount = 0
            }
        })
    }

    private fun onReady() {
        if (!fadeInPending) return
        fadeInPending = false
        val current = player ?: return
        Log.d(TAG, "ready, fading in to $targetVolume")
        fadeJob?.cancel()
        fadeJob = appScope.launch(Dispatchers.Main) {
            rampVolume(current, targetVolume, FADE_IN_MS)
            fadeOutBeforeTrackEnd(current)
        }
    }

    private suspend fun fadeOutBeforeTrackEnd(current: Player) {
        while (true) {
            val duration = current.duration
            if (duration != C.TIME_UNSET && duration > FADE_OUT_TRACK_END_MS && current.isPlaying) {
                val remaining = duration - current.currentPosition
                if (remaining in 1..FADE_OUT_TRACK_END_MS) {
                    Log.d(TAG, "track ending (remaining=${remaining}ms), fading out")
                    rampVolume(current, 0f, remaining)
                    return
                }
            }
            if (current.playbackState == Player.STATE_ENDED) return
            delay(END_WATCH_POLL_MS)
        }
    }

    private suspend fun rampVolume(current: Player, target: Float, durationMs: Long) {
        val start = current.volume
        if (durationMs < RAMP_STEP_MS || start == target) {
            current.volume = target
            return
        }
        val startRoot = sqrt(start)
        val targetRoot = sqrt(target)
        val steps = (durationMs / RAMP_STEP_MS).toInt()
        for (step in 1..steps) {
            delay(RAMP_STEP_MS)
            val root = startRoot + (targetRoot - startRoot) * step / steps
            current.volume = root * root
        }
    }

    private companion object {
        const val TAG = "PicnicThemeMusic"

        const val STOP_GRACE_MS = 200L
        const val FADE_IN_MS = 1000L

        const val FADE_OUT_STOP_MS = 300L

        const val FADE_OUT_TRACK_END_MS = 2500L
        const val RAMP_STEP_MS = 50L
        const val END_WATCH_POLL_MS = 250L

        val ThemeMusicVolume.playerVolume: Float?
            get() = when (this) {
                ThemeMusicVolume.DISABLED -> null
                ThemeMusicVolume.QUIET -> 0.05f
                ThemeMusicVolume.LOW -> 0.1f
                ThemeMusicVolume.MEDIUM -> 0.25f
                ThemeMusicVolume.HIGH -> 0.5f
                ThemeMusicVolume.LOUD -> 0.75f
            }
    }
}

val LocalThemeMusicPlayer = staticCompositionLocalOf<ThemeMusicPlayer> {
    error("ThemeMusicPlayer not provided")
}
