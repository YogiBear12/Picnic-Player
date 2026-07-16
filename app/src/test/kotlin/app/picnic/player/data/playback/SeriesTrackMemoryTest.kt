package app.picnic.player.data.playback

import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures with multi-Default English dubs + varied EN/JP subs.
 */
class SeriesTrackMemoryTest {

    private val remasteredTitle = "2009 5.1 FUNi Remastered Dub [ergiman]"
    private val originalTitle = "2001 2.0 Original FUNi Dub [Krycek7o2]"
    private val bltTitle = "1995 2.0 BLT Dub [iKaos]"
    private val jpAudioTitle = "1986 Mono Broadcast Audio [TeamMirolo/Kei17]"

    private fun dbccStreams(): List<MediaStream> = listOf(
        audio(1, "jpn", isDefault = true, title = jpAudioTitle),
        audio(2, "eng", isDefault = true, title = originalTitle),
        audio(3, "eng", isDefault = true, title = remasteredTitle),
        audio(4, "eng", isDefault = true, title = bltTitle),
        subtitle(5, "eng", isDefault = true, isForced = false, title = "Stylized Subtitles [hydes]"),
        subtitle(6, "eng", isDefault = false, isForced = true, title = "Signs/Songs/NEPs [Mario2109/LukZ/Ploffy]"),
        subtitle(7, "eng", isDefault = false, isForced = false, title = "Basic Subtitles [hydes/Mario2109]"),
        subtitle(8, "eng", isDefault = false, isForced = false, title = "FUNi Dubtitles [SkyArmyRecru1t]"),
        subtitle(9, "jpn", isDefault = false, isForced = false, title = "JP Subtitles [butler]")
    )

    @Test
    fun hierarchy_seasonOverrideBeatsSeries() {
        val record = SeriesTrackMemoryRecord(
            audio = RememberedTrack(language = "eng", title = remasteredTitle),
            seasons = mapOf(
                "season-2" to SeasonTrackMemory(
                    audio = RememberedTrack(language = "eng", title = bltTitle)
                )
            )
        )
        val s1 = resolveEffectiveMemory(record, "season-1")
        val s2 = resolveEffectiveMemory(record, "season-2")
        assertEquals(remasteredTitle, s1.audio?.title)
        assertEquals(bltTitle, s2.audio?.title)
    }

    @Test
    fun seriesRemember_remastered_exactTitle() {
        val memory = EffectiveTrackMemory(
            audio = RememberedTrack(language = "eng", title = remasteredTitle)
        )
        val pick = pickTracksWithMemory(
            streams = dbccStreams(),
            memory = memory,
            preferredAudioLanguage = "ja",
            preferredSubtitleLanguage = "en",
            deviceSubtitleLanguage = "en",
            alwaysDisplaySubtitles = false
        )
        // Memory wins over global JP preference (S1).
        assertEquals(3, pick.audioIndex)
    }

    @Test
    fun seasonOverride_blt_doesNotWipeSeries() {
        val series = RememberedTrack(language = "eng", title = remasteredTitle)
        val pick = RememberedTrack(language = "eng", title = bltTitle)
        assertEquals(
            TrackMemoryWriteScope.SEASON,
            decideTrackMemoryWriteScope(series, pick, seasonId = "season-2")
        )
        val updated = applyTrackMemoryWrite(
            existing = SeriesTrackMemoryRecord(audio = series),
            seasonId = "season-2",
            kind = TrackMemoryKind.AUDIO,
            pick = pick,
            scope = TrackMemoryWriteScope.SEASON
        )
        assertEquals(remasteredTitle, updated.audio?.title)
        assertEquals(bltTitle, updated.seasons["season-2"]?.audio?.title)

        // Other seasons still resolve to series Remastered.
        val s1 = resolveEffectiveMemory(updated, "season-1")
        assertEquals(remasteredTitle, s1.audio?.title)
        val s2Pick = pickTracksWithMemory(
            streams = dbccStreams(),
            memory = resolveEffectiveMemory(updated, "season-2"),
            preferredAudioLanguage = null,
            preferredSubtitleLanguage = "en",
            deviceSubtitleLanguage = "en",
            alwaysDisplaySubtitles = true
        )
        assertEquals(4, s2Pick.audioIndex)
    }

    @Test
    fun fuzzy_highConfidence_stripsBracketTag() {
        val remembered = RememberedTrack(
            language = "eng",
            title = "2009 5.1 FUNi Remastered Dub"
        )
        val index = matchRememberedTrack(dbccStreams(), MediaStreamType.AUDIO, remembered)
        assertEquals(3, index)
    }

