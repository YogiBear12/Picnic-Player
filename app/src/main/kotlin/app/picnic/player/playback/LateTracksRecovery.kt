@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class)

package app.picnic.player.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.ForwardingExtractor
import androidx.media3.extractor.ForwardingExtractorOutput
import androidx.media3.extractor.ForwardingExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mkv.MatroskaExtractor
import java.io.EOFException

private const val SCAN_WINDOW_BYTES = 8 * 1024

fun recoverLateTracks(delegate: ExtractorsFactory): ExtractorsFactory = LateTracksExtractorsFactory(delegate)

private class LateTracksExtractorsFactory(delegate: ExtractorsFactory) : ForwardingExtractorsFactory(delegate) {
    override fun createExtractors(): Array<Extractor> = super.createExtractors().map(::wrap).toTypedArray()

    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> = super.createExtractors(uri, responseHeaders).map(::wrap).toTypedArray()

    private fun wrap(extractor: Extractor): Extractor = if (extractor.underlyingImplementation is MatroskaExtractor) LateTracksExtractor(extractor) else extractor
}

private enum class RecoveryState { SCAN, SEEK_TO_TRACKS, READING_TRACKS, SEEK_TO_START, PASSTHROUGH }

private class LateTracksExtractor(private val delegate: Extractor) : ForwardingExtractor(delegate) {
    private var output: LateTracksOutput? = null
    private var tracksPosition = 0L
    private var state = RecoveryState.SCAN

    override fun init(output: ExtractorOutput) {
        val wrapped = LateTracksOutput(output)
        this.output = wrapped
        delegate.init(wrapped)
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        if (state == RecoveryState.SCAN) {
            val found = scanTracksPosition(input)
            state = if (found == null) {
                RecoveryState.PASSTHROUGH
            } else {
                tracksPosition = found
                RecoveryState.SEEK_TO_TRACKS
            }
            PlaybackDiagnostics.log("late tracks: ${found?.let { "Tracks at $it, recovering" } ?: "not needed"}")
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
            PlaybackDiagnostics.log("late tracks: read ${wrapped.trackCount} track(s), restarting from the top")
            state = RecoveryState.SEEK_TO_START
            return Extractor.RESULT_CONTINUE
        }
        return result
    }

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

private class LateTracksOutput(delegate: ExtractorOutput) : ForwardingExtractorOutput(delegate) {
    private val outputs = mutableMapOf<Int, TrackOutput>()

    val trackCount: Int get() = outputs.size

    var tracksEnded = false
        private set

    override fun track(id: Int, type: Int): TrackOutput = outputs.getOrPut(id) { super.track(id, type) }

    override fun endTracks() {
        if (tracksEnded) return
        tracksEnded = true
        super.endTracks()
    }
}
