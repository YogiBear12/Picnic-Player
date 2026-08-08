package app.picnic.player.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.picnic.player.data.auth.UserScope
import app.picnic.player.data.playback.quality.QualityRung
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Volume for the theme music that plays while browsing an item's details.
 *  DISABLED turns the feature off entirely. */
enum class ThemeMusicVolume { DISABLED, QUIET, LOW, MEDIUM, HIGH, LOUD }

/** Per-segment skip behaviour for a Jellyfin media-segment type.
 *  ASK_TO_SKIP shows the skip button; SKIP_AUTOMATICALLY seeks past it with no button;
 *  DO_NOT_SKIP shows no button and never seeks. */
enum class SegmentAction { ASK_TO_SKIP, SKIP_AUTOMATICALLY, DO_NOT_SKIP }

/** Text-cue size for rendered subtitles (SRT/VTT/…). Mapped to sp in the playback layer. */
enum class SubtitleSize { SMALLER, SMALL, STANDARD, LARGE, LARGER }

/** Text-cue colour for rendered subtitles. Mapped to ARGB in the playback layer. */
enum class SubtitleColour { WHITE, YELLOW, CYAN, GREEN }

/** Fill of the subtitle background: see-through black or fully opaque black. */
enum class SubtitleBackgroundFill { TRANSLUCENT, SOLID }

/**
 * Shape of the subtitle background. WRAPPED fills behind each line of text (Media3's default);
 * BOXED draws one rectangle behind the whole cue block.
 */
enum class SubtitleBackground { OFF, WRAPPED, BOXED }

/**
 * The rectangle text cues are anchored to: the display, the video's display rect, or the picture
 * inside that rect once the encoder's baked-in black bars are measured. AUTOMATIC falls back to
 * IMAGE on any item whose bars cannot be measured.
 */
enum class SubtitleArea { SCREEN, IMAGE, AUTOMATIC }

/**
 * How text-based subtitle cues are drawn (#54). ASS/SSA cues keep their authored
 * styling via libass and are untouched by these preferences.
 *
 * [backgroundFill] applies only while [background] is on. [insetPercent] lifts cues off the bottom
 * edge of [area], as a percentage of that area's height.
 */
data class SubtitleAppearance(
    val size: SubtitleSize = SubtitleSize.STANDARD,
    val colour: SubtitleColour = SubtitleColour.WHITE,
    val background: SubtitleBackground = SubtitleBackground.OFF,
    val backgroundFill: SubtitleBackgroundFill = SubtitleBackgroundFill.TRANSLUCENT,
    val area: SubtitleArea = SubtitleArea.IMAGE,
    val insetPercent: Int = DefaultInsetPercent
) {
    companion object {
        const val DefaultInsetPercent = 8
        const val MinInsetPercent = 0
        const val MaxInsetPercent = 40
    }
}

data class PlaybackSettings(
    val skipForwardSeconds: Int = 30,
    val skipBackwardSeconds: Int = 10,
    val osdHideSeconds: Int = 3,
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
    /** Announce full compatibility so the server always direct-plays (never transcodes).
     *  Advanced/expert: may cause playback errors on genuinely unsupported media. */
    val forceDirectPlay: Boolean = false,
    /** Package name of the custom YouTube app to open trailers with */
    val trailerYouTubePackage: String? = null,
    // ── Per-user, below. Scoped to the signed-in profile; factory defaults when signed out.
    val defaultVideoQuality: QualityRung? = null,
    /**
     * Local audio-language override (ISO 639), or null = no override (resolve to the Jellyfin
     * server preference, else the device language). Device-local (#15); does not write Jellyfin
     * UserConfiguration.
     */
    val preferredAudioLanguage: String? = null,
    /**
     * When true, playback starts on the file's default audio track and
     * [preferredAudioLanguage] is ignored. Series/season track memory and OSD picks still win.
     */
    val preferDefaultAudioTrack: Boolean = false,
    /**
     * Local subtitle-language override (ISO 639), or null = no override (resolve to the Jellyfin
     * server preference, else the device language).
     */
    val preferredSubtitleLanguage: String? = null,
    /** When true, Always show subtitles; when false, Smart (audio vs sub language). */
    val alwaysDisplaySubtitles: Boolean = false,
    /** Style for rendered text subtitle cues (#54). */
    val subtitleAppearance: SubtitleAppearance = SubtitleAppearance()
)

/**
 * User-tunable settings persisted in DataStore.
 *
 * Most settings are app-wide. The fields under "User Preferences" in the Playback tab are
 * **per-user**: they are stored under [UserScope.key] and resolved against the signed-in
 * profile, so two people sharing a TV keep their own languages, subtitle styling and default
 * quality.
 *
 * The active profile is read from the [UserScope.ACTIVE_SESSION] key in this same DataStore
 * rather than from `CredentialStore.activeSessionFlow`. That flow is seeded asynchronously at
 * startup, so combining on it would publish factory defaults first and the real values a
 * moment later — a visible flash of wrong values in Settings. Reading the key out of the same
 * `Preferences` snapshot keeps the resolution atomic with the rest of the read and still
 * re-emits on profile switch, because switching writes that key.
 */
