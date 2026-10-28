package app.picnic.player.data.media

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageFacetsTest {

    @Test
    fun codesWithTheSameNameMergeIntoOneRow() {
        val facets = languageFacets(listOf("chi", "eng", "zho"), deviceLanguage = "de")
        assertEquals(listOf("Chinese", "English"), facets.map { it.label })
        assertEquals(setOf("chi", "zho"), facets.first().codes)
    }

    @Test
    fun distinctLanguagesStaySeparate() {
        val facets = languageFacets(listOf("nob", "nor"), deviceLanguage = "en")
        assertEquals(listOf(setOf("nob"), setOf("nor")), facets.sortedBy { it.codes.first() }.map { it.codes })
    }

    @Test
    fun deviceLanguageIsPinnedFirst() {
        val facets = languageFacets(listOf("eng", "fre", "jpn"), deviceLanguage = "ja")
        assertEquals(listOf("Japanese", "English", "French"), facets.map { it.label })
    }

    @Test
    fun deviceLanguagePinsThroughABibliographicCode() {
        val facets = languageFacets(listOf("eng", "ger", "jpn"), deviceLanguage = "de")
        assertEquals("German", facets.first().label)
    }

    @Test
    fun unspokenCodesAreRelabeledAndLast() {
        val facets = languageFacets(listOf("und", "jpn", "zxx", "eng"), deviceLanguage = "en")
        assertEquals(listOf("English", "Japanese", "Unknown", "No dialogue"), facets.map { it.label })
    }
}
