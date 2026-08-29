@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.ui.SubtitleView
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.jellyfin.JellyfinImages
import app.picnic.player.data.media.ItemQueue
import app.picnic.player.data.media.MediaRepository
import app.picnic.player.data.media.PlaylistRepository
import app.picnic.player.data.media.QueueKind
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
import app.picnic.player.data.playback.SegmentKind
import app.picnic.player.data.playback.StreamInfo
import app.picnic.player.data.playback.Trickplay
import app.picnic.player.data.playback.TrickplayCache
import app.picnic.player.data.playback.TrickplayFrame
import app.picnic.player.data.playback.msToTicks
import app.picnic.player.data.playback.playbackTick
import app.picnic.player.data.playback.quality.QualityOption
import app.picnic.player.data.playback.quality.QualityRung
import app.picnic.player.data.playback.quality.SourceQuality
import app.picnic.player.data.playback.quality.qualityOptions
import app.picnic.player.data.playback.refinePlayMethod
import app.picnic.player.data.playback.ticksToMs
import app.picnic.player.data.playback.trackMemoryKey
import app.picnic.player.data.settings.PlaybackSettings
import app.picnic.player.data.settings.SettingsStore
import app.picnic.player.data.settings.SubtitleAppearanceEditor
import app.picnic.player.data.settings.SubtitleAppearanceSetting
import app.picnic.player.data.settings.SubtitleArea
import app.picnic.player.data.settings.TrackMemoryStore
import app.picnic.player.di.ApplicationScope
import app.picnic.player.playback.AudioBoost
import app.picnic.player.playback.AudioRoute
import app.picnic.player.playback.AudioRoutePolicy
import app.picnic.player.playback.BlackBarTrack
import app.picnic.player.playback.BlackBars
import app.picnic.player.playback.CueLatchedBars
import app.picnic.player.playback.NightMode
import app.picnic.player.playback.PlaybackDiagnostics
import app.picnic.player.playback.PlaybackEngineFactory
import app.picnic.player.playback.PlaybackSessionController
import app.picnic.player.playback.SleepMode
import app.picnic.player.playback.StreamLoader
import app.picnic.player.playback.StreamRequest
import app.picnic.player.playback.StreamResult
import app.picnic.player.playback.StreamTarget
import app.picnic.player.playback.ThemeMusicPlayer
import app.picnic.player.playback.VideoDynamicRange
import app.picnic.player.playback.stateName
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
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
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

private const val LAST_FRAME_MS = 200L

private const val TRANSCODING_INFO_ATTEMPTS = 5
private const val AUDIO_ROUTE_SETTLE_MS = 1_500L

