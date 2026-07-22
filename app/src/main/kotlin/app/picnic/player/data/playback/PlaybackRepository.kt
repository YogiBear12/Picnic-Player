package app.picnic.player.data.playback

import android.content.Context
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.device.DeviceIdentityStore
import app.picnic.player.data.jellyfin.JellyfinFactory
import app.picnic.player.data.media.LibraryChange
import app.picnic.player.data.media.LibraryChangeBus
import app.picnic.player.data.playback.profile.DynamicProfileBuilder
import app.picnic.player.data.settings.SettingsStore
import app.picnic.player.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.mediaSegmentsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
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
) {
    private fun api(session: UserSession) = jellyfin.api(session.server.baseUrl, session.accessToken)

    /** Runs a network + deserialize [block] off the caller's dispatcher — see MediaRepository.onIo. */
    private suspend inline fun <T> onIo(crossinline block: suspend () -> T): T = withContext(ioDispatcher) { block() }

    /** Negotiates a playable source for [itemId] and returns the stream to load. */
    suspend fun resolveStream(
        session: UserSession,
        itemId: UUID,
        startTicks: Long?,
        mediaSourceId: String? = null
    ): StreamInfo = onIo {
        val settings = settingsStore.settings.first()
        val response = api(session).mediaInfoApi.getPostedPlaybackInfo(
            itemId = itemId,
            data = PlaybackInfoDto(
                userId = UUID.fromString(session.userId),
                deviceProfile = DynamicProfileBuilder.build(context, settings),
                startTimeTicks = startTicks,
                maxStreamingBitrate = 120_000_000,
                autoOpenLiveStream = true
            )
        ).content
        val source = response.mediaSources.firstOrNull() ?: error("No playable source")
        buildStreamInfo(session, itemId, source, response.playSessionId)
    }

    private fun buildStreamInfo(
        session: UserSession,
        itemId: UUID,
        source: MediaSourceInfo,
        playSessionId: String?
    ): StreamInfo {
        val base = session.server.baseUrl.trimEnd('/')
        val sourceId = source.id ?: itemId.toString()
        if (source.supportsDirectPlay == true || source.supportsDirectStream == true) {
            val url = buildString {
                append(base).append("/Videos/").append(itemId).append("/stream")
                append("?static=true&mediaSourceId=").append(sourceId)
                if (playSessionId != null) append("&playSessionId=").append(playSessionId)
                source.container?.let { append("&container=").append(it) }
                append("&api_key=").append(session.accessToken)
            }
            val method =
                if (source.supportsDirectPlay == true) {
                    PlayMethodKind.DIRECT_PLAY
                } else {
                    PlayMethodKind.DIRECT_STREAM
                }
            return streamInfo(url, method, playSessionId, sourceId, source)
        }
        source.transcodingUrl?.let {
            return streamInfo(base + it, PlayMethodKind.TRANSCODE, playSessionId, sourceId, source)
        }
        // Last resort: attempt a static stream anyway.
        val url =
            "$base/Videos/$itemId/stream?static=true&mediaSourceId=$sourceId&api_key=${session.accessToken}"
        return streamInfo(url, PlayMethodKind.DIRECT_STREAM, playSessionId, sourceId, source)
    }

    private fun streamInfo(
        url: String,
        method: PlayMethodKind,
        playSessionId: String?,
        sourceId: String,
        source: MediaSourceInfo
    ): StreamInfo = StreamInfo(
        url = url,
        playMethod = method,
        playSessionId = playSessionId,
        mediaSourceId = sourceId,
        runTimeTicks = source.runTimeTicks,
        mediaStreams = source.mediaStreams.orEmpty(),
        defaultAudioStreamIndex = source.defaultAudioStreamIndex,
        defaultSubtitleStreamIndex = source.defaultSubtitleStreamIndex,
        mediaSource = source
    )

    // --- progress reporting (resume + Continue Watching) --------------------

    suspend fun reportStart(session: UserSession, info: StreamInfo, itemId: UUID, positionTicks: Long) = onIo {
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

    suspend fun reportStopped(
        session: UserSession,
        info: StreamInfo,
        itemId: UUID,
        positionTicks: Long,
        seriesId: UUID? = null
    ) = onIo {
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
