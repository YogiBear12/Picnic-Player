package app.picnic.player.data.media

import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemKindsTest {

    @Test
    fun containersAreNotPlayable() {
        assertTrue(BaseItemKind.MOVIE in PLAYABLE_KINDS)
        assertTrue(BaseItemKind.EPISODE in PLAYABLE_KINDS)
        assertFalse(BaseItemKind.SERIES in PLAYABLE_KINDS)
        assertFalse(BaseItemKind.SEASON in PLAYABLE_KINDS)
        assertFalse(BaseItemKind.BOX_SET in PLAYABLE_KINDS)
    }

    @Test
    fun playableAndNonVideoKindsDoNotOverlap() {
        assertTrue(PLAYABLE_KINDS.none { it in NON_VIDEO_KINDS })
    }
}
