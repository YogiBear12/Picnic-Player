package app.picnic.player.data.media

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeContentTest {

    private fun day(dayOfMonth: Int): LocalDateTime = LocalDateTime.of(2026, 8, dayOfMonth, 20, 0)

    private fun item(
        id: UUID = UUID.randomUUID(),
        seriesId: UUID? = null,
        name: String? = null,
        kind: BaseItemKind = BaseItemKind.EPISODE
    ) = BaseItemDto(id = id, type = kind, seriesId = seriesId, name = name)

    private fun episode(
        seriesId: UUID,
        season: Int,
        number: Int,
        playedAt: LocalDateTime? = null
    ): BaseItemDto {
        val id = UUID.randomUUID()
        return BaseItemDto(
            id = id,
            type = BaseItemKind.EPISODE,
            seriesId = seriesId,
            parentIndexNumber = season,
            indexNumber = number,
            userData = UserItemDataDto(
                playbackPositionTicks = 1200L,
                playCount = 1,
                isFavorite = false,
                played = false,
                lastPlayedDate = playedAt,
                key = "test",
                itemId = id
            )
        )
    }

    private fun millisOf(playedAt: LocalDateTime): Long = playedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun resume_ordersSeriesEpisodesByRecencyAsEachIsWatched() {
        val series = UUID.randomUUID()
        val oldest = episode(series, 1, 5, day(1))
        val middle = episode(series, 1, 8, day(2))
        val newest = episode(series, 1, 10, day(3))

        assertEquals(listOf(newest), HomeContent.preferredPerSeries(listOf(oldest, middle, newest)))
        assertEquals(listOf(middle), HomeContent.preferredPerSeries(listOf(oldest, middle)))
        assertEquals(listOf(oldest), HomeContent.preferredPerSeries(listOf(oldest)))
    }

    @Test
    fun resume_keepsMostRecentlyPlayedEpisodePerSeries() {
        val series = UUID.randomUUID()
        val watchedToday = episode(series, season = 1, number = 10, playedAt = day(3))
        val watchedLastWeek = episode(series, season = 1, number = 8, playedAt = day(1))
        val out = HomeContent.combineContinueWatching(listOf(watchedToday, watchedLastWeek), emptyList())
        assertEquals(listOf(watchedToday.id), out.map { it.id })
    }

    @Test
    fun resume_earliestEpisodeWinsWhenNeverPlayed() {
        val series = UUID.randomUUID()
        val seasonTwo = episode(series, season = 2, number = 1)
        val seasonOne = episode(series, season = 1, number = 12)
        val out = HomeContent.combineContinueWatching(listOf(seasonTwo, seasonOne), emptyList())
        assertEquals(listOf(seasonOne.id), out.map { it.id })
    }

    @Test
    fun resume_playedEpisodeBeatsNeverPlayedEpisode() {
        val series = UUID.randomUUID()
        val neverPlayed = episode(series, season = 1, number = 2)
        val played = episode(series, season = 1, number = 9, playedAt = day(1))
        val out = HomeContent.combineContinueWatching(listOf(neverPlayed, played), emptyList())
        assertEquals(listOf(played.id), out.map { it.id })
    }

    @Test
    fun resume_keepsRowSlotOfFirstEpisodeOfSeries() {
        val series = UUID.randomUUID()
        val movie = item(kind = BaseItemKind.MOVIE)
        val older = episode(series, season = 1, number = 10, playedAt = day(1))
        val newer = episode(series, season = 1, number = 8, playedAt = day(3))
        val out = HomeContent.combineContinueWatching(listOf(older, movie, newer), emptyList())
        assertEquals(listOf(newer.id, movie.id), out.map { it.id })
    }

    @Test
    fun resume_moviesAreNeverCollapsed() {
        val a = item(kind = BaseItemKind.MOVIE)
        val b = item(kind = BaseItemKind.MOVIE)
        val out = HomeContent.combineContinueWatching(listOf(a, b), emptyList())
        assertEquals(listOf(a.id, b.id), out.map { it.id })
    }

    @Test
    fun hiddenSeries_dropsEveryEpisodePlayedBeforeRemoval() {
        val series = UUID.randomUUID()
        val removedAt = day(3)
        val shown = episode(series, season = 1, number = 10, playedAt = removedAt)
        val older = episode(series, season = 1, number = 8, playedAt = day(1))
        val out = HomeContent.withoutHidden(
            listOf(shown, older),
            mapOf(series.toString() to millisOf(removedAt))
        )
        assertEquals(emptyList<UUID>(), out.map { it.id })
    }

    @Test
    fun hiddenSeries_returnsAfterAnyLaterPlay() {
        val series = UUID.randomUUID()
        val removedAt = day(3)
        val playedAgain = episode(series, season = 1, number = 8, playedAt = day(5))
        val out = HomeContent.withoutHidden(
            listOf(playedAgain),
            mapOf(series.toString() to millisOf(removedAt))
        )
        assertEquals(listOf(playedAgain.id), out.map { it.id })
    }

    @Test
    fun hiddenSeries_keepsNextUpEpisodeHidden() {
        val series = UUID.randomUUID()
        val neverPlayed = item(seriesId = series)
        val out = HomeContent.withoutHidden(
            listOf(neverPlayed),
            mapOf(series.toString() to millisOf(day(3)))
        )
        assertEquals(emptyList<UUID>(), out.map { it.id })
    }

    @Test
    fun hiddenMovie_isKeyedByItsOwnId() {
        val movie = item(kind = BaseItemKind.MOVIE)
        val other = item(kind = BaseItemKind.MOVIE)
        val out = HomeContent.withoutHidden(
            listOf(movie, other),
            mapOf(movie.id.toString() to millisOf(day(3)))
        )
        assertEquals(listOf(other.id), out.map { it.id })
    }

    @Test
    fun undatedItems_keepTheirIncomingOrder() {
        val r = item(name = "resume")
        val n = item(name = "nextup")
        val out = HomeContent.combineContinueWatching(listOf(r), listOf(n))
        assertEquals(listOf(r.id, n.id), out.map { it.id })
    }

    @Test
    fun queuedEpisode_outranksAnOlderResumeItem() {
        val queued = item(seriesId = UUID.randomUUID())
        val staleResume = episode(UUID.randomUUID(), season = 1, number = 4, playedAt = day(1))
        val out = HomeContent.combineContinueWatching(
            resume = listOf(staleResume),
            nextUp = listOf(queued),
            queuedDates = mapOf(queued.id to day(9))
        )
        assertEquals(listOf(queued.id, staleResume.id), out.map { it.id })
    }

    @Test
    fun continueWatchingRow_ordersWithTheQueuedDates() {
        val queued = item(seriesId = UUID.randomUUID())
        val staleResume = episode(UUID.randomUUID(), season = 1, number = 4, playedAt = day(1))
        val rows = HomeContent.buildHomeRows(
            resume = listOf(staleResume),
            nextUp = listOf(queued),
            latestByLibrary = emptyList(),
            pinnedLibraryIds = emptyList(),
            queuedDates = mapOf(queued.id to day(9))
        )
        assertEquals(listOf(queued.id, staleResume.id), rows.first().items.map { it.id })
    }

    @Test
    fun nextUp_dedupedByItemId() {
        val shared = UUID.randomUUID()
        val r = item(id = shared)
        val n = item(id = shared)
        val out = HomeContent.combineContinueWatching(listOf(r), listOf(n))
        assertEquals(1, out.size)
    }

    @Test
    fun nextUp_dedupedBySeriesAlreadyResuming() {
        val series = UUID.randomUUID()
        val resumeEp = item(seriesId = series)
        val nextEp = item(seriesId = series)
        val out = HomeContent.combineContinueWatching(listOf(resumeEp), listOf(nextEp))
        assertEquals(listOf(resumeEp.id), out.map { it.id })
    }

    @Test
    fun nextUp_differentSeries_kept() {
        val a = item(seriesId = UUID.randomUUID())
        val b = item(seriesId = UUID.randomUUID())
        val out = HomeContent.combineContinueWatching(listOf(a), listOf(b))
        assertEquals(2, out.size)
    }

    @Test
    fun continueWatchingRow_firstAndFlagged_whenNonEmpty() {
        val resume = item()
        val lib = item(name = "Movies", kind = BaseItemKind.COLLECTION_FOLDER)
        val rows = HomeContent.buildHomeRows(
            resume = listOf(resume),
            nextUp = emptyList(),
            latestByLibrary = listOf(lib to listOf(item(kind = BaseItemKind.MOVIE))),
            pinnedLibraryIds = listOf(lib.id)
        )
        assertEquals("Continue watching", rows.first().title)
        assertEquals(true, rows.first().continueWatching)
        assertEquals("Recently added in Movies", rows[1].title)
    }

    @Test
    fun noContinueWatchingRow_whenEmpty() {
        val lib = item(name = "Shows", kind = BaseItemKind.COLLECTION_FOLDER)
        val rows = HomeContent.buildHomeRows(
            resume = emptyList(),
            nextUp = emptyList(),
            latestByLibrary = listOf(lib to listOf(item(kind = BaseItemKind.SERIES))),
            pinnedLibraryIds = listOf(lib.id)
        )
        assertEquals(1, rows.size)
        assertEquals("Recently added in Shows", rows.first().title)
    }

    @Test
    fun emptyLibraries_skipped() {
        val libA = item(name = "A", kind = BaseItemKind.COLLECTION_FOLDER)
        val libB = item(name = "B", kind = BaseItemKind.COLLECTION_FOLDER)
        val rows = HomeContent.buildHomeRows(
            resume = emptyList(),
            nextUp = emptyList(),
            latestByLibrary = listOf(
                libA to emptyList(),
                libB to listOf(item(kind = BaseItemKind.MOVIE))
            ),
            pinnedLibraryIds = listOf(libA.id, libB.id)
        )
        assertEquals(1, rows.size)
        assertEquals("Recently added in B", rows.first().title)
    }

    @Test
    fun pinnedOrder_filtersAndOrdersLibraryRows() {
        val libA = item(name = "A", kind = BaseItemKind.COLLECTION_FOLDER)
        val libB = item(name = "B", kind = BaseItemKind.COLLECTION_FOLDER)
        val libC = item(name = "C", kind = BaseItemKind.COLLECTION_FOLDER)
        val rows = HomeContent.buildHomeRows(
            resume = emptyList(),
            nextUp = emptyList(),
            latestByLibrary = listOf(
                libA to listOf(item(kind = BaseItemKind.MOVIE)),
                libB to listOf(item(kind = BaseItemKind.MOVIE)),
                libC to listOf(item(kind = BaseItemKind.MOVIE))
            ),
            pinnedLibraryIds = listOf(libC.id, libA.id)
        )
        assertEquals(
            listOf("Recently added in C", "Recently added in A"),
            rows.map { it.title }
        )
    }

    private val movies = item(name = "Movies", kind = BaseItemKind.COLLECTION_FOLDER)
    private val shows = item(name = "Shows", kind = BaseItemKind.COLLECTION_FOLDER)
    private val slots = HomeContent.visibleSlots(HomeContent.homeSlots(listOf(movies, shows)), listOf(movies.id, shows.id))
    private val continueSlot = slots[0]
    private val moviesSlot = slots[1]
    private val showsSlot = slots[2]

    @Test
    fun homeSlots_continueWatchingFirstThenPinnedLibraries() {
        assertEquals(
            listOf("continue-watching", "library-${movies.id}", "library-${shows.id}"),
            HomeContent.visibleSlots(HomeContent.homeSlots(listOf(movies, shows)), listOf(movies.id, shows.id)).map { it.key }
        )
        assertEquals(
            listOf("Continue watching", "Recently added in Shows"),
            HomeContent.visibleSlots(HomeContent.homeSlots(listOf(movies, shows)), listOf(shows.id)).map { it.title }
        )
    }

    @Test
    fun slotKey_matchesTheRowItBecomes() {
        val row = checkNotNull(moviesSlot.row(listOf(item(kind = BaseItemKind.MOVIE))))
        assertEquals(moviesSlot.key, row.key)
        val continueRow = checkNotNull(continueSlot.row(listOf(item())))
        assertEquals(continueSlot.key, continueRow.key)
    }

    @Test
    fun duplicateLibraryTitles_keepDistinctKeysAndRows() {
        val first = item(name = "Movies", kind = BaseItemKind.COLLECTION_FOLDER)
        val second = item(name = "Movies", kind = BaseItemKind.COLLECTION_FOLDER)
        val slots = HomeContent.visibleSlots(HomeContent.homeSlots(listOf(first, second)), listOf(first.id, second.id))
        val firstRow = slots[1].row(listOf(item(kind = BaseItemKind.MOVIE)))
        val secondRow = slots[2].row(listOf(item(kind = BaseItemKind.MOVIE)))
        val revealed = HomeContent.reveal(
            slots,
            mapOf(slots[0].key to null, slots[1].key to firstRow, slots[2].key to secondRow)
        )
        assertEquals(listOf(firstRow, secondRow), revealed.rows)
        assertEquals(2, revealed.rows.map { it.key }.distinct().size)
    }

    @Test
    fun visibleSlots_followChangedPinsDuringLoad() {
        val all = HomeContent.homeSlots(listOf(movies, shows))
        assertEquals(listOf(continueSlot, showsSlot), HomeContent.visibleSlots(all, listOf(shows.id)))
        assertEquals(listOf(continueSlot, moviesSlot, showsSlot), HomeContent.visibleSlots(all, listOf(movies.id, shows.id)))
    }

    @Test
    fun reveal_nothingResolved_allPending() {
        val revealed = HomeContent.reveal(slots, emptyMap())
        assertEquals(emptyList<HomeRow>(), revealed.rows)
        assertEquals(slots, revealed.pending)
    }

    @Test
    fun reveal_holdsALoadedRowBelowOneStillLoading() {
        val moviesRow = moviesSlot.row(listOf(item(kind = BaseItemKind.MOVIE)))
        val revealed = HomeContent.reveal(slots, mapOf(moviesSlot.key to moviesRow))
        assertEquals(emptyList<HomeRow>(), revealed.rows)
        assertEquals(slots, revealed.pending)
    }

    @Test
    fun reveal_emptyContinueWatchingCollapsesAndUnblocksTheNextRow() {
        val moviesRow = moviesSlot.row(listOf(item(kind = BaseItemKind.MOVIE)))
        val revealed = HomeContent.reveal(
            slots,
            mapOf(continueSlot.key to null, moviesSlot.key to moviesRow)
        )
        assertEquals(listOf(moviesRow), revealed.rows)
        assertEquals(listOf(showsSlot), revealed.pending)
    }

    @Test
    fun reveal_everythingEmpty_noRowsNothingPending() {
        val revealed = HomeContent.reveal(slots, slots.associate { it.key to null })
        assertEquals(emptyList<HomeRow>(), revealed.rows)
        assertEquals(emptyList<HomeSlot>(), revealed.pending)
    }
}
