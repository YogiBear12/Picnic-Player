package app.picnic.player.ui.navigation

import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenItemTest {
    private val itemId = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    private val seriesId = UUID.fromString("00000000-0000-0000-0000-0000000000b1")
    private val seasonId = UUID.fromString("00000000-0000-0000-0000-0000000000c1")

    private fun item(kind: BaseItemKind, series: UUID? = null, season: UUID? = null) = BaseItemDto(
        id = itemId,
        type = kind,
        seriesId = series,
        seasonId = season
    )

    private fun openedKey(item: BaseItemDto): Any? {
        val viewModel = AppNavigationViewModel()
        viewModel.openItem(item, bgUrl = "bg", ambUrl = "amb")
        return viewModel.backStack.lastOrNull()
    }

    private fun assertDetailKey(key: Any?) {
        val detail = key as DetailKey
        assertEquals(itemId.toString(), detail.itemId)
        assertEquals("bg", detail.bgUrl)
        assertEquals("amb", detail.ambUrl)
    }

    @Test
    fun `a movie opens its detail screen`() {
        assertDetailKey(openedKey(item(BaseItemKind.MOVIE)))
    }

    @Test
    fun `a series opens its detail screen`() {
        assertDetailKey(openedKey(item(BaseItemKind.SERIES)))
    }

    @Test
    fun `an episode opens its series episode list focused on itself`() {
        val key = openedKey(item(BaseItemKind.EPISODE, series = seriesId, season = seasonId))
        assertEquals(
            EpisodesKey(
                seriesId = seriesId.toString(),
                ambUrl = "amb",
                focusEpisodeId = itemId.toString(),
                seasonId = seasonId.toString()
            ),
            key
        )
    }

    @Test
    fun `an episode with no series falls back to detail`() {
        assertDetailKey(openedKey(item(BaseItemKind.EPISODE)))
    }

    @Test
    fun `an episode with no season still opens the episode list`() {
        val key = openedKey(item(BaseItemKind.EPISODE, series = seriesId))
        assertEquals(
            EpisodesKey(
                seriesId = seriesId.toString(),
                ambUrl = "amb",
                focusEpisodeId = itemId.toString(),
                seasonId = null
            ),
            key
        )
    }
}
