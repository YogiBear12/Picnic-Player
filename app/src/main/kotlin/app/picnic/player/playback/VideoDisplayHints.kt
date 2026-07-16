package app.picnic.player.playback

import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType

/**
 * Video dimensions / frame rate used to pick an HDMI display mode.
 * Prefer Jellyfin [MediaStream] values; ExoPlayer format fills gaps only.
 */
data class VideoDisplayHints(
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Float? = null
) {
    val hasAny: Boolean
        get() = (width != null && width > 0) ||
            (height != null && height > 0) ||
            (frameRate != null && frameRate > 0f)
}

/** First video stream's width / height / real-or-average frame rate from Jellyfin. */
fun List<MediaStream>.videoDisplayHints(): VideoDisplayHints? {
    val video = firstOrNull { it.type == MediaStreamType.VIDEO } ?: return null
    return video.videoDisplayHints().takeIf { it.hasAny }
}

fun MediaStream.videoDisplayHints(): VideoDisplayHints {
    val rate = realFrameRate?.takeIf { it > 0f }
        ?: averageFrameRate?.takeIf { it > 0f }
    return VideoDisplayHints(
        width = width?.takeIf { it > 0 },
        height = height?.takeIf { it > 0 },
        frameRate = rate
    )
}

/** Fully resolved stream params ready for HDMI mode selection. */
data class ResolvedVideoDisplayParams(
    val width: Int,
    val height: Int,
    val frameRate: Float
)

/**
 * Prefer [jellyfin] fields; use player-reported values only where Jellyfin is missing.
 * Returns null when width, height, or frame rate is still unknown.
 */
fun coalesceVideoDisplayParams(
    jellyfin: VideoDisplayHints?,
    formatWidth: Int,
    formatHeight: Int,
    formatFrameRate: Float
): ResolvedVideoDisplayParams? {
    val width = jellyfin?.width?.takeIf { it > 0 }
        ?: formatWidth.takeIf { it > 0 }
        ?: return null
    val height = jellyfin?.height?.takeIf { it > 0 }
        ?: formatHeight.takeIf { it > 0 }
        ?: return null
    val frameRate = jellyfin?.frameRate?.takeIf { it > 0f }
        ?: formatFrameRate.takeIf { it > 0f }
        ?: return null
    return ResolvedVideoDisplayParams(width = width, height = height, frameRate = frameRate)
}
