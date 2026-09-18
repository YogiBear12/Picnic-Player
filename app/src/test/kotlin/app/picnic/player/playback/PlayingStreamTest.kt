@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.playback

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import app.picnic.player.data.playback.PlayMethodKind
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.TranscodingInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayingStreamTest {
    @Test
    fun directPlay_namesTheSourceCodecAndProfile() {
        val playing = playing(
            playMethod = PlayMethodKind.DIRECT_PLAY,
            mediaStreams = listOf(video(codec = "hevc", profile = "Main 10", bitRate = 14_000_000))
        )
        assertTrue(playing.videoDirect)
        assertEquals("HEVC Main 10", playing.videoCodec)
        assertEquals(14_000_000L, playing.videoBitrate)
    }

    @Test
    fun transcode_prefersTheDecoderOverTheServerPlan() {
        val playing = playing(
            playMethod = PlayMethodKind.TRANSCODE,
            transcodingInfo = transcoding(videoCodec = "h264"),
            videoFormat = format(MimeTypes.VIDEO_H265)
        )
        assertEquals("HEVC", playing.videoCodec)
    }

    @Test
    fun transcode_fallsBackToTheServerPlanBeforeTheDecoderReports() {
        val playing = playing(
            playMethod = PlayMethodKind.TRANSCODE,
            transcodingInfo = transcoding(videoCodec = "h264"),
            videoFormat = null
        )
        assertEquals("H264", playing.videoCodec)
    }

    @Test
    fun transcode_saysNothingWhenNeitherSideKnows() {
        assertNull(playing(playMethod = PlayMethodKind.TRANSCODE).videoCodec)
    }

    @Test
    fun audio_followsTheSelectedTrackNotTheFirst() {
        val playing = playing(
            playMethod = PlayMethodKind.DIRECT_PLAY,
            mediaStreams = listOf(
                video(),
                audio(index = 1, codec = "aac", channels = 2, bitRate = 128_000),
                audio(index = 2, codec = "truehd", channels = 8, bitRate = 4_000_000)
            ),
            selectedAudioIndex = 2
        )
        assertEquals("TRUEHD", playing.audioCodec)
        assertEquals(8, playing.audioChannels)
    }

    @Test
    fun audio_refusesToGuessBetweenSeveralUnidentifiedTracks() {
        val playing = playing(
            playMethod = PlayMethodKind.DIRECT_PLAY,
            mediaStreams = listOf(
                video(),
                audio(index = 1, codec = "aac", channels = 2),
                audio(index = 2, codec = "truehd", channels = 8)
            ),
            selectedAudioIndex = null,
            audioFormat = format(MimeTypes.AUDIO_E_AC3, channelCount = 6)
        )
        assertEquals("EAC3", playing.audioCodec)
        assertEquals(6, playing.audioChannels)
    }

    @Test
    fun audio_takesTheOnlyTrackWhenThereIsOnlyOne() {
        val playing = playing(
            playMethod = PlayMethodKind.DIRECT_PLAY,
            mediaStreams = listOf(video(), audio(index = 1, codec = "aac", channels = 2)),
            selectedAudioIndex = null
        )
        assertEquals("AAC", playing.audioCodec)
        assertEquals(2, playing.audioChannels)
    }

    @Test
    fun rangeComesFromTheDecoderSoATonemappedTranscodeReadsAsSdr() {
        val playing = playing(
            playMethod = PlayMethodKind.TRANSCODE,
            mediaStreams = listOf(video(codec = "hevc")),
            videoFormat = format(MimeTypes.VIDEO_H265, colorTransfer = C.COLOR_TRANSFER_SDR)
        )
        assertEquals(VideoDynamicRange.SDR, playing.dynamicRange)
    }

    @Test
    fun rangeIsUnknownUntilTheDecoderReports() {
        assertNull(playing(playMethod = PlayMethodKind.TRANSCODE).dynamicRange)
    }

    @Test
    fun dolbyVisionDecodedThroughHevc_reportsTheHdr10BaseLayer() {
        val playing = playing(
            playMethod = PlayMethodKind.DIRECT_PLAY,
            videoFormat = format(
                MimeTypes.VIDEO_DOLBY_VISION,
                colorTransfer = C.COLOR_TRANSFER_ST2084
            ),
            videoDecoderMimeType = MimeTypes.VIDEO_H265
        )

        assertEquals(VideoDynamicRange.HDR10, playing.dynamicRange)
    }

    @Test
    fun videoDirectWhileAudioTranscodes() {
        val playing = playing(
            playMethod = PlayMethodKind.TRANSCODE,
            transcodingInfo = transcoding(isVideoDirect = true, audioCodec = "aac"),
            mediaStreams = listOf(video(codec = "hevc", profile = "Main 10"))
        )
        assertTrue(playing.videoDirect)
        assertEquals("HEVC Main 10", playing.videoCodec)
        assertEquals(false, playing.audioDirect)
    }

    private fun playing(
        playMethod: PlayMethodKind? = null,
        transcodingInfo: TranscodingInfo? = null,
        mediaStreams: List<MediaStream> = emptyList(),
        selectedAudioIndex: Int? = null,
        videoFormat: Format? = null,
        audioFormat: Format? = null,
        videoDecoderMimeType: String? = null
    ) = playingStream(
        playMethod,
        transcodingInfo,
        mediaStreams,
        selectedAudioIndex,
        videoFormat,
        audioFormat,
        videoDecoderMimeType
    )

    private fun format(
        mimeType: String,
        colorTransfer: Int? = null,
        channelCount: Int = Format.NO_VALUE
    ): Format = Format.Builder()
        .setSampleMimeType(mimeType)
        .setChannelCount(channelCount)
        .setColorInfo(
            colorTransfer?.let {
                ColorInfo.Builder().setColorSpace(C.COLOR_SPACE_BT709).setColorTransfer(it).build()
            }
        )
        .build()

    private fun transcoding(
        videoCodec: String? = null,
        audioCodec: String? = null,
        isVideoDirect: Boolean = false,
        isAudioDirect: Boolean = false
    ) = TranscodingInfo(
        videoCodec = videoCodec,
        audioCodec = audioCodec,
        isVideoDirect = isVideoDirect,
        isAudioDirect = isAudioDirect,
        completionPercentage = null,
        width = null,
        height = null,
        audioChannels = null,
        bitrate = null,
        framerate = null,
        transcodeReasons = emptyList()
    )

    private fun video(codec: String = "h264", profile: String? = null, bitRate: Int? = null) = stream(MediaStreamType.VIDEO, index = 0, codec = codec, profile = profile, bitRate = bitRate)

    private fun audio(index: Int, codec: String, channels: Int? = null, bitRate: Int? = null) = stream(MediaStreamType.AUDIO, index = index, codec = codec, channels = channels, bitRate = bitRate)

    private fun stream(
        type: MediaStreamType,
        index: Int,
        codec: String,
        profile: String? = null,
        channels: Int? = null,
        bitRate: Int? = null
    ) = MediaStream(
        type = type,
        index = index,
        codec = codec,
        profile = profile,
        channels = channels,
        bitRate = bitRate,
        isInterlaced = false,
        isDefault = false,
        isForced = false,
        isHearingImpaired = false,
        isExternal = false,
        isTextSubtitleStream = false,
        supportsExternalStream = false
    )
}
