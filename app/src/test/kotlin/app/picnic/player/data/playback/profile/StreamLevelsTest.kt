package app.picnic.player.data.playback.profile

import android.media.MediaCodecInfo.CodecProfileLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamLevelsTest {
    @Test
    fun avcLevelsUseTenTimesTheLevelNumber() {
        assertEquals(42, avcStreamLevel(CodecProfileLevel.AVCLevel42))
        assertEquals(51, avcStreamLevel(CodecProfileLevel.AVCLevel51))
        assertEquals(30, avcStreamLevel(CodecProfileLevel.AVCLevel3))
    }

    @Test
    fun avcLevel1bKeepsItsOwnNumber() {
        assertEquals(9, avcStreamLevel(CodecProfileLevel.AVCLevel1b))
        assertEquals(10, avcStreamLevel(CodecProfileLevel.AVCLevel1))
    }

    @Test
    fun hevcLevelsUseThirtyTimesTheLevelNumber() {
        assertEquals(120, hevcStreamLevel(CodecProfileLevel.HEVCMainTierLevel4))
        assertEquals(153, hevcStreamLevel(CodecProfileLevel.HEVCMainTierLevel51))
        assertEquals(153, hevcStreamLevel(CodecProfileLevel.HEVCHighTierLevel51))
    }

    @Test
    fun unmappedCodecLevelClampsDownToTheLevelBelowIt() {
        assertEquals(42, avcStreamLevel(CodecProfileLevel.AVCLevel42 + 1))
        assertEquals(120, hevcStreamLevel(CodecProfileLevel.HEVCHighTierLevel4 + 1))
    }

    @Test
    fun codecLevelBelowEveryKnownLevelIsReportedAsUnknown() {
        assertEquals(UNKNOWN_STREAM_LEVEL, avcStreamLevel(0))
        assertEquals(UNKNOWN_STREAM_LEVEL, hevcStreamLevel(0))
    }
}
