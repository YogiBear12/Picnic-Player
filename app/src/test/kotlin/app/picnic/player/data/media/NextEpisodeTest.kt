package app.picnic.player.data.media

import java.time.LocalDateTime
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextEpisodeTest {

    private fun episode(
        season: Int? = 1,
        number: Int? = 1,
        positionTicks: Long = 0L,
        lastPlayed: LocalDateTime? = null,
        id: UUID = UUID.randomUUID()
    ) = BaseItemDto(
        id = id,
        type = BaseItemKind.EPISODE,
        parentIndexNumber = season,
        indexNumber = number,
        userData = UserItemDataDto(
            playbackPositionTicks = positionTicks,
            playCount = 0,
            isFavorite = false,
            played = false,
            lastPlayedDate = lastPlayed,
            key = "test",
            itemId = id
        )
    )

    @Test
    fun noResumableEpisode_keepsNextUp() {
        val nextUp = episode(number = 1)
        assertEquals(nextUp, preferResumeEpisode(nextUp, emptyList()))
    }

    @Test
    fun resumableLaterThanNextUp_wins() {
        val nextUp = episode(number = 1)
        val inProgress = episode(number = 2, positionTicks = 500L)
        assertEquals(inProgress, preferResumeEpisode(nextUp, listOf(inProgress)))
    }

    @Test
    fun resumableEarlierThanNextUp_stillWins() {
        val nextUp = episode(season = 4, number = 2)
        val inProgress = episode(season = 1, number = 3, positionTicks = 500L)
        assertEquals(inProgress, preferResumeEpisode(nextUp, listOf(inProgress)))
    }

    @Test
    fun manyResumable_takesEarliestEpisodeNotMostRecentlyPlayed() {
        val later = episode(season = 2, number = 1, positionTicks = 500L, lastPlayed = LocalDateTime.of(2026, 2, 1, 0, 0))
        val earlier = episode(season = 1, number = 3, positionTicks = 500L, lastPlayed = LocalDateTime.of(2026, 1, 1, 0, 0))
        assertEquals(earlier, preferResumeEpisode(episode(number = 1), listOf(later, earlier)))
    }

    @Test
    fun zeroPositionResumable_ignored() {
        val nextUp = episode(number = 1)
        val notStarted = episode(number = 2, positionTicks = 0L)
        assertEquals(nextUp, preferResumeEpisode(nextUp, listOf(notStarted)))
    }

    @Test
    fun missingEpisodeNumbers_sortLast() {
        val unnumbered = episode(season = null, number = null, positionTicks = 500L)
        val numbered = episode(season = 3, number = 4, positionTicks = 500L)
        assertEquals(numbered, preferResumeEpisode(null, listOf(unnumbered, numbered)))
    }

    @Test
    fun noNextUp_usesResumable() {
        val inProgress = episode(number = 2, positionTicks = 500L)
        assertEquals(inProgress, preferResumeEpisode(null, listOf(inProgress)))
    }

    @Test
    fun nothingAtAll_isNull() {
        assertNull(preferResumeEpisode(null, emptyList()))
    }

    @Test
    fun firstEpisodeToPlay_skipsSpecials() {
        val special = episode(season = 0, number = 1)
        val premiere = episode(season = 1, number = 1)
        assertEquals(premiere, firstEpisodeToPlay(listOf(special, premiere)))
    }

    @Test
    fun firstEpisodeToPlay_fallsBackToSpecialsWhenThatIsAll() {
        val special = episode(season = 0, number = 2)
        val earlierSpecial = episode(season = 0, number = 1)
        assertEquals(earlierSpecial, firstEpisodeToPlay(listOf(special, earlierSpecial)))
    }

    @Test
    fun firstEpisodeToPlay_sortsUnnumberedLast() {
        val unnumbered = episode(season = null, number = null)
        val numbered = episode(season = 1, number = 1)
        assertEquals(numbered, firstEpisodeToPlay(listOf(unnumbered, numbered)))
    }

    @Test
    fun firstEpisodeToPlay_emptyIsNull() {
        assertNull(firstEpisodeToPlay(emptyList()))
    }
}
