package app.picnic.player.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** OSD style. Modern only for now, but modelled as an enum + dispatcher so a new
 *  style can be added without touching call sites (per product direction). */
enum class OsdStyle { MODERN }

/** On-bar scrubbing behaviour. Confirm previews then seeks on Center; Instant
 *  seeks live as you scrub. */
enum class SeekMode { CONFIRM, INSTANT }

/** Volume for the theme music that plays while browsing an item's details.
 *  DISABLED turns the feature off entirely. */
enum class ThemeMusicVolume { DISABLED, QUIET, LOW, MEDIUM, HIGH, LOUD }

/** Per-segment skip behaviour for a Jellyfin media-segment type.
 *  ASK_TO_SKIP shows the skip button; SKIP_AUTOMATICALLY seeks past it with no button;
 *  DO_NOT_SKIP shows no button and never seeks. */
enum class SegmentAction { ASK_TO_SKIP, SKIP_AUTOMATICALLY, DO_NOT_SKIP }

/** Text-cue size for rendered subtitles (SRT/VTT/…). Mapped to sp in the playback layer. */
enum class SubtitleSize { SMALLER, STANDARD, LARGER }

/** Text-cue colour for rendered subtitles. Mapped to ARGB in the playback layer. */
enum class SubtitleColour { WHITE, YELLOW, CYAN, GREEN }

/** Fill of the subtitle background box: see-through black or fully opaque black. */
enum class SubtitleBackgroundStyle { TRANSLUCENT, SOLID }

/**
 * How text-based subtitle cues are drawn (#54). ASS/SSA cues keep their authored
 * styling via libass and are untouched by these preferences.
 *
 * [background] draws a single black box behind the whole caption block;
 * [backgroundStyle] only applies while it is on.
 */
data class SubtitleAppearance(
    val size: SubtitleSize = SubtitleSize.STANDARD,
    val colour: SubtitleColour = SubtitleColour.WHITE,
    val background: Boolean = false,
    val backgroundStyle: SubtitleBackgroundStyle = SubtitleBackgroundStyle.TRANSLUCENT
)

data class PlaybackSettings(
    val osdStyle: OsdStyle = OsdStyle.MODERN,
    val seekMode: SeekMode = SeekMode.CONFIRM,
    val skipForwardSeconds: Int = 30,
    val skipBackwardSeconds: Int = 10,
    val osdHideSeconds: Int = 3,
    val clickToPause: Boolean = true,
    /** Card focus chrome uses artwork-derived colour; false = plain white. */
    val colouredFocus: Boolean = true,
    /** Focused card glow gently pulses brightness; false = static glow. */
    val pulseFocusGlow: Boolean = true,
    /** Dynamic colour-extracted wash on hero/detail backdrops; false = static ocean wash. */
    val ambientBackgrounds: Boolean = true,
    /** Unwatched episode count badge caps at "99+" when true. */
    val capBadgeCount: Boolean = true,
    /** Theme music volume on detail/episode screens; DISABLED (default) plays nothing. */
    val themeMusicVolume: ThemeMusicVolume = ThemeMusicVolume.DISABLED,
    val introAction: SegmentAction = SegmentAction.ASK_TO_SKIP,
    val recapAction: SegmentAction = SegmentAction.ASK_TO_SKIP,
    val outroAction: SegmentAction = SegmentAction.ASK_TO_SKIP,
    val previewAction: SegmentAction = SegmentAction.ASK_TO_SKIP,
    val commercialAction: SegmentAction = SegmentAction.SKIP_AUTOMATICALLY,
    /** Seconds to count down before auto-playing the next episode. Clamped to 3..15. */
    val nextUpCountdownSeconds: Int = 5,
    /** When ON, next-up overlay appears during the outro segment (requires Media Segments).
     *  Silently falls back to the default at-end behaviour when no OUTRO segment is detected. */
    val displayNextUpDuringOutro: Boolean = true,
    /** When ON (default), the app signs straight into the last-used profile on
     *  launch; when OFF it opens the Select User screen instead. */
    val autoLoginLastUser: Boolean = true,
    /** When ON, pressing the Home button during playback enters Picture-in-Picture mode. */
    val pictureInPicture: Boolean = false,
    /** When ON, the app will automatically switch the TV's HDMI refresh rate to match the video's frame rate. */
    val matchRefreshRate: Boolean = false,
    /** When ON, the app will automatically switch the TV's HDMI resolution to match the video's resolution. */
    val matchResolution: Boolean = false,
    /** Downmix audio to stereo */
    val downmixStereo: Boolean = false,
    /** Force Dolby Vision Profile 7 support */
    val forceDoviProfile7: Boolean = false,
    /** Package name of the custom YouTube app to open trailers with */
    val trailerYouTubePackage: String? = null,
    /**
     * Preferred audio language (ISO 639), or null = Unspecified.
     * Device-local (#15); does not write Jellyfin UserConfiguration.
     */
    val preferredAudioLanguage: String? = null,
    /**
     * Preferred subtitle language (ISO 639), or null = Unspecified
     * (selection treats Unspecified as English).
     */
    val preferredSubtitleLanguage: String? = null,
    /** When true, Always show subtitles; when false, Smart (audio vs sub language). */
    val alwaysDisplaySubtitles: Boolean = false,
    /** Style for rendered text subtitle cues (#54). */
    val subtitleAppearance: SubtitleAppearance = SubtitleAppearance()
)

