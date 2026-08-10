package app.picnic.player.data.playback

import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackTrackPickerTest {
    private fun dualAudioStreams(): List<MediaStream> = listOf(
        audio(index = 1, language = "jpn", isDefault = false),
        audio(index = 2, language = "eng", isDefault = true),
        subtitle(index = 3, language = "eng", isDefault = true, isForced = false),
        subtitle(index = 4, language = "eng", isDefault = false, isForced = true),
        subtitle(index = 5, language = "eng", isDefault = false, isForced = false)
    )

    @Test
    fun smart_jpAudio_unspecifiedSub_deviceEn_picksEnDefault() {
        val pick = pickTracks(
            streams = dualAudioStreams(),
            preferredAudioLanguage = "ja",
            preferredSubtitleLanguage = null,
            deviceSubtitleLanguage = "en",
            alwaysDisplaySubtitles = false
        )
        assertEquals(1, pick.audioIndex)
        assertEquals(3, pick.subtitleIndex)
    }

    @Test
    fun smart_jpAudio_enSub_picksEnDefault() {
        val pick = pickTracks(
            streams = dualAudioStreams(),
            preferredAudioLanguage = "ja",
            preferredSubtitleLanguage = "en",
            deviceSubtitleLanguage = "de",
            alwaysDisplaySubtitles = false
        )
        assertEquals(1, pick.audioIndex)
        assertEquals(3, pick.subtitleIndex)
    }

    @Test
    fun smart_enAudio_unspecifiedSub_deviceEn_picksEnForced() {
        val pick = pickTracks(
            streams = dualAudioStreams(),
            preferredAudioLanguage = "en",
            preferredSubtitleLanguage = null,
            deviceSubtitleLanguage = "en",
            alwaysDisplaySubtitles = false
        )
        assertEquals(2, pick.audioIndex)
        assertEquals(4, pick.subtitleIndex)
    }

    @Test
    fun smart_enAudio_enSub_picksEnForced() {
        val pick = pickTracks(
            streams = dualAudioStreams(),
            preferredAudioLanguage = "en",
            preferredSubtitleLanguage = "en",
            deviceSubtitleLanguage = "de",
            alwaysDisplaySubtitles = false
        )
        assertEquals(2, pick.audioIndex)
        assertEquals(4, pick.subtitleIndex)
    }

    @Test
    fun smart_jpAudio_unspecifiedSub_deviceDe_picksNone() {
        val pick = pickTracks(
            streams = dualAudioStreams(),
            preferredAudioLanguage = "ja",
            preferredSubtitleLanguage = null,
            deviceSubtitleLanguage = "de",
            alwaysDisplaySubtitles = false
        )
        assertEquals(1, pick.audioIndex)
        assertNull(pick.subtitleIndex)
    }

    @Test
    fun smart_match_noForcedInPreferred_picksNoneNotEnglishForced() {
        val streams = listOf(
            audio(index = 1, language = "jpn", isDefault = true),
            audio(index = 2, language = "eng", isDefault = false),
            subtitle(index = 3, language = "jpn", isDefault = true, isForced = false),
            subtitle(index = 4, language = "eng", isDefault = false, isForced = true)
        )
        val pick = pickTracks(
            streams = streams,
            preferredAudioLanguage = "ja",
            preferredSubtitleLanguage = "ja",
            deviceSubtitleLanguage = "en",
            alwaysDisplaySubtitles = false
        )
        assertEquals(1, pick.audioIndex)
        assertNull(pick.subtitleIndex)
    }

    @Test
    fun alwaysOn_jpAudio_enSub_picksEnDefault() {
        val pick = pickTracks(
            streams = dualAudioStreams(),
            preferredAudioLanguage = "ja",
            preferredSubtitleLanguage = "en",
            deviceSubtitleLanguage = "de",
            alwaysDisplaySubtitles = true
        )
        assertEquals(1, pick.audioIndex)
        assertEquals(3, pick.subtitleIndex)
    }

    @Test
    fun alwaysOn_enAudio_unspecifiedSub_deviceEn_picksEnDefaultNotForced() {
        val pick = pickTracks(
            streams = dualAudioStreams(),
            preferredAudioLanguage = "en",
            preferredSubtitleLanguage = null,
            deviceSubtitleLanguage = "en",
            alwaysDisplaySubtitles = true
        )
        assertEquals(2, pick.audioIndex)
        assertEquals(3, pick.subtitleIndex)
    }

    @Test
    fun alwaysOn_forcedListedFirst_noDefaultFlags_picksFullTrack() {
        val streams = listOf(
            audio(index = 1, language = "eng", isDefault = true),
            subtitle(index = 2, language = "eng", isDefault = false, isForced = true),
            subtitle(index = 3, language = "eng", isDefault = false, isForced = false)
        )
        val pick = pickTracks(
            streams = streams,
            preferredAudioLanguage = "en",
            preferredSubtitleLanguage = "en",
            deviceSubtitleLanguage = "en",
            alwaysDisplaySubtitles = true
        )
        assertEquals(3, pick.subtitleIndex)
    }

    @Test
    fun alwaysOn_forcedFlaggedDefault_picksFullTrack() {
        val streams = listOf(
            audio(index = 1, language = "eng", isDefault = true),
            subtitle(index = 2, language = "eng", isDefault = true, isForced = true),
            subtitle(index = 3, language = "eng", isDefault = false, isForced = false)
        )
        val pick = pickTracks(
            streams = streams,
            preferredAudioLanguage = "en",
            preferredSubtitleLanguage = "en",
            deviceSubtitleLanguage = "en",
            alwaysDisplaySubtitles = true
        )
        assertEquals(3, pick.subtitleIndex)
    }

    @Test
    fun alwaysOn_onlyForcedInLanguage_picksForced() {
        val streams = listOf(
            audio(index = 1, language = "eng", isDefault = true),
            subtitle(index = 2, language = "eng", isDefault = false, isForced = true)
        )
        val pick = pickTracks(
            streams = streams,
            preferredAudioLanguage = "en",
            preferredSubtitleLanguage = "en",
            deviceSubtitleLanguage = "en",
            alwaysDisplaySubtitles = true
        )
        assertEquals(2, pick.subtitleIndex)
    }

    @Test
    fun alwaysOn_onlyUndefinedLanguageForced_picksForced() {
        val streams = listOf(
            audio(index = 1, language = "eng", isDefault = true),
            subtitle(index = 2, language = "und", isDefault = false, isForced = true)
        )
        val pick = pickTracks(
            streams = streams,
            preferredAudioLanguage = "en",
            preferredSubtitleLanguage = "en",
            deviceSubtitleLanguage = "en",
            alwaysDisplaySubtitles = true
        )
        assertEquals(2, pick.subtitleIndex)
    }

    @Test
    fun alwaysOn_undefinedLanguageForcedLosesToForcedInPreferredLanguage() {
        val streams = listOf(
            audio(index = 1, language = "eng", isDefault = true),
            subtitle(index = 2, language = "und", isDefault = false, isForced = true),
            subtitle(index = 3, language = "eng", isDefault = false, isForced = true)
        )
        val pick = pickTracks(
            streams = streams,
            preferredAudioLanguage = "en",
            preferredSubtitleLanguage = "en",
            deviceSubtitleLanguage = "en",
            alwaysDisplaySubtitles = true
        )
        assertEquals(3, pick.subtitleIndex)
    }

    @Test
    fun alwaysOn_singleFullTrackInLanguage_picksIt() {
        val streams = listOf(
            audio(index = 1, language = "eng", isDefault = true),
            subtitle(index = 2, language = "eng", isDefault = false, isForced = false)
        )
        val pick = pickTracks(
            streams = streams,
            preferredAudioLanguage = "en",
            preferredSubtitleLanguage = "en",
            deviceSubtitleLanguage = "en",
            alwaysDisplaySubtitles = true
        )
        assertEquals(2, pick.subtitleIndex)
    }

    @Test
    fun alwaysOn_noTrackInLanguageAndNoForced_picksNone() {
        val streams = listOf(
            audio(index = 1, language = "eng", isDefault = true),
            subtitle(index = 2, language = "deu", isDefault = true, isForced = false)
        )
        val pick = pickTracks(
            streams = streams,
            preferredAudioLanguage = "en",
            preferredSubtitleLanguage = "en",
            deviceSubtitleLanguage = "en",
            alwaysDisplaySubtitles = true
        )
        assertNull(pick.subtitleIndex)
    }

    @Test
    fun smart_matchingAudio_undefinedLanguageForced_picksForced() {
        val streams = listOf(
            audio(index = 1, language = "eng", isDefault = true),
            subtitle(index = 2, language = "und", isDefault = false, isForced = true)
        )
        val pick = pickTracks(
            streams = streams,
            preferredAudioLanguage = "en",
            preferredSubtitleLanguage = "en",
            deviceSubtitleLanguage = "en",
            alwaysDisplaySubtitles = false
        )
        assertEquals(2, pick.subtitleIndex)
    }

    @Test
    fun alwaysOn_externalFullTrack_beatsEmbeddedDefault() {
        val streams = listOf(
            audio(index = 1, language = "eng", isDefault = true),
            subtitle(index = 2, language = "eng", isDefault = true, isForced = false),
            subtitle(index = 3, language = "eng", isDefault = false, isForced = false, isExternal = true)
        )
        val pick = pickTracks(
            streams = streams,
            preferredAudioLanguage = "en",
            preferredSubtitleLanguage = "en",
            deviceSubtitleLanguage = "en",
            alwaysDisplaySubtitles = true
        )
        assertEquals(3, pick.subtitleIndex)
    }

    @Test
    fun alwaysOn_externalForcedNeverBeatsEmbeddedFullTrack() {
        val streams = listOf(
            audio(index = 1, language = "eng", isDefault = true),
            subtitle(index = 2, language = "eng", isDefault = false, isForced = true, isExternal = true),
            subtitle(index = 3, language = "eng", isDefault = false, isForced = false)
        )
        val pick = pickTracks(
            streams = streams,
            preferredAudioLanguage = "en",
            preferredSubtitleLanguage = "en",
            deviceSubtitleLanguage = "en",
            alwaysDisplaySubtitles = true
        )
        assertEquals(3, pick.subtitleIndex)
    }

    @Test
    fun smart_foreignAudio_externalFullTrack_beatsEmbeddedDefault() {
        val streams = listOf(
            audio(index = 1, language = "jpn", isDefault = true),
            subtitle(index = 2, language = "eng", isDefault = true, isForced = false),
            subtitle(index = 3, language = "eng", isDefault = false, isForced = false, isExternal = true)
        )
        val pick = pickTracks(
            streams = streams,
            preferredAudioLanguage = "ja",
            preferredSubtitleLanguage = "en",
            deviceSubtitleLanguage = "en",
            alwaysDisplaySubtitles = false
        )
        assertEquals(3, pick.subtitleIndex)
    }

    @Test
    fun smart_matchingAudio_externalForced_beatsEmbeddedForced() {
        val streams = listOf(
            audio(index = 1, language = "eng", isDefault = true),
            subtitle(index = 2, language = "eng", isDefault = true, isForced = true),
            subtitle(index = 3, language = "eng", isDefault = false, isForced = true, isExternal = true)
        )
        val pick = pickTracks(
            streams = streams,
            preferredAudioLanguage = "en",
            preferredSubtitleLanguage = "en",
            deviceSubtitleLanguage = "en",
            alwaysDisplaySubtitles = false
        )
        assertEquals(3, pick.subtitleIndex)
    }

    @Test
    fun unspecifiedAudio_picksDefaultAudio() {
        val pick = pickTracks(
            streams = dualAudioStreams(),
            preferredAudioLanguage = null,
            preferredSubtitleLanguage = "en",
            deviceSubtitleLanguage = "de",
            alwaysDisplaySubtitles = false
        )
        assertEquals(2, pick.audioIndex)
        assertEquals(4, pick.subtitleIndex)
    }

    @Test
    fun audioMiss_fallsBackToDefaultAudio() {
        val streams = listOf(
            audio(index = 1, language = "jpn", isDefault = true),
            audio(index = 2, language = "eng", isDefault = false)
        )
        assertEquals(
            1,
            pickAudioIndex(streams, preferredAudioLanguage = "fr")
        )
    }

    @Test
    fun subtitleMiss_picksNone_noEnglishFallback() {
        val streams = listOf(
            audio(index = 1, language = "jpn", isDefault = true),
            subtitle(index = 2, language = "eng", isDefault = true, isForced = false),
            subtitle(index = 3, language = "deu", isDefault = false, isForced = false)
        )
        assertNull(
            pickSubtitleIndex(
                streams = streams,
                preferredSubtitleLanguage = "fr",
                deviceSubtitleLanguage = "en",
                alwaysDisplaySubtitles = true,
                selectedAudioLanguage = "jpn"
            )
        )
    }

    @Test
    fun commentary_nonDefault_ignoredForAutoPick() {
        val streams = listOf(
            audio(index = 1, language = "eng", isDefault = false, title = "English Commentary"),
            audio(index = 2, language = "eng", isDefault = true, title = "English")
        )
        assertEquals(2, pickAudioIndex(streams, preferredAudioLanguage = "en"))
    }

    @Test
    fun commentary_default_stillEligible() {
        val streams = listOf(
            audio(index = 1, language = "eng", isDefault = true, title = "Director Commentary"),
            audio(index = 2, language = "eng", isDefault = false, title = "English")
        )
        assertEquals(1, pickAudioIndex(streams, preferredAudioLanguage = null))
    }

    @Test
    fun preferDefault_ignoresAudioLanguagePreference() {
        val streams = listOf(
            audio(index = 1, language = "jpn", isDefault = true),
            audio(index = 2, language = "eng", isDefault = false)
        )
        assertEquals(2, pickAudioIndex(streams, preferredAudioLanguage = "en"))
        assertEquals(
            1,
            pickAudioIndex(streams, preferredAudioLanguage = "en", preferDefaultAudioTrack = true)
        )
    }

    @Test
    fun preferDefault_noStreamFlaggedDefault_picksFirstAudio() {
        val streams = listOf(
            audio(index = 1, language = "jpn", isDefault = false),
            audio(index = 2, language = "eng", isDefault = false)
        )
        assertEquals(
            1,
            pickAudioIndex(streams, preferredAudioLanguage = "en", preferDefaultAudioTrack = true)
        )
    }

    @Test
    fun preferDefault_smartSubtitlesFollowTheDefaultAudio() {
        val streams = listOf(
            audio(index = 1, language = "jpn", isDefault = true),
            audio(index = 2, language = "eng", isDefault = false),
            subtitle(index = 3, language = "eng", isDefault = true, isForced = false),
            subtitle(index = 4, language = "eng", isDefault = false, isForced = true)
        )
        val pick = pickTracks(
            streams = streams,
            preferredAudioLanguage = "en",
            preferredSubtitleLanguage = "en",
            deviceSubtitleLanguage = "en",
            alwaysDisplaySubtitles = false,
            preferDefaultAudioTrack = true
        )
        assertEquals(1, pick.audioIndex)
        assertEquals(3, pick.subtitleIndex)
    }

    @Test
    fun languageMatches_iso2AndIso3() {
        assertEquals(true, languageMatches("jpn", "ja"))
        assertEquals(true, languageMatches("eng", "en"))
        assertEquals(true, languageMatches("en-US", "en"))
        assertEquals(false, languageMatches("jpn", "en"))
    }
}

private fun audio(
    index: Int,
    language: String,
    isDefault: Boolean,
    title: String? = null
): MediaStream = MediaStream(
    type = MediaStreamType.AUDIO,
    index = index,
    language = language,
    title = title,
    isInterlaced = false,
    isDefault = isDefault,
    isForced = false,
    isHearingImpaired = false,
    isExternal = false,
    isTextSubtitleStream = false,
    supportsExternalStream = false
)

private fun subtitle(
    index: Int,
    language: String,
    isDefault: Boolean,
    isForced: Boolean,
    isExternal: Boolean = false
): MediaStream = MediaStream(
    type = MediaStreamType.SUBTITLE,
    index = index,
    language = language,
    isInterlaced = false,
    isDefault = isDefault,
    isForced = isForced,
    isHearingImpaired = false,
    isExternal = isExternal,
    isTextSubtitleStream = true,
    supportsExternalStream = false
)
