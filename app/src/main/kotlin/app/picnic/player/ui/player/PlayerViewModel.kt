@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import app.picnic.player.data.playback.MediaSegment
import app.picnic.player.data.playback.OUTRO_END_TOLERANCE_MS
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
import app.picnic.player.data.playback.TrickplayTiles
import app.picnic.player.data.playback.pickTracksWithMemory
import app.picnic.player.data.playback.playbackTick
import app.picnic.player.data.playback.resolveLanguageCode
import app.picnic.player.data.settings.PlaybackSettings
import app.picnic.player.data.settings.SeriesTrackMemoryStore
import app.picnic.player.data.settings.SettingsStore
import app.picnic.player.di.ApplicationScope
import app.picnic.player.playback.AudioBoost
import app.picnic.player.playback.JellyfinTrackSelection
import app.picnic.player.playback.NightMode
import app.picnic.player.playback.PlaybackEngine
import app.picnic.player.playback.PlaybackSessionController
import app.picnic.player.playback.SleepMode
import app.picnic.player.playback.SleepTimerState
import app.picnic.player.playback.ThemeMusicPlayer
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.TranscodingInfo

private const val TICKS_PER_MS = 10_000L

// OUTRO_END_TOLERANCE_MS lives in data/playback/PlaybackTick.kt (shared with the pure tick decision).

// How far short of the media end to seek when skipping an outro that runs to the end, so the final
// frame renders before the natural end-of-stream rather than freezing the pre-skip frame.
private const val LAST_FRAME_MS = 200L

/**
 * One-shot navigation the player screen must perform on behalf of a remote command (#119).
 * The ViewModel cannot navigate, so it signals and the screen calls its existing callbacks.
 */
sealed interface PlayerNavEvent {
    /** Leave the player (remote Stop). */
    data object Exit : PlayerNavEvent

    /** Launch the next episode (remote NextTrack). */
    data class PlayNext(val itemId: String) : PlayerNavEvent
}

/** A selectable audio/subtitle track. */
data class TrackOption(
    val id: String,
    /** Secondary line (track title / description). */
    val label: String?,
    val language: String?,
    /** Primary line — regional language name from Jellyfin. */
    val displayLanguage: String,
    val selected: Boolean
)

/** One trickplay thumbnail: the sprite-sheet URL plus the cell to crop to. */
data class TrickplayFrame(
    val url: String,
    val column: Int,
    val row: Int,
    val columns: Int,
    val rows: Int,
    val aspect: Float
)

/** Active scrub preview for a screen-level overlay (does not affect OSD layout). */
data class TrickplayPreview(
    val frame: TrickplayFrame,
    val fraction: Float
)

/** One chapter marker for the OSD Down Menu chapters row. */
data class ChapterMark(
    val index: Int,
    val title: String,
    val startMs: Long,
    /** Chapter image if the item has one; null falls back to a trickplay frame. */
    val imageUrl: String?
)

/**
 * Next episode data for the next-up overlay. Formatted for display — the ViewModel
 * owns the formatting so the overlay composable stays stateless/previewable.
 */
data class NextUpItem(
    val id: String,
    val title: String,
    /** Season and episode label e.g. "S1 E11". Empty if unavailable. */
    val seasonEpisode: String,
    /** Formatted meta line e.g. "Jun 4, 2013 • 11m • 8.2 ★". */
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
    val trickplaySheetWidth: Int? = null,
    val trickplayTiles: TrickplayTiles? = null,
    val subtitleCues: List<Cue> = emptyList(),
    val currentSegment: MediaSegment? = null,
    /** Next episode to auto-play; null if unavailable (movies, series finale). */
    val nextUp: NextUpItem? = null,
    /** True once playback has ended or (opt-in) entered an OUTRO segment, triggering the overlay. */
    val endedAwaitingNext: Boolean = false,
    /** True only in opt-in outro mode: the video is still playing under the shrunk corner. */
    val videoStillPlaying: Boolean = false,
    val chapters: List<ChapterMark> = emptyList(),
    /** Session-only subtitle offset (ms); resets each item. */
    val subtitleDelayMs: Long = 0,
    /** Session-only playback speed; resets each item. */
    val playbackSpeed: Float = 1.0f,
    /** Session audio enhancement — persists across autoplayed episodes (app-scoped). */
    val audioBoost: AudioBoost = AudioBoost.OFF,
    val nightMode: NightMode = NightMode.OFF,
    /** Sleep timer state — persists across autoplayed episodes (app-scoped). */
    val sleep: SleepTimerState = SleepTimerState(),
    val showStatsForNerds: Boolean = false,
    val playMethod: app.picnic.player.data.playback.PlayMethodKind? = null,
    val mediaSourceId: String? = null,
    val mediaSource: org.jellyfin.sdk.model.api.MediaSourceInfo? = null,
    val transcodingInfo: TranscodingInfo? = null,
    val videoDecoderName: String? = null,
    val audioDecoderName: String? = null,
    val estimatedBitrate: Long? = null,
    val tracks: List<app.picnic.player.ui.player.osd.TrackSupport> = emptyList()
)

