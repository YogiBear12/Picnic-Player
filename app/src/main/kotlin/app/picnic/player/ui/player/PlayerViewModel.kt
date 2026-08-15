@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.ui.SubtitleView
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.jellyfin.JellyfinImages
import app.picnic.player.data.playback.BlackBarProbe
import app.picnic.player.data.playback.DirectPlayVeto
import app.picnic.player.data.playback.MediaSegment
import app.picnic.player.data.playback.OUTRO_END_TOLERANCE_MS
import app.picnic.player.data.playback.PlayMethodKind
import app.picnic.player.data.playback.PlaybackPhase
import app.picnic.player.data.playback.PlaybackRepository
import app.picnic.player.data.playback.PlaybackTickInput
import app.picnic.player.data.playback.PlaybackTickSettings
import app.picnic.player.data.playback.PlayerCommand
import app.picnic.player.data.playback.PlayerCommandBus
import app.picnic.player.data.playback.RememberedTrack
import app.picnic.player.data.playback.SegmentKind
import app.picnic.player.data.playback.StreamInfo
import app.picnic.player.data.playback.TrackMemoryKind
import app.picnic.player.data.playback.Trickplay
import app.picnic.player.data.playback.TrickplayFrame
import app.picnic.player.data.playback.msToTicks
import app.picnic.player.data.playback.pickTracksWithMemory
import app.picnic.player.data.playback.playbackTick
import app.picnic.player.data.playback.quality.QualityOption
import app.picnic.player.data.playback.quality.QualityRung
import app.picnic.player.data.playback.quality.SourceQuality
import app.picnic.player.data.playback.quality.qualityOptions
import app.picnic.player.data.playback.refinePlayMethod
import app.picnic.player.data.playback.resolveLanguageCode
import app.picnic.player.data.playback.ticksToMs
import app.picnic.player.data.playback.trickplaySheetCacheKey
import app.picnic.player.data.settings.PlaybackSettings
import app.picnic.player.data.settings.SeriesTrackMemoryStore
import app.picnic.player.data.settings.SettingsStore
import app.picnic.player.data.settings.SubtitleArea
import app.picnic.player.di.ApplicationScope
import app.picnic.player.playback.AudioBoost
import app.picnic.player.playback.AudioRoute
import app.picnic.player.playback.AudioRoutePolicy
import app.picnic.player.playback.BlackBarTrack
import app.picnic.player.playback.BlackBars
import app.picnic.player.playback.CueLatchedBars
import app.picnic.player.playback.JellyfinTrackSelection
import app.picnic.player.playback.NightMode
import app.picnic.player.playback.PlaybackDiagnostics
import app.picnic.player.playback.PlaybackEngineFactory
import app.picnic.player.playback.PlaybackSessionController
import app.picnic.player.playback.SideloadedTrackId
import app.picnic.player.playback.SleepMode
import app.picnic.player.playback.SleepTimerState
import app.picnic.player.playback.StreamLoader
import app.picnic.player.playback.StreamRequest
import app.picnic.player.playback.StreamResult
import app.picnic.player.playback.StreamTarget
import app.picnic.player.playback.ThemeMusicPlayer
import app.picnic.player.playback.VideoDynamicRange
import app.picnic.player.playback.externalSubtitleCount
import app.picnic.player.ui.browse.ShortDateFormat
import app.picnic.player.ui.browse.TICKS_PER_MINUTE
import app.picnic.player.util.LanguageDisplay
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.TranscodingInfo

private const val LAST_FRAME_MS = 200L

private const val PLAY_METHOD_ATTEMPTS = 5
private const val AUDIO_ROUTE_SETTLE_MS = 1_500L

sealed interface PlayerNavEvent {
    data object Exit : PlayerNavEvent

    data class PlayNext(val itemId: String) : PlayerNavEvent
}

data class TrackOption(
    val id: String,
    val label: String?,
    val language: String?,
    val displayLanguage: String,
    val selected: Boolean
)

data class TrickplayPreview(
    val frame: TrickplayFrame,
    val fraction: Float
)

data class ChapterMark(
    val index: Int,
    val title: String,
    val startMs: Long,
    val imageUrl: String?
)

