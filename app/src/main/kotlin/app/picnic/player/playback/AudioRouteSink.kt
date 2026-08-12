@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.playback

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink

class AudioRouteSink(sink: AudioSink) : ForwardingAudioSink(sink) {
    @Volatile
    var route: AudioRoute = AudioRoute.NATIVE

    override fun supportsFormat(format: Format): Boolean = when {
        mustDecode(format) -> false
        else -> super.supportsFormat(format)
    }

    override fun getFormatSupport(format: Format): Int {
        val support = super.getFormatSupport(format)
        return if (mustDecode(format) && support == AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY) {
            AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING
        } else {
            support
        }
    }

    private fun mustDecode(format: Format): Boolean = when (route) {
        AudioRoute.PCM -> format.sampleMimeType != MimeTypes.AUDIO_RAW
        AudioRoute.NATIVE -> false
    }
}
