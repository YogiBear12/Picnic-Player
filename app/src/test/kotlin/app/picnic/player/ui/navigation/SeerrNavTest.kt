package app.picnic.player.ui.navigation

import app.picnic.player.data.seerr.SeerrCatalogItem
import app.picnic.player.data.seerr.SeerrMediaRequest
import app.picnic.player.data.seerr.SeerrMediaStatus
import app.picnic.player.data.seerr.SeerrMediaType
import app.picnic.player.data.seerr.SeerrRequestMediaRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeerrNavTest {
    private val jellyfinId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    private val undashedJellyfinId = "a1b2c3d4e5f67890abcdef1234567890"

    @Test
    fun resolveSeerrNavKey_availableCatalog_opensJellyfinDetail() {
        val key = resolveSeerrNavKey(
            item = catalog(
                mediaStatus = SeerrMediaStatus.AVAILABLE,
                jellyfinMediaId = undashedJellyfinId
            ),
            bgUrl = "bg",
            ambUrl = "amb"
        )
        assertTrue(key is DetailKey)
        key as DetailKey
        assertEquals(jellyfinId, key.itemId)
        assertEquals("bg", key.bgUrl)
        assertEquals("amb", key.ambUrl)
    }

    @Test
    fun resolveSeerrNavKey_partialCatalogWithId_opensJellyfinDetail() {
        val key = resolveSeerrNavKey(
            catalog(
                mediaStatus = SeerrMediaStatus.PARTIALLY_AVAILABLE,
                jellyfinMediaId = jellyfinId,
                mediaType = SeerrMediaType.TV
            )
        )
        assertTrue(key is DetailKey)
        assertEquals(jellyfinId, (key as DetailKey).itemId)
    }

    @Test
    fun resolveSeerrNavKey_availableWithoutId_opensSeerrDetail() {
        val key = resolveSeerrNavKey(
            catalog(mediaStatus = SeerrMediaStatus.AVAILABLE, jellyfinMediaId = null)
        )
        assertTrue(key is SeerrDetailKey)
    }

    // 4K-only library copy still opens Jellyfin Detail (CONTEXT: never treat 4K
    // library files as non-library).
    @Test
    fun resolveSeerrNavKey_fourKOnlyCatalog_opensJellyfinDetail() {
        val key = resolveSeerrNavKey(
            catalog(
                mediaStatus = SeerrMediaStatus.AVAILABLE,
                jellyfinMediaId = null,
                jellyfinMediaId4k = undashedJellyfinId
            )
        )
        assertTrue(key is DetailKey)
        assertEquals(jellyfinId, (key as DetailKey).itemId)
    }

    @Test
    fun resolveSeerrNavKey_availableRequest_opensJellyfinDetail() {
        val key = resolveSeerrNavKey(
            SeerrMediaRequest(
                id = 9,
                mediaType = "movie",
                media = SeerrRequestMediaRef(
                    tmdbId = 100,
                    status = SeerrMediaStatus.AVAILABLE,
                    mediaType = "movie",
                    jellyfinMediaId = undashedJellyfinId
                )
            )
        )
        assertTrue(key is DetailKey)
        assertEquals(jellyfinId, (key as DetailKey).itemId)
    }

    @Test
    fun resolveSeerrNavKey_partialRequestWithId_opensJellyfinDetail() {
        val key = resolveSeerrNavKey(
            SeerrMediaRequest(
                id = 9,
                mediaType = "tv",
                media = SeerrRequestMediaRef(
                    tmdbId = 200,
                    status = SeerrMediaStatus.PARTIALLY_AVAILABLE,
                    mediaType = "tv",
                    jellyfinMediaId = jellyfinId
                )
            )
        )
        assertTrue(key is DetailKey)
        assertEquals(jellyfinId, (key as DetailKey).itemId)
    }

    @Test
    fun resolveSeerrNavKey_requestMissingMedia_returnsNull() {
        assertNull(resolveSeerrNavKey(SeerrMediaRequest(id = 1, media = null)))
        assertNull(
            resolveSeerrNavKey(
                SeerrMediaRequest(
                    id = 1,
                    media = SeerrRequestMediaRef(tmdbId = null, status = SeerrMediaStatus.AVAILABLE)
                )
            )
        )
    }

    @Test
    fun resolveSeerrNavKey_personCreditCatalog_matchesDiscoverRouting() {
        val linked = resolveSeerrNavKey(
            catalog(
                mediaStatus = SeerrMediaStatus.AVAILABLE,
                jellyfinMediaId = undashedJellyfinId
            )
        )
        assertTrue(linked is DetailKey)

        val missing = resolveSeerrNavKey(
            catalog(mediaStatus = SeerrMediaStatus.UNKNOWN, jellyfinMediaId = null)
        )
        assertTrue(missing is SeerrDetailKey)
        assertEquals(42, (missing as SeerrDetailKey).tmdbId)
    }

    @Test
    fun personKey_hybridVsLegacyShapes() {
        val hybrid = PersonKey(jellyfinPersonId = jellyfinId, tmdbId = 287)
        assertEquals(jellyfinId, hybrid.jellyfinPersonId)
        assertEquals(287, hybrid.tmdbId)

        val seerrOnly = PersonKey(tmdbId = 287)
        assertNull(seerrOnly.jellyfinPersonId)
        assertEquals(287, seerrOnly.tmdbId)

        val legacy = PersonKey(jellyfinPersonId = jellyfinId)
        assertEquals(jellyfinId, legacy.jellyfinPersonId)
        assertNull(legacy.tmdbId)
    }

    private fun catalog(
        mediaStatus: Int?,
        jellyfinMediaId: String?,
        jellyfinMediaId4k: String? = null,
        mediaType: SeerrMediaType = SeerrMediaType.MOVIE
    ) = SeerrCatalogItem(
        tmdbId = 42,
        mediaType = mediaType,
        title = "Title",
        mediaStatus = mediaStatus,
        jellyfinMediaId = jellyfinMediaId,
        jellyfinMediaId4k = jellyfinMediaId4k
    )
}