data class NextUpItem(
    val id: String,
    val title: String,
    val seasonEpisode: String,
    val meta: String,
    val overview: String,
    val backdropUrl: String?,
    val thumbUrl: String?
)

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val buffering: Boolean = true,
    val error: String? = null,
    val isLoading: Boolean = true,
    val backdropUrl: String? = null,
    val title: String = "",
    val subtitle: String = "",
    val audioTracks: List<TrackOption> = emptyList(),
    val subtitleTracks: List<TrackOption> = emptyList(),
    val selectedAudioId: String? = null,
    val selectedSubtitleId: String? = null,
    val subtitleCues: List<Cue> = emptyList(),
    val currentSegment: MediaSegment? = null,
    val nextUp: NextUpItem? = null,
    val endedAwaitingNext: Boolean = false,
    val videoStillPlaying: Boolean = false,
    val chapters: List<ChapterMark> = emptyList(),
    val subtitleDelayMs: Long = 0,
    val playbackSpeed: Float = 1.0f,
    val audioBoost: AudioBoost = AudioBoost.OFF,
    val nightMode: NightMode = NightMode.OFF,
    val sleep: SleepTimerState = SleepTimerState(),
    val showStatsForNerds: Boolean = false,
    val playMethod: PlayMethodKind? = null,
    val mediaSourceId: String? = null,
    val mediaSource: org.jellyfin.sdk.model.api.MediaSourceInfo? = null,
    val transcodingInfo: TranscodingInfo? = null,
    val videoDecoderName: String? = null,
    val audioDecoderName: String? = null,
    val estimatedBitrate: Long? = null,
    val notice: String? = null,
    val qualityOptions: List<QualityOption> = emptyList(),
    val streamRung: QualityRung? = null,
    val tracks: List<app.picnic.player.ui.player.osd.TrackSupport> = emptyList()
) {
    val activeQuality: QualityOption? get() = when {
        playMethod != PlayMethodKind.TRANSCODE -> QualityOption.Original
        streamRung != null -> QualityOption.Transcode(streamRung)
        else -> null
    }
}

