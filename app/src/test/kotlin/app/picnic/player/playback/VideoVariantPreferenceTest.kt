@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.playback

import androidx.media3.common.MimeTypes
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoVariantPreferenceTest {

    @Test
    fun `the source codec outranks the h264 entrance variant a transcode adds`() {
        assertEquals(
            listOf(MimeTypes.VIDEO_DOLBY_VISION, MimeTypes.VIDEO_H265),
            preferredVideoMimeTypes(listOf(stream(MediaStreamType.VIDEO, "hevc"), stream(MediaStreamType.AUDIO, "eac3")))
        )
    }

    @Test
    fun `every known source codec ranks itself first`() {
        assertEquals(listOf(MimeTypes.VIDEO_DOLBY_VISION, MimeTypes.VIDEO_H265), preferred("h265"))
        assertEquals(listOf(MimeTypes.VIDEO_DOLBY_VISION, MimeTypes.VIDEO_AV1), preferred("av1"))
        assertEquals(listOf(MimeTypes.VIDEO_VP9), preferred("vp9"))
        assertEquals(listOf(MimeTypes.VIDEO_H264), preferred("h264"))
        assertEquals(listOf(MimeTypes.VIDEO_H264), preferred("avc"))
    }

    @Test
    fun `codec case does not matter`() {
        assertEquals(listOf(MimeTypes.VIDEO_DOLBY_VISION, MimeTypes.VIDEO_H265), preferred("HEVC"))
    }

    @Test
    fun `an unknown or absent video codec expresses no preference`() {
        assertEquals(emptyList<String>(), preferred("mpeg4"))
        assertEquals(emptyList<String>(), preferred(null))
        assertEquals(emptyList<String>(), preferredVideoMimeTypes(listOf(stream(MediaStreamType.AUDIO, "eac3"))))
        assertEquals(emptyList<String>(), preferredVideoMimeTypes(emptyList()))
    }
}

private fun preferred(codec: String?): List<String> = preferredVideoMimeTypes(listOf(stream(MediaStreamType.VIDEO, codec)))

private fun stream(type: MediaStreamType, codec: String?): MediaStream = MediaStream(
    type = type,
    index = 0,
    codec = codec,
    isInterlaced = false,
    isDefault = true,
    isForced = false,
    isHearingImpaired = false,
    isExternal = false,
    isTextSubtitleStream = false,
    supportsExternalStream = false
)
