package app.picnic.player.ui.detail

import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailPlayTargetTest {
    private val movieId = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    private val seriesId = UUID.fromString("00000000-0000-0000-0000-0000000000b1")
    private val episodeId = UUID.fromString("00000000-0000-0000-0000-0000000000c1")

    private fun item(
        id: UUID,
        kind: BaseItemKind,
        positionTicks: Long? = null,
        season: Int? = null,
        episode: Int? = null
    ) = BaseItemDto(
        id = id,
        type = kind,
        parentIndexNumber = season,
        indexNumber = episode,
        userData = positionTicks?.let {
            UserItemDataDto(
                playbackPositionTicks = it,
                playCount = 0,
                isFavorite = false,
                played = false,
                key = id.toString(),
                itemId = id
            )
        }
    )

    @Test
    fun `unwatched movie plays itself`() {
        val target = detailPlayTarget(item(movieId, BaseItemKind.MOVIE), nextUpEpisode = null)
        assertEquals(movieId.toString(), target.itemId)
        assertNull(target.resumeTicks)
        assertEquals("Play", target.title)
    }

    @Test
    fun `part watched movie resumes`() {
        val target = detailPlayTarget(item(movieId, BaseItemKind.MOVIE, positionTicks = 5_000), nextUpEpisode = null)
        assertEquals(5_000L, target.resumeTicks)
        assertEquals("Resume", target.title)
    }

    @Test
    fun `zero position is not a resume`() {
        val target = detailPlayTarget(item(movieId, BaseItemKind.MOVIE, positionTicks = 0), nextUpEpisode = null)
        assertNull(target.resumeTicks)
        assertEquals("Play", target.title)
    }

    @Test
    fun `series plays its next up episode`() {
        val target = detailPlayTarget(
            item(seriesId, BaseItemKind.SERIES),
            item(episodeId, BaseItemKind.EPISODE, season = 2, episode = 5)
        )
        assertEquals(episodeId.toString(), target.itemId)
        assertEquals("Play S2 E5", target.title)
    }

    @Test
    fun `series resumes a part watched episode`() {
        val target = detailPlayTarget(
            item(seriesId, BaseItemKind.SERIES),
            item(episodeId, BaseItemKind.EPISODE, positionTicks = 900, season = 2, episode = 5)
        )
        assertEquals(900L, target.resumeTicks)
        assertEquals("Resume S2 E5", target.title)
    }

    @Test
    fun `specials label as season zero`() {
        val target = detailPlayTarget(
            item(seriesId, BaseItemKind.SERIES),
            item(episodeId, BaseItemKind.EPISODE, season = 0, episode = 3)
        )
        assertEquals("Play S0 E3", target.title)
    }

    @Test
    fun `series with no next up falls back to itself`() {
        val target = detailPlayTarget(item(seriesId, BaseItemKind.SERIES), nextUpEpisode = null)
        assertEquals(seriesId.toString(), target.itemId)
        assertEquals("Play", target.title)
    }

    @Test
    fun `a next up episode is ignored for a movie`() {
        val target = detailPlayTarget(
            item(movieId, BaseItemKind.MOVIE),
            item(episodeId, BaseItemKind.EPISODE, season = 1, episode = 1)
        )
        assertEquals(movieId.toString(), target.itemId)
        assertEquals("Play", target.title)
    }
}
