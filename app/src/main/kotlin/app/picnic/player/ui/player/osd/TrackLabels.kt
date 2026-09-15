package app.picnic.player.ui.player.osd

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

private val CodecNames = mapOf(
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
            isForced -> add("Forced")
            isHearingImpaired -> add("SDH")
            isExternal -> add("EXT")
        }
        codecChip(codec)?.let { add(it) }
    }
    return TrackLabel(stripKnownTokens(title, languageName, languageTag, codec), chips)
}

internal fun codecChip(codec: String?): String? {
    val key = codec?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
    return CodecNames[key] ?: key.uppercase(Locale.ROOT)
}

private fun stripKnownTokens(
    title: String?,
    languageName: String,
    languageTag: String?,
    codec: String?
): String? {
    val raw = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val drop = buildList {
        add(languageName)
        languageTag?.let { add(it) }
        codec?.let { add(it) }
        codecChip(codec)?.let { add(it) }
        addAll(FlagWords)
    }.filter { it.isNotBlank() }

    val kept = raw.split(" - ", " · ")
        .map { part -> cleanPart(part, drop) }
        .filter { it.isNotEmpty() }
    return kept.joinToString(" · ").takeIf { it.isNotEmpty() }
}

private fun cleanPart(part: String, drop: List<String>): String {
    var text = part.trim()
    for (token in drop) {
        text = text.replace(Regex("(?i)\\b${Regex.escape(token)}\\b"), " ")
    }
    return unwrap(
        text
            .replace(Regex("\\(\\s*\\)|\\[\\s*]"), " ")
            .replace(Regex("\\s{2,}"), " ")
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
