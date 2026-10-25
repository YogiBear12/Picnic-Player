package app.picnic.player.ui.browse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavRailFocusTest {

    private val destinations = listOf(BrowseDest.Search, BrowseDest.Home, BrowseDest.Playlists)

    @Test
    fun firstRowUsesNextNeighbor() {
        assertEquals(BrowseDest.Home.key, panelNeighbor(destinations, BrowseDest.Search.key))
    }

    @Test
    fun middleRowUsesPreviousNeighbor() {
        assertEquals(BrowseDest.Search.key, panelNeighbor(destinations, BrowseDest.Home.key))
    }

    @Test
    fun lastRowUsesPreviousNeighbor() {
        assertEquals(BrowseDest.Home.key, panelNeighbor(destinations, BrowseDest.Playlists.key))
    }

    @Test
    fun onlyRowHasNoNeighbor() {
        assertNull(panelNeighbor(listOf(BrowseDest.Home), BrowseDest.Home.key))
    }

    @Test
    fun missingKeyHasNoNeighbor() {
        assertNull(panelNeighbor(destinations, BrowseDest.Discover.key))
    }

    @Test
    fun morePageTargetsBackRegardlessOfMoreVisibility() {
        listOf(false, true).forEach { moreVisible ->
            assertEquals(ChromeFocusTarget.MoreBack, pageFocusTarget(NavPanelPage.More, moreVisible, BrowseDest.Home.key))
        }
    }

    @Test
    fun primaryPageTargetsMoreWhenVisible() {
        assertEquals(ChromeFocusTarget.MoreItem, pageFocusTarget(NavPanelPage.Primary, true, BrowseDest.Home.key))
    }

    @Test
    fun primaryPageTargetsSelectedRowWithoutMore() {
        assertEquals(
            ChromeFocusTarget.Row(BrowseDest.Playlists.key),
            pageFocusTarget(NavPanelPage.Primary, false, BrowseDest.Playlists.key)
        )
    }
}
