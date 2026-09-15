package app.picnic.player.ui.player.osd

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackLabelsTest {

    private fun label(
        title: String?,
        languageName: String = "English",
        languageTag: String? = "eng",
        forced: Boolean = false,
        hearingImpaired: Boolean = false,
        external: Boolean = false,
        codec: String? = "ass"
    ) = subtitleTrackLabel(title, languageName, languageTag, forced, hearingImpaired, external, codec)

    @Test
    fun keepsTheAuthoredTitleAndNamesTheCodec() {
        val result = label("Signs/Songs [MTBB]")
        assertEquals("Signs/Songs [MTBB]", result.secondary)
        assertEquals(listOf("ASS"), result.chips)
    }

    @Test
    fun dropsALanguageTheRowAlreadyShows() {
        assertEquals("Full Subs (Official Subs)", label("Full Subs (Official Subs) - English", codec = "pgssub").secondary)
    }

    @Test
    fun dropsFlagWordsBakedIntoTheTitle() {
        assertEquals("Signs", label("English (Signs) - Default", forced = false).secondary)
    }

    @Test
    fun collapsesToNothingWhenOnlyTheLanguageWasThere() {
        val result = label("German - Default", languageName = "German", languageTag = "ger")
        assertEquals(null, result.secondary)
        assertEquals(listOf("ASS"), result.chips)
    }

    @Test
    fun forcedOutranksHearingImpaired() {
        assertEquals(listOf("Forced", "ASS"), label("Signs", forced = true, hearingImpaired = true).chips)
    }

    @Test
    fun namesHearingImpairedAsSdh() {
        assertEquals(listOf("SDH", "SRT"), label("CC", hearingImpaired = true, codec = "subrip").chips)
    }

    @Test
    fun marksAnExternalTrack() {
        assertEquals(listOf("EXT", "SRT"), label("Fansub", external = true, codec = "srt").chips)
    }

    @Test
    fun normalizesTheCodecNamesTheServerSends() {
        assertEquals("PGS", codecChip("PGSSUB"))
        assertEquals("SRT", codecChip("subrip"))
        assertEquals("ASS", codecChip("ssa"))
        assertEquals("VOBSUB", codecChip("dvdsub"))
        assertEquals(null, codecChip(" "))
    }

    @Test
    fun passesAnUnknownCodecThroughUppercased() {
        assertEquals("TTML", codecChip("ttml"))
    }
}
