package app.picnic.player.data.playback

import org.jellyfin.sdk.model.api.TranscodingInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class RefinePlayMethodTest {
    private fun info(videoDirect: Boolean, audioDirect: Boolean = true) = TranscodingInfo(
        audioCodec = if (audioDirect) null else "aac",
        videoCodec = if (videoDirect) null else "h264",
        container = "ts",
        isVideoDirect = videoDirect,
        isAudioDirect = audioDirect,
        bitrate = null,
        framerate = null,
        completionPercentage = null,
        width = null,
        height = null,
        audioChannels = null,
        hardwareAccelerationType = null,
        transcodeReasons = emptyList()
    )

    @Test
    fun remuxWithVideoCopy_becomesDirectStream() {
        assertEquals(
            PlayMethodKind.DIRECT_STREAM,
            refinePlayMethod(PlayMethodKind.TRANSCODE, info(videoDirect = true, audioDirect = true))
        )
        assertEquals(
            PlayMethodKind.DIRECT_STREAM,
            refinePlayMethod(PlayMethodKind.TRANSCODE, info(videoDirect = true, audioDirect = false))
        )
    }

    @Test
    fun videoReencode_staysTranscode() {
        assertEquals(
            PlayMethodKind.TRANSCODE,
            refinePlayMethod(PlayMethodKind.TRANSCODE, info(videoDirect = false))
        )
    }

    @Test
    fun directPlay_unchanged() {
        assertEquals(
            PlayMethodKind.DIRECT_PLAY,
            refinePlayMethod(PlayMethodKind.DIRECT_PLAY, info(videoDirect = false))
        )
    }

    @Test
    fun nullInfo_keepsInitial() {
        assertEquals(
            PlayMethodKind.TRANSCODE,
            refinePlayMethod(PlayMethodKind.TRANSCODE, null)
        )
    }
}
