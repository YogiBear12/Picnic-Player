package app.picnic.player.data.playback

import android.content.Context
import androidx.media3.common.MimeTypes
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.device.DeviceIdentityStore
import app.picnic.player.data.jellyfin.JellyfinFactory
import app.picnic.player.data.media.LibraryChange
import app.picnic.player.data.media.LibraryChangeBus
import app.picnic.player.data.playback.profile.DynamicProfileBuilder
import app.picnic.player.data.playback.quality.QualityOption
import app.picnic.player.data.playback.quality.QualityRung
import app.picnic.player.data.playback.quality.SourceQuality
import app.picnic.player.data.playback.quality.clampToCeiling
import app.picnic.player.data.settings.PlaybackSettings
import app.picnic.player.data.settings.SettingsStore
import app.picnic.player.di.IoDispatcher
import app.picnic.player.playback.StreamNegotiation
import app.picnic.player.playback.StreamNegotiator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.hlsSegmentApi
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.mediaSegmentsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.TranscodingInfo

enum class SegmentKind { INTRO, OUTRO, RECAP, PREVIEW, COMMERCIAL, UNKNOWN }

data class MediaSegment(
    val id: String,
    val kind: SegmentKind,
    val startMs: Long,
    val endMs: Long
)

/**
 * Stream negotiation (PlaybackInfo + device profile), trickplay, and
 * playback-progress reporting via the Jellyfin SDK.
 */
