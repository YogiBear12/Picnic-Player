package app.picnic.player.data.playback

import org.jellyfin.sdk.model.api.CultureDto
import org.junit.Assert.assertEquals
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
    fun resolveLanguageCode_appOverrideThenServerThenDevice() {
        assertEquals("fr", resolveLanguageCode("fr", "en", deviceLanguage = "de"))
        assertEquals("en", resolveLanguageCode(null, "en", deviceLanguage = "de"))
        assertEquals("en", resolveLanguageCode("", "en", deviceLanguage = "de"))
        assertEquals("de", resolveLanguageCode(null, "", deviceLanguage = "de"))
        assertEquals("de", resolveLanguageCode(null, null, deviceLanguage = "de"))
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
