package app.picnic.player.ui.settings

import app.picnic.player.data.seerr.SeerrMediaRequest
import app.picnic.player.data.seerr.SeerrMediaStatus
import app.picnic.player.data.seerr.SeerrRequestDisplay
import app.picnic.player.data.seerr.SeerrRequestMediaRef
import app.picnic.player.data.seerr.SeerrRequestSeason
import app.picnic.player.data.seerr.SeerrRequestStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestRowMetaTest {
    @Test
    fun requestRowMetaLine_movie_yearOnly() {
        val row = displayRow(
            mediaType = "movie",
            yearLabel = "2019"
        )
        assertEquals("MOVIE • 2019", requestRowMetaLine(row))
    }

    @Test
    fun requestRowMetaLine_movie_ignoresRequestedSeasons() {
        val row = displayRow(
            mediaType = "movie",
            yearLabel = "2019",
            seasons = listOf(1, 2)
        )
        assertEquals("MOVIE • 2019", requestRowMetaLine(row))
    }

    @Test
    fun requestRowMetaLine_tv_requestedSeasonRanges() {
        val row = displayRow(
            mediaType = "tv",
            yearLabel = "2022",
            seasons = listOf(1, 2, 3, 4, 5, 7, 8)
        )
        assertEquals("SHOW • 2022 • Seasons 1–5, 7–8", requestRowMetaLine(row))
    }

    @Test
    fun requestRowMetaLine_tv_singleSeason() {
        val row = displayRow(
            mediaType = "tv",
            yearLabel = "2020",
            seasons = listOf(1)
        )
        assertEquals("SHOW • 2020 • Season 1", requestRowMetaLine(row))
    }

    @Test
    fun requestRowMetaLine_tv_emptySeasons_yearOnly() {
        val row = displayRow(
            mediaType = "tv",
            yearLabel = "2020",
            seasons = emptyList()
        )
        assertEquals("SHOW • 2020", requestRowMetaLine(row))
    }

    @Test
    fun requestRowMetaLine_tv_specialsOnly() {
        val row = displayRow(
            mediaType = "tv",
            yearLabel = "2021",
            seasons = listOf(0)
        )
        assertEquals("SHOW • 2021 • Specials", requestRowMetaLine(row))
    }

    @Test
    fun requestRowMetaLine_tv_specialsAndNumbered() {
        val row = displayRow(
            mediaType = "tv",
            yearLabel = "2021",
            seasons = listOf(0, 1, 2)
        )
        assertEquals("SHOW • 2021 • Specials, Seasons 1–2", requestRowMetaLine(row))
    }

    @Test
    fun requestRowMetaLine_tv_typeUntilHydrate() {
        val row = displayRow(mediaType = "tv")
        assertEquals("SHOW", requestRowMetaLine(row))
    }

    @Test
    fun requestContextMenuActions_movieGoToOnlyWhenCannotCancel() {
        assertEquals(
            listOf(RequestMenuEntry(RequestMenuAction.GO_TO, "Go to movie")),
            requestContextMenuActions(displayRow(mediaType = "movie").request, canCancel = false)
        )
    }

    @Test
    fun requestContextMenuActions_seriesIncludesCancelWhenAllowed() {
        assertEquals(
            listOf(
                RequestMenuEntry(RequestMenuAction.GO_TO, "Go to show"),
                RequestMenuEntry(RequestMenuAction.CANCEL, "Cancel request")
            ),
            requestContextMenuActions(displayRow(mediaType = "tv").request, canCancel = true)
        )
    }

    @Test
    fun requestContextMenuActions_unknownUsesGenericTitleLabel() {
        assertEquals(
            listOf(
                RequestMenuEntry(RequestMenuAction.GO_TO, "Go to title"),
                RequestMenuEntry(RequestMenuAction.CANCEL, "Cancel request")
            ),
            requestContextMenuActions(displayRow(mediaType = "music").request, canCancel = true)
        )
    }

    @Test
    fun requestRowStatusLabel_pending() {
        assertEquals("Pending", requestRowStatusLabel(request(status = SeerrRequestStatus.PENDING)))
    }

    @Test
    fun requestRowStatusLabel_approved() {
        assertEquals("In Progress", requestRowStatusLabel(request(status = SeerrRequestStatus.APPROVED)))
    }

    @Test
    fun requestRowStatusLabel_declined() {
        assertEquals("Declined", requestRowStatusLabel(request(status = SeerrRequestStatus.DECLINED)))
    }

    @Test
    fun requestRowStatusLabel_failed() {
        assertEquals("Failed", requestRowStatusLabel(request(status = SeerrRequestStatus.FAILED)))
    }

    @Test
    fun requestRowStatusLabel_completed() {
        assertEquals("Available", requestRowStatusLabel(request(status = SeerrRequestStatus.COMPLETED)))
    }

    @Test
    fun requestRowStatusLabel_nullOrUnknown_defaultsPending() {
        assertEquals("Pending", requestRowStatusLabel(request(status = null)))
        assertEquals("Pending", requestRowStatusLabel(request(status = 99)))
    }

    @Test
    fun requestRowStatusLabel_ignoresMediaAvailable() {
        val req = request(
            status = SeerrRequestStatus.PENDING,
            mediaStatus = SeerrMediaStatus.AVAILABLE
        )
        assertEquals("Pending", requestRowStatusLabel(req))
    }

    @Test
    fun requestRowStatusLabel_approved_ignoresMediaAvailable() {
        val req = request(
            status = SeerrRequestStatus.APPROVED,
            mediaStatus = SeerrMediaStatus.AVAILABLE
        )
        assertEquals("In Progress", requestRowStatusLabel(req))
    }

    @Test
    fun requestRowStatusLabel_completed_ignoresMediaPartiallyAvailable() {
        val req = request(
            status = SeerrRequestStatus.COMPLETED,
            mediaStatus = SeerrMediaStatus.PARTIALLY_AVAILABLE
        )
        assertEquals("Available", requestRowStatusLabel(req))
    }

    @Test
    fun partitionSettingsRequestSections_splitsAndSortsAz() {
        val rows = listOf(
            displayRow(id = 1, title = "zeta", status = SeerrRequestStatus.PENDING),
            displayRow(id = 2, title = "Alpha", status = SeerrRequestStatus.APPROVED),
            displayRow(id = 3, title = "middle", status = SeerrRequestStatus.COMPLETED),
            displayRow(id = 4, title = "done", status = SeerrRequestStatus.COMPLETED),
            displayRow(id = 5, title = "nope", status = SeerrRequestStatus.DECLINED),
            displayRow(id = 6, title = "fail", status = SeerrRequestStatus.FAILED)
        )
        val sections = partitionSettingsRequestSections(rows)
        assertEquals(listOf("Alpha", "zeta"), sections.active.map { it.title })
        assertEquals(listOf("done", "middle"), sections.completed.map { it.title })
    }

    @Test
    fun partitionSettingsRequestSections_nullAndUnknownGoToActive() {
        val rows = listOf(
            displayRow(id = 1, title = "b", status = null),
            displayRow(id = 2, title = "a", status = 99),
            displayRow(id = 3, title = "c", status = SeerrRequestStatus.DECLINED)
        )
        val sections = partitionSettingsRequestSections(rows)
        assertEquals(listOf("a", "b"), sections.active.map { it.title })
        assertEquals(emptyList<String>(), sections.completed.map { it.title })
    }

    @Test
    fun partitionSettingsRequestSections_hidesOnlyDeclinedFailed() {
        val rows = listOf(
            displayRow(id = 1, title = "p", status = SeerrRequestStatus.PENDING),
            displayRow(id = 2, title = "d", status = SeerrRequestStatus.DECLINED),
            displayRow(id = 3, title = "f", status = SeerrRequestStatus.FAILED),
            displayRow(id = 4, title = "c", status = SeerrRequestStatus.COMPLETED)
        )
        val sections = partitionSettingsRequestSections(rows)
        assertEquals(listOf(1), sections.active.map { it.request.id })
        assertEquals(listOf(4), sections.completed.map { it.request.id })
    }

    private fun request(
        status: Int? = null,
        mediaStatus: Int? = null
    ) = SeerrMediaRequest(
        id = 1,
        status = status,
        media = SeerrRequestMediaRef(tmdbId = 10, status = mediaStatus, mediaType = "tv")
    )

    private fun displayRow(
        mediaType: String = "movie",
        yearLabel: String? = null,
        seasons: List<Int> = emptyList(),
        id: Int = 1,
        title: String = "Title",
        status: Int? = SeerrRequestStatus.PENDING
    ): SeerrRequestDisplay {
        val request = SeerrMediaRequest(
            id = id,
            status = status,
            mediaType = mediaType,
            media = SeerrRequestMediaRef(tmdbId = 10, mediaType = mediaType),
            seasons = seasons.map { SeerrRequestSeason(seasonNumber = it) }
        )
        return SeerrRequestDisplay(
            request = request,
            title = title,
            yearLabel = yearLabel
        )
    }
}
