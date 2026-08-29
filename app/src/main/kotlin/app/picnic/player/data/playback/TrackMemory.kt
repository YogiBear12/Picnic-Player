package app.picnic.player.data.playback

import java.util.Locale
import java.util.UUID
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType

@Serializable
data class RememberedTrack(
    val language: String? = null,
    val title: String? = null,
    /** Subtitle-only: explicit Off, with no language or title. Never matches a stream. */
    val off: Boolean = false
) {
    companion object {
        fun off(): RememberedTrack = RememberedTrack(off = true)

        fun of(stream: MediaStream): RememberedTrack? {
            val title = stream.memoryTitle() ?: return null
            return RememberedTrack(
                language = stream.language?.trim()?.takeIf { it.isNotEmpty() },
                title = title,
                off = false
            )
        }
    }
}

@Serializable
data class TrackMemoryRecord(
    val audio: RememberedTrack? = null,
    val subtitle: RememberedTrack? = null,
    val seasons: Map<String, SeasonTrackMemory> = emptyMap()
)

@Serializable
data class SeasonTrackMemory(
    val audio: RememberedTrack? = null,
    val subtitle: RememberedTrack? = null
)

enum class TrackMemoryWriteScope {
    ITEM,
    SEASON
}

fun trackMemoryKey(type: BaseItemKind?, itemId: UUID?, seriesId: UUID?): String? = when (type) {
    BaseItemKind.EPISODE -> seriesId?.toString()
    BaseItemKind.MOVIE -> itemId?.toString()
    else -> null
}

data class EffectiveTrackMemory(
    val audio: RememberedTrack? = null,
    val subtitle: RememberedTrack? = null
)

fun resolveEffectiveMemory(
    record: TrackMemoryRecord?,
    seasonId: String?
): EffectiveTrackMemory {
    if (record == null) return EffectiveTrackMemory()
    val season = seasonId?.let { record.seasons[it] }
    return EffectiveTrackMemory(
        audio = season?.audio ?: record.audio,
        subtitle = season?.subtitle ?: record.subtitle
    )
}

fun pickTracksWithMemory(
    streams: List<MediaStream>,
    memory: EffectiveTrackMemory?,
    preferredAudioLanguage: String?,
    preferredSubtitleLanguage: String?,
    deviceSubtitleLanguage: String,
    alwaysDisplaySubtitles: Boolean,
    preferDefaultAudioTrack: Boolean = false
): TrackPick {
    val global = pickTracks(
        streams = streams,
        preferredAudioLanguage = preferredAudioLanguage,
        preferredSubtitleLanguage = preferredSubtitleLanguage,
        deviceSubtitleLanguage = deviceSubtitleLanguage,
        alwaysDisplaySubtitles = alwaysDisplaySubtitles,
        preferDefaultAudioTrack = preferDefaultAudioTrack
    )
    if (memory == null) return global

    val audioIndex = memory.audio?.let { matchRememberedTrack(streams, MediaStreamType.AUDIO, it) }
        ?: global.audioIndex

    fun smartSubtitlesForSelectedAudio(): Int? {
        val audioLanguage = streams.firstOrNull { it.index == audioIndex }?.language
        return pickSubtitleIndex(
            streams = streams,
            preferredSubtitleLanguage = preferredSubtitleLanguage,
            deviceSubtitleLanguage = deviceSubtitleLanguage,
            alwaysDisplaySubtitles = alwaysDisplaySubtitles,
            selectedAudioLanguage = audioLanguage
        )
    }

    val subtitleIndex = when {
        memory.subtitle?.off == true -> null
        memory.subtitle != null ->
            matchRememberedTrack(streams, MediaStreamType.SUBTITLE, memory.subtitle)
                ?: smartSubtitlesForSelectedAudio()
        memory.audio != null && audioIndex != global.audioIndex -> smartSubtitlesForSelectedAudio()
        else -> global.subtitleIndex
    }

    return TrackPick(audioIndex = audioIndex, subtitleIndex = subtitleIndex)
}

fun matchRememberedTrack(
    streams: List<MediaStream>,
    type: MediaStreamType,
    remembered: RememberedTrack
): Int? {
    if (remembered.off) return null
    val rememberedTitle = remembered.title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val candidates = streams.filter { it.type == type }
        .filter { remembered.language.isNullOrBlank() || languageMatches(it.language, remembered.language!!) }
    if (candidates.isEmpty()) return null

    exactTitleMatch(candidates, rememberedTitle)?.let { return it.index }

    return conservativeFuzzyMatch(candidates, rememberedTitle)?.index
}

fun decideTrackMemoryWriteScope(
    itemPreference: RememberedTrack?,
    pick: RememberedTrack,
    seasonId: String?
): TrackMemoryWriteScope {
    if (itemPreference == null) return TrackMemoryWriteScope.ITEM
    if (rememberedTracksEquivalent(itemPreference, pick)) return TrackMemoryWriteScope.ITEM
    if (seasonId.isNullOrBlank()) return TrackMemoryWriteScope.ITEM
    return TrackMemoryWriteScope.SEASON
}

