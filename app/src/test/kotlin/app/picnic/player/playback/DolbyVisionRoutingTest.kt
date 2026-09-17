package app.picnic.player.playback

import com.suyashbelekar.exoplayerhdrutils.video.transformers.DoviStrategy
import org.junit.Assert.assertEquals
import org.junit.Test

class DolbyVisionRoutingTest {
    @Test
    fun nonDolbyVisionDisplay_keepsProfile7ForMedia3HdrFallback() {
        assertEquals(
            DoviStrategy.KEEP,
            doviProfile7Strategy(
                displaySupportsDolbyVision = false,
                supportsNativeProfile7 = false,
                supportsProfile8 = true
            )
        )
    }

    @Test
    fun nativeProfile7Decoder_keepsProfile7() {
        assertEquals(
            DoviStrategy.KEEP,
            doviProfile7Strategy(
                displaySupportsDolbyVision = true,
                supportsNativeProfile7 = true,
                supportsProfile8 = true
            )
        )
    }

    @Test
    fun profile8Decoder_convertsProfile7WhenDolbyVisionOutputIsAvailable() {
        assertEquals(
            DoviStrategy.CONVERT_TO_P8,
            doviProfile7Strategy(
                displaySupportsDolbyVision = true,
                supportsNativeProfile7 = false,
                supportsProfile8 = true
            )
        )
    }

    @Test
    fun noCompatibleDolbyVisionDecoder_keepsProfile7ForMedia3Fallback() {
        assertEquals(
            DoviStrategy.KEEP,
            doviProfile7Strategy(
                displaySupportsDolbyVision = true,
                supportsNativeProfile7 = false,
                supportsProfile8 = false
            )
        )
    }
}
