package app.picnic.player.data.playback

import androidx.media3.common.PlaybackException

/**
 * Whether the original file is still worth asking for, for the item being watched.
 *
 * Some containers the server is happy to hand over cannot be read here — a Matroska track
 * compressed with zlib (`ContentCompAlgo 0`) is the common one, since media3 implements only
 * header stripping. The failure lands while parsing the container, before any decoder starts, so
 * no track selection avoids it and the same bytes will fail every time. Asking the server for the
 * same streams in a fresh container clears it without re-encoding the video.
 *
 * Once vetoed, it stays vetoed for the rest of the viewing: every later renegotiation — a subtitle
 * pick, an audio pick, a quality change — must keep direct play off, or the server hands back the
 * file that just failed. That is why callers read [allowsDirectPlay] rather than deciding
 * per-request.
 */
class DirectPlayVeto {

    private var vetoed = false

    val allowsDirectPlay: Boolean get() = !vetoed

    /**
     * Records a playback failure.
     *
     * @return true if the item should be renegotiated without direct play. False for anything a
     * fresh container cannot fix — a decoder or network failure is not a container problem, and
     * retrying it would only delay the error the user needs to see — and false the second time,
     * since a remux that fails too is a real error.
     */
    fun onPlaybackError(errorCode: Int): Boolean {
        if (vetoed || !isContainerUnreadable(errorCode)) return false
        vetoed = true
        return true
    }

    private fun isContainerUnreadable(errorCode: Int): Boolean = errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
        errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
}
