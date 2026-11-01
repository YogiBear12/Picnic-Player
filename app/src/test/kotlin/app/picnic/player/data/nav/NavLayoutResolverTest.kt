package app.picnic.player.data.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavLayoutResolverTest {

    @Test
    fun firstRun_pinsAllLibraries_andDiscoverByDefault() {
        val layout = NavLayoutResolver.reconcile(
            availableIds = listOf("lib:a", "lib:b", NAV_ID_DISCOVER),
            saved = null,
            legacyDiscoverPinned = true
        )
        assertEquals(listOf("lib:a", "lib:b", NAV_ID_DISCOVER), layout.pinnedIds)
        assertTrue(layout.unpinnedIds.isEmpty())
    }

    @Test
    fun firstRun_legacyDiscoverOff_putsDiscoverInMore() {
        val layout = NavLayoutResolver.reconcile(
            availableIds = listOf("lib:a", NAV_ID_DISCOVER),
            saved = null,
            legacyDiscoverPinned = false
        )
        assertEquals(listOf("lib:a"), layout.pinnedIds)
        assertEquals(listOf(NAV_ID_DISCOVER), layout.unpinnedIds)
    }

    @Test
    fun firstRun_legacyDiscoverOff_beforeSeerrLinks_staysUnpinnedOnceLinked() {
        val first = NavLayoutResolver.reconcile(listOf("lib:a"), saved = null, legacyDiscoverPinned = false)
        val linked = NavLayoutResolver.reconcile(listOf("lib:a", NAV_ID_DISCOVER), first)
        assertEquals(listOf("lib:a"), linked.pinnedIds)
        assertEquals(listOf(NAV_ID_DISCOVER), linked.unpinnedIds)
    }

    @Test
    fun newLibrary_appendedAfterHiddenIds() {
        val saved = NavLayout(pinnedIds = listOf("lib:a", NAV_ID_DISCOVER))
        val layout = NavLayoutResolver.reconcile(listOf("lib:a", "lib:b"), saved)
        assertEquals(listOf("lib:a", NAV_ID_DISCOVER, "lib:b"), layout.pinnedIds)
    }

    @Test
    fun newLibrary_appendedToPinned() {
        val saved = NavLayout(pinnedIds = listOf("lib:a"), unpinnedIds = listOf(NAV_ID_DISCOVER))
        val layout = NavLayoutResolver.reconcile(
            availableIds = listOf("lib:a", "lib:b", NAV_ID_DISCOVER),
            saved = saved
        )
        assertEquals(listOf("lib:a", "lib:b"), layout.pinnedIds)
        assertEquals(listOf(NAV_ID_DISCOVER), layout.unpinnedIds)
    }

    @Test
    fun unavailableIds_keptInSavedPositions() {
        val saved = NavLayout(
            pinnedIds = listOf("lib:a", "lib:gone"),
            unpinnedIds = listOf("lib:also-gone", NAV_ID_DISCOVER)
        )
        val layout = NavLayoutResolver.reconcile(
            availableIds = listOf("lib:a", NAV_ID_DISCOVER),
            saved = saved
        )
        assertEquals(saved, layout)
    }

    @Test
    fun pinnedDiscover_returnsToItsIndex_afterBeingUnavailable() {
        val saved = NavLayout(pinnedIds = listOf("lib:a", NAV_ID_DISCOVER, "lib:b"))
        val without = NavLayoutResolver.reconcile(listOf("lib:a", "lib:b"), saved)
        val back = NavLayoutResolver.reconcile(listOf("lib:a", "lib:b", NAV_ID_DISCOVER), without)
        assertEquals(listOf("lib:a", NAV_ID_DISCOVER, "lib:b"), back.pinnedIds)
    }

    @Test
    fun unpinnedDiscover_staysUnpinned_afterBeingUnavailable() {
        val saved = NavLayout(pinnedIds = listOf("lib:a"), unpinnedIds = listOf(NAV_ID_DISCOVER))
        val without = NavLayoutResolver.reconcile(listOf("lib:a"), saved)
        val back = NavLayoutResolver.reconcile(listOf("lib:a", NAV_ID_DISCOVER), without)
        assertEquals(listOf("lib:a"), back.pinnedIds)
        assertEquals(listOf(NAV_ID_DISCOVER), back.unpinnedIds)
    }

    @Test
    fun pin_movesFromUnpinnedToEndOfPinned() {
        val start = NavLayout(pinnedIds = listOf("lib:a"), unpinnedIds = listOf("lib:b"))
        val layout = NavLayoutResolver.pin(start, "lib:b")
        assertEquals(listOf("lib:a", "lib:b"), layout.pinnedIds)
        assertTrue(layout.unpinnedIds.isEmpty())
        assertTrue(layout.isPinned("lib:b"))
    }

    @Test
    fun unpin_movesFromPinnedToEndOfUnpinned() {
        val start = NavLayout(pinnedIds = listOf("lib:a", "lib:b"), unpinnedIds = emptyList())
        val layout = NavLayoutResolver.unpin(start, "lib:a")
        assertEquals(listOf("lib:b"), layout.pinnedIds)
        assertEquals(listOf("lib:a"), layout.unpinnedIds)
        assertFalse(layout.isPinned("lib:a"))
    }

    @Test
    fun move_swapsWithinPinnedOnly() {
        val start = NavLayout(pinnedIds = listOf("a", "b", "c"), unpinnedIds = listOf("d"))
        val down = NavLayoutResolver.move(start, "a", 1) { true }
        assertEquals(listOf("b", "a", "c"), down.pinnedIds)
        assertEquals(listOf("d"), down.unpinnedIds)
        val up = NavLayoutResolver.move(down, "c", -1) { true }
        assertEquals(listOf("b", "c", "a"), up.pinnedIds)
    }

    @Test
    fun move_skipsIdsThatAreNotShown() {
        val start = NavLayout(pinnedIds = listOf("a", NAV_ID_DISCOVER, "b"))
        val moved = NavLayoutResolver.move(start, "b", -1) { it != NAV_ID_DISCOVER }
        assertEquals(listOf("b", NAV_ID_DISCOVER, "a"), moved.pinnedIds)
    }

    @Test
    fun firstRun_putsWatchTogetherInMore() {
        val layout = NavLayoutResolver.reconcile(listOf("lib:a", NAV_ID_WATCH_TOGETHER, NAV_ID_DISCOVER), saved = null)
        assertEquals(listOf("lib:a", NAV_ID_DISCOVER), layout.pinnedIds)
        assertEquals(listOf(NAV_ID_WATCH_TOGETHER), layout.unpinnedIds)
    }

    @Test
    fun firstRun_legacyDiscoverOff_putsBothInMore() {
        val layout = NavLayoutResolver.reconcile(listOf("lib:a", NAV_ID_WATCH_TOGETHER), saved = null, legacyDiscoverPinned = false)
        assertEquals(listOf("lib:a"), layout.pinnedIds)
        assertEquals(listOf(NAV_ID_DISCOVER, NAV_ID_WATCH_TOGETHER), layout.unpinnedIds)
    }

    @Test
    fun newWatchTogether_goesToEndOfMore() {
        val saved = NavLayout(pinnedIds = listOf("lib:a"), unpinnedIds = listOf(NAV_ID_DISCOVER))
        val layout = NavLayoutResolver.reconcile(listOf("lib:a", "lib:b", NAV_ID_WATCH_TOGETHER, NAV_ID_DISCOVER), saved)
        assertEquals(listOf("lib:a", "lib:b"), layout.pinnedIds)
        assertEquals(listOf(NAV_ID_DISCOVER, NAV_ID_WATCH_TOGETHER), layout.unpinnedIds)
    }

    @Test
    fun pinnedWatchTogether_staysPinned() {
        val saved = NavLayout(pinnedIds = listOf("lib:a", NAV_ID_WATCH_TOGETHER))
        val layout = NavLayoutResolver.reconcile(listOf("lib:a", NAV_ID_WATCH_TOGETHER), saved)
        assertEquals(saved, layout)
    }

    @Test
    fun pinnedWatchTogether_keepsItsPin_afterLosingAccess() {
        val saved = NavLayout(pinnedIds = listOf(NAV_ID_WATCH_TOGETHER, "lib:a"))
        val without = NavLayoutResolver.reconcile(listOf("lib:a"), saved)
        val back = NavLayoutResolver.reconcile(listOf("lib:a", NAV_ID_WATCH_TOGETHER), without)
        assertEquals(saved, back)
    }
}
