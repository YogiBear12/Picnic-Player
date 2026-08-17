package app.picnic.player.ui.player

enum class ReloadReason(val failureNotice: String) {
    PLAYBACK_FAILED("Couldn't play this file"),
    AUDIO_CHANGE("Couldn't change audio"),
    SUBTITLE_CHANGE("Couldn't load subtitles"),
    QUALITY_CHANGE("Couldn't change quality")
}
