package app.picnic.player.data.paging

import org.junit.Assert.assertEquals
import org.junit.Test

class AlignedPageStartTest {
    @Test
    fun zero() {
        assertEquals(0, alignedPageStart(0, 50))
    }

    @Test
    fun midPage() {
        assertEquals(100, alignedPageStart(125, 50))
    }

    @Test
    fun lastOfPage() {
        assertEquals(100, alignedPageStart(149, 50))
    }

    @Test
    fun firstOfNextPage() {
        assertEquals(150, alignedPageStart(150, 50))
    }

    @Test
    fun refreshStartsOnePageBeforeTheAnchorPage() {
        assertEquals(50, refreshStart(149, 50))
        assertEquals(100, refreshStart(150, 50))
    }

    @Test
    fun refreshStartNeverGoesBelowZero() {
        assertEquals(0, refreshStart(10, 50))
    }
}