@OptIn(FlowPreview::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val playbackRepository: PlaybackRepository,
    private val blackBarProbe: BlackBarProbe,
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository,
    private val playlistRepository: PlaylistRepository,
    private val settingsStore: SettingsStore,
    private val subtitleAppearanceEditor: SubtitleAppearanceEditor,
    private val trackMemoryStore: TrackMemoryStore,
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
    private var queue: ItemQueue? = null
    private var seriesId: UUID? = null
    private val latchedBars = CueLatchedBars()
    private var loaded = false

    private val viewingJob = SupervisorJob(viewModelScope.coroutineContext[Job])
    private val viewingScope = CoroutineScope(viewModelScope.coroutineContext + viewingJob)

    private val tracks = PlayerTracks(
        player = player,
        authRepository = authRepository,
        mediaRepository = mediaRepository,
        trackMemoryStore = trackMemoryStore,
        scope = viewModelScope,
        onOptionsChanged = { options ->
            _state.update {
                it.copy(
                    audioTracks = options.audio,
                    subtitleTracks = options.subtitle,
                    selectedAudioId = options.selectedAudioId,
                    selectedSubtitleId = options.selectedSubtitleId
                )
            }
        },
        onReload = { reason -> reload(reason) }
    )

    private val trickplayCache = TrickplayCache(appContext, appScope, viewingScope)

    private var ticker: Job? = null
    private var progressJob: Job? = null
    private var reloadJob: Job? = null

    private val streamTarget = object : StreamTarget {
        override val positionMs: Long get() = resumeAwarePositionMs()

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
            this@PlayerViewModel.tracks.applySelections()
        }
        override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
            latchedBars.onCueBoundary(player.currentPosition)
            _state.update { it.copy(subtitleCues = cueGroup.cues) }
        }
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (!PlaybackDiagnostics.enabled) return
            PlaybackDiagnostics.log(
                "position ${oldPosition.positionMs} -> ${newPosition.positionMs} reason=${discontinuityName(reason)}"
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            if (directPlayVeto.onPlaybackError(error.errorCode)) {
                reload(ReloadReason.PLAYBACK_FAILED)
                return
            }
            _state.update { it.copy(error = error.message ?: "Playback error", isLoading = false) }
        }
    }

    private fun recoverIfNothingToPlay() {
        if (player.currentTracks.groups.isNotEmpty()) return
        PlaybackDiagnostics.log("prepared with no playable tracks; remuxAlreadyTried=${!directPlayVeto.allowsDirectPlay}")
        if (directPlayVeto.onNoPlayableTracks()) {
            reload(ReloadReason.PLAYBACK_FAILED)
        } else {
            _state.update { it.copy(error = "This file has no playable video or audio", isLoading = false) }
        }
    }

    private fun resumeAwarePositionMs(): Long = maxOf(player.currentPosition, pendingSeekMs)

    private fun applyPendingSeek() {
        val target = pendingSeekMs
        if (target <= 0) return
        if (player.playbackState != Player.STATE_READY || player.duration <= 0) {
            PlaybackDiagnostics.log(
                "pending seek $target held: state=${stateName(player.playbackState)} duration=${player.duration}"
            )
            return
        }
        pendingSeekMs = 0
        PlaybackDiagnostics.log(
            "pending seek $target applied: duration=${player.duration} position=${player.currentPosition}"
        )
        player.seekTo(target)
    }

    private fun discontinuityName(reason: Int): String = when (reason) {
        Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> "AUTO_TRANSITION"
        Player.DISCONTINUITY_REASON_SEEK -> "SEEK"
        Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT -> "SEEK_ADJUSTMENT"
        Player.DISCONTINUITY_REASON_SKIP -> "SKIP"
        Player.DISCONTINUITY_REASON_REMOVE -> "REMOVE"
        Player.DISCONTINUITY_REASON_INTERNAL -> "INTERNAL"
        else -> "UNKNOWN($reason)"
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

        override fun onLoadError(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
            error: IOException,
            wasCanceled: Boolean
        ) {
            if (wasCanceled) return
            tracks.onLoadFailed(loadEventInfo.uri.toString())
        }
    }

    private var transcodingInfoJob: Job? = null

    init {
        themeMusicPlayer.stop()
        viewModelScope.launch {
            combine(
                trickplayCache.current,
                settings.map { it.subtitleAppearance.area }.distinctUntilChanged(),
                ::Pair
            ).collectLatest { (trickplay, area) ->
                val sheets = trickplay?.sheets().orEmpty()
                val track = if (area == SubtitleArea.AUTOMATIC && sheets.isNotEmpty()) {
                    trickplayCache.awaitPrefetch()
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

    fun stepSubtitleAppearance(setting: SubtitleAppearanceSetting, forward: Boolean) = viewModelScope.launch { subtitleAppearanceEditor.step(setting, forward) }

    fun load(
        itemIdString: String,
        startTicks: Long?,
        mediaSourceId: String? = null,
        queue: ItemQueue? = null
    ) {
        if (loaded) return
        loaded = true
        val id = UUID.fromString(itemIdString)
        itemId = id
        this.queue = queue
        outroNextUpShown = false
        viewingScope.launch {
            val activeSession = authRepository.activeSession()
            if (activeSession == null) {
                _state.update { it.copy(error = "No active session", buffering = false, isLoading = false) }
                return@launch
            }
            session = activeSession

            launch { ensureTranscodePermission() }

            val itemDeferred = async {
                runCatching { mediaRepository.item(id) }.getOrNull()
            }

            launch {
                segments = runCatching { playbackRepository.mediaSegments(id) }.getOrDefault(emptyList())
                autoSkipped.clear()
            }

            val result = streamLoader.load(
                StreamRequest(
                    itemId = id,
                    seriesId = seriesId,
                    session = activeSession,
                    positionTicks = startTicks ?: 0L,
                    mediaSourceId = mediaSourceId,
                    quality = sessionController.qualityOverride.value,
                    audioStreamIndex = tracks.audioIndex,
                    subtitleStreamIndex = tracks.subtitleIndex,
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
            tracks.initDefaults(settings.value)
            if (tracks.needsStreamForSelection()) {
                reload(ReloadReason.SUBTITLE_CHANGE)
            } else {
                tracks.applySelections()
            }
            tracks.publishOptions()
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
    }

    private suspend fun refreshTranscodingInfoOnce(
        session: UserSession,
        mediaSourceId: String?,
        initialMethod: PlayMethodKind
    ): Boolean {
        val info = playbackRepository.getTranscodingInfo(mediaSourceId) ?: return false
        _state.update {
            it.copy(
                transcodingInfo = info,
                playMethod = refinePlayMethod(initialMethod, info)
            )
        }
        return true
    }

    private fun settleTranscodingInfo(info: StreamInfo) {
        if (info.playMethod != PlayMethodKind.TRANSCODE) return
        transcodingInfoJob?.cancel()
        transcodingInfoJob = viewingScope.launch {
            val activeSession = session ?: return@launch
            repeat(TRANSCODING_INFO_ATTEMPTS) {
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

    fun trickplayFor(positionMs: Long): TrickplayFrame? = trickplayCache.frameFor(positionMs)

    fun selectAudio(streamIndex: String) = tracks.selectAudio(streamIndex)

    fun selectSubtitle(streamIndex: String?) = tracks.selectSubtitle(streamIndex)

    private fun isConverting() = stream?.playMethod == PlayMethodKind.TRANSCODE

    fun clearNotice() = _state.update { it.copy(notice = null) }

    fun selectQuality(option: QualityOption) {
        if (option == _state.value.activeQuality) return
        val previous = sessionController.qualityOverride.value
        sessionController.setQualityOverride(option)
        reload(ReloadReason.QUALITY_CHANGE, quality = option, revertTo = previous)
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
            reload(ReloadReason.AUDIO_CHANGE)
        }
    }

    private fun reload(
        reason: ReloadReason,
        quality: QualityOption? = sessionController.qualityOverride.value,
        revertTo: QualityOption? = quality
    ) {
        val activeSession = session ?: return
        val id = itemId ?: return
        val current = stream
        val delayMs = _state.value.subtitleDelayMs
        val speed = _state.value.playbackSpeed

        val resumeMs = resumeAwarePositionMs()

        reloadJob?.cancel()
        reloadJob = viewingScope.launch {
            PlaybackDiagnostics.log(
                "reload reason=$reason resumeMs=$resumeMs position=${player.currentPosition} pendingSeek=$pendingSeekMs"
            )
            _state.update {
                it.copy(buffering = true, error = null, notice = null, subtitleCues = emptyList())
            }
            val result = streamLoader.load(
                StreamRequest(
                    itemId = id,
                    seriesId = seriesId,
                    positionTicks = resumeMs.msToTicks(),
                    mediaSourceId = current?.mediaSourceId,
                    session = activeSession,
                    quality = quality,
                    audioStreamIndex = tracks.audioIndex,
                    subtitleStreamIndex = tracks.subtitleIndex,
                    resumePlaying = player.playWhenReady,
                    replacing = current,
                    allowDirectPlay = directPlayVeto.allowsDirectPlay
                )
            )
            when (result) {
                is StreamResult.Loaded -> adopt(result.stream, delayMs, speed)
                is StreamResult.Failed -> {
                    sessionController.setQualityOverride(revertTo)
                    _state.update { it.copy(notice = reason.failureNotice) }
                    result.restored?.let { adopt(it, delayMs, speed) }
                }
            }
        }
    }

    private fun adopt(info: StreamInfo, subtitleDelayMs: Long, speed: Float) {
        stream = info
        tracks.adopt(info)
        refreshQualityOptions(info)
        engine.setSubtitleDelayMs(subtitleDelayMs)
        player.setPlaybackSpeed(speed)
        _state.update {
            it.copy(
                isLoading = false,
                playMethod = info.playMethod,
                mediaSourceId = info.mediaSourceId,
                mediaSource = info.mediaSource,
                transcodingInfo = null,
                directPlayBlockedBy = info.directPlayBlockedBy
            )
        }
        settleTranscodingInfo(info)
    }

    private suspend fun ensureTranscodePermission() {
        canTranscode = runCatching { mediaRepository.canTranscodeVideo() }.getOrDefault(true)
    }

    private fun applyItemMetadata(activeSession: UserSession, id: UUID, item: BaseItemDto) {
        seriesId = item.seriesId
        tracks.onItemMetadata(trackMemoryKey(item.type, id, item.seriesId), item.seasonId?.toString())
        val sheets = playbackRepository.trickplayFromItem(item)?.let { (sheetWidth, tiles) ->
            Trickplay(tiles) { tileIndex ->
                JellyfinImages.trickplayTile(activeSession, id, sheetWidth, tileIndex)
            }
        }
        trickplayCache.replaceWith(sheets)
        val chapterMarks = item.chapters?.mapIndexed { index, ch ->
            ChapterMark(
                index = index,
                title = ch.name?.takeIf { it.isNotBlank() } ?: "Chapter ${index + 1}",
                startMs = ch.startPositionTicks.ticksToMs(),
                imageUrl = ch.imageTag?.let {
                    JellyfinImages.chapterImage(activeSession, id, index, it)
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
        viewingScope.launch {
            val next = runCatching { nextInQueue(id, item) }.getOrNull()
            if (next != null) {
                _state.update { s ->
                    s.copy(nextUp = buildNextUpItem(activeSession, next))
                }
            }
        }
    }

    private suspend fun nextInQueue(id: UUID, item: BaseItemDto): BaseItemDto? {
        val playing = queue
        val parentId = playing?.id
            ?: return if (item.type == BaseItemKind.EPISODE) mediaRepository.nextEpisode(id) else null
        val entries = when (playing.kind) {
            QueueKind.PLAYLIST -> playlistRepository.playlistItems(parentId)
            QueueKind.COLLECTION -> mediaRepository.collectionQueue(parentId).last()
        }
        return playing.itemAfter(entries)
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

    private fun mediaItemFor(info: StreamInfo): MediaItem = MediaItem.Builder()
        .setUri(info.url)
        .setSubtitleConfigurations(tracks.subtitleConfigurationsFor(info))
        .build()

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
                session ?: continue
                val info = stream ?: continue
                val id = itemId ?: continue
                runCatching {
                    playbackRepository.reportProgress(
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
        trickplayCache.clear()
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
