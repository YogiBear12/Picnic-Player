package app.picnic.player.data.playback

import org.jellyfin.sdk.model.api.MediaStream

/** How a resolved source is delivered. */
enum class PlayMethodKind { DIRECT_PLAY, DIRECT_STREAM, TRANSCODE }

/** A ready-to-play stream resolved from the server's PlaybackInfo. */
data class StreamInfo(
    val url: String,
    val playMethod: PlayMethodKind,
    val playSessionId: String?,
    val mediaSourceId: String,
    val runTimeTicks: Long?,
    /** Server stream metadata for track labels (regional language names, codecs). */
    val mediaStreams: List<MediaStream> = emptyList(),
    /**
     * Default audio/subtitle stream indices the server picked from the requesting user's
     * preferences (audio + subtitle language, subtitle mode). Null = none / fall back.
     */
    val defaultAudioStreamIndex: Int? = null,
    val defaultSubtitleStreamIndex: Int? = null,
    val mediaSource: org.jellyfin.sdk.model.api.MediaSourceInfo
)

/** Trickplay tile-set geometry for the scrub preview. */
data class TrickplayTiles(
    val width: Int,
    val height: Int,
    val tileWidth: Int,
    val tileHeight: Int,
    val thumbnailCount: Int,
    val intervalMs: Int
) {
    /** (tileIndex, row, col) for a position in ms. */
    fun tileFor(positionMs: Long): Triple<Int, Int, Int> {
        val perTile = tileWidth * tileHeight
        val index = if (intervalMs > 0) (positionMs / intervalMs).toInt() else 0
        val clamped = index.coerceIn(0, (thumbnailCount - 1).coerceAtLeast(0))
        val tileIndex = if (perTile > 0) clamped / perTile else 0
        val within = if (perTile > 0) clamped % perTile else 0
        val row = if (tileWidth > 0) within / tileWidth else 0
        val col = if (tileWidth > 0) within % tileWidth else 0
        return Triple(tileIndex, row, col)
    }
}
