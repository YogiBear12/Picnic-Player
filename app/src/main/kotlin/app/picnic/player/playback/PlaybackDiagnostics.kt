@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class)

package app.picnic.player.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.ForwardingExtractor
import androidx.media3.extractor.ForwardingExtractorOutput
import androidx.media3.extractor.ForwardingExtractorsFactory
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import app.picnic.player.BuildConfig
import app.picnic.player.data.playback.StreamInfo
import java.io.IOException
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType

const val PLAYBACK_LOG_TAG = "PicnicPlayback"

object PlaybackDiagnostics : AnalyticsListener {
    val enabled: Boolean = BuildConfig.DEBUG

    fun log(message: String) {
        if (enabled) Log.d(PLAYBACK_LOG_TAG, message)
    }

    fun logNegotiation(pass: Int, requestedRung: Any?, source: MediaSourceInfo) {
        if (!enabled) return
        val video = source.mediaStreams.orEmpty().firstOrNull { it.type == MediaStreamType.VIDEO }
        val audio = source.mediaStreams.orEmpty().firstOrNull { it.type == MediaStreamType.AUDIO && it.isDefault }
        log(
            "negotiation pass=$pass requestedRung=$requestedRung " +
                "supportsDirectPlay=${source.supportsDirectPlay} supportsDirectStream=${source.supportsDirectStream} " +
                "bitrate=${source.bitrate} container=${source.container}"
        )
        log(
            "negotiation pass=$pass video=${video?.codec} ${video?.width}x${video?.height} " +
                "range=${video?.videoRangeType} profile=${video?.profile} level=${video?.level} " +
                "audio=${audio?.codec} ch=${audio?.channels}"
        )
        log("negotiation pass=$pass transcodingUrl=${source.transcodingUrl?.let(::redact)}")
    }

    fun logStream(info: StreamInfo) {
        if (!enabled) return
        log("--- stream negotiated ---")
        log("playMethod=${info.playMethod} rung=${info.rung} mediaSourceId=${info.mediaSourceId}")
        log("url=${redact(info.url)}")
        info.mediaSource?.let { source ->
            log(
                "container=${source.container} transcodingContainer=${source.transcodingContainer} " +
                    "protocol=${source.transcodingSubProtocol} bitrate=${source.bitrate} " +
                    "supportsDirectPlay=${source.supportsDirectPlay} supportsDirectStream=${source.supportsDirectStream} " +
                    "supportsTranscoding=${source.supportsTranscoding}"
            )
        }
        log("defaultAudioIndex=${info.defaultAudioStreamIndex} defaultSubtitleIndex=${info.defaultSubtitleStreamIndex}")
        info.mediaStreams.forEach { log("serverStream ${it.summary()}") }
        info.externalSubtitles.forEach { log("externalSubtitle index=${it.streamIndex} mime=${it.mimeType} url=${redact(it.url)}") }
    }

    fun logRenderers(player: ExoPlayer) {
        if (!enabled) return
        val types = (0 until player.rendererCount).joinToString(", ") { i ->
            "$i=${trackTypeName(player.getRendererType(i))}"
        }
        log("renderers: $types")
    }

    fun logAudioDecoderCandidates(context: Context, format: Format) {
        if (!enabled) return
        val mimeType = format.sampleMimeType ?: return
        val infos = runCatching { MediaCodecUtil.getDecoderInfos(mimeType, false, false) }
            .getOrElse {
                log("decoder query failed for $mimeType: $it")
                return
            }
        log("platform decoders for $mimeType: ${infos.size}")
        infos.forEach { info ->
            val supported = runCatching { info.isFormatSupported(context, format) }.getOrElse { "threw $it" }
            val sampleRateOk = runCatching { info.isAudioSampleRateSupportedV21(format.sampleRate) }
                .getOrElse { "threw $it" }
            val channelsOk = runCatching { info.isAudioChannelCountSupportedV21(format.channelCount) }
                .getOrElse { "threw $it" }
            log(
                "  ${info.name} hw=${info.hardwareAccelerated} supportsThisFormat=$supported " +
                    "sampleRate${format.sampleRate}Ok=$sampleRateOk channels${format.channelCount}Ok=$channelsOk"
            )
        }
    }

