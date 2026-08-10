package app.picnic.player.playback

object MatroskaTracksLocator {
    private const val ID_SEGMENT = 0x18538067L
    private const val ID_SEEK_HEAD = 0x114D9B74L
    private const val ID_SEEK = 0x4DBBL
    private const val ID_SEEK_ID = 0x53ABL
    private const val ID_SEEK_POSITION = 0x53ACL
    private const val ID_TRACKS = 0x1654AE6BL
    private const val ID_CLUSTER = 0x1F43B675L

    /** Offsets are absolute: [buffer] must start at byte zero of the file. */
    fun lateTracksPosition(buffer: ByteArray, length: Int): Long? {
        val cursor = Cursor(buffer, length)
        while (true) {
            val id = cursor.readId() ?: return null
            val size = cursor.readSize() ?: return null
            if (id == ID_SEGMENT) break
            if (!cursor.skip(size)) return null
        }
        val segmentDataStart = cursor.position.toLong()
        var tracksPosition: Long? = null
        while (cursor.position < length) {
            val id = cursor.readId() ?: return null
            val size = cursor.readSize() ?: return null
            when (id) {
                ID_TRACKS -> return null
                ID_CLUSTER -> return tracksPosition
                ID_SEEK_HEAD -> tracksPosition = parseSeekHead(cursor, cursor.position + size, segmentDataStart) ?: return null
                else -> if (!cursor.skip(size)) return null
            }
        }
        return null
    }

    private fun parseSeekHead(cursor: Cursor, end: Long, segmentDataStart: Long): Long? {
        var tracksPosition: Long? = null
        while (cursor.position < end) {
            val id = cursor.readId() ?: return null
            val size = cursor.readSize() ?: return null
            if (id != ID_SEEK) {
                if (!cursor.skip(size)) return null
                continue
            }
            val entryEnd = cursor.position + size
            var seekId: Long? = null
            var seekPosition: Long? = null
            while (cursor.position < entryEnd) {
                val childId = cursor.readId() ?: return null
                val childSize = cursor.readSize() ?: return null
                when (childId) {
                    ID_SEEK_ID -> seekId = cursor.readUnsigned(childSize) ?: return null
                    ID_SEEK_POSITION -> seekPosition = cursor.readUnsigned(childSize) ?: return null
                    else -> if (!cursor.skip(childSize)) return null
                }
            }
            if (seekId == ID_TRACKS && seekPosition != null) tracksPosition = segmentDataStart + seekPosition
        }
        return tracksPosition
    }

    private class Cursor(private val buffer: ByteArray, private val limit: Int) {
        var position: Int = 0
            private set

        fun readId(): Long? {
            val first = byteAt(position) ?: return null
            val width = leadingWidth(first) ?: return null
            if (width > 4) return null
            var value = 0L
            for (i in 0 until width) {
                value = (value shl 8) or (byteAt(position + i)?.toLong() ?: return null)
            }
            position += width
            return value
        }

        fun readSize(): Long? {
            val first = byteAt(position) ?: return null
            val width = leadingWidth(first) ?: return null
            if (width > 8) return null
            val valueMask = (0x80 shr (width - 1)) - 1
            var value = (first and valueMask).toLong()
            var allOnes = value == valueMask.toLong()
            for (i in 1 until width) {
                val byte = byteAt(position + i) ?: return null
                allOnes = allOnes && byte == 0xFF
                value = (value shl 8) or byte.toLong()
            }
            position += width
            return if (allOnes) null else value
        }

        fun readUnsigned(size: Long): Long? {
            if (size <= 0 || size > 8) return null
            var value = 0L
            for (i in 0 until size.toInt()) {
                value = (value shl 8) or (byteAt(position + i)?.toLong() ?: return null)
            }
            position += size.toInt()
            return value
        }

        fun skip(size: Long): Boolean {
            val target = position + size
            if (size < 0 || target > limit) return false
            position = target.toInt()
            return true
        }

        private fun byteAt(index: Int): Int? = if (index < 0 || index >= limit) null else buffer[index].toInt() and 0xFF

        private fun leadingWidth(first: Int): Int? {
            if (first == 0) return null
            var mask = 0x80
            var width = 1
            while (first and mask == 0) {
                mask = mask shr 1
                width++
            }
            return width
        }
    }
}
