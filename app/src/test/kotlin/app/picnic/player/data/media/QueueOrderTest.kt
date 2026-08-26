package app.picnic.player.data.media

import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueOrderTest {

    private fun entry(seed: Int) = BaseItemDto(
        id = UUID.nameUUIDFromBytes(byteArrayOf(seed.toByte())),
        type = BaseItemKind.MOVIE
    )

    private val entries = (1..12).map { entry(it) }

    private fun queue(shuffleSeed: Long? = null, position: Int = 0) = ItemQueue(parentId = UUID.randomUUID().toString(), kind = QueueKind.PLAYLIST, shuffleSeed = shuffleSeed, position = position)

    @Test
    fun noSeed_keepsPlaylistOrder() {
        assertEquals(entries, queue().order(entries))
    }

    @Test
    fun sameSeed_sameOrderEveryCall() {
        assertEquals(queue(shuffleSeed = 42L).order(entries), queue(shuffleSeed = 42L).order(entries))
    }

    @Test
    fun seed_shufflesAndKeepsEveryEntryOnce() {
        val shuffled = queue(shuffleSeed = 42L).order(entries)
        assertNotEquals(entries, shuffled)
        assertEquals(entries.toSet(), shuffled.toSet())
        assertEquals(entries.size, shuffled.size)
    }

    @Test
    fun itemAfter_followsPosition() {
        assertEquals(entries[1], queue(position = 0).itemAfter(entries))
        assertEquals(entries[5], queue(position = 4).itemAfter(entries))
    }

    @Test
    fun itemAfter_endsOnLastEntry() {
        assertNull(queue(position = entries.lastIndex).itemAfter(entries))
    }

    @Test
    fun duplicateEntries_advancePastTheFirstCopy() {
        val repeated = listOf(entry(1), entry(2), entry(1), entry(3))
        assertEquals(repeated[2], queue(position = 1).itemAfter(repeated))
        assertEquals(repeated[3], queue(position = 2).itemAfter(repeated))
    }

    @Test
    fun walkingADuplicateHeavyQueueTerminates() {
        val repeated = listOf(entry(1), entry(2), entry(1), entry(2))
        var queue = queue()
        var steps = 0
        while (queue.itemAfter(repeated) != null) {
            queue = queue.advanced()
            steps++
        }
        assertEquals(repeated.lastIndex, steps)
    }
}
