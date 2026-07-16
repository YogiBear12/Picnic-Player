package app.picnic.player.data.seerr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeerrSeasonPickerTest {

    private fun tmdbSeasons(vararg numbers: Int): List<SeerrTvSeason> = numbers.map { SeerrTvSeason(seasonNumber = it) }

    private fun mediaSeason(number: Int, status: Int) = SeerrMediaSeason(seasonNumber = number, status = status)

    private fun request(
        id: Int = 1,
        status: Int? = SeerrRequestStatus.APPROVED,
        is4k: Boolean = false,
        seasons: List<Int> = emptyList()
    ) = SeerrMediaRequest(
        id = id,
        status = status,
        is4k = is4k,
        seasons = seasons.map { SeerrRequestSeason(seasonNumber = it) }
    )

    private val active: (SeerrMediaRequest) -> Boolean = { req ->
        when (req.status) {
            SeerrRequestStatus.DECLINED,
            SeerrRequestStatus.FAILED,
            SeerrRequestStatus.COMPLETED
            -> false
            else -> true
        }
    }

    @Test
    fun buildSeasonPickItems_omitsSpecialsAndSorts() {
        val items = buildSeasonPickItems(
            seasons = tmdbSeasons(0, 2, 1, 1),
            mediaInfo = null,
            isActiveRequest = active
        )
        assertEquals(listOf(1, 2), items.map { it.seasonNumber })
        assertTrue(items.all { it.selectable })
    }

    @Test
    fun buildSeasonPickItems_preservesLibraryStatusCodes() {
        val mediaInfo = SeerrMediaInfo(
            seasons = listOf(
                mediaSeason(1, SeerrMediaStatus.AVAILABLE),
                mediaSeason(2, SeerrMediaStatus.PROCESSING),
                mediaSeason(3, SeerrMediaStatus.PARTIALLY_AVAILABLE)
            )
        )
        val items = buildSeasonPickItems(
            seasons = tmdbSeasons(1, 2, 3, 4),
            mediaInfo = mediaInfo,
            isActiveRequest = active
        )
        assertEquals(SeerrSeasonAvailability.Available, items[0].availability)
        assertEquals(SeerrMediaStatus.AVAILABLE, items[0].mediaStatus)
        assertEquals(SeerrMediaStatus.PROCESSING, items[1].mediaStatus)
        assertEquals(SeerrMediaStatus.PARTIALLY_AVAILABLE, items[2].mediaStatus)
        assertFalse(items[0].selectable)
        assertFalse(items[1].selectable)
        assertFalse(items[2].selectable)
        assertTrue(items[3].selectable)
        assertNull(items[3].mediaStatus)
    }

    @Test
    fun buildSeasonPickItems_marksActiveRequestSeasonsRequested() {
        val mediaInfo = SeerrMediaInfo(
            requests = listOf(request(seasons = listOf(2, 3)))
        )
        val items = buildSeasonPickItems(
            seasons = tmdbSeasons(1, 2, 3),
            mediaInfo = mediaInfo,
            isActiveRequest = active
        )
        assertEquals(SeerrSeasonAvailability.Selectable, items[0].availability)
        assertEquals(SeerrSeasonAvailability.Requested, items[1].availability)
        assertEquals(SeerrSeasonAvailability.Requested, items[2].availability)
        assertNull(items[1].mediaStatus)
    }

    @Test
    fun buildSeasonPickItems_libraryTakesPrecedenceOverRequest() {
        val mediaInfo = SeerrMediaInfo(
            seasons = listOf(mediaSeason(1, SeerrMediaStatus.AVAILABLE)),
            requests = listOf(request(seasons = listOf(1)))
        )
        val items = buildSeasonPickItems(
            seasons = tmdbSeasons(1),
            mediaInfo = mediaInfo,
            isActiveRequest = active
        )
        assertEquals(SeerrSeasonAvailability.Available, items.single().availability)
        assertEquals(SeerrMediaStatus.AVAILABLE, items.single().mediaStatus)
    }

    @Test
    fun buildSeasonPickItems_ignoresInactiveAnd4kRequests() {
        val mediaInfo = SeerrMediaInfo(
            requests = listOf(
                request(id = 1, status = SeerrRequestStatus.COMPLETED, seasons = listOf(1)),
                request(id = 2, is4k = true, seasons = listOf(2))
            )
        )
        val items = buildSeasonPickItems(
            seasons = tmdbSeasons(1, 2),
            mediaInfo = mediaInfo,
            isActiveRequest = active
        )
        assertTrue(items.all { it.selectable })
    }

    @Test
    fun activeNon4kRequest_excludes4kRequests() {
        val fourK = request(id = 4, is4k = true, seasons = listOf(1))
        val non4k = request(id = 5, seasons = listOf(2))
        assertEquals(non4k, activeNon4kRequest(listOf(fourK, non4k), active))
    }

    @Test
    fun activeNon4kRequest_returnsNullWhenOnly4kIsActive() {
        assertNull(activeNon4kRequest(listOf(request(id = 4, is4k = true)), active))
    }

    @Test
    fun canRequestMoreSeasons_requiresSelectableAndPermission() {
        val selectable = listOf(
            SeerrSeasonPickItem(1, SeerrSeasonAvailability.Selectable)
        )
        val blocked = listOf(
            SeerrSeasonPickItem(1, SeerrSeasonAvailability.Requested)
        )
        val user = SeerrUser(id = 1, permissions = SeerrPermission.REQUEST_TV)

        assertTrue(
            canRequestMoreSeasons(
                seasons = selectable,
                user = user,
                mediaStatus = SeerrMediaStatus.PARTIALLY_AVAILABLE,
                hasActiveRequest = false
            )
        )
        assertFalse(
            canRequestMoreSeasons(
                seasons = blocked,
                user = user,
                mediaStatus = SeerrMediaStatus.PARTIALLY_AVAILABLE,
                hasActiveRequest = false
            )
        )
        assertFalse(
            canRequestMoreSeasons(
                seasons = selectable,
                user = SeerrUser(id = 2, permissions = SeerrPermission.NONE),
                mediaStatus = SeerrMediaStatus.PARTIALLY_AVAILABLE,
                hasActiveRequest = false
            )
        )
    }

    @Test
    fun seasonsForTvRequest_mergesExistingAndSelectedSortedDistinct() {
        val existing = request(seasons = listOf(1, 3))
        assertEquals(
            listOf(1, 2, 3, 5),
            seasonsForTvRequest(selected = listOf(5, 2, 1), existingRequest = existing)
        )
    }

    @Test
    fun seasonsForTvRequest_createOnlyUsesSelected() {
        assertEquals(
            listOf(4, 6),
            seasonsForTvRequest(selected = listOf(6, 4), existingRequest = null)
        )
    }

    @Test
    fun seasonLibraryBadgeLabel_mapsLibraryStatuses() {
        assertEquals("Available", seasonLibraryBadgeLabel(SeerrMediaStatus.AVAILABLE))
        assertEquals(
            "Partially available",
            seasonLibraryBadgeLabel(SeerrMediaStatus.PARTIALLY_AVAILABLE)
        )
        assertEquals("Processing", seasonLibraryBadgeLabel(SeerrMediaStatus.PROCESSING))
    }

    @Test
    fun seasonLibraryBadgeLabel_returnsNullForNonLibraryStatuses() {
        assertNull(seasonLibraryBadgeLabel(SeerrMediaStatus.PENDING))
        assertNull(seasonLibraryBadgeLabel(SeerrMediaStatus.UNKNOWN))
        assertNull(seasonLibraryBadgeLabel(null))
    }
}