    fun logAudioOutputCapabilities(context: Context, format: Format) {
        if (!enabled) return
        val capabilities = AudioCapabilities.getCapabilities(context)
        log(
            "audio output: maxChannels=${capabilities.maxChannelCount} " +
                "pcm16=${capabilities.supportsEncoding(C.ENCODING_PCM_16BIT)} " +
                "passthrough=${capabilities.isPassthroughPlaybackSupported(format)} " +
                "trackChannels=${format.channelCount}"
        )
    }

    fun logTracks(tracks: Tracks) {
        if (!enabled) return
        log("--- tracks (${tracks.groups.size} groups) ---")
        if (tracks.groups.isEmpty()) {
            log("NO TRACK GROUPS: the extractor exposed nothing to play")
            return
        }
        tracks.groups.forEach { group ->
            log(
                "group type=${trackTypeName(group.type)} length=${group.length} " +
                    "supported=${group.isSupported} selected=${group.isSelected}"
            )
            for (i in 0 until group.length) {
                log(
                    "  track $i support=${formatSupportName(group.getTrackSupport(i))} " +
                        "selected=${group.isTrackSelected(i)} ${group.getTrackFormat(i).summary()}"
                )
            }
        }
        listOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT).forEach { type ->
            log(
                "${trackTypeName(type)}: typeSupported=${tracks.isTypeSupported(type)} " +
                    "typeSelected=${tracks.isTypeSelected(type)}"
            )
        }
    }

    fun logTrackSelectionOutcome(
        result: TrackSelectionResult,
        audioIndex: Int?,
        subtitleIndex: Int?,
        externalSubtitleCount: Int,
        supportsDirectPlay: Boolean
    ) {
        if (!enabled) return
        log(
            "track selection: audioIndex=$audioIndex subtitleIndex=$subtitleIndex " +
                "externalSubs=$externalSubtitleCount supportsDirectPlay=$supportsDirectPlay " +
                "audioSelected=${result.audioSelected} subtitleSelected=${result.subtitleSelected} " +
                "applied=${result.bothSelected}"
        )
    }

    override fun onTracksChanged(eventTime: AnalyticsListener.EventTime, tracks: Tracks) = logTracks(tracks)

    override fun onVideoInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?
    ) = log("video input format: ${format.summary()}")

    override fun onAudioInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?
    ) = log("audio input format: ${format.summary()}")

    override fun onVideoDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long
    ) = log("video decoder initialized: $decoderName in ${initializationDurationMs}ms")

    override fun onAudioDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long
    ) = log("audio decoder initialized: $decoderName in ${initializationDurationMs}ms")

    override fun onVideoEnabled(eventTime: AnalyticsListener.EventTime, decoderCounters: DecoderCounters) = log("video renderer enabled")

    override fun onVideoDisabled(eventTime: AnalyticsListener.EventTime, decoderCounters: DecoderCounters) = log("video renderer disabled: ${decoderCounters.summary()}")

    override fun onAudioEnabled(eventTime: AnalyticsListener.EventTime, decoderCounters: DecoderCounters) = log("audio renderer enabled")

    override fun onAudioDisabled(eventTime: AnalyticsListener.EventTime, decoderCounters: DecoderCounters) = log("audio renderer disabled: ${decoderCounters.summary()}")

    override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) = log("first frame rendered to $output after ${renderTimeMs}ms")

    override fun onVideoSizeChanged(eventTime: AnalyticsListener.EventTime, videoSize: VideoSize) = log("video size ${videoSize.width}x${videoSize.height} par=${videoSize.pixelWidthHeightRatio}")

    override fun onSurfaceSizeChanged(eventTime: AnalyticsListener.EventTime, width: Int, height: Int) = log("surface size ${width}x$height")

    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) = log("dropped $droppedFrames frames in ${elapsedMs}ms")

    override fun onVideoCodecError(eventTime: AnalyticsListener.EventTime, videoCodecError: Exception) {
        if (enabled) Log.w(PLAYBACK_LOG_TAG, "video codec error", videoCodecError)
    }

    override fun onAudioCodecError(eventTime: AnalyticsListener.EventTime, audioCodecError: Exception) {
        if (enabled) Log.w(PLAYBACK_LOG_TAG, "audio codec error", audioCodecError)
    }

    override fun onAudioSinkError(eventTime: AnalyticsListener.EventTime, audioSinkError: Exception) {
        if (enabled) Log.w(PLAYBACK_LOG_TAG, "audio sink error", audioSinkError)
    }

    override fun onAudioUnderrun(
        eventTime: AnalyticsListener.EventTime,
        bufferSize: Int,
        bufferSizeMs: Long,
        elapsedSinceLastFeedMs: Long
    ) = log("audio underrun: bufferSize=$bufferSize bufferSizeMs=$bufferSizeMs sinceLastFeed=${elapsedSinceLastFeedMs}ms")

    override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) = log("state=${stateName(state)}")

    override fun onPlayWhenReadyChanged(eventTime: AnalyticsListener.EventTime, playWhenReady: Boolean, reason: Int) = log("playWhenReady=$playWhenReady reason=$reason")

    override fun onIsPlayingChanged(eventTime: AnalyticsListener.EventTime, isPlaying: Boolean) = log("isPlaying=$isPlaying")

    override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
        if (enabled) Log.w(PLAYBACK_LOG_TAG, "player error ${error.errorCodeName}", error)
    }

    override fun onLoadError(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
        error: IOException,
        wasCanceled: Boolean
    ) {
        if (enabled) Log.w(PLAYBACK_LOG_TAG, "load error (canceled=$wasCanceled) uri=${redact(loadEventInfo.uri.toString())}", error)
    }

    override fun onLoadCompleted(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData
    ) = log(
        "load completed: bytes=${loadEventInfo.bytesLoaded} in ${loadEventInfo.loadDurationMs}ms " +
            "dataType=${mediaLoadData.dataType} trackType=${trackTypeName(mediaLoadData.trackType)} " +
            "trackFormat=${mediaLoadData.trackFormat?.summary()}"
    )
}