fun applyTrackMemoryWrite(
    existing: TrackMemoryRecord?,
    seasonId: String?,
    kind: TrackMemoryKind,
    pick: RememberedTrack,
    scope: TrackMemoryWriteScope
): TrackMemoryRecord {
    val base = existing ?: TrackMemoryRecord()
    return when (scope) {
        TrackMemoryWriteScope.ITEM -> {
            val clearedSeason = if (!seasonId.isNullOrBlank()) {
                clearSeasonKind(base.seasons, seasonId, kind)
            } else {
                base.seasons
            }
            when (kind) {
                TrackMemoryKind.AUDIO -> base.copy(audio = pick, seasons = clearedSeason)
                TrackMemoryKind.SUBTITLE -> base.copy(subtitle = pick, seasons = clearedSeason)
            }
        }
        TrackMemoryWriteScope.SEASON -> {
            val sid = seasonId ?: return base
            val season = base.seasons[sid] ?: SeasonTrackMemory()
            val updated = when (kind) {
                TrackMemoryKind.AUDIO -> season.copy(audio = pick)
                TrackMemoryKind.SUBTITLE -> season.copy(subtitle = pick)
            }
            base.copy(seasons = base.seasons + (sid to updated))
        }
    }
}

enum class TrackMemoryKind { AUDIO, SUBTITLE }

fun MediaStream.memoryTitle(): String? = title?.trim()?.takeIf { it.isNotEmpty() }
    ?: displayTitle?.trim()?.takeIf { it.isNotEmpty() }

internal fun rememberedTracksEquivalent(a: RememberedTrack, b: RememberedTrack): Boolean {
    if (a.off && b.off) return true
    if (a.off != b.off) return false
    val langOk = when {
        a.language.isNullOrBlank() && b.language.isNullOrBlank() -> true
        a.language.isNullOrBlank() || b.language.isNullOrBlank() -> false
        else -> languageMatches(a.language, b.language!!)
    }
    if (!langOk) return false
    return normalizeExactTitle(a.title) == normalizeExactTitle(b.title)
}

private fun clearSeasonKind(
    seasons: Map<String, SeasonTrackMemory>,
    seasonId: String,
    kind: TrackMemoryKind
): Map<String, SeasonTrackMemory> {
    val season = seasons[seasonId] ?: return seasons
    val updated = when (kind) {
        TrackMemoryKind.AUDIO -> season.copy(audio = null)
        TrackMemoryKind.SUBTITLE -> season.copy(subtitle = null)
    }
    return if (updated.audio == null && updated.subtitle == null) {
        seasons - seasonId
    } else {
        seasons + (seasonId to updated)
    }
}

private fun exactTitleMatch(candidates: List<MediaStream>, rememberedTitle: String): MediaStream? {
    val want = normalizeExactTitle(rememberedTitle) ?: return null
    val hits = candidates.filter { stream ->
        stream.titleCandidates().any { normalizeExactTitle(it) == want }
    }
    return hits.singleOrNull()
}

private fun conservativeFuzzyMatch(candidates: List<MediaStream>, rememberedTitle: String): MediaStream? {
    val want = normalizeFuzzyTitle(rememberedTitle) ?: return null
    if (want.length < 6) return null

    data class Scored(val stream: MediaStream, val score: Double)
    val scored = candidates.mapNotNull { stream ->
        val best = stream.titleCandidates().mapNotNull { normalizeFuzzyTitle(it) }
            .maxOfOrNull { fuzzyScore(want, it) } ?: 0.0
        if (best >= FUZZY_MIN_SCORE) Scored(stream, best) else null
    }.sortedByDescending { it.score }

    if (scored.isEmpty()) return null
    val best = scored.first()
    val second = scored.getOrNull(1)
    if (second != null && best.score - second.score < FUZZY_UNIQUE_GAP) return null
    if (second != null && second.score >= FUZZY_MIN_SCORE && best.score < 1.0) return null
    return best.stream
}

private fun MediaStream.titleCandidates(): List<String> = listOfNotNull(title, displayTitle)
    .map { it.trim() }.filter { it.isNotEmpty() }.distinct()

private fun normalizeExactTitle(raw: String?): String? = raw?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

private fun normalizeFuzzyTitle(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return raw.lowercase(Locale.ROOT)
        .replace(BRACKET_TAG, " ")
        .replace(NON_ALNUM, " ")
        .replace(WHITESPACE, " ")
        .trim()
        .takeIf { it.isNotEmpty() }
}

private fun fuzzyScore(a: String, b: String): Double {
    if (a == b) return 1.0
    val (shorter, longer) = if (a.length <= b.length) a to b else b to a
    if (longer.contains(shorter)) {
        val ratio = shorter.length.toDouble() / longer.length
        if (ratio >= FUZZY_CONTAIN_RATIO) return ratio
    }
    val ta = a.split(' ').filter { it.length > 1 }.toSet()
    val tb = b.split(' ').filter { it.length > 1 }.toSet()
    if (ta.isEmpty() || tb.isEmpty()) return 0.0
    val inter = ta.intersect(tb).size.toDouble()
    val union = ta.union(tb).size.toDouble()
    return inter / union
}

private const val FUZZY_MIN_SCORE = 0.92
private const val FUZZY_UNIQUE_GAP = 0.15
private const val FUZZY_CONTAIN_RATIO = 0.88

private val BRACKET_TAG = Regex("""\[[^\]]*\]""")
private val NON_ALNUM = Regex("""[^a-z0-9.]+""")
private val WHITESPACE = Regex("""\s+""")
