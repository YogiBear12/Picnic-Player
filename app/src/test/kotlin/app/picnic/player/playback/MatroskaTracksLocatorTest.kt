package app.picnic.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatroskaTracksLocatorTest {
    private val ebmlHeader = element(0x1A45DFA3L, ByteArray(31))
    private val cluster = element(0x1F43B675L, ByteArray(64))

    @Test
    fun findsTracksWrittenAfterTheClusters() {
        val seekHead = element(
            0x114D9B74L,
            seekEntry(0x1549A966L, 200L) + seekEntry(0x1654AE6BL, 174_322_454L) + seekEntry(0x1C53BB6BL, 174_294_876L)
        )
        val segmentContent = seekHead + element(0xECL, ByteArray(61)) + cluster
        val file = ebmlHeader + element(0x18538067L, segmentContent)

        val segmentDataStart = ebmlHeader.size + headerLength(0x18538067L, segmentContent.size)
        assertEquals(segmentDataStart + 174_322_454L, MatroskaTracksLocator.lateTracksPosition(file, file.size))
    }

    @Test
    fun anOrdinaryFileNeedsNoRecovery() {
        val seekHead = element(0x114D9B74L, seekEntry(0x1654AE6BL, 300L))
        val tracks = element(0x1654AE6BL, ByteArray(48))
        val file = ebmlHeader + element(0x18538067L, seekHead + tracks + cluster)

        assertNull(MatroskaTracksLocator.lateTracksPosition(file, file.size))
    }

    @Test
    fun aFileWithNoClusterInTheWindowNeedsNoRecovery() {
        val seekHead = element(0x114D9B74L, seekEntry(0x1654AE6BL, 174_322_454L))
        val file = ebmlHeader + element(0x18538067L, seekHead)

        assertNull(MatroskaTracksLocator.lateTracksPosition(file, file.size))
    }

    @Test
    fun lateClustersWithoutASeekHeadEntryCannotBeRecovered() {
        val seekHead = element(0x114D9B74L, seekEntry(0x1C53BB6BL, 900L))
        val file = ebmlHeader + element(0x18538067L, seekHead + cluster)

        assertNull(MatroskaTracksLocator.lateTracksPosition(file, file.size))
    }

    @Test
    fun aSeekHeadBeyondTheScanWindowIsNotGuessedAt() {
        val seekHead = element(0x114D9B74L, seekEntry(0x1654AE6BL, 174_322_454L))
        val file = ebmlHeader + element(0x18538067L, element(0xECL, ByteArray(4000)) + seekHead + cluster)

        assertNull(MatroskaTracksLocator.lateTracksPosition(file, 1024))
    }

    @Test
    fun aFileWithNoSegmentRecoversNothing() {
        assertNull(MatroskaTracksLocator.lateTracksPosition(ebmlHeader, ebmlHeader.size))
    }

    @Test
    fun anUnknownSizeElementRecoversNothing() {
        val file = ebmlHeader + byteArrayOf(0x18, 0x53, 0x80.toByte(), 0x67, 0xFF.toByte())

        assertNull(MatroskaTracksLocator.lateTracksPosition(file, file.size))
    }

    @Test
    fun aTruncatedSeekEntryRecoversNothing() {
        val file = ebmlHeader + element(0x18538067L, element(0x114D9B74L, byteArrayOf(0x4D, 0xBB.toByte())))

        assertNull(MatroskaTracksLocator.lateTracksPosition(file, file.size))
    }

    private fun seekEntry(seekId: Long, position: Long): ByteArray = element(
        0x4DBBL,
        element(0x53ABL, idBytes(seekId)) + element(0x53ACL, unsignedBytes(position))
    )

    private fun element(id: Long, content: ByteArray): ByteArray = idBytes(id) + sizeBytes(content.size.toLong()) + content

    private fun headerLength(id: Long, contentSize: Int): Int = idBytes(id).size + sizeBytes(contentSize.toLong()).size

    private fun idBytes(id: Long): ByteArray {
        val bytes = mutableListOf<Byte>()
        var remaining = id
        while (remaining > 0) {
            bytes.add(0, (remaining and 0xFF).toByte())
            remaining = remaining shr 8
        }
        return bytes.toByteArray()
    }

    private fun unsignedBytes(value: Long): ByteArray {
        if (value == 0L) return byteArrayOf(0)
        val bytes = mutableListOf<Byte>()
        var remaining = value
        while (remaining > 0) {
            bytes.add(0, (remaining and 0xFF).toByte())
            remaining = remaining shr 8
        }
        return bytes.toByteArray()
    }

    private fun sizeBytes(size: Long): ByteArray = if (size <= 0x7E) {
        byteArrayOf((0x80 or size.toInt()).toByte())
    } else {
        byteArrayOf(
            (0x10 or ((size shr 24).toInt() and 0x0F)).toByte(),
            ((size shr 16) and 0xFF).toByte(),
            ((size shr 8) and 0xFF).toByte(),
            (size and 0xFF).toByte()
        )
    }
}
