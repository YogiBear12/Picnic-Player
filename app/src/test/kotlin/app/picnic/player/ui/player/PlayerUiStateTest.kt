package app.picnic.player.ui.player

import app.picnic.player.data.playback.PlayMethodKind
import app.picnic.player.data.playback.quality.QualityOption
import app.picnic.player.data.playback.quality.QualityRung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerUiStateTest {
    @Test
    fun aTranscodeIsRunningAtTheNegotiatedRung() {
        val state = PlayerUiState(playMethod = PlayMethodKind.TRANSCODE, streamRung = QualityRung.P1080_6)
        assertEquals(QualityOption.Transcode(QualityRung.P1080_6), state.activeQuality)
    }

    @Test
    fun aServerImposedTranscodeMatchesNoQualityOption() {
        val state = PlayerUiState(playMethod = PlayMethodKind.TRANSCODE, streamRung = null)
        assertNull(state.activeQuality)
    }

    @Test
    fun aRemuxReadsAsOriginal() {
        val state = PlayerUiState(playMethod = PlayMethodKind.DIRECT_STREAM, streamRung = QualityRung.P1080_6)
        assertEquals(QualityOption.Original, state.activeQuality)
    }

    @Test
    fun directPlayReadsAsOriginal() {
        val state = PlayerUiState(playMethod = PlayMethodKind.DIRECT_PLAY, streamRung = QualityRung.P1080_6)
        assertEquals(QualityOption.Original, state.activeQuality)
    }

    @Test
    fun anUnknownPlayMethodReadsAsOriginal() {
        assertEquals(QualityOption.Original, PlayerUiState(streamRung = QualityRung.P1080_6).activeQuality)
    }
}
