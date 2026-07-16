package app.picnic.player.data.seerr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeerrPlayBridgeTest {

    private val dashed = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    private val undashed = "a1b2c3d4e5f67890abcdef1234567890"
    private val fourKDashed = "b2c3d4e5-f6a7-8901-bcde-f23456789012"
    private val fourKUndashed = "b2c3d4e5f6a78901bcdef23456789012"

    @Test
    fun gate_hdDashedId_opensJellyfin() {
        assertEquals(DetailTarget.Jellyfin(dashed), seerrDetailTarget(dashed))
        assertEquals(dashed, jellyfinDetailIdOrNull(dashed))
        assertTrue(shouldOpenJellyfinDetail(dashed))
    }

    @Test
    fun gate_hdUndashedId_insertsDashes() {
        assertEquals(DetailTarget.Jellyfin(dashed), seerrDetailTarget(undashed))
        assertEquals(dashed, jellyfinDetailIdOrNull(undashed))
    }

    @Test
    fun gate_noId_opensSeerr() {
        assertEquals(DetailTarget.Seerr, seerrDetailTarget(null, null))
        assertNull(jellyfinDetailIdOrNull(null, null))
        assertNull(jellyfinDetailIdOrNull("", ""))
        assertNull(jellyfinDetailIdOrNull("   ", "   "))
        assertFalse(shouldOpenJellyfinDetail("   ", null))
    }

    @Test
    fun gate_invalidId_opensSeerr() {
        assertNull(jellyfinDetailIdOrNull("jf-abc"))
        assertNull(jellyfinDetailIdOrNull("not-a-uuid"))
        assertNull(jellyfinDetailIdOrNull("gggggggggggggggggggggggggggggggg"))
        assertFalse(shouldOpenJellyfinDetail("jf-abc"))
    }

    // 4K-only in library counts as in-library — opens Jellyfin Detail everywhere.
    @Test
    fun gate_fourKOnly_opensJellyfin() {
        assertEquals(DetailTarget.Jellyfin(fourKDashed), seerrDetailTarget(null, fourKUndashed))
        assertEquals(fourKDashed, jellyfinDetailIdOrNull(null, fourKUndashed))
        assertTrue(shouldOpenJellyfinDetail(null, fourKUndashed))
        assertTrue(shouldOpenJellyfinDetail("   ", fourKDashed))
    }

    // Both present (un-merged same-TMDB entries): v1 prefers HD.
    @Test
    fun gate_bothIds_prefersHd() {
        assertEquals(DetailTarget.Jellyfin(dashed), seerrDetailTarget(undashed, fourKUndashed))
        assertEquals(dashed, jellyfinDetailIdOrNull(undashed, fourKUndashed))
    }

    // Invalid HD but valid 4K still opens Jellyfin via the 4K fallback.
    @Test
    fun gate_invalidHdValidFourK_fallsBackToFourK() {
        assertEquals(fourKDashed, jellyfinDetailIdOrNull("jf-abc", fourKUndashed))
    }

    @Test
    fun normalizeJellyfinUuid_dashedUndashedBlankInvalid() {
        assertEquals(dashed, normalizeJellyfinUuid(dashed))
        assertEquals(dashed, normalizeJellyfinUuid(undashed))
        assertEquals(dashed, normalizeJellyfinUuid("  $undashed  "))
        assertNull(normalizeJellyfinUuid(null))
        assertNull(normalizeJellyfinUuid(""))
        assertNull(normalizeJellyfinUuid("   "))
        assertNull(normalizeJellyfinUuid("jf-abc"))
        assertNull(normalizeJellyfinUuid("1234"))
    }
}