    @Test
    fun fuzzy_doesNotGuessAmongSimilarDubs() {
        // Language-only EN memory with a vague title must not pick among Original/Remastered/BLT.
        assertNull(
            matchRememberedTrack(
                dbccStreams(),
                MediaStreamType.AUDIO,
                RememberedTrack(language = "eng", title = "FUNi Dub")
            )
        )
        assertNull(
            matchRememberedTrack(
                dbccStreams(),
                MediaStreamType.AUDIO,
                RememberedTrack(language = "eng", title = "English")
            )
        )
    }

    @Test
    fun noMatch_fallsThroughToGlobalPrefs() {
        val memory = EffectiveTrackMemory(
            audio = RememberedTrack(language = "eng", title = "Completely Unknown Dub Title")
        )
        val pick = pickTracksWithMemory(
            streams = dbccStreams(),
            memory = memory,
            preferredAudioLanguage = "ja",
            preferredSubtitleLanguage = "en",
            deviceSubtitleLanguage = "en",
            alwaysDisplaySubtitles = false
        )
        // Global prefers JP audio.
        assertEquals(1, pick.audioIndex)
    }

    @Test
    fun subtitleOff_rememberedAndApplied() {
        val memory = EffectiveTrackMemory(
            audio = RememberedTrack(language = "eng", title = remasteredTitle),
            subtitle = RememberedTrack.off()
        )
        val pick = pickTracksWithMemory(
            streams = dbccStreams(),
            memory = memory,
            preferredAudioLanguage = "ja",
            preferredSubtitleLanguage = "en",
            deviceSubtitleLanguage = "en",
            alwaysDisplaySubtitles = true
        )
        assertEquals(3, pick.audioIndex)
        assertNull(pick.subtitleIndex)
    }

    @Test
    fun subtitle_exactTitle_stylized() {
        val index = matchRememberedTrack(
            dbccStreams(),
            MediaStreamType.SUBTITLE,
            RememberedTrack(language = "eng", title = "Stylized Subtitles [hydes]")
        )
        assertEquals(5, index)
    }

    @Test
    fun write_seedsSeriesWhenEmpty() {
        val pick = RememberedTrack(language = "eng", title = remasteredTitle)
        assertEquals(
            TrackMemoryWriteScope.SERIES,
            decideTrackMemoryWriteScope(seriesPreference = null, pick = pick, seasonId = "s1")
        )
        val updated = applyTrackMemoryWrite(
            existing = null,
            seasonId = "s1",
            kind = TrackMemoryKind.AUDIO,
            pick = pick,
            scope = TrackMemoryWriteScope.SERIES
        )
        assertEquals(remasteredTitle, updated.audio?.title)
        assertTrue(updated.seasons.isEmpty())
    }

    @Test
    fun write_matchingSeriesPick_updatesSeriesNotSeason() {
        val series = RememberedTrack(language = "eng", title = remasteredTitle)
        val pick = RememberedTrack(language = "eng", title = remasteredTitle)
        assertEquals(
            TrackMemoryWriteScope.SERIES,
            decideTrackMemoryWriteScope(series, pick, seasonId = "season-2")
        )
    }

    @Test
    fun write_seriesClearsSeasonOverrideForKind() {
        val existing = SeriesTrackMemoryRecord(
            audio = RememberedTrack(language = "eng", title = remasteredTitle),
            seasons = mapOf(
                "season-2" to SeasonTrackMemory(
                    audio = RememberedTrack(language = "eng", title = bltTitle)
                )
            )
        )
        val updated = applyTrackMemoryWrite(
            existing = existing,
            seasonId = "season-2",
            kind = TrackMemoryKind.AUDIO,
            pick = RememberedTrack(language = "eng", title = remasteredTitle),
            scope = TrackMemoryWriteScope.SERIES
        )
        assertNull(updated.seasons["season-2"])
        assertEquals(remasteredTitle, updated.audio?.title)
    }

    @Test
    fun write_offVsTrack_isSeasonOverride() {
        val series = RememberedTrack(language = "eng", title = "Stylized Subtitles [hydes]")
        val pick = RememberedTrack.off()
        assertEquals(
            TrackMemoryWriteScope.SEASON,
            decideTrackMemoryWriteScope(series, pick, seasonId = "season-1")
        )
    }

