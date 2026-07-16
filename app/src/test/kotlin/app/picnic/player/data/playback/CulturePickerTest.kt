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