@Singleton
class PlaybackRepository @Inject constructor(
    private val jellyfin: JellyfinFactory,
    private val changeBus: LibraryChangeBus,
    private val settingsStore: SettingsStore,
    private val deviceIdentityStore: DeviceIdentityStore,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : StreamNegotiator {
    private fun api(session: UserSession) = jellyfin.api(session.server.baseUrl, session.accessToken)

    override suspend fun stopEncoding(session: UserSession, playSessionId: String?) {
        if (playSessionId == null) return
        onIo {
            runCatching {
                api(session).hlsSegmentApi.stopEncodingProcess(
                    deviceId = deviceIdentityStore.deviceId,
                    playSessionId = playSessionId
                )
            }
        }
    }

    /** Runs a network + deserialize [block] off the caller's dispatcher — see MediaRepository.onIo. */
    private suspend inline fun <T> onIo(crossinline block: suspend () -> T): T = withContext(ioDispatcher) { block() }

    override suspend fun resolveStream(negotiation: StreamNegotiation): StreamInfo = onIo {
        val session = negotiation.session
        val settings = settingsStore.settings.first()
        // No explicit choice follows the Default video quality setting; choosing Original is a
        // choice, and must not fall back to it.
        val chosen = negotiation.quality ?: settings.defaultVideoQuality?.let { QualityOption.Transcode(it) }
        val rung = (chosen as? QualityOption.Transcode)?.rung
        val ceiling = QualityRung.conversionCeiling(settings.allowFourKTranscoding)
        val first = negotiate(negotiation, settings, rung)
        val clamped = clampedRung(first.source, rung, ceiling)
            ?: return@onIo buildStreamInfo(session, negotiation.itemId, first.source, first.playSessionId, rung)
        stopEncoding(session, first.playSessionId)
        val second = negotiate(negotiation, settings, clamped)
        buildStreamInfo(session, negotiation.itemId, second.source, second.playSessionId, clamped)
    }

    private class Negotiated(val source: MediaSourceInfo, val playSessionId: String?)

    private suspend fun negotiate(
        negotiation: StreamNegotiation,
        settings: PlaybackSettings,
        rung: QualityRung?
    ): Negotiated {
        val session = negotiation.session
        val response = api(session).mediaInfoApi.getPostedPlaybackInfo(
            itemId = negotiation.itemId,
            data = PlaybackInfoDto(
                userId = UUID.fromString(session.userId),
                deviceProfile = DynamicProfileBuilder.build(context, settings, rung),
                startTimeTicks = negotiation.startTicks,
                maxStreamingBitrate = rung?.videoBitrate,
                mediaSourceId = negotiation.mediaSourceId,
                audioStreamIndex = negotiation.audioStreamIndex,
                subtitleStreamIndex = negotiation.subtitleStreamIndex,
                // Direct play off still allows a remux: the server rewrites the container and
                // copies both streams, so nothing is re-encoded.
                enableDirectPlay = negotiation.allowDirectPlay,
                allowVideoStreamCopy = true,
                allowAudioStreamCopy = true,
                alwaysBurnInSubtitleWhenTranscoding = false,
                autoOpenLiveStream = true
            )
        ).content
        val source = response.mediaSources.firstOrNull() ?: error("No playable source")
        return Negotiated(source, response.playSessionId)
    }

    private fun clampedRung(source: MediaSourceInfo, requested: QualityRung?, ceiling: QualityRung): QualityRung? {
        if (source.supportsDirectPlay == true || source.transcodingUrl == null) return null
        val quality = SourceQuality.of(source.bitrate, source.mediaStreams.orEmpty())
        return clampToCeiling(quality, requested, ceiling)
    }

    private fun buildStreamInfo(
        session: UserSession,
        itemId: UUID,
        source: MediaSourceInfo,
        playSessionId: String?,
        rung: QualityRung?
    ): StreamInfo {
        val base = session.server.baseUrl.trimEnd('/')
        val sourceId = source.id ?: itemId.toString()
        // `static=true` is the original file, byte for byte — only ever right when the server
        // granted direct play. Anything else it offers, it offers because the file as stored is not
        // what should be sent, so take the stream it planned instead.
        if (source.supportsDirectPlay == true) {
            val url = buildString {
                append(base).append("/Videos/").append(itemId).append("/stream")
                append("?static=true&mediaSourceId=").append(sourceId)
                if (playSessionId != null) append("&playSessionId=").append(playSessionId)
                source.container?.let { append("&container=").append(it) }
                append("&api_key=").append(session.accessToken)
            }
            return streamInfo(url, PlayMethodKind.DIRECT_PLAY, playSessionId, sourceId, source, session = session)
        }
        source.transcodingUrl?.let { path ->
            // Played exactly as returned: editing it yields a stream the server did not plan.
            val url = base + path
            return streamInfo(url, PlayMethodKind.TRANSCODE, playSessionId, sourceId, source, rung, session)
        }
        val url =
            "$base/Videos/$itemId/stream?static=true&mediaSourceId=$sourceId&api_key=${session.accessToken}"
        return streamInfo(url, PlayMethodKind.DIRECT_STREAM, playSessionId, sourceId, source, session = session)
    }

    private fun externalSubtitles(session: UserSession, source: MediaSourceInfo): List<ExternalSubtitle> {
        val base = session.server.baseUrl.trimEnd('/')
        return source.mediaStreams.orEmpty()
            .filter { it.type == MediaStreamType.SUBTITLE && it.deliveryMethod == SubtitleDeliveryMethod.EXTERNAL }
            .mapNotNull { stream ->
                val path = stream.deliveryUrl ?: return@mapNotNull null
                val url = if (path.startsWith("http")) path else base + path
                ExternalSubtitle(
                    streamIndex = stream.index,
                    url = if (url.contains("api_key=")) url else appendApiKey(url, session.accessToken),
                    mimeType = subtitleMimeType(stream.codec, path),
                    language = stream.language,
                    title = stream.displayTitle ?: stream.title
                )
            }
    }

    private fun appendApiKey(url: String, token: String): String = url + (if (url.contains('?')) "&" else "?") + "api_key=" + token

    private fun subtitleMimeType(codec: String?, path: String): String {
        val extension = path.substringAfterLast('.', "").substringBefore('?').lowercase()
        return when (codec?.lowercase() ?: extension) {
            "srt", "subrip" -> MimeTypes.APPLICATION_SUBRIP
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "vtt", "webvtt" -> MimeTypes.TEXT_VTT
            "ttml", "dfxp" -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    private fun streamInfo(
        url: String,
        method: PlayMethodKind,
        playSessionId: String?,
        sourceId: String,
        source: MediaSourceInfo,
        rung: QualityRung? = null,
        session: UserSession? = null
    ): StreamInfo = StreamInfo(
        url = url,
        playMethod = method,
        playSessionId = playSessionId,
        mediaSourceId = sourceId,
        runTimeTicks = source.runTimeTicks,
        mediaStreams = source.mediaStreams.orEmpty(),
        defaultAudioStreamIndex = source.defaultAudioStreamIndex,
        defaultSubtitleStreamIndex = source.defaultSubtitleStreamIndex,
        mediaSource = source,
        rung = rung,
        externalSubtitles = session?.let { externalSubtitles(it, source) }.orEmpty()
    )

    // --- progress reporting (resume + Continue Watching) --------------------

    override suspend fun reportStarted(session: UserSession, info: StreamInfo, itemId: UUID, positionTicks: Long): Unit = onIo {
        api(session).playStateApi.reportPlaybackStart(
            PlaybackStartInfo(
                itemId = itemId,
                mediaSourceId = info.mediaSourceId,
                playSessionId = info.playSessionId,
                positionTicks = positionTicks,
                canSeek = true,
                isPaused = false,
                isMuted = false,
                playMethod = info.sdkPlayMethod(),
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT
            )
        )
    }

    suspend fun reportProgress(
        session: UserSession,
        info: StreamInfo,
        itemId: UUID,
        positionTicks: Long,
        isPaused: Boolean
    ) = onIo {
        api(session).playStateApi.reportPlaybackProgress(
            PlaybackProgressInfo(
                itemId = itemId,
                mediaSourceId = info.mediaSourceId,
                playSessionId = info.playSessionId,
                positionTicks = positionTicks,
                isPaused = isPaused,
                canSeek = true,
                isMuted = false,
                playMethod = info.sdkPlayMethod(),
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT
            )
        )
    }

    override suspend fun reportStopped(
        session: UserSession,
        info: StreamInfo,
        itemId: UUID,
        positionTicks: Long,
        seriesId: UUID?
    ): Unit = onIo {
        api(session).playStateApi.reportPlaybackStopped(
            PlaybackStopInfo(
                itemId = itemId,
                mediaSourceId = info.mediaSourceId,
                playSessionId = info.playSessionId,
                positionTicks = positionTicks,
                failed = false
            )
        )
        // Resume position / watched state moved on the server — let other screens recompute.
        changeBus.emit(LibraryChange.ItemUpdated(itemId.toString(), seriesId?.toString()))
    }

    private fun StreamInfo.sdkPlayMethod(): PlayMethod = when (playMethod) {
        PlayMethodKind.DIRECT_PLAY -> PlayMethod.DIRECT_PLAY
        PlayMethodKind.DIRECT_STREAM -> PlayMethod.DIRECT_STREAM
        PlayMethodKind.TRANSCODE -> PlayMethod.TRANSCODE
    }

    // --- media segments -----------------------------------------------------

    /** Media segments for [itemId] (intro/outro/recap/...), empty if none/unsupported. */
    suspend fun mediaSegments(session: UserSession, itemId: UUID): List<MediaSegment> = onIo {
        runCatching {
            val response = api(session).mediaSegmentsApi.getItemSegments(itemId).content
            response.items.mapNotNull { dto ->
                val kind = when (dto.type) {
                    MediaSegmentType.INTRO -> SegmentKind.INTRO
                    MediaSegmentType.OUTRO -> SegmentKind.OUTRO
                    MediaSegmentType.RECAP -> SegmentKind.RECAP
                    MediaSegmentType.PREVIEW -> SegmentKind.PREVIEW
                    MediaSegmentType.COMMERCIAL -> SegmentKind.COMMERCIAL
                    MediaSegmentType.UNKNOWN -> SegmentKind.UNKNOWN
                    null -> return@mapNotNull null
                }
                if (kind == SegmentKind.UNKNOWN) return@mapNotNull null
                MediaSegment(
                    id = dto.id?.toString() ?: return@mapNotNull null,
                    kind = kind,
                    startMs = (dto.startTicks ?: 0L) / 10_000L,
                    endMs = (dto.endTicks ?: 0L) / 10_000L
                )
            }
        }.getOrDefault(emptyList())
    }

    // --- trickplay ----------------------------------------------------------

    fun trickplayTileUrl(session: UserSession, itemId: UUID, width: Int, tileIndex: Int): String {
        val base = session.server.baseUrl.trimEnd('/')
        return "$base/Videos/$itemId/Trickplay/$width/$tileIndex.jpg?api_key=${session.accessToken}"
    }

    /** Chapter image URL for chapter [index] on [itemId], tagged for cache-busting. */
    fun chapterImageUrl(session: UserSession, itemId: UUID, index: Int, imageTag: String): String {
        val base = session.server.baseUrl.trimEnd('/')
        return "$base/Items/$itemId/Images/Chapter/$index?tag=$imageTag&api_key=${session.accessToken}"
    }

    /** Best trickplay tile-set (largest width ≤ [maxWidth]) from an item DTO, or null. */
    fun trickplayFromItem(item: BaseItemDto, maxWidth: Int = 480): Pair<Int, TrickplayTiles>? {
        val bySource = item.trickplay ?: return null
        val byWidth = bySource.values.firstOrNull() ?: return null
        if (byWidth.isEmpty()) return null
        val widths = byWidth.keys.mapNotNull { it.toIntOrNull() }
        val width = widths.filter { it <= maxWidth }.maxOrNull() ?: widths.minOrNull() ?: return null
        val info = byWidth[width.toString()] ?: return null
        return width to TrickplayTiles(
            width = info.width,
            height = info.height,
            tileWidth = info.tileWidth,
            tileHeight = info.tileHeight,
            thumbnailCount = info.thumbnailCount,
            intervalMs = info.interval.let { raw ->
                // Jellyfin versions may return ms or ticks; values > ~1 h in "ms" are ticks.
                if (raw > 3_600_000) raw / 10_000 else raw
            }
        )
    }

    /** Best trickplay tile-set (largest width ≤ [maxWidth]) for [itemId], or null. */
    suspend fun trickplay(
        session: UserSession,
        itemId: UUID,
        maxWidth: Int = 480
    ): Pair<Int, TrickplayTiles>? = onIo {
        val item = api(session).userLibraryApi.getItem(itemId).content
        trickplayFromItem(item, maxWidth)
    }

    /**
     * Live transcoding/remux stats for the current device session.
     * Prefers a session whose [mediaSourceId] matches and that already has [TranscodingInfo].
     */
    suspend fun getTranscodingInfo(
        session: UserSession,
        mediaSourceId: String? = null
    ): TranscodingInfo? = onIo {
        runCatching {
            val sessions = api(session).sessionApi
                .getSessions(deviceId = deviceIdentityStore.deviceId)
                .content
            val matched = sessions.firstOrNull { s ->
                mediaSourceId != null &&
                    s.playState?.mediaSourceId == mediaSourceId &&
                    s.transcodingInfo != null
            }
            val withInfo = sessions.firstOrNull { it.transcodingInfo != null }
            (matched ?: withInfo ?: sessions.firstOrNull())?.transcodingInfo
        }.getOrNull()
    }
}