/**
 * Drives the media3 player for one item: resolves the stream via the
 * SDK, loads it, mirrors state + tracks, and reports progress. Video renders on
 * a SurfaceView supplied by the screen.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val playbackRepository: PlaybackRepository,
    private val authRepository: AuthRepository,
    private val mediaRepository: app.picnic.player.data.media.MediaRepository,
    private val settingsStore: SettingsStore,
    private val seriesTrackMemoryStore: SeriesTrackMemoryStore,
    private val sessionController: PlaybackSessionController,
    private val pictureInPictureSupport: app.picnic.player.data.device.PictureInPictureSupport,
    playerCommandBus: PlayerCommandBus,
    themeMusicPlayer: ThemeMusicPlayer,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    private val engine = PlaybackEngine(appContext)

    /** Video player. Built with libass (ASS/SSA) + hardware-first renderers. */
    val player: ExoPlayer get() = engine.player

    val settings: StateFlow<PlaybackSettings> =
        settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, PlaybackSettings())

    /** Hide PiP OSD / auto-enter when the device does not advertise system PiP. */
    val pictureInPictureSupported: Boolean = pictureInPictureSupport.isSupported

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    // Remote-control actions the *screen* must service (it owns navigation, the VM does not):
    // a remote Stop exits the player, a remote NextTrack launches the next episode.
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
    private var trickplay: Pair<Int, TrickplayTiles>? = null
    private var loaded = false
    private var ticker: Job? = null
    private var progressJob: Job? = null
    private var trickplayPrefetchJob: Job? = null
    private var hasPresentedFirstFrame = false

    private var segments: List<MediaSegment> = emptyList()
    private val autoSkipped = mutableSetOf<String>()

    // Outro next-up is shown at most once per item; dismissing it must not re-trigger while the
    // outro segment is still on screen.
    private var outroNextUpShown = false

    private val listener = object : Player.Listener {
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
            _state.update { it.copy(subtitleCues = cueGroup.cues) }
        }
        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(error = error.message ?: "Playback error", isLoading = false) }
        }
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

    init {
        // Real playback owns the audio output from here; browse-time theme music yields
        // immediately rather than overlapping the opening seconds of the stream.
        themeMusicPlayer.stop()
        player.addListener(listener)
        player.addAnalyticsListener(analyticsListener)
        // Mirror app-scoped session controls into this item's engine + UI state. Collecting the
        // current values re-applies them to a fresh engine on each autoplayed episode.
        viewModelScope.launch {
            sessionController.audioBoost.collect { level ->
                engine.setAudioBoostMillibels(level.gainMb)
                _state.update { it.copy(audioBoost = level) }
            }
        }
        viewModelScope.launch {
            sessionController.nightMode.collect { level ->
                engine.setNightMode(level.strength)
                _state.update { it.copy(nightMode = level) }
            }
        }
        viewModelScope.launch {
            sessionController.sleep.collect { s -> _state.update { it.copy(sleep = s) } }
        }
        viewModelScope.launch {
            sessionController.sleepExpired.collect { player.playWhenReady = false }
        }
        // Remote control (#119, Slice 2): apply commands from another device/dashboard to this
        // player. This VM is the sole collector while the player is composed; once it is cleared
        // there is no collector, so commands arriving with nothing playing are dropped safely.
        viewModelScope.launch {
            playerCommandBus.commands.collect { applyRemoteCommand(it) }
        }
    }

    /** Maps a remote [PlayerCommand] onto the existing player controls. Safe before [load]. */
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
            // No previous/queue model on a single-item player — safely ignored.
            PlayerCommand.PreviousTrack -> Unit
            is PlayerCommand.SetAudioIndex -> selectAudio(command.index.toString())
            is PlayerCommand.SetSubtitleIndex -> selectSubtitle(command.index?.toString())
        }
    }

    /** Wire libass overlay (call from [SubtitleView] [AndroidView] update). */
    fun attachSubtitleView(subtitleView: SubtitleView) = engine.attachSubtitleView(
        subtitleView,
        settings.value.subtitleAppearance
    )

    /** libass overlay view; host it sized to the video display rect. */
    fun assOverlayView(context: Context) = engine.assOverlayView(context)

    fun setPictureInPicture(pip: Boolean) {
        viewModelScope.launch {
            settingsStore.setPictureInPicture(pip)
        }
    }

    fun load(itemIdString: String, startTicks: Long?, mediaSourceId: String? = null) {
        if (loaded) return
        loaded = true
        val id = UUID.fromString(itemIdString)
        itemId = id
        outroNextUpShown = false
        viewModelScope.launch {
            val activeSession = authRepository.activeSession()
            if (activeSession == null) {
                _state.update { it.copy(error = "No active session", buffering = false, isLoading = false) }
                return@launch
            }
            session = activeSession

            // Item metadata runs in parallel with stream negotiation; we await it before track
            // pick so series/season memory (#15 stage 2) has ids without delaying the HTTP start.
            val itemDeferred = async {
                runCatching { mediaRepository.item(activeSession, id) }.getOrNull()
            }

            launch {
                segments = runCatching { playbackRepository.mediaSegments(activeSession, id) }.getOrDefault(emptyList())
                autoSkipped.clear()
            }

            try {
                val info = playbackRepository.resolveStream(activeSession, id, startTicks, mediaSourceId)
                val item = itemDeferred.await()
                if (item != null) {
                    applyItemMetadata(activeSession, id, item)
                }
                stream = info
                mediaStreams = info.mediaStreams
                initDefaultTrackIndices()
                rebuildTrackOptions()
                player.setMediaItem(MediaItem.fromUri(info.url))
                player.prepare()
                // Session-only controls reset for the new item.
                engine.setSubtitleDelayMs(0)
                player.setPlaybackSpeed(1.0f)
                _state.update {
                    it.copy(
                        subtitleDelayMs = 0,
                        playbackSpeed = 1.0f,
                        showStatsForNerds = false,
                        playMethod = info.playMethod,
                        mediaSourceId = info.mediaSourceId,
                        mediaSource = info.mediaSource,
                        transcodingInfo = null
                    )
                }
                updateTranscodingInfoJob()
                if ((startTicks ?: 0L) > 0L) player.seekTo(startTicks!! / TICKS_PER_MS)
                player.playWhenReady = true
                startTicker()
                startProgressReports()
                launch {
                    runCatching {
                        playbackRepository.reportStart(
                            activeSession,
                            info,
                            id,
                            player.currentPosition * TICKS_PER_MS
                        )
                    }
                    // After the session exists, refine Transcode→Direct Stream when video is remuxed.
                    if (info.playMethod == app.picnic.player.data.playback.PlayMethodKind.TRANSCODE) {
                        refreshTranscodingInfoOnce(activeSession, info.mediaSourceId, info.playMethod)
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Could not start playback", buffering = false, isLoading = false) }
            }
        }
    }

    fun playPause() {
        player.playWhenReady = !player.playWhenReady
    }

    fun seekTo(positionMs: Long) = player.seekTo(positionMs.coerceAtLeast(0))

    fun seekBy(deltaMs: Long) = player.seekTo((player.currentPosition + deltaMs).coerceAtLeast(0))

    /** Session-only subtitle offset in ms (positive = later). Applies to text + ASS. */
    fun setSubtitleDelayMs(ms: Long) {
        engine.setSubtitleDelayMs(ms)
        _state.update { it.copy(subtitleDelayMs = ms) }
    }

    /** Session-only playback speed (media3 native, pitch-corrected). */
    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _state.update { it.copy(playbackSpeed = speed) }
    }

    // Session controls delegated to the app-scoped controller (survive autoplay).
    fun setAudioBoost(level: AudioBoost) = sessionController.setAudioBoost(level)
    fun setNightMode(level: NightMode) = sessionController.setNightMode(level)
    fun setSleep(mode: SleepMode) = sessionController.setSleep(mode)

    fun toggleStatsForNerds() {
        _state.update { it.copy(showStatsForNerds = !it.showStatsForNerds) }
        updateTranscodingInfoJob()
    }

    private fun updateTranscodingInfoJob() {
        // Gate on the *resolved* stream method (not the refined UI label): remuxes start as
        // TRANSCODE in PlaybackInfo and stay on the transcoding pipeline even after we display
        // Direct Stream once isVideoDirect is known.
        val pipelineTranscode = stream?.playMethod == app.picnic.player.data.playback.PlayMethodKind.TRANSCODE
        if (_state.value.showStatsForNerds && pipelineTranscode) {
            if (transcodingInfoJob == null) {
                transcodingInfoJob = viewModelScope.launch {
                    val session = authRepository.activeSession() ?: return@launch
                    while (isActive) {
                        refreshTranscodingInfoOnce(
                            session,
                            mediaSourceId = stream?.mediaSourceId,
                            initialMethod = stream?.playMethod
                                ?: app.picnic.player.data.playback.PlayMethodKind.TRANSCODE
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
        initialMethod: app.picnic.player.data.playback.PlayMethodKind
    ) {
        val info = playbackRepository.getTranscodingInfo(session, mediaSourceId) ?: return
        _state.update {
            it.copy(
                transcodingInfo = info,
                playMethod = app.picnic.player.data.playback.refinePlayMethod(initialMethod, info)
            )
        }
    }

    /** Real player exit (back to browse): clear session audio + sleep timer. */
    fun onPlayerExit() = sessionController.reset()

    /** Trickplay sprite sheet URL for [tileIndex], or null if unavailable. */
    fun trickplayTileUrl(tileIndex: Int): String? {
        val s = session ?: return null
        val id = itemId ?: return null
        val (width, _) = trickplay ?: return null
        return playbackRepository.trickplayTileUrl(s, id, width, tileIndex)
    }

    /** Trickplay sprite cell for [positionMs], or null if unavailable. */
    fun trickplayFor(positionMs: Long): TrickplayFrame? {
        val s = session ?: return null
        val id = itemId ?: return null
        val (width, tiles) = trickplay ?: return null
        val (tileIndex, row, col) = tiles.tileFor(positionMs)
        val aspect = if (tiles.height > 0) tiles.width.toFloat() / tiles.height else 16f / 9f
        return TrickplayFrame(
            url = playbackRepository.trickplayTileUrl(s, id, width, tileIndex),
            column = col,
            row = row,
            columns = tiles.tileWidth.coerceAtLeast(1),
            rows = tiles.tileHeight.coerceAtLeast(1),
            aspect = aspect
        )
    }

    fun selectAudio(streamIndex: String) {
        selectedAudioIndex = streamIndex.toIntOrNull() ?: return
        applyTrackSelections()
        persistOsdTrackMemory(audio = true)
    }

    fun selectSubtitle(streamIndex: String?) {
        selectedSubtitleIndex = streamIndex?.toIntOrNull()
        applyTrackSelections()
        persistOsdTrackMemory(audio = false)
    }

    // Jellyfin user-config language preferences, resolved behind the local app override
    // (app > server > device). Fetched once per player session and memoised.
    private var serverAudioLanguage: String? = null
    private var serverSubtitleLanguage: String? = null
    private var serverLanguagePrefsLoaded = false

    private suspend fun ensureServerLanguagePrefs() {
        if (serverLanguagePrefsLoaded) return
        serverLanguagePrefsLoaded = true
        val session = authRepository.activeSession() ?: return
        val config = runCatching { mediaRepository.userConfiguration(session) }.getOrNull()
        serverAudioLanguage = config?.audioLanguagePreference
        serverSubtitleLanguage = config?.subtitleLanguagePreference
    }

    /**
     * Initial track choice (#15): season → series memory when confident, else device-local
     * [PlaybackSettings] via [pickTracksWithMemory]. Movies stay on global prefs only.
     */
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
            alwaysDisplaySubtitles = prefs.alwaysDisplaySubtitles
        )
        selectedAudioIndex = pick.audioIndex
        selectedSubtitleIndex = pick.subtitleIndex
    }

    /** L1/W2: remember OSD audio/subtitle picks for TV episodes only. */
    private fun persistOsdTrackMemory(audio: Boolean) {
        if (itemType != BaseItemKind.EPISODE) return
        val series = seriesId ?: return
        val season = seasonId?.toString()
        val pick = if (audio) {
            val stream = mediaStreams.firstOrNull {
                it.type == MediaStreamType.AUDIO && it.index == selectedAudioIndex
            } ?: return
            // Skip blank titles — R1 needs a title string to match later.
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
        trickplay = playbackRepository.trickplayFromItem(item)
        prefetchTrickplayTiles()
        val chapterMarks = item.chapters?.mapIndexed { index, ch ->
            ChapterMark(
                index = index,
                title = ch.name?.takeIf { it.isNotBlank() } ?: "Chapter ${index + 1}",
                startMs = ch.startPositionTicks / TICKS_PER_MS,
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
                trickplaySheetWidth = trickplay?.first,
                trickplayTiles = trickplay?.second,
                chapters = chapterMarks
            )
        }
        if (item.type == BaseItemKind.EPISODE) {
            viewModelScope.launch {
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

    /** Always rebuild audio + subtitle selection together. */
    private fun applyTrackSelections() {
        if (mediaStreams.isEmpty()) return
        val result = JellyfinTrackSelection.createTrackSelections(
            trackSelectionParams = player.trackSelectionParameters,
            tracks = player.currentTracks,
            supportsDirectPlay = true,
            audioIndex = selectedAudioIndex,
            subtitleIndex = selectedSubtitleIndex,
            mediaStreams = mediaStreams
        )
        if (result.bothSelected) {
            player.trackSelectionParameters = result.trackSelectionParameters
        }
        rebuildTrackOptions()
    }

    /** Track panel lists Jellyfin [mediaStreams] only — never ExoPlayer groups. */
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

    /** Prefetch trickplay sprite sheets into Coil's cache while playback starts. */
    private fun prefetchTrickplayTiles() {
        val s = session ?: return
        val id = itemId ?: return
        val (width, tiles) = trickplay ?: return
        val perTile = tiles.tileWidth * tiles.tileHeight
        if (perTile <= 0) return
        val tileCount = (tiles.thumbnailCount + perTile - 1) / perTile
        trickplayPrefetchJob?.cancel()
        trickplayPrefetchJob = viewModelScope.launch {
            val loader = appContext.imageLoader
            for (tileIndex in 0 until tileCount) {
                if (!isActive) return@launch
                val url = playbackRepository.trickplayTileUrl(s, id, width, tileIndex)
                loader.enqueue(
                    ImageRequest.Builder(appContext)
                        .data(url)
                        .size(Size.ORIGINAL)
                        .build()
                )
            }
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                pushState()
                delay(500)
            }
        }
    }

    private fun startProgressReports() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
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
                        player.currentPosition * TICKS_PER_MS,
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

        // Apply the decision's side-effect requests to the real player/session.
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
            // Outro runs to the end of the file. Seeking onto the end-of-stream renders no frame, so
            // the pre-skip frame would stay frozen in the next-up window. Seek just short of the end
            // instead, letting the final frame render before playback reaches its natural end and the
            // next-up screen appears.
            player.seekTo((duration - LAST_FRAME_MS).coerceAtLeast(0))
        } else {
            player.seekTo(seg.endMs)
        }
    }

    /** Returns the next item id stored in state, or null. Used by the screen to navigate. */
    fun nextItemId(): String? = _state.value.nextUp?.id

    /**
     * Dismisses the next-up overlay shown during an OUTRO segment and returns to the playing video.
     * The outro-shown guard stays set so the overlay does not immediately re-trigger; the default
     * at-end overlay still appears when playback actually reaches STATE_ENDED.
     */
    fun dismissNextUp() {
        _state.update { it.copy(endedAwaitingNext = false, videoStillPlaying = false) }
    }

    override fun onCleared() {
        ticker?.cancel()
        progressJob?.cancel()
        trickplayPrefetchJob?.cancel()
        val s = session
        val info = stream
        val id = itemId
        val series = seriesId
        val positionTicks = player.currentPosition * TICKS_PER_MS
        player.removeListener(listener)
        engine.release()
        if (s != null && info != null && id != null) {
            appScope.launch {
                runCatching { playbackRepository.reportStopped(s, info, id, positionTicks, series) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers (file-level, not part of the ViewModel)
// ---------------------------------------------------------------------------

/**
 * Maps a Jellyfin episode DTO to the display-ready [NextUpItem] shown in the overlay.
 * All formatting is done here so the composable stays stateless.
 */
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
