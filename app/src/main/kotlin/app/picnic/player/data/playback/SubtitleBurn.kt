package app.picnic.player.data.playback

import app.picnic.player.data.settings.BurnInSubtitles
import app.picnic.player.data.settings.PlaybackSettings

enum class SubtitleBurn { NONE, WHEN_VIDEO_CONVERTED, ALWAYS }

fun subtitleBurn(settings: PlaybackSettings, subtitleStreamIndex: Int?): SubtitleBurn = when {
    subtitleStreamIndex == null || settings.forceDirectPlay -> SubtitleBurn.NONE
    settings.burnInSubtitles == BurnInSubtitles.ALWAYS -> SubtitleBurn.ALWAYS
    settings.burnInSubtitles == BurnInSubtitles.AUTOMATIC -> SubtitleBurn.WHEN_VIDEO_CONVERTED
    else -> SubtitleBurn.NONE
}
