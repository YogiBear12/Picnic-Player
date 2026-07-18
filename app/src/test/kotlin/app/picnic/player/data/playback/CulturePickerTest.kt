package app.picnic.player.data.playback

import org.jellyfin.sdk.model.api.CultureDto
import org.junit.Assert.assertEquals
import org.junit.Test

class CulturePickerTest {

    @Test
    fun culturePickerOptions_unspecifiedFirstThenSortedDisplayNames() {
        val options = culturePickerOptions(
            listOf(
                culture("zulu", "Zulu", "zu"),
                culture("english", "English", "en"),
                culture("japanese", "Japanese", "ja")
            )
        )
        assertEquals("Unspecified", options.first().displayName)
        assertEquals(null, options.first().languageCode)
        assertEquals(listOf("English", "Japanese", "Zulu"), options.drop(1).map { it.displayName })
        assertEquals(listOf("en", "ja", "zu"), options.drop(1).map { it.languageCode })
    }

    @Test
    fun effectiveLanguageCode_appOverrideWinsThenServerThenNull() {
        assertEquals("fr", effectiveLanguageCode(appPreference = "fr", serverPreference = "en"))
        assertEquals("en", effectiveLanguageCode(appPreference = null, serverPreference = "en"))
        assertEquals("en", effectiveLanguageCode(appPreference = "", serverPreference = "en"))
        assertEquals(null, effectiveLanguageCode(appPreference = null, serverPreference = ""))
        assertEquals(null, effectiveLanguageCode(appPreference = null, serverPreference = null))
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
        assertEquals(listOf("Unspecified", "German", "English", "French"), pinned.options.map { it.displayName })
        assertEquals(1, pinned.separatorAfterIndex)
        // Device language appears once (pinned), not again in the A–Z body.
        assertEquals(1, pinned.options.count { it.languageCode == "de" })
    }

    @Test
    fun pinnedLanguageOptions_deviceLanguageAbsentPinsOnlyUnspecified() {
        val base = culturePickerOptions(listOf(culture("english", "English", "en")))
        val pinned = pinnedLanguageOptions(base, deviceLanguage = "de")
        assertEquals(listOf("Unspecified", "English"), pinned.options.map { it.displayName })
        assertEquals(0, pinned.separatorAfterIndex)
    }

    @Test
    fun cultureDisplayName_resolvesFromOptions() {
        val options = culturePickerOptions(listOf(culture("japanese", "Japanese", "ja")))
        assertEquals("Unspecified", cultureDisplayName(null, options) { it })
        assertEquals("Japanese", cultureDisplayName("ja", options) { "fallback" })
        assertEquals("Japanese", cultureDisplayName("jpn", options) { "fallback" })
    }
}

private fun culture(name: String, displayName: String, twoLetter: String) = CultureDto(
    name = name,
    displayName = displayName,
    twoLetterIsoLanguageName = twoLetter,
    threeLetterIsoLanguageName = "",
    threeLetterIsoLanguageNames = emptyList()
)
