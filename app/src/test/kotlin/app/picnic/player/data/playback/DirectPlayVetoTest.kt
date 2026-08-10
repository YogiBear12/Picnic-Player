package app.picnic.player.data.playback

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectPlayVetoTest {
    private val veto = DirectPlayVeto()

    private val unreadableContainer = PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED

    @Test
    fun directPlayIsAllowedUntilSomethingFails() {
        assertTrue(veto.allowsDirectPlay)
    }

    @Test
    fun anUnreadableContainerTriggersOneRenegotiation() {
        assertTrue(veto.onPlaybackError(unreadableContainer))
        assertFalse(veto.allowsDirectPlay)
    }

    @Test
    fun anUnsupportedContainerAlsoTriggersIt() {
        assertTrue(veto.onPlaybackError(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED))
    }

    @Test
    fun theVetoOutlivesTheRetry() {
        veto.onPlaybackError(unreadableContainer)
        assertFalse(veto.allowsDirectPlay)
        assertFalse(veto.allowsDirectPlay)
    }

    @Test
    fun aSecondFailureIsARealError() {
        veto.onPlaybackError(unreadableContainer)
        assertFalse(veto.onPlaybackError(unreadableContainer))
    }

    @Test
    fun aSourceThatPreparesWithNothingToPlayTriggersOneRenegotiation() {
        assertTrue(veto.onNoPlayableTracks())
        assertFalse(veto.allowsDirectPlay)
    }

    @Test
    fun aSecondEmptyPreparationIsARealError() {
        veto.onNoPlayableTracks()
        assertFalse(veto.onNoPlayableTracks())
    }

    @Test
    fun anEmptyPreparationAfterAContainerErrorIsARealError() {
        veto.onPlaybackError(unreadableContainer)
        assertFalse(veto.onNoPlayableTracks())
    }

    @Test
    fun decoderFailuresAreNotContainerProblems() {
        assertFalse(veto.onPlaybackError(PlaybackException.ERROR_CODE_DECODING_FAILED))
        assertFalse(veto.onPlaybackError(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED))
        assertFalse(veto.onPlaybackError(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED))
        assertTrue(veto.allowsDirectPlay)
    }

    @Test
    fun networkFailuresAreNotContainerProblems() {
        assertFalse(veto.onPlaybackError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertFalse(veto.onPlaybackError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
        assertFalse(veto.onPlaybackError(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
        assertTrue(veto.allowsDirectPlay)
    }

    @Test
    fun manifestFailuresComeFromTheStreamWeWouldRetryInto() {
        assertFalse(veto.onPlaybackError(PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED))
        assertFalse(veto.onPlaybackError(PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED))
        assertTrue(veto.allowsDirectPlay)
    }
}
