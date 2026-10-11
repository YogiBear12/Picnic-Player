package app.picnic.player.ui.navigation

import androidx.compose.ui.geometry.Rect
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardOriginsTest {
    private val nav = AppNavigationViewModel()
    private val origins = nav.cardOrigins
    private fun origin(name: String) = CardOrigin(Rect(0f, 0f, 10f, 15f), "bg-$name", "card-$name")
    private fun item(kind: BaseItemKind = BaseItemKind.MOVIE) = BaseItemDto(id = UUID.randomUUID(), type = kind)
    private fun topDetails() = nav.backStack.last() as DetailKey

    @Test
    fun cardClickBindsToThePushedDetails() {
        origins.click(origin("a")) { nav.openItem(item(), ambUrl = null) }
        assertEquals(origin("a"), origins.originOf(topDetails()))
    }

    @Test
    fun anOpenOutsideTheClickHasNoOrigin() {
        origins.click(origin("a")) { nav.push(PersonKey(tmdbId = 1)) }
        nav.openItem(item(), ambUrl = null)
        assertNull(origins.originOf(topDetails()))
    }

    @Test
    fun detailsThatAreNotFullscreenHaveNoOrigin() {
        origins.click(origin("a")) { nav.openItem(item(BaseItemKind.MUSIC_ALBUM), ambUrl = null) }
        assertNull(origins.originOf(topDetails()))
    }

    @Test
    fun stackedOpensKeepTheirOwnOrigins() {
        origins.click(origin("a")) { nav.openItem(item(), ambUrl = null) }
        val first = topDetails()
        origins.click(origin("b")) { nav.openItem(item(), ambUrl = null) }
        val second = topDetails()
        nav.pop()
        origins.click(origin("c")) { nav.openItem(item(), ambUrl = null) }
        assertEquals(origin("a"), origins.originOf(first))
        assertNull(origins.originOf(second))
    }

    @Test
    fun onlyTheFirstShowGrows() {
        origins.click(origin("a")) { nav.openItem(item(), ambUrl = null) }
        val details = topDetails()
        assertFalse(origins.wasShown(details))
        origins.markShown(details)
        assertTrue(origins.wasShown(details))
    }
}