fun instrumentExtractors(delegate: ExtractorsFactory): ExtractorsFactory = if (PlaybackDiagnostics.enabled) LoggingExtractorsFactory(delegate) else delegate

private class LoggingExtractorsFactory(delegate: ExtractorsFactory) : ForwardingExtractorsFactory(delegate) {
    override fun createExtractors(): Array<Extractor> = super.createExtractors().map { LoggingExtractor(it) }.toTypedArray()

    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> {
        PlaybackDiagnostics.log("--- extractor selection for ${redact(uri.toString())} ---")
        responseHeaders.entries.forEach { entry ->
            val key = entry.key
            if (key != null && key.lowercase() in LoggedResponseHeaders) {
                PlaybackDiagnostics.log("header $key: ${entry.value.joinToString()}")
            }
        }
        return super.createExtractors(uri, responseHeaders).map { LoggingExtractor(it) }.toTypedArray()
    }
}

private class LoggingExtractor(private val delegate: Extractor) : ForwardingExtractor(delegate) {
    private val name: String get() = delegate.javaClass.simpleName

    override fun sniff(input: ExtractorInput): Boolean {
        val sniffed = delegate.sniff(input)
        if (sniffed) {
            PlaybackDiagnostics.log("sniff MATCH $name")
        } else {
            val failures = delegate.sniffFailureDetails.joinToString { it.javaClass.simpleName }
            PlaybackDiagnostics.log("sniff miss $name${if (failures.isEmpty()) "" else " ($failures)"}")
        }
        return sniffed
    }

    override fun init(output: ExtractorOutput) {
        PlaybackDiagnostics.log("extractor in use: ${delegate.javaClass.name}")
        delegate.init(LoggingExtractorOutput(output))
    }
}

private class LoggingExtractorOutput(delegate: ExtractorOutput) : ForwardingExtractorOutput(delegate) {
    private var emitted = 0

