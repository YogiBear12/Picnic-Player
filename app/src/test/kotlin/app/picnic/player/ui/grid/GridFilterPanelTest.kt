package app.picnic.player.ui.grid

import app.picnic.player.data.media.GridFilterFacets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GridFilterPanelTest {
    @Test
    fun genreGrid_putsContentTypeFirstAndHidesGenres() {
        val sections = availableFilterSections(
            facets = GridFilterFacets(),
            offered = setOf(GridFilterSection.CONTENT_TYPE)
        )

        assertEquals(GridFilterSection.CONTENT_TYPE, sections.first())
        assertFalse(GridFilterSection.GENRES in sections)
    }

    @Test
    fun offeringNothingStillKeepsTheFacetIndependentSections() {
        val sections = availableFilterSections(facets = GridFilterFacets(), offered = emptySet())

        assertFalse(GridFilterSection.CONTENT_TYPE in sections)
        assertFalse(GridFilterSection.GENRES in sections)
        assertTrue(GridFilterSection.SORT_BY in sections)
    }

    @Test
    fun libraryGrid_hidesContentType() {
        val sections = availableFilterSections(
            facets = GridFilterFacets(),
            offered = setOf(GridFilterSection.GENRES)
        )

        assertFalse(GridFilterSection.CONTENT_TYPE in sections)
        assertFalse(GridFilterSection.GENRES in sections)
    }
}
