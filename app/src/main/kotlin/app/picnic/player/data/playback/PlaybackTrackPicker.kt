package app.picnic.player.data.playback

import java.util.Locale
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType

/** Result of client-side audio + subtitle pick at playback start. */
data class TrackPick(
    val audioIndex: Int?,
    val subtitleIndex: Int?
)

/**
 * Device-local track selection for #15 stage 1. Beats Jellyfin server defaults for this
 * install. Pure logic — no I/O — so JVM unit tests can cover Smart / Always fixtures.
 *
 * @param preferredAudioLanguage two-letter (or three-letter) ISO code, or null = Unspecified
 * @param preferredSubtitleLanguage two-letter (or three-letter) ISO code, or null = Unspecified
 *   (Unspecified subtitle preference resolves to [deviceSubtitleLanguage])
 * @param deviceSubtitleLanguage language from the device locale; used when subtitle pref is Unspecified
 * @param alwaysDisplaySubtitles true = Always; false = Smart
 */
fun pickTracks(
    streams: List<MediaStream>,
    preferredAudioLanguage: String?,
    preferredSubtitleLanguage: String?,
    deviceSubtitleLanguage: String,
    alwaysDisplaySubtitles: Boolean
): TrackPick {
    val audioIndex = pickAudioIndex(streams, preferredAudioLanguage)
    val audioLanguage = streams.firstOrNull { it.index == audioIndex }?.language
    val subtitleIndex = pickSubtitleIndex(
        streams = streams,
        preferredSubtitleLanguage = preferredSubtitleLanguage,
        deviceSubtitleLanguage = deviceSubtitleLanguage,
        alwaysDisplaySubtitles = alwaysDisplaySubtitles,
        selectedAudioLanguage = audioLanguage
    )
    return TrackPick(audioIndex = audioIndex, subtitleIndex = subtitleIndex)
}

fun pickAudioIndex(
    streams: List<MediaStream>,
    preferredAudioLanguage: String?
): Int? {
    val audios = streams.filter { it.type == MediaStreamType.AUDIO }
    if (audios.isEmpty()) return null
    // Commentary-style extras are never auto-picked unless marked Default.
    val pool = audios.filter { !it.isCommentaryStyle() || it.isDefault }
    val candidates = pool.ifEmpty { audios }

    if (!preferredAudioLanguage.isNullOrBlank()) {
        val matches = candidates.filter { languageMatches(it.language, preferredAudioLanguage) }
        preferDefaultElseFirst(matches)?.let { return it.index }
    }
    return preferDefaultElseFirst(candidates)?.index
}

fun pickSubtitleIndex(
    streams: List<MediaStream>,
    preferredSubtitleLanguage: String?,
    deviceSubtitleLanguage: String,
    alwaysDisplaySubtitles: Boolean,
    selectedAudioLanguage: String?
): Int? {
    val subs = streams.filter { it.type == MediaStreamType.SUBTITLE }
    if (subs.isEmpty()) return null
    val preferred = preferredSubtitleLanguage?.takeIf { it.isNotBlank() }
        ?: deviceSubtitleLanguage.takeIf { it.isNotBlank() }
        ?: return null

    if (alwaysDisplaySubtitles) {
        return pickAnyInLanguage(subs, preferred)
    }

    // Smart: compare selected audio language to preferred subtitle language.
    // Match → Forced in preferred only (else none). No secondary-language fallback.
    val audioMatchesPreferred = selectedAudioLanguage != null &&
        languageMatches(selectedAudioLanguage, preferred)
    return if (audioMatchesPreferred) {
        pickForcedInLanguage(subs, preferred)
    } else {
        pickFullInLanguage(subs, preferred)
    }
}

/** True when [streamLang] and [preferred] name the same language (iso2/iso3 tolerant). */
fun languageMatches(streamLang: String?, preferred: String): Boolean {
    if (streamLang.isNullOrBlank()) return false
    return normalizeLanguage(streamLang) == normalizeLanguage(preferred)
}

internal fun normalizeLanguage(raw: String): String {
    val tag = raw.trim().lowercase(Locale.ROOT).replace('_', '-').substringBefore('-')
    if (tag.length == 3) {
        iso3ToIso2(tag)?.let { return it }
    }
    return tag
}

private fun pickAnyInLanguage(subs: List<MediaStream>, language: String): Int? = preferDefaultElseFirst(
    subs.filter { languageMatches(it.language, language) }
)?.index

/** Full (non-Forced) subtitle in [language]: Default else first among matches. */
private fun pickFullInLanguage(subs: List<MediaStream>, language: String): Int? = preferDefaultElseFirst(
    subs.filter { languageMatches(it.language, language) && !it.isForced }
)?.index

private fun pickForcedInLanguage(subs: List<MediaStream>, language: String): Int? = preferDefaultElseFirst(
    subs.filter { languageMatches(it.language, language) && it.isForced }
)?.index

private fun preferDefaultElseFirst(streams: List<MediaStream>): MediaStream? = streams.firstOrNull { it.isDefault } ?: streams.firstOrNull()

private fun MediaStream.isCommentaryStyle(): Boolean {
    val haystack = listOfNotNull(title, displayTitle, comment).joinToString(" ")
    return COMMENTARY.containsMatchIn(haystack)
}

private fun iso3ToIso2(code: String): String? {
    val lower = code.lowercase(Locale.ROOT)
    for (lang in Locale.getISOLanguages()) {
        val locale = Locale(lang)
        if (locale.isO3Language.equals(lower, ignoreCase = true)) return lang
    }
    return null
}

private val COMMENTARY = Regex("commentary", RegexOption.IGNORE_CASE)