    override fun track(id: Int, type: Int): TrackOutput {
        emitted++
        PlaybackDiagnostics.log("extractor emitted track id=$id type=${trackTypeName(type)}")
        return super.track(id, type)
    }

    override fun endTracks() {
        PlaybackDiagnostics.log("extractor endTracks: $emitted track(s) emitted")
        super.endTracks()
    }

    override fun seekMap(seekMap: SeekMap) {
        PlaybackDiagnostics.log("seekMap ${seekMap.javaClass.simpleName} durationUs=${seekMap.durationUs} seekable=${seekMap.isSeekable}")
        super.seekMap(seekMap)
    }
}

private val LoggedResponseHeaders = setOf("content-type", "content-length", "content-range", "content-encoding", "transfer-encoding")

private fun DecoderCounters.summary(): String = "rendered=$renderedOutputBufferCount skipped=$skippedOutputBufferCount dropped=$droppedBufferCount decoderInits=$decoderInitCount"

private fun Format.summary(): String = buildList {
    add("id=$id")
    sampleMimeType?.let { add("mime=$it") }
    containerMimeType?.let { add("container=$it") }
    codecs?.let { add("codecs=$it") }
    if (width != Format.NO_VALUE) add("size=${width}x$height")
    if (frameRate != Format.NO_VALUE.toFloat()) add("fps=$frameRate")
    if (channelCount != Format.NO_VALUE) add("channels=$channelCount")
    if (sampleRate != Format.NO_VALUE) add("sampleRate=$sampleRate")
    if (bitrate != Format.NO_VALUE) add("bitrate=$bitrate")
    language?.let { add("lang=$it") }
    add("selectionFlags=$selectionFlags")
    add("roleFlags=$roleFlags")
    colorInfo?.let { add("color=$it") }
    drmInitData?.let { add("drm=${it.schemeType}") }
}.joinToString(" ")

private fun MediaStream.summary(): String = buildList {
    add("index=$index")
    add("type=$type")
    codec?.let { add("codec=$it") }
    profile?.let { add("profile=$it") }
    if (width != null) add("size=${width}x$height")
    channels?.let { add("channels=$it") }
    language?.let { add("lang=$it") }
    bitRate?.let { add("bitrate=$it") }
    add("external=$isExternal")
    add("default=$isDefault")
    deliveryMethod?.let { add("delivery=$it") }
}.joinToString(" ")

private fun trackTypeName(type: Int): String = when (type) {
    C.TRACK_TYPE_VIDEO -> "VIDEO"
    C.TRACK_TYPE_AUDIO -> "AUDIO"
    C.TRACK_TYPE_TEXT -> "TEXT"
    C.TRACK_TYPE_METADATA -> "METADATA"
    C.TRACK_TYPE_IMAGE -> "IMAGE"
    C.TRACK_TYPE_CAMERA_MOTION -> "CAMERA_MOTION"
    C.TRACK_TYPE_NONE -> "NONE"
    else -> "UNKNOWN($type)"
}

private fun formatSupportName(support: Int): String = when (support) {
    C.FORMAT_HANDLED -> "HANDLED"
    C.FORMAT_EXCEEDS_CAPABILITIES -> "EXCEEDS_CAPABILITIES"
    C.FORMAT_UNSUPPORTED_DRM -> "UNSUPPORTED_DRM"
    C.FORMAT_UNSUPPORTED_SUBTYPE -> "UNSUPPORTED_SUBTYPE"
    C.FORMAT_UNSUPPORTED_TYPE -> "UNSUPPORTED_TYPE"
    else -> "UNKNOWN($support)"
}

internal fun stateName(state: Int): String = when (state) {
    Player.STATE_IDLE -> "IDLE"
    Player.STATE_BUFFERING -> "BUFFERING"
    Player.STATE_READY -> "READY"
    Player.STATE_ENDED -> "ENDED"
    else -> "UNKNOWN($state)"
}

private val SecretParams = Regex("(api_key|ApiKey|X-Emby-Token|Token)=[^&]*", RegexOption.IGNORE_CASE)

private fun redact(url: String): String = SecretParams.replace(url, "$1=***")
