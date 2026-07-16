package app.picnic.player.data.seerr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JellyfinProviderIdsTest {

    @Test
    fun tmdbIdFromProviderIds_matchesKeyCaseInsensitively() {
        assertEquals(123, tmdbIdFromProviderIds(mapOf("Tmdb" to "123")))
        assertEquals(456, tmdbIdFromProviderIds(mapOf("tmdb" to " 456 ")))
        assertEquals(789, tmdbIdFromProviderIds(mapOf("TMDB" to "789")))
    }

    @Test
    fun tmdbIdFromProviderIds_rejectsMissingInvalidAndNonPositive() {
        assertNull(tmdbIdFromProviderIds(null))
        assertNull(tmdbIdFromProviderIds(emptyMap()))
        assertNull(tmdbIdFromProviderIds(mapOf("Imdb" to "tt123")))
        assertNull(tmdbIdFromProviderIds(mapOf("Tmdb" to "")))
        assertNull(tmdbIdFromProviderIds(mapOf("Tmdb" to "abc")))
        assertNull(tmdbIdFromProviderIds(mapOf("Tmdb" to "0")))
        assertNull(tmdbIdFromProviderIds(mapOf("Tmdb" to "-1")))
    }
}
