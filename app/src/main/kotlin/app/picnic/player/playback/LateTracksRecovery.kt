@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class)

package app.picnic.player.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.text.SubtitleParser
import java.io.EOFException

private const val SCAN_WINDOW_BYTES = 8 * 1024

fun recoverLateTracks(delegate: ExtractorsFactory): ExtractorsFactory = LateTracksExtractorsFactory(delegate)

private class LateTracksExtractorsFactory(private val delegate: ExtractorsFactory) : ExtractorsFactory {

    override fun createExtractors(): Array<Extractor> = delegate.createExtractors().map(::wrap).toTypedArray()

    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> = delegate.createExtractors(uri, responseHeaders).map(::wrap).toTypedArray()

    private fun wrap(extractor: Extractor): Extractor = if (extractor.underlyingImplementation is MatroskaExtractor) LateTracksExtractor(extractor) else extractor

    override fun setSubtitleParserFactory(subtitleParserFactory: SubtitleParser.Factory): ExtractorsFactory {
        delegate.setSubtitleParserFactory(subtitleParserFactory)
        return this
    }

    @Deprecated("Delegates a deprecated media3 hook", ReplaceWith("this"))
    override fun experimentalSetTextTrackTranscodingEnabled(enabled: Boolean): ExtractorsFactory {
        @Suppress("DEPRECATION")
        delegate.experimentalSetTextTrackTranscodingEnabled(enabled)
        return this
    }

    override fun experimentalSetCodecsToParseWithinGopSampleDependencies(codecsToParse: Int): ExtractorsFactory {
        delegate.experimentalSetCodecsToParseWithinGopSampleDependencies(codecsToParse)
        return this
    }
}

private enum class RecoveryState { SCAN, SEEK_TO_TRACKS, READING_TRACKS, SEEK_TO_START, PASSTHROUGH }

private class LateTracksExtractor(private val delegate: Extractor) : Extractor {

    private var output: LateTracksOutput? = null
    private var tracksPosition = 0L
    private var state = RecoveryState.SCAN

    override fun sniff(input: ExtractorInput): Boolean = delegate.sniff(input)

    override fun init(output: ExtractorOutput) {
        val wrapped = LateTracksOutput(output)
        this.output = wrapped
        delegate.init(wrapped)
    }

    // The jump has to be decided before the delegate parses anything: its read() only returns once it
    // has a sample to hand over, and with no tracks registered that is never, so it runs to the last byte.
    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        if (state == RecoveryState.SCAN) {
            val found = scanTracksPosition(input)
            state = if (found == null) {
                RecoveryState.PASSTHROUGH
            } else {
                tracksPosition = found
                RecoveryState.SEEK_TO_TRACKS
            }
        }

        when (state) {
            RecoveryState.SEEK_TO_TRACKS -> {
                state = RecoveryState.READING_TRACKS
                seekPosition.position = tracksPosition
                return Extractor.RESULT_SEEK
            }
            RecoveryState.SEEK_TO_START -> {
                state = RecoveryState.PASSTHROUGH
                seekPosition.position = 0
                return Extractor.RESULT_SEEK
            }
            else -> Unit
        }

        val result = delegate.read(input, seekPosition)
        if (state != RecoveryState.READING_TRACKS || result == Extractor.RESULT_SEEK) return result

        val wrapped = output
        if (wrapped != null && (wrapped.tracksEnded || result == Extractor.RESULT_END_OF_INPUT)) {
            state = RecoveryState.SEEK_TO_START
            return Extractor.RESULT_CONTINUE
        }
        return result
    }

    override fun seek(position: Long, timeUs: Long) = delegate.seek(position, timeUs)

    override fun release() = delegate.release()

    override fun getUnderlyingImplementation(): Extractor = delegate.underlyingImplementation

    private fun scanTracksPosition(input: ExtractorInput): Long? {
        if (input.position != 0L) return null
        val length = input.length
        val window = if (length != C.LENGTH_UNSET.toLong() && length < SCAN_WINDOW_BYTES) length.toInt() else SCAN_WINDOW_BYTES
        val buffer = ByteArray(window)
        return try {
            if (!input.peekFully(buffer, 0, window, true)) null else MatroskaTracksLocator.lateTracksPosition(buffer, window)
        } catch (e: EOFException) {
            null
        } finally {
            input.resetPeekPosition()
        }
    }
}

private class LateTracksOutput(private val delegate: ExtractorOutput) : ExtractorOutput {

    private val outputs = mutableMapOf<Int, TrackOutput>()

    var tracksEnded = false
        private set

    // Reused, not re-requested: the restart reaches Tracks a second time, and media3 answers a repeat
    // call with a discarded output that silently swallows every sample routed to it.
    override fun track(id: Int, type: Int): TrackOutput = outputs.getOrPut(id) { delegate.track(id, type) }

    override fun endTracks() {
        if (tracksEnded) return
        tracksEnded = true
        delegate.endTracks()
    }

    override fun seekMap(seekMap: SeekMap) = delegate.seekMap(seekMap)
}
