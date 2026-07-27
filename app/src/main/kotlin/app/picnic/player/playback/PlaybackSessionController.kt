package app.picnic.player.playback

import app.picnic.player.data.playback.quality.QualityOption
import app.picnic.player.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Audio-boost level. [gainMb] is the LoudnessEnhancer target gain in millibels (100 mB = 1 dB). */
enum class AudioBoost(val gainMb: Int) { OFF(0), LOW(400), MED(800), HIGH(1300) }

/** Night-mode compression level. [strength] feeds the DynamicsProcessing config (0/1/2). */
enum class NightMode(val strength: Int) { OFF(0), LIGHT(1), STRONG(2) }

/**
 * Sleep-timer mode. [durationMinutes] is null for the end-of-X modes, which are resolved at
 * playback-end rather than by wall clock.
 */
enum class SleepMode(val durationMinutes: Int?) {
    OFF(null),
    MIN_15(15),
    MIN_30(30),
    MIN_45(45),
    MIN_60(60),
    END_OF_EPISODE(null),
    END_OF_QUEUE(null)
}

data class SleepTimerState(
    val mode: SleepMode = SleepMode.OFF,
    /** Wall-clock fire time for duration modes; null otherwise. */
    val endTimeMs: Long? = null,
    /** Remaining ms for duration modes; 0 otherwise. */
    val remainingMs: Long = 0
) {
    val active: Boolean get() = mode != SleepMode.OFF
}

/**
 * App-scoped holder for the in-player session controls that must outlive a single item: audio
 * enhancement (boost + night mode) and the sleep timer. Being a [Singleton], it survives the
 * per-item [PlayerViewModel]s created during autoplay, so settings persist across episode
 * transitions and reset only when the player session truly ends ([reset]).
 *
 * The DSP is applied by [PlaybackEngine]; this class only holds intent + drives the sleep clock.
 */
@Singleton
class PlaybackSessionController @Inject constructor(
    @ApplicationScope private val appScope: CoroutineScope
) {
    private val _audioBoost = MutableStateFlow(AudioBoost.OFF)
    val audioBoost: StateFlow<AudioBoost> = _audioBoost.asStateFlow()

    private val _nightMode = MutableStateFlow(NightMode.OFF)
    val nightMode: StateFlow<NightMode> = _nightMode.asStateFlow()

    private val _qualityOverride = MutableStateFlow<QualityOption?>(null)
    val qualityOverride: StateFlow<QualityOption?> = _qualityOverride.asStateFlow()

    private val _sleep = MutableStateFlow(SleepTimerState())
    val sleep: StateFlow<SleepTimerState> = _sleep.asStateFlow()

    /** Emits when a duration sleep timer reaches zero — the player should pause. */
    private val _sleepExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sleepExpired: SharedFlow<Unit> = _sleepExpired.asSharedFlow()

    private var tickJob: Job? = null
    private var handingOff = false

    fun setAudioBoost(level: AudioBoost) {
        _audioBoost.value = level
    }
    fun setNightMode(level: NightMode) {
        _nightMode.value = level
    }

    fun setQualityOverride(option: QualityOption?) {
        _qualityOverride.value = option
    }

    /** The next player belongs to the same viewing, so its session controls carry over. */
    fun handOffToNextItem() {
        handingOff = true
    }

    /**
     * A player was torn down. Anything scoped to one viewing is dropped unless the next item is
     * already taking over, which is how a quality choice survives an autoplayed episode but not a
     * return to browsing — however the player was left.
     */
    fun playerTornDown() {
        if (handingOff) {
            handingOff = false
        } else {
            _qualityOverride.value = null
        }
    }

    fun setSleep(mode: SleepMode) {
        tickJob?.cancel()
        when (val minutes = mode.durationMinutes) {
            null -> _sleep.value = SleepTimerState(mode = mode, endTimeMs = null, remainingMs = 0)
            else -> {
                val end = System.currentTimeMillis() + minutes * 60_000L
                _sleep.value = SleepTimerState(mode = mode, endTimeMs = end, remainingMs = end - System.currentTimeMillis())
                startTick(end)
            }
        }
    }

    fun cancelSleep() {
        tickJob?.cancel()
        _sleep.value = SleepTimerState()
    }

    private fun startTick(endTimeMs: Long) {
        tickJob = appScope.launch {
            while (isActive) {
                val remaining = endTimeMs - System.currentTimeMillis()
                if (remaining <= 0) {
                    _sleep.value = SleepTimerState()
                    _sleepExpired.tryEmit(Unit)
                    return@launch
                }
                _sleep.value = _sleep.value.copy(remainingMs = remaining)
                delay(1_000)
            }
        }
    }

    /** Full session teardown: clear audio enhancement and cancel any sleep timer. */
    fun reset() {
        _audioBoost.value = AudioBoost.OFF
        _nightMode.value = NightMode.OFF
        _qualityOverride.value = null
        cancelSleep()
    }
}
