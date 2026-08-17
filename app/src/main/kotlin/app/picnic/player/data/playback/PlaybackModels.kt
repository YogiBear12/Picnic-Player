package app.picnic.player.data.playback

import org.jellyfin.sdk.model.api.MediaStream

enum class PlayMethodKind { DIRECT_PLAY, DIRECT_STREAM, TRANSCODE }

fun refinePlayMethod(
    initial: PlayMethodKind,
    info: org.jellyfin.sdk.model.api.TranscodingInfo?
): PlayMethodKind {
    if (info == null || initial == PlayMethodKind.DIRECT_PLAY) return initial
    return if (info.isVideoDirect) PlayMethodKind.DIRECT_STREAM else PlayMethodKind.TRANSCODE
}

data class ExternalSubtitle(
    val streamIndex: Int,
    val url: String,
    val mimeType: String,
    val language: String?,
    val title: String?
)

data class StreamInfo(
    val url: String,
    val playMethod: PlayMethodKind,
    val playSessionId: String?,
    val mediaSourceId: String,
    val runTimeTicks: Long?,
    val mediaStreams: List<MediaStream> = emptyList(),
    val defaultAudioStreamIndex: Int? = null,
    val defaultSubtitleStreamIndex: Int? = null,
    val mediaSource: org.jellyfin.sdk.model.api.MediaSourceInfo? = null,
    val rung: app.picnic.player.data.playback.quality.QualityRung? = null,
    val externalSubtitles: List<ExternalSubtitle> = emptyList(),
    val directPlayBlockedBy: List<String> = emptyList()
)

data class TrickplayTiles(
    val width: Int,
    val height: Int,
    val tileWidth: Int,
    val tileHeight: Int,
    val thumbnailCount: Int,
    val intervalMs: Int
) {
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
