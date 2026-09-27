package app.picnic.player.ui.detail

import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeStartIndexTest {
    private fun episode(played: Boolean, positionTicks: Long = 0L): BaseItemDto {
        val id = UUID.randomUUID()
        return BaseItemDto(
            id = id,
            type = BaseItemKind.EPISODE,
            userData = UserItemDataDto(
                playbackPositionTicks = positionTicks,
                playCount = 0,
                isFavorite = false,
                played = played,
                key = "test",
                itemId = id
            )
        )
    }

    @Test
    fun targetPresent() {
        val episodes = listOf(episode(false), episode(true), episode(false))
        assertEquals(1, episodeStartIndex(episodes, episodes[1].id.toString()))
    }

    @Test
    fun targetAbsentFallsToFirstUnwatched() {
        val episodes = listOf(episode(true), episode(true), episode(false))
        assertEquals(2, episodeStartIndex(episodes, UUID.randomUUID().toString()))
    }

    @Test
    fun partlyWatchedCountsAsResumable() {
        val episodes = listOf(episode(true), episode(true, positionTicks = 500L), episode(false))
        assertEquals(1, episodeStartIndex(episodes, null))
    }

    @Test
    fun allWatchedGivesZero() {
        assertEquals(0, episodeStartIndex(listOf(episode(true), episode(true)), null))
    }

    @Test
    fun emptyListGivesZero() {
        assertEquals(0, episodeStartIndex(emptyList(), null))
    }
}
