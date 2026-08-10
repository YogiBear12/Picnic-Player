package app.picnic.player.data.playback

import androidx.media3.common.PlaybackException

class DirectPlayVeto {
    private var vetoed = false

    val allowsDirectPlay: Boolean get() = !vetoed

    /** @return true when the item should be renegotiated without direct play; false once already vetoed. */
    fun onPlaybackError(errorCode: Int): Boolean {
        if (vetoed || !isContainerUnreadable(errorCode)) return false
        vetoed = true
        return true
    }

    fun onNoPlayableTracks(): Boolean {
        if (vetoed) return false
        vetoed = true
        return true
    }

    private fun isContainerUnreadable(errorCode: Int): Boolean = errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
        errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
}