/** User-tunable playback settings persisted in DataStore. */
@Singleton
class SettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    val settings: Flow<PlaybackSettings> = dataStore.data.map { p ->
        PlaybackSettings(
            osdStyle = p[OSD_STYLE]?.let { enumOrNull<OsdStyle>(it) } ?: OsdStyle.MODERN,
            seekMode = p[SEEK_MODE]?.let { enumOrNull<SeekMode>(it) } ?: SeekMode.CONFIRM,
            skipForwardSeconds = p[SKIP_FWD] ?: 30,
            skipBackwardSeconds = p[SKIP_BACK] ?: 10,
            osdHideSeconds = p[OSD_HIDE] ?: 3,
            clickToPause = p[CLICK_PAUSE] ?: true,
            colouredFocus = p[COLOURED_FOCUS] ?: true,
            pulseFocusGlow = p[PULSE_FOCUS_GLOW] ?: true,
            ambientBackgrounds = p[AMBIENT_BACKGROUNDS] ?: true,
            capBadgeCount = p[CAP_BADGE_COUNT] ?: true,
            themeMusicVolume = p[THEME_MUSIC_VOLUME]?.let { enumOrNull<ThemeMusicVolume>(it) }
                ?: ThemeMusicVolume.DISABLED,
            introAction = p[INTRO_ACTION]?.let { enumOrNull<SegmentAction>(it) } ?: SegmentAction.ASK_TO_SKIP,
            recapAction = p[RECAP_ACTION]?.let { enumOrNull<SegmentAction>(it) } ?: SegmentAction.ASK_TO_SKIP,
            outroAction = p[OUTRO_ACTION]?.let { enumOrNull<SegmentAction>(it) } ?: SegmentAction.ASK_TO_SKIP,
            previewAction = p[PREVIEW_ACTION]?.let { enumOrNull<SegmentAction>(it) } ?: SegmentAction.ASK_TO_SKIP,
            commercialAction = p[COMMERCIAL_ACTION]?.let { enumOrNull<SegmentAction>(it) } ?: SegmentAction.SKIP_AUTOMATICALLY,
            nextUpCountdownSeconds = (p[NEXT_UP_COUNTDOWN] ?: 5).coerceIn(0, 15),
            displayNextUpDuringOutro = p[DISPLAY_NEXT_UP_DURING_OUTRO] ?: true,
            autoLoginLastUser = p[AUTO_LOGIN_LAST_USER] ?: true,
            pictureInPicture = p[PICTURE_IN_PICTURE] ?: false,
            matchRefreshRate = p[MATCH_REFRESH_RATE] ?: false,
            matchResolution = p[MATCH_RESOLUTION] ?: false,
            downmixStereo = p[DOWNMIX_STEREO] ?: false,
            forceDoviProfile7 = p[FORCE_DOVI_PROFILE_7] ?: false,
            trailerYouTubePackage = p[TRAILER_YOUTUBE_PACKAGE],
            preferredAudioLanguage = p[PREFERRED_AUDIO_LANGUAGE],
            preferredSubtitleLanguage = p[PREFERRED_SUBTITLE_LANGUAGE],
            alwaysDisplaySubtitles = p[ALWAYS_DISPLAY_SUBTITLES] ?: false,
            subtitleAppearance = SubtitleAppearance(
                size = p[SUBTITLE_SIZE]?.let { enumOrNull<SubtitleSize>(it) } ?: SubtitleSize.STANDARD,
                colour = p[SUBTITLE_COLOUR]?.let { enumOrNull<SubtitleColour>(it) } ?: SubtitleColour.WHITE,
                background = p[SUBTITLE_BACKGROUND] ?: false,
                backgroundStyle = p[SUBTITLE_BACKGROUND_STYLE]?.let { enumOrNull<SubtitleBackgroundStyle>(it) }
                    ?: SubtitleBackgroundStyle.TRANSLUCENT
            )
        )
    }

    suspend fun setOsdStyle(value: OsdStyle) = put { it[OSD_STYLE] = value.name }
    suspend fun setSeekMode(value: SeekMode) = put { it[SEEK_MODE] = value.name }
    suspend fun setSkipForwardSeconds(value: Int) = put { it[SKIP_FWD] = value }
    suspend fun setSkipBackwardSeconds(value: Int) = put { it[SKIP_BACK] = value }
    suspend fun setOsdHideSeconds(value: Int) = put { it[OSD_HIDE] = value }
    suspend fun setClickToPause(value: Boolean) = put { it[CLICK_PAUSE] = value }
    suspend fun setColouredFocus(value: Boolean) = put { it[COLOURED_FOCUS] = value }
    suspend fun setPulseFocusGlow(value: Boolean) = put { it[PULSE_FOCUS_GLOW] = value }
    suspend fun setAmbientBackgrounds(value: Boolean) = put { it[AMBIENT_BACKGROUNDS] = value }
    suspend fun setCapBadgeCount(value: Boolean) = put { it[CAP_BADGE_COUNT] = value }
    suspend fun setThemeMusicVolume(value: ThemeMusicVolume) = put { it[THEME_MUSIC_VOLUME] = value.name }
    suspend fun setIntroAction(value: SegmentAction) = put { it[INTRO_ACTION] = value.name }
    suspend fun setRecapAction(value: SegmentAction) = put { it[RECAP_ACTION] = value.name }
    suspend fun setOutroAction(value: SegmentAction) = put { it[OUTRO_ACTION] = value.name }
    suspend fun setPreviewAction(value: SegmentAction) = put { it[PREVIEW_ACTION] = value.name }
    suspend fun setCommercialAction(value: SegmentAction) = put { it[COMMERCIAL_ACTION] = value.name }
    suspend fun setNextUpCountdownSeconds(value: Int) = put { it[NEXT_UP_COUNTDOWN] = value.coerceIn(0, 15) }
    suspend fun setDisplayNextUpDuringOutro(value: Boolean) = put { it[DISPLAY_NEXT_UP_DURING_OUTRO] = value }
    suspend fun setAutoLoginLastUser(value: Boolean) = put { it[AUTO_LOGIN_LAST_USER] = value }
    suspend fun setPictureInPicture(value: Boolean) = put { it[PICTURE_IN_PICTURE] = value }
    suspend fun setMatchRefreshRate(value: Boolean) = put { it[MATCH_REFRESH_RATE] = value }
    suspend fun setMatchResolution(value: Boolean) = put { it[MATCH_RESOLUTION] = value }
    suspend fun setDownmixStereo(value: Boolean) = put { it[DOWNMIX_STEREO] = value }
    suspend fun setForceDoviProfile7(value: Boolean) = put { it[FORCE_DOVI_PROFILE_7] = value }
    suspend fun setTrailerYouTubePackage(value: String?) = put {
        if (value == null) {
            it.remove(TRAILER_YOUTUBE_PACKAGE)
        } else {
            it[TRAILER_YOUTUBE_PACKAGE] = value
        }
    }

    suspend fun setPreferredAudioLanguage(value: String?) = put {
        if (value.isNullOrBlank()) {
            it.remove(PREFERRED_AUDIO_LANGUAGE)
        } else {
            it[PREFERRED_AUDIO_LANGUAGE] = value
        }
    }

    suspend fun setPreferredSubtitleLanguage(value: String?) = put {
        if (value.isNullOrBlank()) {
            it.remove(PREFERRED_SUBTITLE_LANGUAGE)
        } else {
            it[PREFERRED_SUBTITLE_LANGUAGE] = value
        }
    }

    suspend fun setAlwaysDisplaySubtitles(value: Boolean) = put {
        it[ALWAYS_DISPLAY_SUBTITLES] = value
    }

    suspend fun setSubtitleSize(value: SubtitleSize) = put { it[SUBTITLE_SIZE] = value.name }
    suspend fun setSubtitleColour(value: SubtitleColour) = put { it[SUBTITLE_COLOUR] = value.name }
    suspend fun setSubtitleBackground(value: Boolean) = put { it[SUBTITLE_BACKGROUND] = value }
    suspend fun setSubtitleBackgroundStyle(value: SubtitleBackgroundStyle) = put {
        it[SUBTITLE_BACKGROUND_STYLE] = value.name
    }

    private suspend fun put(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private companion object {
        val OSD_STYLE = stringPreferencesKey("playback.osdStyle")
        val SEEK_MODE = stringPreferencesKey("playback.seekMode")
        val SKIP_FWD = intPreferencesKey("playback.skipForwardSeconds")
        val SKIP_BACK = intPreferencesKey("playback.skipBackwardSeconds")
        val OSD_HIDE = intPreferencesKey("playback.osdHideSeconds")
        val CLICK_PAUSE = booleanPreferencesKey("playback.clickToPause")
        val COLOURED_FOCUS = booleanPreferencesKey("ui.colouredFocus")
        val PULSE_FOCUS_GLOW = booleanPreferencesKey("ui.pulseFocusGlow")
        val AMBIENT_BACKGROUNDS = booleanPreferencesKey("ui.ambientBackgrounds")
        val CAP_BADGE_COUNT = booleanPreferencesKey("ui.capBadgeCount")
        val THEME_MUSIC_VOLUME = stringPreferencesKey("ui.themeMusicVolume")
        val INTRO_ACTION = stringPreferencesKey("playback.introAction")
        val RECAP_ACTION = stringPreferencesKey("playback.recapAction")
        val OUTRO_ACTION = stringPreferencesKey("playback.outroAction")
        val PREVIEW_ACTION = stringPreferencesKey("playback.previewAction")
        val COMMERCIAL_ACTION = stringPreferencesKey("playback.commercialAction")
        val NEXT_UP_COUNTDOWN = intPreferencesKey("playback.nextUpCountdownSeconds")
        val DISPLAY_NEXT_UP_DURING_OUTRO = booleanPreferencesKey("playback.displayNextUpDuringOutro")
        val AUTO_LOGIN_LAST_USER = booleanPreferencesKey("account.autoLoginLastUser")
        val PICTURE_IN_PICTURE = booleanPreferencesKey("playback.pictureInPicture")
        val MATCH_REFRESH_RATE = booleanPreferencesKey("playback.matchRefreshRate")
        val MATCH_RESOLUTION = booleanPreferencesKey("playback.matchResolution")
        val DOWNMIX_STEREO = booleanPreferencesKey("playback.downmixStereo")
        val FORCE_DOVI_PROFILE_7 = booleanPreferencesKey("playback.forceDoviProfile7")
        val TRAILER_YOUTUBE_PACKAGE = stringPreferencesKey("playback.trailerYouTubePackage")
        val PREFERRED_AUDIO_LANGUAGE = stringPreferencesKey("playback.preferredAudioLanguage")
        val PREFERRED_SUBTITLE_LANGUAGE = stringPreferencesKey("playback.preferredSubtitleLanguage")
        val ALWAYS_DISPLAY_SUBTITLES = booleanPreferencesKey("playback.alwaysDisplaySubtitles")
        val SUBTITLE_SIZE = stringPreferencesKey("playback.subtitleSize")
        val SUBTITLE_COLOUR = stringPreferencesKey("playback.subtitleColour")
        val SUBTITLE_BACKGROUND = booleanPreferencesKey("playback.subtitleBackground")
        val SUBTITLE_BACKGROUND_STYLE = stringPreferencesKey("playback.subtitleBackgroundStyle")

        inline fun <reified T : Enum<T>> enumOrNull(name: String): T? = runCatching { enumValueOf<T>(name) }.getOrNull()
    }
}
