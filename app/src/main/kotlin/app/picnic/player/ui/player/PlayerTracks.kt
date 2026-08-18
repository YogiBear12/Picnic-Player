@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.ui.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.media.MediaRepository
import app.picnic.player.data.playback.PlayMethodKind
import app.picnic.player.data.playback.RememberedTrack
import app.picnic.player.data.playback.StreamInfo
import app.picnic.player.data.playback.TrackMemoryKind
import app.picnic.player.data.playback.pickTracksWithMemory
import app.picnic.player.data.playback.resolveLanguageCode
import app.picnic.player.data.settings.PlaybackSettings
import app.picnic.player.data.settings.SeriesTrackMemoryStore
import app.picnic.player.playback.JellyfinTrackSelection
import app.picnic.player.playback.PlaybackDiagnostics
import app.picnic.player.playback.SideloadedTrackId
import app.picnic.player.playback.externalSubtitleCount
import app.picnic.player.util.LanguageDisplay
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod

data class TrackOptions(
    val audio: List<TrackOption>,
    val subtitle: List<TrackOption>,
    val selectedAudioId: String?,
    val selectedSubtitleId: String?
)

class PlayerTracks(
    private val player: ExoPlayer,
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository,
    private val seriesTrackMemoryStore: SeriesTrackMemoryStore,
    private val scope: CoroutineScope,
    private val onOptionsChanged: (TrackOptions) -> Unit,
    private val onReload: (ReloadReason) -> Unit
) {
    var audioIndex: Int? = null
        private set

    var subtitleIndex: Int? = null
        private set

    private var mediaStreams: List<MediaStream> = emptyList()
    private var externalSubtitles: List<ExternalSubtitleRef> = emptyList()
    private var converting = false

    private val exhausted = mutableSetOf<Int>()
    private var attachedSubtitleIndex: Int? = null
    private var requestedSubtitleIndex: Int? = null

    private var itemType: BaseItemKind? = null
    private var seriesId: UUID? = null
    private var seasonId: UUID? = null

    private var serverAudioLanguage: String? = null
    private var serverSubtitleLanguage: String? = null
    private var serverLanguagePrefsLoaded = false

    private class ExternalSubtitleRef(val streamIndex: Int, val urlPrefix: String)

    fun adopt(info: StreamInfo) {
        mediaStreams = info.mediaStreams
        converting = info.playMethod == PlayMethodKind.TRANSCODE
        externalSubtitles = info.externalSubtitles.map {
            ExternalSubtitleRef(it.streamIndex, it.url.substringBefore('?'))
        }
        publishOptions()
    }

    fun onStreamRequested(streamIndex: Int?) {
        requestedSubtitleIndex = streamIndex
    }

    fun onItemMetadata(type: BaseItemKind?, series: UUID?, season: UUID?) {
        itemType = type
        seriesId = series
        seasonId = season
    }

    suspend fun initDefaults(prefs: PlaybackSettings) {
        ensureServerLanguagePrefs()
        val deviceLanguage = Locale.getDefault().language
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
        audioIndex = pick.audioIndex
        subtitleIndex = pick.subtitleIndex
    }

    private suspend fun ensureServerLanguagePrefs() {
        if (serverLanguagePrefsLoaded) return
        serverLanguagePrefsLoaded = true
        val session = authRepository.activeSession() ?: return
        val config = runCatching { mediaRepository.userConfiguration(session) }.getOrNull()
        serverAudioLanguage = config?.audioLanguagePreference
        serverSubtitleLanguage = config?.subtitleLanguagePreference
    }

    fun selectAudio(streamIndex: String) {
        audioIndex = streamIndex.toIntOrNull() ?: return
        persistOsdTrackMemory(audio = true)
        if (converting) onReload(ReloadReason.AUDIO_CHANGE) else applySelections()
    }

    fun selectSubtitle(streamIndex: String?) {
        val previous = subtitleIndex
        val next = streamIndex?.toIntOrNull()
        subtitleIndex = next
        persistOsdTrackMemory(audio = false)
        if (requiresNewStream(previous, next)) {
            onReload(ReloadReason.SUBTITLE_CHANGE)
        } else {
            applySelections()
        }
    }

    private fun requiresNewStream(previous: Int?, next: Int?): Boolean {
        val leavingBurnedIn = converting && isBurnedIn(previous)
        val retryExhausted = next != null && exhausted.remove(next)
        return leavingBurnedIn || needsBurnIn(next) || retryExhausted || needsAttach(next)
    }

    fun needsStreamForSelection(): Boolean = needsAttach(subtitleIndex) || needsBurnIn(subtitleIndex)

    private fun needsBurnIn(streamIndex: Int?): Boolean = converting && isBurnedIn(streamIndex) && streamIndex != requestedSubtitleIndex

    fun needsAttach(streamIndex: Int?): Boolean = isSideloaded(streamIndex) && streamIndex != attachedSubtitleIndex

    private fun isSideloaded(streamIndex: Int?): Boolean {
        val index = streamIndex ?: return false
        return externalSubtitles.any { it.streamIndex == index }
    }

    private fun isBurnedIn(streamIndex: Int?): Boolean {
        val index = streamIndex ?: return false
        return mediaStreams.firstOrNull { it.index == index }?.deliveryMethod ==
            SubtitleDeliveryMethod.ENCODE
    }

    fun subtitleConfigurationsFor(info: StreamInfo): List<MediaItem.SubtitleConfiguration> {
        val wanted = subtitleIndex ?: info.defaultSubtitleStreamIndex
        val attach = info.externalSubtitles.filter { it.streamIndex == wanted }
        attachedSubtitleIndex = attach.firstOrNull()?.streamIndex
        return attach.map { subtitle ->
            MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitle.url))
                .setId(SideloadedTrackId.of(subtitle.streamIndex))
                .setMimeType(subtitle.mimeType)
                .setLanguage(subtitle.language)
                .setLabel(subtitle.title)
                .build()
        }
    }

    fun onLoadFailed(uri: String) {
        externalSubtitles.firstOrNull { uri.startsWith(it.urlPrefix) }
            ?.let { exhausted += it.streamIndex }
    }

    fun applySelections() {
        if (mediaStreams.isEmpty()) return
        if (needsBurnIn(subtitleIndex)) {
            onReload(ReloadReason.SUBTITLE_CHANGE)
            return
        }
        if (converting && isBurnedIn(subtitleIndex)) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            publishOptions()
            return
        }
        val result = JellyfinTrackSelection.createTrackSelections(
            trackSelectionParams = player.trackSelectionParameters,
            tracks = player.currentTracks,
            supportsDirectPlay = !converting,
            audioIndex = audioIndex,
            subtitleIndex = subtitleIndex,
            mediaStreams = mediaStreams
        )
        PlaybackDiagnostics.logTrackSelectionOutcome(
            result = result,
            audioIndex = audioIndex,
            subtitleIndex = subtitleIndex,
            externalSubtitleCount = mediaStreams.externalSubtitleCount,
            supportsDirectPlay = !converting
        )
        if (result.bothSelected) {
            player.trackSelectionParameters = result.trackSelectionParameters
        }
        publishOptions()
    }

    fun publishOptions() {
        if (mediaStreams.isEmpty()) return
        onOptionsChanged(
            TrackOptions(
                audio = mediaStreams
                    .filter { it.type == MediaStreamType.AUDIO }
                    .map { it.toTrackOption(it.index == audioIndex) },
                subtitle = mediaStreams
                    .filter { it.type == MediaStreamType.SUBTITLE }
                    .map { it.toTrackOption(it.index == subtitleIndex) },
                selectedAudioId = audioIndex?.toString(),
                selectedSubtitleId = subtitleIndex?.toString()
            )
        )
    }

    private fun persistOsdTrackMemory(audio: Boolean) {
        if (itemType != BaseItemKind.EPISODE) return
        val series = seriesId ?: return
        val season = seasonId?.toString()
        val pick = if (audio) {
            val stream = mediaStreams.firstOrNull {
                it.type == MediaStreamType.AUDIO && it.index == audioIndex
            } ?: return
            RememberedTrack.of(stream) ?: return
        } else {
            val index = subtitleIndex
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
        scope.launch {
            seriesTrackMemoryStore.rememberOsdPick(
                seriesId = series.toString(),
                seasonId = season,
                kind = kind,
                pick = pick
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
}
