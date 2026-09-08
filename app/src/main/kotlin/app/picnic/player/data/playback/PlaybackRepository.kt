package app.picnic.player.data.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MimeTypes
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.device.DeviceIdentityStore
import app.picnic.player.data.jellyfin.JellyfinFactory
import app.picnic.player.data.jellyfin.carriesApiKey
import app.picnic.player.data.jellyfin.withApiKey
import app.picnic.player.data.media.LibraryChange
import app.picnic.player.data.media.LibraryChangeBus
import app.picnic.player.data.playback.profile.DynamicProfileBuilder
import app.picnic.player.data.playback.quality.ConversionPlan
import app.picnic.player.data.playback.quality.NegotiatedSource
import app.picnic.player.data.playback.quality.QualityOption
import app.picnic.player.data.playback.quality.QualityRung
import app.picnic.player.data.playback.quality.SourceQuality
import app.picnic.player.data.playback.quality.conversionPlan
import app.picnic.player.data.settings.PlaybackSettings
import app.picnic.player.data.settings.SettingsStore
import app.picnic.player.di.IoDispatcher
import app.picnic.player.playback.PlaybackDiagnostics
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
import org.jellyfin.sdk.model.api.TranscodeReason
import org.jellyfin.sdk.model.api.TranscodingInfo

enum class SegmentKind { INTRO, OUTRO, RECAP, PREVIEW, COMMERCIAL }

data class MediaSegment(
    val id: String,
    val kind: SegmentKind,
    val startMs: Long,
    val endMs: Long
)

