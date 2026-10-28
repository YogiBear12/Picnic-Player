package app.picnic.player.data.playback

import org.jellyfin.sdk.model.api.CultureDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CulturePickerTest {

    @Test
    fun culturePickerOptions_sortedByDisplayName() {
        val options = culturePickerOptions(
            listOf(
                culture("zulu", "Zulu", "zu"),
                culture("english", "English", "en"),
                culture("japanese", "Japanese", "ja")
            )
        )
        assertEquals(listOf("English", "Japanese", "Zulu"), options.map { it.displayName })
        assertEquals(listOf("en", "ja", "zu"), options.map { it.languageCode })
    }

    @Test
    fun resolveLanguageCode_appOverrideThenDevice() {
        assertEquals("fr", resolveLanguageCode("fr", deviceLanguage = "de"))
        assertEquals("de", resolveLanguageCode("", deviceLanguage = "de"))
        assertEquals("de", resolveLanguageCode(null, deviceLanguage = "de"))
    }

    @Test
    fun pinnedLanguageOptions_liftsDeviceLanguageAndDedupes() {
        val base = culturePickerOptions(
            listOf(
                culture("english", "English", "en"),
                culture("french", "French", "fr"),
                culture("german", "German", "de")
            )
        )
        val pinned = pinnedLanguageOptions(base, deviceLanguage = "de")
        assertEquals(listOf("German", "English", "French"), pinned.options.map { it.displayName })
        assertEquals(0, pinned.separatorAfterIndex)
        // Device language appears once (pinned), not again in the A–Z body.
        assertEquals(1, pinned.options.count { it.languageCode == "de" })
    }

    @Test
    fun pinnedLanguageOptions_deviceLanguageAbsentPinsNothing() {
        val base = culturePickerOptions(listOf(culture("english", "English", "en")))
        val pinned = pinnedLanguageOptions(base, deviceLanguage = "de")
        assertEquals(listOf("English"), pinned.options.map { it.displayName })
        assertEquals(-1, pinned.separatorAfterIndex)
    }

    @Test
    fun languagePickerRows_audioPinsDefaultAboveDeviceLanguage() {
        val base = culturePickerOptions(
            listOf(
                culture("english", "English", "en"),
                culture("german", "German", "de")
            )
        )
        val picker = languagePickerRows(base, deviceLanguage = "de", includeOriginalLanguage = true)
        assertEquals(listOf("Original language", "German", "English"), picker.rows.map { it.label })
        // Divider sits under the Default + device-language block.
        assertEquals(1, picker.separatorAfterIndex)
    }

    @Test
    fun languagePickerRows_subtitlesHaveNoDefaultRow() {
        val base = culturePickerOptions(listOf(culture("english", "English", "en")))
        val picker = languagePickerRows(base, deviceLanguage = "de", includeOriginalLanguage = false)
        assertEquals(listOf("English"), picker.rows.map { it.label })
        assertEquals(-1, picker.separatorAfterIndex)
    }

    @Test
    fun languagePickerRows_defaultRowAloneWhenDeviceLanguageAbsent() {
        val base = culturePickerOptions(listOf(culture("english", "English", "en")))
        val picker = languagePickerRows(base, deviceLanguage = "de", includeOriginalLanguage = true)
        assertEquals(listOf("Original language", "English"), picker.rows.map { it.label })
        assertEquals(0, picker.separatorAfterIndex)
    }

    @Test
    fun selectedLanguageRow_defaultBeatsLanguageCode() {
        val base = culturePickerOptions(listOf(culture("english", "English", "en")))
        val rows = languagePickerRows(base, deviceLanguage = "en", includeOriginalLanguage = true).rows
        assertEquals(
            LanguagePickerRow.OriginalLanguage,
            selectedLanguageRow(rows, languageCode = "en", preferOriginalLanguage = true)
        )
        assertEquals(
            "English",
            selectedLanguageRow(rows, languageCode = "eng", preferOriginalLanguage = false)?.label
        )
        assertNull(selectedLanguageRow(rows, languageCode = null, preferOriginalLanguage = false))
    }

    @Test
    fun cultureDisplayName_resolvesFromOptions() {
        val options = culturePickerOptions(listOf(culture("japanese", "Japanese", "ja")))
        assertEquals("Japanese", cultureDisplayName("ja", options) { "fallback" })
        assertEquals("Japanese", cultureDisplayName("jpn", options) { "fallback" })
        assertEquals("fallback", cultureDisplayName("ko", options) { "fallback" })
    }
}

private fun culture(name: String, displayName: String, twoLetter: String) = CultureDto(
    name = name,
    displayName = displayName,
    twoLetterIsoLanguageName = twoLetter,
    threeLetterIsoLanguageName = "",
    threeLetterIsoLanguageNames = emptyList()
)
