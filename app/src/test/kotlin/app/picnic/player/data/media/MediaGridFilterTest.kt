package app.picnic.player.data.media

import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaGridFilterTest {

    @Test
    fun genreScope_isNotAnActiveFilter() {
        val filter = MediaGridFilter(genreId = UUID.randomUUID())

        assertFalse(filter.isActive)
    }

    @Test
    fun contentType_isAnActiveFilterWhenNotAll() {
        val filter = MediaGridFilter(contentType = GridContentType.MOVIES)

        assertTrue(filter.isActive)
    }

    @Test
    fun clearUserFilters_preservesGenreScopeAndResetsContentType() {
        val genreId = UUID.randomUUID()
        val cleared = MediaGridFilter(
            genreId = genreId,
            contentType = GridContentType.SERIES,
            favoritesOnly = true
        ).clearUserFilters()

        assertTrue(cleared.genreId == genreId)
        assertTrue(cleared.contentType == GridContentType.ALL)
        assertFalse(cleared.isActive)
    }
}