@OptIn(FlowPreview::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val playbackRepository: PlaybackRepository,
    private val blackBarProbe: BlackBarProbe,
    private val authRepository: AuthRepository,
    private val mediaRepository: app.picnic.player.data.media.MediaRepository,
    private val settingsStore: SettingsStore,
    private val subtitleAppearanceEditor: app.picnic.player.data.settings.SubtitleAppearanceEditor,
    private val seriesTrackMemoryStore: SeriesTrackMemoryStore,
    private val sessionController: PlaybackSessionController,
    private val pictureInPictureSupport: app.picnic.player.data.device.PictureInPictureSupport,
    engineFactory: PlaybackEngineFactory,
    private val appForegroundState: app.picnic.player.data.socket.AppForegroundState,
    playerCommandBus: PlayerCommandBus,
    themeMusicPlayer: ThemeMusicPlayer,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    private val engine = engineFactory.create()

    val player: ExoPlayer get() = engine.player

    val settings: StateFlow<PlaybackSettings> =
        settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, PlaybackSettings())

    val pictureInPictureSupported: Boolean = pictureInPictureSupport.isSupported

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _navEvents = MutableSharedFlow<PlayerNavEvent>(extraBufferCapacity = 4)
    val navEvents: SharedFlow<PlayerNavEvent> = _navEvents.asSharedFlow()

    private var session: UserSession? = null
    private var stream: StreamInfo? = null
    private var itemId: UUID? = null
    private var seriesId: UUID? = null
    private var seasonId: UUID? = null
    private var itemType: BaseItemKind? = null
    private var mediaStreams: List<MediaStream> = emptyList()
    private var selectedAudioIndex: Int? = null
    private var selectedSubtitleIndex: Int? = null
    private val trickplay = MutableStateFlow<Trickplay?>(null)
    private val latchedBars = CueLatchedBars()
    private var loaded = false

    private val viewingJob = SupervisorJob(viewModelScope.coroutineContext[Job])
    private val viewingScope = CoroutineScope(viewModelScope.coroutineContext + viewingJob)

    private var ticker: Job? = null
    private var progressJob: Job? = null
    private var trickplayPrefetchJob: Job? = null
    private var reloadJob: Job? = null

    private val streamTarget = object : StreamTarget {
        override val positionMs: Long get() = player.currentPosition

        override fun stop() = player.stop()

        override fun load(stream: StreamInfo, resumeMs: Long) {
            PlaybackDiagnostics.logStream(stream)
            PlaybackDiagnostics.log("loading at resumeMs=$resumeMs directPlayAllowed=${directPlayVeto.allowsDirectPlay}")
            player.setMediaItem(mediaItemFor(stream))
            player.prepare()
            pendingSeekMs = resumeMs
        }

        override fun resume(playing: Boolean) {
            player.playWhenReady = playing
        }
    }

    private val streamLoader = StreamLoader(streamTarget, playbackRepository)

    private var pendingSeekMs: Long = 0

    private var canTranscode = true

    private val directPlayVeto = DirectPlayVeto()
    private var appliedAudioRoute = AudioRoute.NATIVE
    private var inPictureInPicture = false
    private var tornDown = false
    private var hasPresentedFirstFrame = false

    private var segments: List<MediaSegment> = emptyList()
    private val autoSkipped = mutableSetOf<String>()

    private var outroNextUpShown = false

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            applyPendingSeek()
            if (playbackState == Player.STATE_READY) recoverIfNothingToPlay()
        }

        override fun onEvents(player: Player, events: Player.Events) {
            pushState()
            if (events.containsAny(Player.EVENT_TRACKS_CHANGED)) {
                _state.update { it.copy(tracks = app.picnic.player.ui.player.osd.checkForSupport(player.currentTracks)) }
            }
        }
        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            applyTrackSelections()
        }
        override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
            latchedBars.onCueBoundary(player.currentPosition)
            _state.update { it.copy(subtitleCues = cueGroup.cues) }
        }
        override fun onPlayerError(error: PlaybackException) {
            if (directPlayVeto.onPlaybackError(error.errorCode)) {
                reload(
                    quality = sessionController.qualityOverride.value,
                    failureNotice = "Couldn't play this file"
                )
                return
            }
            _state.update { it.copy(error = error.message ?: "Playback error", isLoading = false) }
        }
    }

    // media3 raises nothing for a container it took no tracks from — it reaches READY and the position
    // advances over a black screen — so the empty track list is the only signal there is.
    private fun recoverIfNothingToPlay() {
        if (player.currentTracks.groups.isNotEmpty()) return
        PlaybackDiagnostics.log("prepared with no playable tracks; remuxAlreadyTried=${!directPlayVeto.allowsDirectPlay}")
        if (directPlayVeto.onNoPlayableTracks()) {
            reload(
                quality = sessionController.qualityOverride.value,
                failureNotice = "Couldn't play this file"
            )
        } else {
            _state.update { it.copy(error = "This file has no playable video or audio", isLoading = false) }
        }
    }

    private fun resumeAwarePositionMs(): Long = maxOf(player.currentPosition, pendingSeekMs)

    private fun applyPendingSeek() {
        val target = pendingSeekMs
        if (target <= 0) return
        if (player.playbackState != Player.STATE_READY || player.duration <= 0) return
        pendingSeekMs = 0
        player.seekTo(target)
    }

    private fun getDecoderString(decoderName: String): String = try {
        val codecInfo = android.media.MediaCodecList(android.media.MediaCodecList.ALL_CODECS)
            .codecInfos
            .firstOrNull { !it.isEncoder && it.name == decoderName }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && codecInfo?.isHardwareAccelerated == true) {
            "$decoderName (HW)"
        } else {
            decoderName
        }
    } catch (e: Exception) {
        decoderName
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            _state.update { it.copy(videoDecoderName = getDecoderString(decoderName)) }
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            _state.update { it.copy(audioDecoderName = getDecoderString(decoderName)) }
        }

        override fun onBandwidthEstimate(
            eventTime: AnalyticsListener.EventTime,
            totalLoadTimeMs: Int,
            totalBytesLoaded: Long,
            bitrateEstimate: Long
        ) {
            _state.update { it.copy(estimatedBitrate = bitrateEstimate) }
        }
    }

    private var transcodingInfoJob: Job? = null
    private var playMethodJob: Job? = null

    init {
        themeMusicPlayer.stop()
        viewModelScope.launch {
            combine(
                trickplay,
                settings.map { it.subtitleAppearance.area }.distinctUntilChanged(),
                ::Pair
            ).collectLatest { (trickplay, area) ->
                val sheets = trickplay?.sheets().orEmpty()
                val track = if (area == SubtitleArea.AUTOMATIC && sheets.isNotEmpty()) {
                    trickplayPrefetchJob?.join()
                    blackBarProbe.detect(sheets)
                } else {
                    BlackBarTrack.None
                }
                latchedBars.setTrack(track, player.currentPosition)
            }
        }
        player.addListener(listener)
        player.addAnalyticsListener(analyticsListener)
        if (PlaybackDiagnostics.enabled) {
            player.addAnalyticsListener(PlaybackDiagnostics)
            PlaybackDiagnostics.logRenderers(player)
        }
        appliedAudioRoute = AudioRoutePolicy.requiredRoute(
            sessionController.audioBoost.value,
            sessionController.nightMode.value,
            _state.value.playbackSpeed
        )
        engine.setAudioRoute(appliedAudioRoute)
        viewModelScope.launch {
            combine(
                sessionController.audioBoost,
                sessionController.nightMode,
                _state.map { it.playbackSpeed }.distinctUntilChanged()
            ) { boost, night, speed -> AudioRoutePolicy.requiredRoute(boost, night, speed) }
                .distinctUntilChanged()
                .debounce(AUDIO_ROUTE_SETTLE_MS)
                .collect(::applyAudioRoute)
        }
        viewModelScope.launch {
            sessionController.audioBoost.collect { level ->
                engine.audioEffects.setBoostMillibels(level.gainMb)
                _state.update { it.copy(audioBoost = level) }
            }
        }
        viewModelScope.launch {
            sessionController.nightMode.collect { level ->
                engine.audioEffects.setNightMode(level.strength)
                _state.update { it.copy(nightMode = level) }
            }
        }
        viewModelScope.launch {
            sessionController.sleep.collect { s -> _state.update { it.copy(sleep = s) } }
        }
        viewModelScope.launch {
            sessionController.sleepExpired.collect { player.playWhenReady = false }
        }
        viewModelScope.launch {
            playerCommandBus.commands.collect { applyRemoteCommand(it) }
        }
        viewModelScope.launch {
            appForegroundState.isVisible.collect { visible ->
                if (!visible && !inPictureInPicture) {
                    endViewing()
                    _navEvents.tryEmit(PlayerNavEvent.Exit)
                }
            }
        }
    }

    fun onPipModeChanged(inPip: Boolean) {
        inPictureInPicture = inPip
    }

    private fun applyRemoteCommand(command: PlayerCommand) {
        when (command) {
            PlayerCommand.Stop -> {
                player.pause()
                _navEvents.tryEmit(PlayerNavEvent.Exit)
            }
            PlayerCommand.Pause -> player.pause()
            PlayerCommand.Unpause -> player.play()
            PlayerCommand.PlayPause -> playPause()
            is PlayerCommand.Seek -> seekTo(command.positionMs)
            PlayerCommand.Rewind -> seekBy(-settings.value.skipBackwardSeconds * 1000L)
            PlayerCommand.FastForward -> seekBy(settings.value.skipForwardSeconds * 1000L)
            PlayerCommand.NextTrack ->
                nextItemId()?.let { _navEvents.tryEmit(PlayerNavEvent.PlayNext(it)) }
            PlayerCommand.PreviousTrack -> Unit
            is PlayerCommand.SetAudioIndex -> selectAudio(command.index.toString())
            is PlayerCommand.SetSubtitleIndex -> selectSubtitle(command.index?.toString())
        }
    }

    val videoDynamicRange: StateFlow<VideoDynamicRange> = engine.videoDynamicRange

    val blackBars: StateFlow<BlackBars> = latchedBars.bars

    fun attachSubtitleView(
        subtitleView: SubtitleView,
        bottomPaddingFraction: Float,
        range: VideoDynamicRange
    ) = engine.attachSubtitleView(
        subtitleView,
        settings.value.subtitleAppearance,
        bottomPaddingFraction,
        range
    )

    fun assOverlayView(context: Context) = engine.assOverlayView(context)

    fun cycleSubtitleSize(forward: Boolean) = viewModelScope.launch { subtitleAppearanceEditor.cycleSize(forward) }

    fun cycleSubtitleColour(forward: Boolean) = viewModelScope.launch { subtitleAppearanceEditor.cycleColour(forward) }

    fun cycleSubtitleBackground(forward: Boolean) = viewModelScope.launch { subtitleAppearanceEditor.cycleBackground(forward) }

    fun cycleSubtitleBackgroundFill(forward: Boolean) = viewModelScope.launch { subtitleAppearanceEditor.cycleBackgroundFill(forward) }

    fun cycleSubtitleArea(forward: Boolean) = viewModelScope.launch { subtitleAppearanceEditor.cycleArea(forward) }

    fun stepSubtitleInset(forward: Boolean) = viewModelScope.launch { subtitleAppearanceEditor.stepInset(forward) }

    fun load(itemIdString: String, startTicks: Long?, mediaSourceId: String? = null) {
        if (loaded) return
        loaded = true
        val id = UUID.fromString(itemIdString)
        itemId = id
        outroNextUpShown = false
        viewingScope.launch {
            val activeSession = authRepository.activeSession()
            if (activeSession == null) {
                _state.update { it.copy(error = "No active session", buffering = false, isLoading = false) }
                return@launch
            }
            session = activeSession

            launch { ensureTranscodePermission(activeSession) }

            val itemDeferred = async {
                runCatching { mediaRepository.item(activeSession, id) }.getOrNull()
            }

            launch {
                segments = runCatching { playbackRepository.mediaSegments(activeSession, id) }.getOrDefault(emptyList())
                autoSkipped.clear()
            }

            val result = streamLoader.load(
                StreamRequest(
                    session = activeSession,
                    itemId = id,
                    seriesId = seriesId,
                    positionTicks = startTicks ?: 0L,
                    mediaSourceId = mediaSourceId,
                    quality = sessionController.qualityOverride.value,
                    audioStreamIndex = selectedAudioIndex,
                    subtitleStreamIndex = selectedSubtitleIndex,
                    resumePlaying = true,
                    replacing = null
                )
            )
            if (result !is StreamResult.Loaded) {
                _state.update { it.copy(error = "Could not start playback", buffering = false, isLoading = false) }
                return@launch
            }
            adopt(result.stream, subtitleDelayMs = 0, speed = 1.0f)
            itemDeferred.await()?.let { applyItemMetadata(activeSession, id, it) }
            initDefaultTrackIndices()
            rebuildTrackOptions()
            _state.update { it.copy(subtitleDelayMs = 0, playbackSpeed = 1.0f, showStatsForNerds = false) }
            startTicker()
            startProgressReports()
        }
    }

    fun playPause() {
        player.playWhenReady = !player.playWhenReady
    }

    fun seekTo(positionMs: Long) = player.seekTo(positionMs.coerceAtLeast(0))

    fun seekBy(deltaMs: Long) = player.seekTo((player.currentPosition + deltaMs).coerceAtLeast(0))

    fun setSubtitleDelayMs(ms: Long) {
        engine.setSubtitleDelayMs(ms)
        _state.update { it.copy(subtitleDelayMs = ms) }
    }

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _state.update { it.copy(playbackSpeed = speed) }
    }

    fun setAudioBoost(level: AudioBoost) = sessionController.setAudioBoost(level)
    fun setNightMode(level: NightMode) = sessionController.setNightMode(level)
    fun setSleep(mode: SleepMode) = sessionController.setSleep(mode)

    fun toggleStatsForNerds() {
        _state.update { it.copy(showStatsForNerds = !it.showStatsForNerds) }
        updateTranscodingInfoJob()
    }

    private fun updateTranscodingInfoJob() {
        val pipelineTranscode = stream?.playMethod == PlayMethodKind.TRANSCODE
        if (_state.value.showStatsForNerds && pipelineTranscode) {
            if (transcodingInfoJob == null) {
                transcodingInfoJob = viewingScope.launch {
                    val session = authRepository.activeSession() ?: return@launch
                    while (isActive) {
                        refreshTranscodingInfoOnce(
                            session,
                            mediaSourceId = stream?.mediaSourceId,
                            initialMethod = stream?.playMethod
                                ?: PlayMethodKind.TRANSCODE
                        )
                        delay(2.seconds)
                    }
                }
            }
        } else {
            transcodingInfoJob?.cancel()
            transcodingInfoJob = null
        }
    }

    private suspend fun refreshTranscodingInfoOnce(
        session: UserSession,
        mediaSourceId: String?,
        initialMethod: PlayMethodKind
    ): Boolean {
        val info = playbackRepository.getTranscodingInfo(session, mediaSourceId) ?: return false
        _state.update {
            it.copy(
                transcodingInfo = info,
                playMethod = refinePlayMethod(initialMethod, info)
            )
        }
        return true
    }

    private fun settlePlayMethod(info: StreamInfo) {
        if (info.playMethod != PlayMethodKind.TRANSCODE) return
        playMethodJob?.cancel()
        playMethodJob = viewingScope.launch {
            val activeSession = session ?: return@launch
            repeat(PLAY_METHOD_ATTEMPTS) {
                if (refreshTranscodingInfoOnce(activeSession, info.mediaSourceId, PlayMethodKind.TRANSCODE)) {
                    return@launch
                }
                delay(1.seconds)
            }
        }
    }

    fun endViewing() {
        sessionController.reset()
        endPlayback()
    }

    fun trickplayFor(positionMs: Long): TrickplayFrame? = trickplay.value?.frameFor(positionMs)

    fun selectAudio(streamIndex: String) {
        selectedAudioIndex = streamIndex.toIntOrNull() ?: return
        persistOsdTrackMemory(audio = true)
        if (isConverting()) renegotiate() else applyTrackSelections()
    }

    fun selectSubtitle(streamIndex: String?) {
        val previous = selectedSubtitleIndex
        selectedSubtitleIndex = streamIndex?.toIntOrNull()
        persistOsdTrackMemory(audio = false)
        val burnedIn = isBurnedIn(previous) || isBurnedIn(selectedSubtitleIndex)
        if (isConverting() && burnedIn) renegotiate() else applyTrackSelections()
    }

    private fun isBurnedIn(streamIndex: Int?): Boolean {
        val index = streamIndex ?: return false
        return mediaStreams.firstOrNull { it.index == index }?.deliveryMethod ==
            org.jellyfin.sdk.model.api.SubtitleDeliveryMethod.ENCODE
    }

    private fun isConverting() = stream?.playMethod == PlayMethodKind.TRANSCODE

    private fun renegotiate() = reload(sessionController.qualityOverride.value)

    fun clearNotice() = _state.update { it.copy(notice = null) }

    fun selectQuality(option: QualityOption) {
        if (option == _state.value.activeQuality) return
        val previous = sessionController.qualityOverride.value
        sessionController.setQualityOverride(option)
        reload(option, revertTo = previous)
    }

    private fun applyAudioRoute(required: AudioRoute) {
        val reloadNeeded = AudioRoutePolicy.reloadNeeded(
            required = required,
            applied = appliedAudioRoute,
            trackCanPassThrough = engine.currentAudioCanPassThrough()
        )
        appliedAudioRoute = required
        engine.setAudioRoute(required)
        if (reloadNeeded) {
            reload(sessionController.qualityOverride.value, failureNotice = "Couldn't change audio")
        }
    }

    private fun reload(
        quality: QualityOption?,
        revertTo: QualityOption? = quality,
        failureNotice: String = "Couldn't change quality"
    ) {
        val activeSession = session ?: return
        val id = itemId ?: return
        val current = stream
        val delayMs = _state.value.subtitleDelayMs
        val speed = _state.value.playbackSpeed

        val resumeMs = resumeAwarePositionMs()

        reloadJob?.cancel()
        reloadJob = viewingScope.launch {
            _state.update {
                it.copy(buffering = true, error = null, notice = null, subtitleCues = emptyList())
            }
            val result = streamLoader.load(
                StreamRequest(
                    session = activeSession,
                    itemId = id,
                    seriesId = seriesId,
                    positionTicks = resumeMs.msToTicks(),
                    mediaSourceId = current?.mediaSourceId,
                    quality = quality,
                    audioStreamIndex = selectedAudioIndex,
                    subtitleStreamIndex = selectedSubtitleIndex,
                    resumePlaying = player.playWhenReady,
                    replacing = current,
                    allowDirectPlay = directPlayVeto.allowsDirectPlay
                )
            )
            when (result) {
                is StreamResult.Loaded -> adopt(result.stream, delayMs, speed)
                is StreamResult.Failed -> {
                    sessionController.setQualityOverride(revertTo)
                    _state.update { it.copy(notice = failureNotice) }
                    result.restored?.let { adopt(it, delayMs, speed) }
                }
            }
        }
    }

    private fun adopt(info: StreamInfo, subtitleDelayMs: Long, speed: Float) {
        stream = info
        mediaStreams = info.mediaStreams
        refreshQualityOptions(info)
        rebuildTrackOptions()
        engine.setSubtitleDelayMs(subtitleDelayMs)
        player.setPlaybackSpeed(speed)
        _state.update {
            it.copy(
                isLoading = false,
                playMethod = info.playMethod,
                mediaSourceId = info.mediaSourceId,
                mediaSource = info.mediaSource,
                transcodingInfo = null
            )
        }
        settlePlayMethod(info)
        updateTranscodingInfoJob()
    }

    private var serverAudioLanguage: String? = null
    private var serverSubtitleLanguage: String? = null
    private var serverLanguagePrefsLoaded = false

    private suspend fun ensureTranscodePermission(session: UserSession) {
        canTranscode = runCatching { mediaRepository.canTranscodeVideo(session) }.getOrDefault(true)
    }

    private suspend fun ensureServerLanguagePrefs() {
        if (serverLanguagePrefsLoaded) return
        serverLanguagePrefsLoaded = true
        val session = authRepository.activeSession() ?: return
        val config = runCatching { mediaRepository.userConfiguration(session) }.getOrNull()
        serverAudioLanguage = config?.audioLanguagePreference
        serverSubtitleLanguage = config?.subtitleLanguagePreference
    }

    private suspend fun initDefaultTrackIndices() {
        val prefs = settings.value
        ensureServerLanguagePrefs()
        val deviceLanguage = java.util.Locale.getDefault().language
        val memory = if (itemType == BaseItemKind.EPISODE) {
            seriesId?.let { sid ->
                seriesTrackMemoryStore.effectiveMemory(sid.toString(), seasonId?.toString())
            }
        } else {
            null
        }
        val pick = pickTracksWithMemory(
            streams = mediaStreams,
            memory = memory,
            preferredAudioLanguage = resolveLanguageCode(
                prefs.preferredAudioLanguage,
                serverAudioLanguage,
                deviceLanguage
            ),
            preferredSubtitleLanguage = resolveLanguageCode(
                prefs.preferredSubtitleLanguage,
                serverSubtitleLanguage,
                deviceLanguage
            ),
            deviceSubtitleLanguage = deviceLanguage,
            alwaysDisplaySubtitles = prefs.alwaysDisplaySubtitles,
            preferDefaultAudioTrack = prefs.preferDefaultAudioTrack
        )
        selectedAudioIndex = pick.audioIndex
        selectedSubtitleIndex = pick.subtitleIndex
    }

    private fun persistOsdTrackMemory(audio: Boolean) {
        if (itemType != BaseItemKind.EPISODE) return
        val series = seriesId ?: return
        val season = seasonId?.toString()
        val pick = if (audio) {
            val stream = mediaStreams.firstOrNull {
                it.type == MediaStreamType.AUDIO && it.index == selectedAudioIndex
            } ?: return
            RememberedTrack.of(stream) ?: return
        } else {
            val index = selectedSubtitleIndex
            if (index == null) {
                RememberedTrack.off()
            } else {
                val stream = mediaStreams.firstOrNull {
                    it.type == MediaStreamType.SUBTITLE && it.index == index
                } ?: return
                RememberedTrack.of(stream) ?: return
            }
        }
        val kind = if (audio) TrackMemoryKind.AUDIO else TrackMemoryKind.SUBTITLE
        viewModelScope.launch {
            seriesTrackMemoryStore.rememberOsdPick(
                seriesId = series.toString(),
                seasonId = season,
                kind = kind,
                pick = pick
            )
        }
    }

    private fun applyItemMetadata(activeSession: UserSession, id: UUID, item: BaseItemDto) {
        seriesId = item.seriesId
        seasonId = item.seasonId
        itemType = item.type
        val sheets = playbackRepository.trickplayFromItem(item)?.let { (sheetWidth, tiles) ->
            Trickplay(tiles) { tileIndex ->
                playbackRepository.trickplayTileUrl(activeSession, id, sheetWidth, tileIndex)
            }
        }
        evictTrickplaySheets(trickplay.value)
        prefetchTrickplayTiles(sheets?.tileUrls().orEmpty())
        trickplay.value = sheets
        val chapterMarks = item.chapters?.mapIndexed { index, ch ->
            ChapterMark(
                index = index,
                title = ch.name?.takeIf { it.isNotBlank() } ?: "Chapter ${index + 1}",
                startMs = ch.startPositionTicks.ticksToMs(),
                imageUrl = ch.imageTag?.let {
                    playbackRepository.chapterImageUrl(activeSession, id, index, it)
                }
            )
        }.orEmpty()
        _state.update {
            it.copy(
                title = item.seriesName ?: item.name.orEmpty(),
                subtitle = episodeOsdLine(item),
                backdropUrl = JellyfinImages.backdrop(activeSession, item),
                chapters = chapterMarks
            )
        }
        if (item.type == BaseItemKind.EPISODE) {
            viewingScope.launch {
                val next = runCatching {
                    mediaRepository.nextEpisode(activeSession, id)
                }.getOrNull()
                if (next != null) {
                    _state.update { s ->
                        s.copy(nextUp = buildNextUpItem(activeSession, next))
                    }
                }
            }
        }
    }

    private fun refreshQualityOptions(info: StreamInfo) {
        val prefs = settings.value
        val options = if (!canTranscode) {
            emptyList()
        } else {
            qualityOptions(
                SourceQuality.of(info.mediaSource?.bitrate, info.mediaStreams),
                QualityRung.conversionCeiling(prefs.allowFourKTranscoding)
            )
        }
        _state.update { it.copy(qualityOptions = options, streamRung = info.rung) }
    }

    private fun mediaItemFor(info: StreamInfo): MediaItem {
        val subtitles = info.externalSubtitles.map { subtitle ->
            MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitle.url))
                .setId(SideloadedTrackId.of(subtitle.streamIndex))
                .setMimeType(subtitle.mimeType)
                .setLanguage(subtitle.language)
                .setLabel(subtitle.title)
                .build()
        }
        return MediaItem.Builder()
            .setUri(info.url)
            .setSubtitleConfigurations(subtitles)
            .build()
    }

    private fun applyTrackSelections() {
        if (mediaStreams.isEmpty()) return
        if (isConverting() && isBurnedIn(selectedSubtitleIndex)) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            rebuildTrackOptions()
            return
        }
        val result = JellyfinTrackSelection.createTrackSelections(
            trackSelectionParams = player.trackSelectionParameters,
            tracks = player.currentTracks,
            supportsDirectPlay = !isConverting(),
            audioIndex = selectedAudioIndex,
            subtitleIndex = selectedSubtitleIndex,
            mediaStreams = mediaStreams
        )
        PlaybackDiagnostics.logTrackSelectionOutcome(
            result = result,
            audioIndex = selectedAudioIndex,
            subtitleIndex = selectedSubtitleIndex,
            externalSubtitleCount = mediaStreams.externalSubtitleCount,
            supportsDirectPlay = !isConverting()
        )
        if (result.bothSelected) {
            player.trackSelectionParameters = result.trackSelectionParameters
        }
        rebuildTrackOptions()
    }

    private fun rebuildTrackOptions() {
        if (mediaStreams.isEmpty()) return
        val audio = mediaStreams
            .filter { it.type == MediaStreamType.AUDIO }
            .map { stream -> stream.toTrackOption(stream.index == selectedAudioIndex) }
        val subtitle = mediaStreams
            .filter { it.type == MediaStreamType.SUBTITLE }
            .map { stream -> stream.toTrackOption(stream.index == selectedSubtitleIndex) }
        _state.update {
            it.copy(
                audioTracks = audio,
                subtitleTracks = subtitle,
                selectedAudioId = selectedAudioIndex?.toString(),
                selectedSubtitleId = selectedSubtitleIndex?.toString()
            )
        }
    }

    private fun MediaStream.toTrackOption(selected: Boolean): TrackOption {
        val (languageLine, secondary) = LanguageDisplay.trackLines(
            LanguageDisplay.name(language),
            displayTitle
        )
        return TrackOption(
            id = index.toString(),
            label = secondary,
            language = language,
            displayLanguage = languageLine,
            selected = selected
        )
    }

    private fun evictTrickplaySheets(sheets: Trickplay?) {
        val urls = sheets?.tileUrls().orEmpty()
        if (urls.isEmpty()) return
        val diskCache = appContext.imageLoader.diskCache ?: return
        appScope.launch {
            urls.forEach { url -> runCatching { diskCache.remove(trickplaySheetCacheKey(url)) } }
        }
    }

    private fun prefetchTrickplayTiles(urls: List<String>) {
        trickplayPrefetchJob?.cancel()
        trickplayPrefetchJob = null
        if (urls.isEmpty()) return
        trickplayPrefetchJob = viewingScope.launch {
            val loader = appContext.imageLoader
            for (url in urls) {
                if (!isActive) return@launch
                loader.execute(
                    ImageRequest.Builder(appContext)
                        .data(url)
                        .diskCacheKey(trickplaySheetCacheKey(url))
                        .size(Size.ORIGINAL)
                        .build()
                )
            }
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewingScope.launch {
            while (isActive) {
                pushState()
                delay(500)
            }
        }
    }

    private fun startProgressReports() {
        progressJob?.cancel()
        progressJob = viewingScope.launch {
            while (isActive) {
                delay(10_000)
                val s = session ?: continue
                val info = stream ?: continue
                val id = itemId ?: continue
                runCatching {
                    playbackRepository.reportProgress(
                        s,
                        info,
                        id,
                        resumeAwarePositionMs().msToTicks(),
                        !player.isPlaying
                    )
                }
            }
        }
    }

    private fun pushState() {
        val current = _state.value
        val decision = playbackTick(
            PlaybackTickInput(
                positionMs = player.currentPosition,
                durationMs = player.duration,
                bufferedMs = player.bufferedPosition,
                isPlaying = player.isPlaying,
                phase = player.playbackState.toPlaybackPhase(),
                segments = segments,
                settings = settings.value.let {
                    PlaybackTickSettings(
                        introAction = it.introAction,
                        recapAction = it.recapAction,
                        outroAction = it.outroAction,
                        previewAction = it.previewAction,
                        commercialAction = it.commercialAction,
                        displayNextUpDuringOutro = it.displayNextUpDuringOutro
                    )
                },
                sleepMode = sessionController.sleep.value.mode,
                hasNextUp = current.nextUp != null,
                hasPresentedFirstFrame = hasPresentedFirstFrame,
                autoSkippedIds = autoSkipped,
                outroNextUpShown = outroNextUpShown,
                prevEndedAwaitingNext = current.endedAwaitingNext,
                prevVideoStillPlaying = current.videoStillPlaying,
                hasError = current.error != null
            )
        )

        if (decision.presentFirstFrame) hasPresentedFirstFrame = true
        decision.autoSkipToMs?.let { player.seekTo(it) }
        decision.markSkippedId?.let { autoSkipped += it }
        if (decision.setOutroNextUpShown) outroNextUpShown = true
        if (decision.pauseForSleep) {
            player.playWhenReady = false
            sessionController.cancelSleep()
        }

        _state.update {
            it.copy(
                isPlaying = decision.isPlaying,
                positionMs = decision.positionMs,
                durationMs = decision.durationMs,
                bufferedMs = decision.bufferedMs,
                buffering = decision.buffering,
                isLoading = decision.isLoading,
                currentSegment = decision.currentSegment,
                endedAwaitingNext = decision.endedAwaitingNext,
                videoStillPlaying = decision.videoStillPlaying
            )
        }
    }

    private fun Int.toPlaybackPhase(): PlaybackPhase = when (this) {
        Player.STATE_IDLE -> PlaybackPhase.IDLE
        Player.STATE_BUFFERING -> PlaybackPhase.BUFFERING
        Player.STATE_READY -> PlaybackPhase.READY
        Player.STATE_ENDED -> PlaybackPhase.ENDED
        else -> PlaybackPhase.OTHER
    }

    fun skipCurrentSegment() {
        val seg = _state.value.currentSegment ?: return
        val duration = player.duration
        if (seg.kind == SegmentKind.OUTRO &&
            duration > 0 &&
            seg.endMs >= duration - OUTRO_END_TOLERANCE_MS
        ) {
            player.seekTo((duration - LAST_FRAME_MS).coerceAtLeast(0))
        } else {
            player.seekTo(seg.endMs)
        }
    }

    fun nextItemId(): String? = _state.value.nextUp?.id

    fun dismissNextUp() {
        _state.update { it.copy(endedAwaitingNext = false, videoStillPlaying = false) }
    }

    fun onAutoplayHandoff() = sessionController.handOffToNextItem()

    fun endPlayback() {
        if (tornDown) return
        tornDown = true
        sessionController.playerTornDown()
        viewingJob.cancelChildren()
        evictTrickplaySheets(trickplay.value)
        trickplay.value = null
        val s = session
        val info = stream
        val id = itemId
        val series = seriesId
        val positionTicks = resumeAwarePositionMs().msToTicks()
        player.removeListener(listener)
        engine.release()
        if (s != null && info != null && id != null) {
            appScope.launch {
                playbackRepository.stopEncoding(s, info.playSessionId)
                runCatching { playbackRepository.reportStopped(s, info, id, positionTicks, series) }
            }
        }
    }

    override fun onCleared() = endPlayback()
}

private fun buildNextUpItem(session: UserSession, item: BaseItemDto): NextUpItem {
    val seasonNum = item.parentIndexNumber
    val epNum = item.indexNumber
    val seasonEpisode = if (seasonNum != null && epNum != null) "S$seasonNum E$epNum" else ""

    val metaParts = mutableListOf<String>()
    item.premiereDate?.let { date ->
        runCatching { metaParts += date.format(ShortDateFormat) }
    }
    item.runTimeTicks?.takeIf { it > 0L }?.let { ticks ->
        metaParts += "${(ticks / TICKS_PER_MINUTE).coerceAtLeast(1)}m"
    }
    item.communityRating?.let { rating ->
        metaParts += "${"%.1f".format(rating)} ★"
    }

    return NextUpItem(
        id = item.id.toString(),
        title = item.name.orEmpty(),
        seasonEpisode = seasonEpisode,
        meta = metaParts.joinToString(" • "),
        overview = item.overview.orEmpty(),
        backdropUrl = JellyfinImages.backdrop(session, item),
        thumbUrl = JellyfinImages.thumb(session, item)
    )
}
