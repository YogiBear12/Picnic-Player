package app.picnic.player.ui.settings

import app.picnic.player.data.seerr.SeerrMediaRequest
import app.picnic.player.data.seerr.SeerrMediaStatus
import app.picnic.player.data.seerr.SeerrRequestDisplay
import app.picnic.player.data.seerr.SeerrRequestMediaRef
import app.picnic.player.data.seerr.SeerrRequestSeason
import app.picnic.player.data.seerr.SeerrRequestStatus
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountCardMetaTest {

    @Test
    fun favoriteTitle_episodeUsesSeriesName() {
        val episode = item(BaseItemKind.EPISODE, name = "Woe's Hollow", seriesName = "Severance")
        assertEquals("Severance", favoriteTitle(episode))
    }

    @Test
    fun favoriteTitle_nonEpisodeKeepsCardDefault() {
        assertNull(favoriteTitle(item(BaseItemKind.MOVIE, name = "Arrival")))
        assertNull(favoriteTitle(item(BaseItemKind.SERIES, name = "Severance")))
    }

    @Test
    fun favoriteSubtitle_episodeShowsCodeAndName() {
        val episode = item(
            BaseItemKind.EPISODE,
            name = "Woe's Hollow",
            seriesName = "Severance",
            season = 2,
            episode = 4
        )
        assertEquals("S2 E4 · Woe's Hollow", favoriteSubtitle(episode))
    }

    @Test
    fun favoriteSubtitle_episodeMissingNumbersFallsBack() {
        val episode = item(BaseItemKind.EPISODE, name = "Pilot", seriesName = "Show")
        assertEquals("S? E? · Pilot", favoriteSubtitle(episode))
    }

    @Test
    fun favoriteSubtitle_collectionAndPlaylistAreLabelled() {
        assertEquals("Collection", favoriteSubtitle(item(BaseItemKind.BOX_SET, name = "Alien")))
        assertEquals("Playlist", favoriteSubtitle(item(BaseItemKind.PLAYLIST, name = "Road trip")))
    }

    @Test
    fun favoriteSubtitle_movieAndSeriesKeepCardDefault() {
        assertNull(favoriteSubtitle(item(BaseItemKind.MOVIE, name = "Arrival")))
        assertNull(favoriteSubtitle(item(BaseItemKind.SERIES, name = "Severance")))
    }

    @Test
    fun requestBadge_declinedAndFailedAreRejected() {
        assertEquals(RequestBadge.REJECTED, requestBadge(row(status = SeerrRequestStatus.DECLINED)))
        assertEquals(RequestBadge.REJECTED, requestBadge(row(status = SeerrRequestStatus.FAILED)))
    }

    @Test
    fun requestBadge_followsMediaStatusNotRequestStatus() {
        val approvedButNotAvailable = row(
            status = SeerrRequestStatus.APPROVED,
            mediaStatus = SeerrMediaStatus.PROCESSING
        )
        assertEquals(RequestBadge.PENDING, requestBadge(approvedButNotAvailable))

        val pendingButAvailable = row(
            status = SeerrRequestStatus.PENDING,
            mediaStatus = SeerrMediaStatus.AVAILABLE
        )
        assertEquals(RequestBadge.AVAILABLE, requestBadge(pendingButAvailable))
    }

    @Test
    fun requestBadge_partiallyAvailableIsAvailable() {
        val partial = row(mediaStatus = SeerrMediaStatus.PARTIALLY_AVAILABLE)
        assertEquals(RequestBadge.AVAILABLE, requestBadge(partial))
    }

    @Test
    fun requestBadge_unknownMediaStatusIsPending() {
        assertEquals(RequestBadge.PENDING, requestBadge(row(mediaStatus = null)))
    }

    @Test
    fun requestSubtitle_tvPrefersRequestedSeasons() {
        val show = row(mediaType = "tv", yearLabel = "2022", seasons = listOf(1, 2, 3))
        assertEquals("Seasons 1–3", requestSubtitle(show))
    }

    @Test
    fun requestSubtitle_tvWithoutSeasonsFallsBackToYear() {
        val show = row(mediaType = "tv", yearLabel = "2022")
        assertEquals("2022", requestSubtitle(show))
    }

    @Test
    fun requestSubtitle_movieIgnoresSeasons() {
        val movie = row(mediaType = "movie", yearLabel = "2019", seasons = listOf(1, 2))
        assertEquals("2019", requestSubtitle(movie))
    }

    private fun item(
        kind: BaseItemKind,
        name: String,
        seriesName: String? = null,
        season: Int? = null,
        episode: Int? = null
    ) = BaseItemDto(
        id = UUID.randomUUID(),
        type = kind,
        name = name,
        seriesName = seriesName,
        parentIndexNumber = season,
        indexNumber = episode
    )

    private fun row(
        mediaType: String = "movie",
        yearLabel: String? = null,
        seasons: List<Int> = emptyList(),
        status: Int? = SeerrRequestStatus.PENDING,
        mediaStatus: Int? = null
    ): SeerrRequestDisplay {
        val request = SeerrMediaRequest(
            id = 1,
            status = status,
            mediaType = mediaType,
            media = SeerrRequestMediaRef(tmdbId = 10, status = mediaStatus, mediaType = mediaType),
            seasons = seasons.map { SeerrRequestSeason(seasonNumber = it) }
        )
        return SeerrRequestDisplay(request = request, title = "Title", yearLabel = yearLabel)
    }
}
