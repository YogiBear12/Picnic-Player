package app.picnic.player.ui.browse

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavDockSlotsTest {

    private fun library(title: String, vararg kinds: BaseItemKind) = BrowseDest.Library(UUID.randomUUID(), title, kinds.toList())

    @Test
    fun dockAlwaysHasFourSlots_profileSearchCurrentMenu() {
        val slots = navDockSlots(BrowseDest.Home)

        assertEquals(4, slots.size)
        assertEquals(NavDockSlot.Avatar, slots[0])
        assertEquals(BrowseDest.Search, (slots[1] as NavDockSlot.Destination).dest)
        assertEquals(BrowseDest.Home, (slots[2] as NavDockSlot.Destination).dest)
        assertEquals(NavDockSlot.Menu, slots[3])
    }

    @Test
    fun thirdSlotTracksSelectedDestination() {
        val movies = library("Movies", BaseItemKind.MOVIE)
        val third = navDockSlots(movies)[2] as NavDockSlot.Destination

        assertEquals(movies, third.dest)
        assertTrue(third.selected)
    }

    @Test
    fun onSearch_searchSlotIsSelected_andThirdSlotFallsBackToHome() {
        val slots = navDockSlots(BrowseDest.Search)
        val searchSlot = slots[1] as NavDockSlot.Destination
        val third = slots[2] as NavDockSlot.Destination

        assertTrue(searchSlot.selected)
        assertEquals(BrowseDest.Home, third.dest)
        assertEquals(false, third.selected)
    }

    @Test
    fun movieLibraryUsesMovieIcon_seriesLibraryUsesTvIcon_mixedFallsBack() {
        assertEquals(Icons.Filled.Movie, iconsFor(library("Movies", BaseItemKind.MOVIE)).first)
        assertEquals(Icons.Filled.Tv, iconsFor(library("Shows", BaseItemKind.SERIES)).first)
        assertEquals(
            Icons.Filled.VideoLibrary,
            iconsFor(library("Mixed", BaseItemKind.MOVIE, BaseItemKind.SERIES)).first
        )
    }

    @Test
    fun everyFixedDestinationHasItsOwnIconPair() {
        val fixed = listOf(
            BrowseDest.Search,
            BrowseDest.Home,
            BrowseDest.Discover,
            BrowseDest.Playlists
        )
        val filled = fixed.map { iconsFor(it).first }

        assertEquals(fixed.size, filled.distinct().size)
        fixed.forEach { dest ->
            val (filledIcon, outlinedIcon) = iconsFor(dest)
            assertTrue("${navLabelFor(dest)} reuses one icon", filledIcon !== outlinedIcon)
        }
    }
}
