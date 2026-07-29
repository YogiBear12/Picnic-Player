package app.picnic.player.playback

import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.playback.StreamInfo
import app.picnic.player.data.playback.msToTicks
import app.picnic.player.data.playback.quality.QualityOption
import app.picnic.player.data.playback.ticksToMs
import java.util.UUID

interface StreamNegotiator {
    suspend fun resolveStream(
        session: UserSession,
        itemId: UUID,
        startTicks: Long?,
        mediaSourceId: String?,
        quality: QualityOption?,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?
    ): StreamInfo

    suspend fun stopEncoding(session: UserSession, playSessionId: String?)

    suspend fun reportStarted(session: UserSession, info: StreamInfo, itemId: UUID, positionTicks: Long)

    suspend fun reportStopped(
        session: UserSession,
        info: StreamInfo,
        itemId: UUID,
        positionTicks: Long,
        seriesId: UUID?
    )
}

interface StreamTarget {
    val positionMs: Long
    fun stop()
    fun load(stream: StreamInfo, resumeMs: Long)
    fun resume(playing: Boolean)
}

data class StreamRequest(
    val session: UserSession,
    val itemId: UUID,
    val seriesId: UUID?,
    val positionTicks: Long,
    val mediaSourceId: String?,
    val quality: QualityOption?,
    val audioStreamIndex: Int?,
    val subtitleStreamIndex: Int?,
    val resumePlaying: Boolean,
    val replacing: StreamInfo?
)

sealed interface StreamResult {
    data class Loaded(val stream: StreamInfo, val positionTicks: Long) : StreamResult
    data class Failed(val restored: StreamInfo?, val positionTicks: Long) : StreamResult
}

/**
 * Puts a negotiated stream into the player. Replacing one that is already running has extra steps,
 * and their order is the whole point of this module:
 *
 * stop playback -> read the position -> end the server's encoder -> report stopped -> negotiate
 * -> load -> resume. A failed negotiation puts the previous stream back where it was.
 */
class StreamLoader(
    private val target: StreamTarget,
    private val negotiator: StreamNegotiator
) {
    suspend fun load(request: StreamRequest): StreamResult {
        val replacing = request.replacing
        // Stop first, or the outgoing stream runs on through negotiation and the incoming one
        // resumes behind where playback actually reached.
        val positionTicks = if (replacing != null) {
            target.stop()
            target.positionMs.msToTicks()
        } else {
            request.positionTicks
        }

        if (replacing != null) {
            negotiator.stopEncoding(request.session, replacing.playSessionId)
            runCatching {
                negotiator.reportStopped(request.session, replacing, request.itemId, positionTicks, request.seriesId)
            }
        }

        val stream = runCatching {
            negotiator.resolveStream(
                session = request.session,
                itemId = request.itemId,
                startTicks = positionTicks,
                mediaSourceId = request.mediaSourceId,
                quality = request.quality,
                audioStreamIndex = request.audioStreamIndex,
                subtitleStreamIndex = request.subtitleStreamIndex
            )
        }.getOrNull()

        if (stream == null) {
            replacing?.let { install(it, positionTicks, request) }
            return StreamResult.Failed(replacing, positionTicks)
        }

        install(stream, positionTicks, request)
        return StreamResult.Loaded(stream, positionTicks)
    }

    private suspend fun install(stream: StreamInfo, positionTicks: Long, request: StreamRequest) {
        target.load(stream, positionTicks.ticksToMs())
        target.resume(request.resumePlaying)
        runCatching { negotiator.reportStarted(request.session, stream, request.itemId, positionTicks) }
    }
}
