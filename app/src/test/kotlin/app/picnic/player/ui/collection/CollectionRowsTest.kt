package app.picnic.player.ui.collection

import app.picnic.player.data.media.CollectionSection
import app.picnic.player.data.media.NON_VIDEO_KINDS
import app.picnic.player.data.media.PLAYABLE_KINDS
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionRowsTest {

    private var seed = 0

    private fun item(kind: BaseItemKind) = BaseItemDto(
        id = UUID.nameUUIDFromBytes(byteArrayOf((seed++).toByte())),
        type = kind
    )

    @Test
    fun emptyCollection_hasNoRows() {
        assertEquals(emptyList<CollectionRow>(), collectionRows(emptyList()))
    }

    @Test
    fun rowsFollowSectionOrder_regardlessOfChildOrder() {
        val children = listOf(
            item(BaseItemKind.BOX_SET),
            item(BaseItemKind.EPISODE),
            item(BaseItemKind.MOVIE),
            item(BaseItemKind.SEASON),
            item(BaseItemKind.SERIES),
            item(BaseItemKind.VIDEO),
            item(BaseItemKind.TRAILER)
        )
        assertEquals(CollectionSection.entries, collectionRows(children).map { it.section })
    }

    @Test
    fun musicVideos_joinTheVideosRow() {
        val video = item(BaseItemKind.VIDEO)
        val musicVideo = item(BaseItemKind.MUSIC_VIDEO)
        val rows = collectionRows(listOf(video, musicVideo))
        assertEquals(listOf(CollectionSection.VIDEOS), rows.map { it.section })
        assertEquals(listOf(video, musicVideo), rows.single().items)
    }

    @Test
    fun kindsWithoutASection_fallToOtherItems() {
        val trailer = item(BaseItemKind.TRAILER)
        val recording = item(BaseItemKind.RECORDING)
        val rows = collectionRows(listOf(item(BaseItemKind.MOVIE), trailer, recording))
        assertEquals(listOf(CollectionSection.MOVIES, CollectionSection.OTHER), rows.map { it.section })
        assertEquals(listOf(trailer, recording), rows.last().items)
    }

    @Test
    fun nonVideoKindsAreExcludedAtTheSource() {
        assertTrue(NON_VIDEO_KINDS.none { kind -> CollectionSection.entries.any { kind in it.kinds } })
    }

    @Test
    fun onlyEpisodeRowUsesLandscapeCards() {
        val rows = collectionRows(listOf(item(BaseItemKind.EPISODE), item(BaseItemKind.MOVIE)))
        assertEquals(
            listOf(CollectionSection.EPISODES),
            rows.filter { it.section.landscape }.map { it.section }
        )
    }

    @Test
    fun everySectionKindIsListedOnce() {
        val kinds = CollectionSection.entries.flatMap { it.kinds }
        assertEquals(kinds.size, kinds.toSet().size)
    }

    @Test
    fun foldersAreNotQueuedForPlayback() {
        assertTrue(BaseItemKind.MOVIE in PLAYABLE_KINDS)
        assertTrue(BaseItemKind.EPISODE in PLAYABLE_KINDS)
        assertFalse(BaseItemKind.SERIES in PLAYABLE_KINDS)
        assertFalse(BaseItemKind.SEASON in PLAYABLE_KINDS)
        assertFalse(BaseItemKind.BOX_SET in PLAYABLE_KINDS)
    }
}
