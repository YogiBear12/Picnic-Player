package app.picnic.player.data.playback

import java.util.Locale
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType

data class TrackPick(
    val audioIndex: Int?,
    val subtitleIndex: Int?
)

/**
 * @param alwaysDisplaySubtitles true = Always, false = Smart
 * @param preferDefaultAudioTrack ignores [preferredAudioLanguage]; subtitles still follow the
 *   language of whichever audio track that lands on
 */
fun pickTracks(
    streams: List<MediaStream>,
    preferredAudioLanguage: String?,
    preferredSubtitleLanguage: String?,
    deviceSubtitleLanguage: String,
    alwaysDisplaySubtitles: Boolean,
    preferDefaultAudioTrack: Boolean = false
): TrackPick {
    val audioIndex = pickAudioIndex(streams, preferredAudioLanguage, preferDefaultAudioTrack)
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
    preferredAudioLanguage: String?,
    preferDefaultAudioTrack: Boolean = false
): Int? {
    val audios = streams.filter { it.type == MediaStreamType.AUDIO }
    if (audios.isEmpty()) return null
    val pool = audios.filter { !it.isCommentaryStyle() || it.isDefault }
    val candidates = pool.ifEmpty { audios }

    if (!preferDefaultAudioTrack && !preferredAudioLanguage.isNullOrBlank()) {
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
        return pickFullInLanguage(subs, preferred) ?: pickForcedInLanguage(subs, preferred)
    }

    val audioMatchesPreferred = selectedAudioLanguage != null &&
        languageMatches(selectedAudioLanguage, preferred)
    return if (audioMatchesPreferred) {
        pickForcedInLanguage(subs, preferred)
    } else {
        pickFullInLanguage(subs, preferred)
    }
}

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

private fun pickFullInLanguage(subs: List<MediaStream>, language: String): Int? = preferExternalThenDefault(
    subs.filter { languageMatches(it.language, language) && !it.isForced }
)?.index

private fun pickForcedInLanguage(subs: List<MediaStream>, language: String): Int? {
    val forced = subs.filter { it.isForced }
    val matches = forced.filter { languageMatches(it.language, language) }
        .ifEmpty { forced.filter { it.language.isUndefinedLanguage() } }
    return preferExternalThenDefault(matches)?.index
}

private fun preferExternalThenDefault(subs: List<MediaStream>): MediaStream? = preferDefaultElseFirst(subs.filter { it.isExternal })
    ?: preferDefaultElseFirst(subs)

private fun String?.isUndefinedLanguage(): Boolean = isNullOrBlank() || trim().lowercase(Locale.ROOT) in UNDEFINED_LANGUAGES

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

private val UNDEFINED_LANGUAGES = setOf("und", "unknown", "undetermined", "mul", "zxx")
