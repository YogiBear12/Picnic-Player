@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.ForwardingExtractor
import androidx.media3.extractor.ForwardingExtractorOutput
import androidx.media3.extractor.ForwardingExtractorsFactory
import androidx.media3.extractor.ForwardingTrackOutput
import androidx.media3.extractor.TrackOutput
import com.suyashbelekar.exoplayerhdrutils.extractor.BitstreamModifyingExtractorOutput
import com.suyashbelekar.exoplayerhdrutils.video.transformers.DoviStrategy
import com.suyashbelekar.exoplayerhdrutils.video.transformers.Hdr10PlusStrategy
import com.suyashbelekar.exoplayerhdrutils.video.transformers.TransformStrategy

private const val PROFILE_7 = "07"
private const val PROFILE_8 = "08"

/**
 * ExoplayerHdrUtils only offers the no-argument `createExtractors()`, so its per-extractor output
 * wrapper is composed here rather than using its factory, which would drop the URI and
 * response-header overload media3 uses to order extractors.
 */
internal class DolbyVisionExtractorsFactory(
    private val strategy: DoviStrategy,
    delegate: ExtractorsFactory
) : ForwardingExtractorsFactory(delegate) {
    private val transformStrategy = TransformStrategy(
        doviP7Fel = strategy,
        doviP7Mel = strategy,
        doviHdr10Plus = Hdr10PlusStrategy.KEEP
    )

    override fun createExtractors(): Array<Extractor> = super.createExtractors().adapting()

    override fun createExtractors(
        uri: Uri,
        responseHeaders: Map<String, List<String>>
    ): Array<Extractor> = super.createExtractors(uri, responseHeaders).adapting()

    private fun Array<Extractor>.adapting(): Array<Extractor> = map {
        DolbyVisionExtractor(it, strategy, transformStrategy)
    }.toTypedArray()
}

private class DolbyVisionExtractor(
    delegate: Extractor,
    private val strategy: DoviStrategy,
    private val transformStrategy: TransformStrategy
) : ForwardingExtractor(delegate) {
    override fun init(output: ExtractorOutput) {
        super.init(
            BitstreamModifyingExtractorOutput(
                DolbyVisionFormatExtractorOutput(output, strategy),
                transformStrategy
            )
        )
    }
}

internal class DolbyVisionFormatExtractorOutput(
    delegate: ExtractorOutput,
    private val strategy: DoviStrategy
) : ForwardingExtractorOutput(delegate) {
    override fun track(id: Int, type: Int): TrackOutput {
        val trackOutput = super.track(id, type)
        return if (type == C.TRACK_TYPE_VIDEO) {
            DolbyVisionFormatTrackOutput(trackOutput, strategy)
        } else {
            trackOutput
        }
    }
}

private class DolbyVisionFormatTrackOutput(
    delegate: TrackOutput,
    private val strategy: DoviStrategy
) : ForwardingTrackOutput(delegate) {
    override fun format(format: Format) {
        super.format(format.forDolbyVisionStrategy(strategy))
    }
}

private fun Format.forDolbyVisionStrategy(strategy: DoviStrategy): Format {
    if (sampleMimeType != MimeTypes.VIDEO_DOLBY_VISION || codecs.dolbyVisionProfile() != PROFILE_7) return this
    return when (strategy) {
        DoviStrategy.KEEP -> this
        DoviStrategy.CONVERT_TO_P8 -> buildUpon().setCodecs(codecs?.withDolbyVisionProfile(PROFILE_8)).build()
        DoviStrategy.DISCARD -> buildUpon()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .setCodecs(null)
            .build()
    }
}

private fun String?.dolbyVisionProfile(): String? {
    val parts = this?.lowercase()?.split('.') ?: return null
    if (parts.size < 2 || parts[0] !in DolbyVisionCodecPrefixes) return null
    return parts[1]
}

private fun String.withDolbyVisionProfile(profile: String): String = split('.')
    .toMutableList()
    .also { it[1] = profile }
    .joinToString(".")
