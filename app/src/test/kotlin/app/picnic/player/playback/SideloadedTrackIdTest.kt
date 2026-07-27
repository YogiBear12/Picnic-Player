package app.picnic.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SideloadedTrackIdTest {

    @Test
    fun roundTripsTheStreamIndex() {
        assertEquals(24, SideloadedTrackId.indexOf(SideloadedTrackId.of(24)))
    }

    @Test
    fun recognisesOnlyItsOwnIds() {
        assertTrue(SideloadedTrackId.isSideloaded("e:7"))
        assertFalse(SideloadedTrackId.isSideloaded("1:2"))
        assertFalse(SideloadedTrackId.isSideloaded(null))
    }

    @Test
    fun ignoresTrackNamesThatMerelyContainThePrefix() {
        assertNull(SideloadedTrackId.indexOf("Commentary e:xtras"))
        assertFalse(SideloadedTrackId.isSideloaded("Commentary e:xtras"))
    }
}