    @Test
    fun nullMemory_usesGlobalOnly() {
        val pick = pickTracksWithMemory(
            streams = dbccStreams(),
            memory = null,
            preferredAudioLanguage = "en",
            preferredSubtitleLanguage = "en",
            deviceSubtitleLanguage = "en",
            alwaysDisplaySubtitles = false
        )
        // Stage-1: first Default EN audio among matches (index 2 Original).
        assertEquals(2, pick.audioIndex)
        // EN audio + preferred EN sub + Smart → Forced.
        assertEquals(6, pick.subtitleIndex)
    }

    @Test
    fun moviesUnchanged_nullSeriesMeansGlobal() {
        // No series memory applied → identical to pickTracks.
        val streams = dbccStreams()
        val withMemory = pickTracksWithMemory(
            streams = streams,
            memory = EffectiveTrackMemory(),
            preferredAudioLanguage = "ja",
            preferredSubtitleLanguage = "en",
            deviceSubtitleLanguage = "en",
            alwaysDisplaySubtitles = false
        )
        val global = pickTracks(
            streams = streams,
            preferredAudioLanguage = "ja",
            preferredSubtitleLanguage = "en",
            deviceSubtitleLanguage = "en",
            alwaysDisplaySubtitles = false
        )
        assertEquals(global, withMemory)
    }

    @Test
    fun rememberedTrackOf_usesTitle() {
        val stream = audio(3, "eng", true, remasteredTitle)
        val remembered = RememberedTrack.of(stream)
        assertNotNull(remembered)
        assertEquals("eng", remembered!!.language)
        assertEquals(remasteredTitle, remembered.title)
        assertEquals(false, remembered.off)
    }

    @Test
    fun rememberedTrackOf_blankTitle_returnsNull() {
        assertNull(RememberedTrack.of(audio(1, "eng", true, title = null, displayTitle = null)))
        assertNull(RememberedTrack.of(audio(1, "eng", true, title = "  ", displayTitle = " ")))
    }

    @Test
    fun subtitleMiss_rerunsSmartAgainstRememberedAudio() {
        // Remastered EN audio remembered; subtitle title missing on this file.
        // Global prefs prefer JP audio → Smart under global would pick full EN Default (Stylized).
        // After audio memory, EN audio + Smart EN prefs → Forced Signs.
        val memory = EffectiveTrackMemory(
            audio = RememberedTrack(language = "eng", title = remasteredTitle),
            subtitle = RememberedTrack(language = "eng", title = "Missing Subtitle Title XYZ")
        )
        val pick = pickTracksWithMemory(
            streams = dbccStreams(),
            memory = memory,
            preferredAudioLanguage = "ja",
            preferredSubtitleLanguage = "en",
            deviceSubtitleLanguage = "en",
            alwaysDisplaySubtitles = false
        )
        assertEquals(3, pick.audioIndex)
        // Must not reuse global.subtitleIndex (3 / Stylized under JP audio).
        assertEquals(6, pick.subtitleIndex)
    }

    @Test
    fun exact_caseInsensitiveTrim() {
        val index = matchRememberedTrack(
            dbccStreams(),
            MediaStreamType.AUDIO,
            RememberedTrack(language = "en", title = "  2009 5.1 FUNi Remastered Dub [ergiman]  ")
        )
        assertEquals(3, index)
    }

    @Test
    fun audioMemory_rerunsSmartSubtitlesWhenSubNotRemembered() {
        // Remastered EN audio + no sub memory + Smart + preferred EN → Forced (audio matches EN).
        val memory = EffectiveTrackMemory(
            audio = RememberedTrack(language = "eng", title = remasteredTitle)
        )
        val pick = pickTracksWithMemory(
            streams = dbccStreams(),
            memory = memory,
            preferredAudioLanguage = "ja",
            preferredSubtitleLanguage = "en",
            deviceSubtitleLanguage = "en",
            alwaysDisplaySubtitles = false
        )
        assertEquals(3, pick.audioIndex)
        assertEquals(6, pick.subtitleIndex)
        assertNotEquals(1, pick.audioIndex)
        assertNotNull(pick.subtitleIndex)
    }
}

private fun audio(
    index: Int,
    language: String,
    isDefault: Boolean,
    title: String? = null,
    displayTitle: String? = null
): MediaStream = MediaStream(
    type = MediaStreamType.AUDIO,
    index = index,
    language = language,
    title = title,
    displayTitle = displayTitle,
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
    title: String? = null
): MediaStream = MediaStream(
    type = MediaStreamType.SUBTITLE,
    index = index,
    language = language,
    title = title,
    isInterlaced = false,
    isDefault = isDefault,
    isForced = isForced,
    isHearingImpaired = false,
    isExternal = false,
    isTextSubtitleStream = true,
    supportsExternalStream = false
)