@Singleton
class PlaybackRepository @Inject constructor(
    private val jellyfin: JellyfinFactory,
    private val authRepository: AuthRepository,
    private val changeBus: LibraryChangeBus,
    private val settingsStore: SettingsStore,
    private val deviceIdentityStore: DeviceIdentityStore,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : StreamNegotiator {
    private fun api(session: UserSession) = jellyfin.api(session.server.baseUrl, session.accessToken)

    private suspend fun session(): UserSession = authRepository.requireSession()

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

    private suspend inline fun <T> onIo(crossinline block: suspend () -> T): T = withContext(ioDispatcher) { block() }

    override suspend fun resolveStream(negotiation: StreamNegotiation): StreamInfo = onIo {
        val session = negotiation.session
        val settings = settingsStore.settings.first()
        val chosen = negotiation.quality ?: settings.defaultVideoQuality?.let { QualityOption.Transcode(it) }
        val rung = (chosen as? QualityOption.Transcode)?.rung
        val ceiling = QualityRung.conversionCeiling(settings.allowFourKTranscoding)
        val burn = subtitleBurn(settings, negotiation.subtitleStreamIndex)
        val forceBurn = burn == SubtitleBurn.ALWAYS
        val first = negotiate(negotiation, settings, rung, pass = 1, burnIn = forceBurn, forceTranscode = forceBurn)
        val blockedBy = transcodeReasons(first.source)
        val negotiated = first.source.negotiated()
        val burnIn = forceBurn ||
            (burn == SubtitleBurn.WHEN_VIDEO_CONVERTED && negotiated.serverIsConverting)
        val plan = conversionPlan(negotiated, rung, ceiling, videoEncodeForced = forceBurn)
        val adoptedRung = (plan as? ConversionPlan.Renegotiate)?.rung ?: rung
        // A forced burn already asked for the burn in pass 1, so only an Automatic burn that
        // pass 1 could not have known about needs a second pass of its own.
        val burnNeedsSecondPass = burnIn && !forceBurn
        val adopted = if (plan is ConversionPlan.Renegotiate || burnNeedsSecondPass) {
            stopEncoding(session, first.playSessionId)
            negotiate(negotiation, settings, adoptedRung, pass = 2, burnIn = burnIn, forceTranscode = forceBurn)
        } else {
            first
        }
        buildStreamInfo(
            negotiation,
            adopted.source,
            adopted.playSessionId,
            adoptedRung,
            blockedBy,
            subtitlesBurnedIn = burnsSubtitles(adopted.source, negotiation.subtitleStreamIndex)
        )
    }

    private class Negotiated(val source: MediaSourceInfo, val playSessionId: String?)

    private suspend fun negotiate(
        negotiation: StreamNegotiation,
        settings: PlaybackSettings,
        rung: QualityRung?,
        pass: Int,
        burnIn: Boolean,
        forceTranscode: Boolean
    ): Negotiated {
        val session = negotiation.session
        PlaybackDiagnostics.logNegotiationRequest(
            pass = pass,
            burnIn = burnIn,
            forceTranscode = forceTranscode,
            subtitleStreamIndex = negotiation.subtitleStreamIndex,
            burnMode = settings.burnInSubtitles
        )
        val response = api(session).mediaInfoApi.getPostedPlaybackInfo(
            itemId = negotiation.itemId,
            data = PlaybackInfoDto(
                userId = session.userUuid,
                deviceProfile = DynamicProfileBuilder.build(context, settings, rung),
                startTimeTicks = negotiation.startTicks,
                maxStreamingBitrate = rung?.videoBitrate,
                mediaSourceId = negotiation.mediaSourceId,
                audioStreamIndex = negotiation.audioStreamIndex,
                subtitleStreamIndex = negotiation.subtitleStreamIndex,
                enableDirectPlay = negotiation.allowDirectPlay && !forceTranscode,
                allowVideoStreamCopy = !forceTranscode,
                allowAudioStreamCopy = true,
                alwaysBurnInSubtitleWhenTranscoding = burnIn,
                autoOpenLiveStream = true
            )
        ).content
        val source = response.mediaSources.firstOrNull() ?: error("No playable source")
        PlaybackDiagnostics.logNegotiation(pass, rung, source)
        return Negotiated(source, response.playSessionId)
    }

    private fun MediaSourceInfo.negotiated(): NegotiatedSource = NegotiatedSource(
        supportsDirectPlay = supportsDirectPlay,
        transcodingUrl = transcodingUrl,
        quality = SourceQuality.of(bitrate, mediaStreams.orEmpty()),
        transcodeReasons = transcodeReasons(this).mapNotNull { TranscodeReason.fromNameOrNull(it) }
    )

    private fun burnsSubtitles(source: MediaSourceInfo, subtitleStreamIndex: Int?): Boolean {
        val index = subtitleStreamIndex ?: return false
        val url = Uri.parse(source.transcodingUrl ?: return false)
        return url.getQueryParameter("alwaysBurnInSubtitleWhenTranscoding").toBoolean() &&
            url.getQueryParameter("SubtitleStreamIndex")?.toIntOrNull() == index
    }

    private fun transcodeReasons(source: MediaSourceInfo): List<String> {
        val url = source.transcodingUrl ?: return emptyList()
        val reasons = Uri.parse(url).getQueryParameter("TranscodeReasons") ?: return emptyList()
        return reasons.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun buildStreamInfo(
        negotiation: StreamNegotiation,
        source: MediaSourceInfo,
        playSessionId: String?,
        rung: QualityRung?,
        directPlayBlockedBy: List<String>,
        subtitlesBurnedIn: Boolean = false
    ): StreamInfo {
        val session = negotiation.session
        val itemId = negotiation.itemId
        val subtitleStreamIndex = negotiation.subtitleStreamIndex
        val base = session.server.baseUrl.trimEnd('/')
        val sourceId = source.id ?: itemId.toString()
        if (source.supportsDirectPlay == true) {
            val url = buildString {
                append(base).append("/Videos/").append(itemId).append("/stream")
                append("?static=true&mediaSourceId=").append(sourceId)
                if (playSessionId != null) append("&playSessionId=").append(playSessionId)
                source.container?.let { append("&container=").append(it) }
            }.withApiKey(session.accessToken)
            return streamInfo(url, PlayMethodKind.DIRECT_PLAY, playSessionId, sourceId, source, negotiatedSubtitleStreamIndex = subtitleStreamIndex, session = session, directPlayBlockedBy = directPlayBlockedBy)
        }
        source.transcodingUrl?.let { path ->
            val url = base + path
            return streamInfo(url, PlayMethodKind.TRANSCODE, playSessionId, sourceId, source, negotiatedSubtitleStreamIndex = subtitleStreamIndex, subtitlesBurnedIn = subtitlesBurnedIn, rung = rung, session = session, directPlayBlockedBy = directPlayBlockedBy)
        }
        val url =
            "$base/Videos/$itemId/stream?static=true&mediaSourceId=$sourceId".withApiKey(session.accessToken)
        return streamInfo(url, PlayMethodKind.DIRECT_STREAM, playSessionId, sourceId, source, negotiatedSubtitleStreamIndex = subtitleStreamIndex, session = session, directPlayBlockedBy = directPlayBlockedBy)
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
                    url = if (url.carriesApiKey()) url else url.withApiKey(session.accessToken),
                    mimeType = subtitleMimeType(stream.codec, path),
                    language = stream.language,
                    title = stream.displayTitle ?: stream.title
                )
            }
    }

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
        negotiatedSubtitleStreamIndex: Int?,
        subtitlesBurnedIn: Boolean = false,
        rung: QualityRung? = null,
        session: UserSession? = null,
        directPlayBlockedBy: List<String> = emptyList()
    ): StreamInfo = StreamInfo(
        url = url,
        playMethod = method,
        playSessionId = playSessionId,
        mediaSourceId = sourceId,
        runTimeTicks = source.runTimeTicks,
        mediaStreams = source.mediaStreams.orEmpty(),
        defaultAudioStreamIndex = source.defaultAudioStreamIndex,
        defaultSubtitleStreamIndex = source.defaultSubtitleStreamIndex,
        negotiatedSubtitleStreamIndex = negotiatedSubtitleStreamIndex,
        subtitlesBurnedIn = subtitlesBurnedIn,
        mediaSource = source,
        rung = rung,
        externalSubtitles = session?.let { externalSubtitles(it, source) }.orEmpty(),
        directPlayBlockedBy = directPlayBlockedBy
    )

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
        info: StreamInfo,
        itemId: UUID,
        positionTicks: Long,
        isPaused: Boolean
    ) = onIo {
        api(session()).playStateApi.reportPlaybackProgress(
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
        changeBus.emit(LibraryChange.ItemUpdated(itemId.toString(), seriesId?.toString()))
    }

    private fun StreamInfo.sdkPlayMethod(): PlayMethod = when (playMethod) {
        PlayMethodKind.DIRECT_PLAY -> PlayMethod.DIRECT_PLAY
        PlayMethodKind.DIRECT_STREAM -> PlayMethod.DIRECT_STREAM
        PlayMethodKind.TRANSCODE -> PlayMethod.TRANSCODE
    }

    suspend fun mediaSegments(itemId: UUID): List<MediaSegment> = onIo {
        runCatching {
            val response = api(session()).mediaSegmentsApi.getItemSegments(itemId).content
            response.items.mapNotNull { dto ->
                MediaSegment(
                    id = dto.id.toString(),
                    kind = dto.type.toSegmentKind() ?: return@mapNotNull null,
                    startMs = dto.startTicks.ticksToMs(),
                    endMs = dto.endTicks.ticksToMs()
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun MediaSegmentType.toSegmentKind(): SegmentKind? = when (this) {
        MediaSegmentType.INTRO -> SegmentKind.INTRO
        MediaSegmentType.OUTRO -> SegmentKind.OUTRO
        MediaSegmentType.RECAP -> SegmentKind.RECAP
        MediaSegmentType.PREVIEW -> SegmentKind.PREVIEW
        MediaSegmentType.COMMERCIAL -> SegmentKind.COMMERCIAL
        MediaSegmentType.UNKNOWN -> null
    }

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
                if (raw > 3_600_000) raw / 10_000 else raw
            }
        )
    }

    suspend fun getTranscodingInfo(
        mediaSourceId: String? = null
    ): TranscodingInfo? = onIo {
        runCatching {
            val sessions = api(session()).sessionApi
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
