package app.picnic.player.data.settings

import app.picnic.player.data.playback.quality.QualityRung

class SettingKey<T> internal constructor(
    val read: (PlaybackSettings) -> T,
    internal val write: suspend (SettingsStore, T) -> Unit
)

object SettingKeys {
    val AutoLoginLastUser = SettingKey({ it.autoLoginLastUser }, SettingsStore::setAutoLoginLastUser)
    val AmbientBackgrounds = SettingKey({ it.ambientBackgrounds }, SettingsStore::setAmbientBackgrounds)
    val ColouredFocus = SettingKey({ it.colouredFocus }, SettingsStore::setColouredFocus)
    val PulseFocusGlow = SettingKey({ it.pulseFocusGlow }, SettingsStore::setPulseFocusGlow)
    val CapBadgeCount = SettingKey({ it.capBadgeCount }, SettingsStore::setCapBadgeCount)
    val ThemeMusicVolume = SettingKey({ it.themeMusicVolume }, SettingsStore::setThemeMusicVolume)

    val SkipForwardSeconds = SettingKey({ it.skipForwardSeconds }, SettingsStore::setSkipForwardSeconds)
    val SkipBackwardSeconds = SettingKey({ it.skipBackwardSeconds }, SettingsStore::setSkipBackwardSeconds)
    val OsdHideSeconds = SettingKey({ it.osdHideSeconds }, SettingsStore::setOsdHideSeconds)
    val NextUpCountdownSeconds = SettingKey({ it.nextUpCountdownSeconds }, SettingsStore::setNextUpCountdownSeconds)
    val DisplayNextUpDuringOutro = SettingKey({ it.displayNextUpDuringOutro }, SettingsStore::setDisplayNextUpDuringOutro)
    val IntroAction = SettingKey({ it.introAction }, SettingsStore::setIntroAction)
    val RecapAction = SettingKey({ it.recapAction }, SettingsStore::setRecapAction)
    val OutroAction = SettingKey({ it.outroAction }, SettingsStore::setOutroAction)
    val PreviewAction = SettingKey({ it.previewAction }, SettingsStore::setPreviewAction)
    val CommercialAction = SettingKey({ it.commercialAction }, SettingsStore::setCommercialAction)

    val DefaultVideoQuality = SettingKey<QualityRung?>({ it.defaultVideoQuality }, SettingsStore::setDefaultVideoQuality)
    val PreferredSubtitleLanguage = SettingKey<String?>({ it.preferredSubtitleLanguage }, SettingsStore::setPreferredSubtitleLanguage)
    val AlwaysDisplaySubtitles = SettingKey({ it.alwaysDisplaySubtitles }, SettingsStore::setAlwaysDisplaySubtitles)

    val PictureInPicture = SettingKey({ it.pictureInPicture }, SettingsStore::setPictureInPicture)
    val MatchRefreshRate = SettingKey({ it.matchRefreshRate }, SettingsStore::setMatchRefreshRate)
    val MatchResolution = SettingKey({ it.matchResolution }, SettingsStore::setMatchResolution)
    val ForceDirectPlay = SettingKey({ it.forceDirectPlay }, SettingsStore::setForceDirectPlay)
    val DownmixStereo = SettingKey({ it.downmixStereo }, SettingsStore::setDownmixStereo)
    val ForceDoviProfile7 = SettingKey({ it.forceDoviProfile7 }, SettingsStore::setForceDoviProfile7)
    val AllowFourKTranscoding = SettingKey({ it.allowFourKTranscoding }, SettingsStore::setAllowFourKTranscoding)
    val TrailerYouTubePackage = SettingKey<String?>({ it.trailerYouTubePackage }, SettingsStore::setTrailerYouTubePackage)
}
