package app.picnic.player.ui.player

import app.picnic.player.data.playback.PlayMethodKind
import app.picnic.player.data.playback.quality.QualityRung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerUiStateTest {

    @Test
    fun aTranscodeIsRunningAtTheNegotiatedRung() {
        val state = PlayerUiState(playMethod = PlayMethodKind.TRANSCODE, streamRung = QualityRung.P1080_6)
        assertEquals(QualityRung.P1080_6, state.activeQuality)
    }

    @Test
    fun aRemuxIsNotRunningAtAnyRung() {
        // The server negotiates a remux as a transcode and hands back a rung with it; once
        // transcoding info reveals the video is untouched, the quality picker must stop showing
        // that rung as the selected one.
        val state = PlayerUiState(playMethod = PlayMethodKind.DIRECT_STREAM, streamRung = QualityRung.P1080_6)
        assertNull(state.activeQuality)
    }

    @Test
    fun directPlayIsNotRunningAtAnyRung() {
        val state = PlayerUiState(playMethod = PlayMethodKind.DIRECT_PLAY, streamRung = QualityRung.P1080_6)
        assertNull(state.activeQuality)
    }

    @Test
    fun anUnknownPlayMethodClaimsNoRung() {
        assertNull(PlayerUiState(streamRung = QualityRung.P1080_6).activeQuality)
    }
}
