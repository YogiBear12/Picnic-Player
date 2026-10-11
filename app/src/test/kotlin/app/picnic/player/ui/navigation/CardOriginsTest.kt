package app.picnic.player.ui.navigation

import androidx.compose.ui.geometry.Rect
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardOriginsTest {
    private val below: NavKey = PlayerKey("below")
    private fun origin(itemId: String) = CardOrigin(itemId, Rect(0f, 0f, 10f, 15f), "bg-$itemId", "card-$itemId")

    @Test
    fun cardClickBindsToThePushedDetails() {
        val origins = CardOrigins()
        val details = DetailKey("a")
        origins.sync(listOf(below))
        origins.report(origin("a"))
        origins.sync(listOf(below, details))
        assertEquals(origin("a"), origins.originOf(details))
    }

    @Test
    fun anOpenWithoutAClickHasNoOrigin() {
        val origins = CardOrigins()
        origins.sync(listOf(below))
        origins.report(origin("a"))
        origins.sync(listOf(below, PersonKey(tmdbId = 1)))
        val later = DetailKey("a")
        origins.sync(listOf(below, PersonKey(tmdbId = 1), later))
        assertNull(origins.originOf(later))
    }

    @Test
    fun stackedOpensKeepTheirOwnOrigins() {
        val origins = CardOrigins()
        val first = DetailKey("a")
        val second = DetailKey("b")
        origins.sync(listOf(below))
        origins.report(origin("a"))
        origins.sync(listOf(below, first))
        origins.report(origin("b"))
        origins.sync(listOf(below, first, second))
        origins.sync(listOf(below, first))
        assertEquals(origin("a"), origins.originOf(first))
        assertNull(origins.originOf(second))
    }

    @Test
    fun onlyTheFirstShowGrows() {
        val origins = CardOrigins()
        val details = DetailKey("a")
        origins.sync(listOf(below, details))
        assertFalse(origins.wasShown(details))
        origins.markShown(details)
        assertTrue(origins.wasShown(details))
    }
}