@Singleton
class SettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    val settings: Flow<PlaybackSettings> = dataStore.data.map { p ->
        val scope = UserScope.decode(p[ACTIVE_SESSION])
        PlaybackSettings(
            skipForwardSeconds = p[SKIP_FWD] ?: 30,
            skipBackwardSeconds = p[SKIP_BACK] ?: 10,
            osdHideSeconds = p[OSD_HIDE] ?: 3,
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
            forceDirectPlay = p[FORCE_DIRECT_PLAY] ?: false,
            trailerYouTubePackage = p[TRAILER_YOUTUBE_PACKAGE],
            defaultVideoQuality = QualityRung.named(p.userString(scope, DEFAULT_VIDEO_QUALITY)),
            preferredAudioLanguage = p.userString(scope, PREFERRED_AUDIO_LANGUAGE),
            preferDefaultAudioTrack = p.userBoolean(scope, PREFER_DEFAULT_AUDIO_TRACK) ?: false,
            preferredSubtitleLanguage = p.userString(scope, PREFERRED_SUBTITLE_LANGUAGE),
            alwaysDisplaySubtitles = p.userBoolean(scope, ALWAYS_DISPLAY_SUBTITLES) ?: false,
            subtitleAppearance = SubtitleAppearance(
                size = p.userString(scope, SUBTITLE_SIZE)?.let { enumOrNull<SubtitleSize>(it) }
                    ?: SubtitleSize.STANDARD,
                colour = p.userString(scope, SUBTITLE_COLOUR)?.let { enumOrNull<SubtitleColour>(it) }
                    ?: SubtitleColour.WHITE,
                background = p.userString(scope, SUBTITLE_BACKGROUND)?.let { enumOrNull<SubtitleBackground>(it) }
                    ?: SubtitleBackground.OFF,
                backgroundFill = p.userString(scope, SUBTITLE_BACKGROUND_FILL)
                    ?.let { enumOrNull<SubtitleBackgroundFill>(it) }
                    ?: SubtitleBackgroundFill.TRANSLUCENT,
                area = p.userString(scope, SUBTITLE_AREA)?.let { enumOrNull<SubtitleArea>(it) }
                    ?: SubtitleArea.IMAGE,
                insetPercent = (p.userInt(scope, SUBTITLE_INSET_PERCENT) ?: SubtitleAppearance.DefaultInsetPercent)
                    .coerceIn(SubtitleAppearance.MinInsetPercent, SubtitleAppearance.MaxInsetPercent)
            )
        )
    }

    suspend fun setSkipForwardSeconds(value: Int) = put { it[SKIP_FWD] = value }
    suspend fun setSkipBackwardSeconds(value: Int) = put { it[SKIP_BACK] = value }
    suspend fun setOsdHideSeconds(value: Int) = put { it[OSD_HIDE] = value }
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
    suspend fun setForceDirectPlay(value: Boolean) = put { it[FORCE_DIRECT_PLAY] = value }
    suspend fun setTrailerYouTubePackage(value: String?) = put {
        if (value == null) {
            it.remove(TRAILER_YOUTUBE_PACKAGE)
        } else {
            it[TRAILER_YOUTUBE_PACKAGE] = value
        }
    }

    suspend fun setDefaultVideoQuality(value: QualityRung?) = putUserString(DEFAULT_VIDEO_QUALITY, value?.name)

    suspend fun setPreferredAudioLanguage(value: String?) = putUserString(PREFERRED_AUDIO_LANGUAGE, value)

    suspend fun setPreferDefaultAudioTrack(value: Boolean) = putUserBoolean(PREFER_DEFAULT_AUDIO_TRACK, value)

    suspend fun setPreferredSubtitleLanguage(value: String?) = putUserString(PREFERRED_SUBTITLE_LANGUAGE, value)

    suspend fun setAlwaysDisplaySubtitles(value: Boolean) = putUserBoolean(ALWAYS_DISPLAY_SUBTITLES, value)

    suspend fun setSubtitleSize(value: SubtitleSize) = putUserString(SUBTITLE_SIZE, value.name)
    suspend fun setSubtitleColour(value: SubtitleColour) = putUserString(SUBTITLE_COLOUR, value.name)
    suspend fun setSubtitleBackground(value: SubtitleBackground) = putUserString(SUBTITLE_BACKGROUND, value.name)
    suspend fun setSubtitleBackgroundFill(value: SubtitleBackgroundFill) = putUserString(SUBTITLE_BACKGROUND_FILL, value.name)
    suspend fun setSubtitleArea(value: SubtitleArea) = putUserString(SUBTITLE_AREA, value.name)
    suspend fun setSubtitleInsetPercent(value: Int) = putUserInt(
        SUBTITLE_INSET_PERCENT,
        value.coerceIn(SubtitleAppearance.MinInsetPercent, SubtitleAppearance.MaxInsetPercent)
    )

    private suspend fun put(block: (MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    /** Writes a per-user key, or removes it when [value] is null/blank. No-op when signed
     *  out — the settings UI is unreachable without a session, and an unscoped write would
     *  land in a key nothing reads. */
    private suspend fun putUserString(name: String, value: String?) = putUser { p, scope ->
        val key = stringPreferencesKey(scope.key(name))
        if (value.isNullOrBlank()) p.remove(key) else p[key] = value
    }

    private suspend fun putUserBoolean(name: String, value: Boolean) = putUser { p, scope ->
        p[booleanPreferencesKey(scope.key(name))] = value
    }

    private suspend fun putUserInt(name: String, value: Int) = putUser { p, scope ->
        p[intPreferencesKey(scope.key(name))] = value
    }

    private suspend fun putUser(block: (MutablePreferences, UserScope) -> Unit) {
        dataStore.edit { p ->
            val scope = UserScope.decode(p[ACTIVE_SESSION]) ?: return@edit
            block(p, scope)
        }
    }

    private companion object {
        val ACTIVE_SESSION = stringPreferencesKey(UserScope.ACTIVE_SESSION)

        val SKIP_FWD = intPreferencesKey("playback.skipForwardSeconds")
        val SKIP_BACK = intPreferencesKey("playback.skipBackwardSeconds")
        val OSD_HIDE = intPreferencesKey("playback.osdHideSeconds")
        val INTRO_ACTION = stringPreferencesKey("playback.introAction")
        val RECAP_ACTION = stringPreferencesKey("playback.recapAction")
        val OUTRO_ACTION = stringPreferencesKey("playback.outroAction")
        val PREVIEW_ACTION = stringPreferencesKey("playback.previewAction")
        val COMMERCIAL_ACTION = stringPreferencesKey("playback.commercialAction")
        val NEXT_UP_COUNTDOWN = intPreferencesKey("playback.nextUpCountdownSeconds")
        val DISPLAY_NEXT_UP_DURING_OUTRO = booleanPreferencesKey("playback.displayNextUpDuringOutro")

        val AUTO_LOGIN_LAST_USER = booleanPreferencesKey("experience.autoLoginLastUser")
        val COLOURED_FOCUS = booleanPreferencesKey("experience.colouredFocus")
        val PULSE_FOCUS_GLOW = booleanPreferencesKey("experience.pulseFocusGlow")
        val AMBIENT_BACKGROUNDS = booleanPreferencesKey("experience.ambientBackgrounds")
        val CAP_BADGE_COUNT = booleanPreferencesKey("experience.capBadgeCount")
        val THEME_MUSIC_VOLUME = stringPreferencesKey("experience.themeMusicVolume")

        val PICTURE_IN_PICTURE = booleanPreferencesKey("advanced.pictureInPicture")
        val MATCH_REFRESH_RATE = booleanPreferencesKey("advanced.matchRefreshRate")
        val MATCH_RESOLUTION = booleanPreferencesKey("advanced.matchResolution")
        val DOWNMIX_STEREO = booleanPreferencesKey("advanced.downmixStereo")
        val FORCE_DOVI_PROFILE_7 = booleanPreferencesKey("advanced.forceDoviProfile7")
        val FORCE_DIRECT_PLAY = booleanPreferencesKey("advanced.forceDirectPlay")
        val TRAILER_YOUTUBE_PACKAGE = stringPreferencesKey("advanced.trailerYouTubePackage")

        // Per-user: names only — the typed key is built per profile via UserScope.key.
        const val DEFAULT_VIDEO_QUALITY = "playback.defaultVideoQuality"
        const val PREFERRED_AUDIO_LANGUAGE = "playback.preferredAudioLanguage"
        const val PREFER_DEFAULT_AUDIO_TRACK = "playback.preferDefaultAudioTrack"
        const val PREFERRED_SUBTITLE_LANGUAGE = "playback.preferredSubtitleLanguage"
        const val ALWAYS_DISPLAY_SUBTITLES = "playback.alwaysDisplaySubtitles"
        const val SUBTITLE_SIZE = "playback.subtitleSize"
        const val SUBTITLE_COLOUR = "playback.subtitleColour"
        const val SUBTITLE_BACKGROUND = "playback.subtitleBackground"
        const val SUBTITLE_BACKGROUND_FILL = "playback.subtitleBackgroundFill"
        const val SUBTITLE_AREA = "playback.subtitleArea"
        const val SUBTITLE_INSET_PERCENT = "playback.subtitleInsetPercent"

        fun Preferences.userString(scope: UserScope?, name: String): String? = scope?.let { this[stringPreferencesKey(it.key(name))] }

        fun Preferences.userBoolean(scope: UserScope?, name: String): Boolean? = scope?.let { this[booleanPreferencesKey(it.key(name))] }

        fun Preferences.userInt(scope: UserScope?, name: String): Int? = scope?.let { this[intPreferencesKey(it.key(name))] }

        inline fun <reified T : Enum<T>> enumOrNull(name: String): T? = runCatching { enumValueOf<T>(name) }.getOrNull()
    }
}
