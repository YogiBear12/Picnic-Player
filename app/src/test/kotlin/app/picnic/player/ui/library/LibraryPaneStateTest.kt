package app.picnic.player.ui.library

import app.picnic.player.ui.library.LibraryPaneViewModel.UiState
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryPaneStateTest {
    @Test
    fun losingCollectionsWhileOnTheTabFallsBackToLibraryAndAsksForFocus() {
        val state = UiState(selectedTab = LibraryTab.COLLECTIONS, collectionsAvailable = true)

        val next = state.withCollectionsAvailable(false)

        assertEquals(LibraryTab.LIBRARY, next.selectedTab)
        assertEquals(1, next.tabFocusNonce)
        assertEquals(false, LibraryTab.COLLECTIONS in next.tabs)
    }

    @Test
    fun losingCollectionsOnAnotherTabKeepsTheSelectionAndFocus() {
        val state = UiState(selectedTab = LibraryTab.GENRES, collectionsAvailable = true)

        val next = state.withCollectionsAvailable(false)

        assertEquals(LibraryTab.GENRES, next.selectedTab)
        assertEquals(0, next.tabFocusNonce)
        assertEquals(false, next.collectionsAvailable)
    }

    @Test
    fun gainingCollectionsOnlyAddsTheTab() {
        val state = UiState(selectedTab = LibraryTab.FOR_YOU)

        val next = state.withCollectionsAvailable(true)

        assertEquals(LibraryTab.FOR_YOU, next.selectedTab)
        assertEquals(0, next.tabFocusNonce)
        assertEquals(LibraryTab.entries.toList(), next.tabs)
    }
}
