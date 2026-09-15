package app.picnic.player.ui.player.osd

import app.picnic.player.util.AudioFormat
import java.util.Locale

data class TrackLabel(val secondary: String?, val chips: List<String>)

private val FlagWords = listOf(
    "hearing impaired",
    "hearing-impaired",
    "full subtitle",
    "forced",
    "default",
    "external",
    "sdh",
    "cc"
)

private val SubtitleCodecNames = mapOf(
    "pgssub" to "PGS",
    "pgs" to "PGS",
    "subrip" to "SRT",
    "srt" to "SRT",
    "ssa" to "ASS",
    "ass" to "ASS",
    "webvtt" to "VTT",
    "vtt" to "VTT",
    "dvdsub" to "VOBSUB",
    "dvd_subtitle" to "VOBSUB",
    "vobsub" to "VOBSUB",
    "mov_text" to "TX3G",
    "dvbsub" to "DVB"
)

private fun wordPattern(token: String) = Regex("(?i)\\b${Regex.escape(token)}\\b")

private val FlagWordPatterns = FlagWords.map { wordPattern(it) }
private val EmptyBrackets = Regex("\\(\\s*\\)|\\[\\s*]")
private val RunsOfSpace = Regex("\\s{2,}")

/**
 * The server composes its own subtitle description, so a stream's own title often repeats the
 * language, the flags and the codec that the row already shows. Strip those, and surface what is
 * left as chips.
 */
fun subtitleTrackLabel(
    title: String?,
    languageName: String,
    languageTag: String?,
    isForced: Boolean,
    isHearingImpaired: Boolean,
    isExternal: Boolean,
    codec: String?
): TrackLabel {
    val chips = buildList {
        when {
            isForced -> add("FORCED")
            isHearingImpaired -> add("SDH")
            isExternal -> add("EXT")
        }
        codecChip(codec)?.let { add(it) }
    }
    return TrackLabel(stripKnownTokens(title, languageName, languageTag, codec), chips)
}

fun audioTrackLabel(
    title: String?,
    languageName: String,
    languageTag: String?,
    format: AudioFormat
): TrackLabel {
    val drop = listOfNotNull(
        format.codecLabel(),
        format.channelLabel(),
        format.spatialLabel(),
        "surround",
        "channels"
    )
    return TrackLabel(
        stripKnownTokens(title, languageName, languageTag, format.codec, drop),
        listOfNotNull(format.label())
    )
}

internal fun codecChip(codec: String?): String? {
    val key = codec?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
    return SubtitleCodecNames[key] ?: key.uppercase(Locale.ROOT)
}

private fun stripKnownTokens(
    title: String?,
    languageName: String,
    languageTag: String?,
    codec: String?,
    extraTokens: List<String> = emptyList()
): String? {
    val raw = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val drop = buildList {
        add(languageName)
        languageTag?.let { add(it) }
        codec?.let { add(it) }
        codecChip(codec)?.let { add(it) }
        addAll(extraTokens)
    }.filter { it.isNotBlank() }.map { wordPattern(it) } + FlagWordPatterns

    val kept = raw.split(" - ", " · ")
        .map { part -> cleanPart(part, drop) }
        .filter { it.isNotEmpty() }
    return kept.joinToString(" · ").takeIf { it.isNotEmpty() }
}

private fun cleanPart(part: String, drop: List<Regex>): String {
    var text = part.trim()
    for (pattern in drop) {
        text = text.replace(pattern, " ")
    }
    return unwrap(
        text
            .replace(EmptyBrackets, " ")
            .replace(RunsOfSpace, " ")
            .trim()
            .trim('-', '·', ',', '/', '|')
            .trim()
    )
}

private fun unwrap(text: String): String {
    val pairs = listOf('(' to ')', '[' to ']')
    for ((open, close) in pairs) {
        if (text.length > 2 && text.first() == open && text.last() == close) {
            val inner = text.substring(1, text.length - 1)
            if (!inner.contains(open) && !inner.contains(close)) return inner.trim()
        }
    }
    return text
}
