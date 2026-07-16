package app.picnic.player.data.playback

import java.util.Locale
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType

/**
 * One remembered audio or subtitle choice (#15 stage 2, R1 / O1).
 * [off] is subtitle-only: explicit Off with no language/title.
 */
@Serializable
data class RememberedTrack(
    val language: String? = null,
    val title: String? = null,
    val off: Boolean = false
) {
    companion object {
        fun off(): RememberedTrack = RememberedTrack(off = true)

        /**
         * Build a remembered track from a stream (R1). Returns null when title is blank —
         * language-only memory cannot match multi-dub releases confidently.
         */
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

/** Series-level prefs plus optional per-season overrides (W2). */
@Serializable
data class SeriesTrackMemoryRecord(
    val audio: RememberedTrack? = null,
    val subtitle: RememberedTrack? = null,
    /** Season Jellyfin id → override; only fields set here override series. */
    val seasons: Map<String, SeasonTrackMemory> = emptyMap()
)

@Serializable
data class SeasonTrackMemory(
    val audio: RememberedTrack? = null,
    val subtitle: RememberedTrack? = null
)

/** Where an OSD pick should be persisted (W2). */
enum class TrackMemoryWriteScope {
    SERIES,
    SEASON
}

/**
 * Effective remembered tracks after season → series hierarchy (no global yet).
 */
data class EffectiveTrackMemory(
    val audio: RememberedTrack? = null,
    val subtitle: RememberedTrack? = null
)

/**
 * Resolve season override → series for one show.
 * [seasonId] null skips season layer (still applies series).
 */
fun resolveEffectiveMemory(
    record: SeriesTrackMemoryRecord?,
    seasonId: String?
): EffectiveTrackMemory {
    if (record == null) return EffectiveTrackMemory()
    val season = seasonId?.let { record.seasons[it] }
    return EffectiveTrackMemory(
        audio = season?.audio ?: record.audio,
        subtitle = season?.subtitle ?: record.subtitle
    )
}

/**
 * Playback-start pick: season → series memory (confident match) else stage-1 globals (S1).
 */
fun pickTracksWithMemory(
    streams: List<MediaStream>,
    memory: EffectiveTrackMemory?,
    preferredAudioLanguage: String?,
    preferredSubtitleLanguage: String?,
    deviceSubtitleLanguage: String,
    alwaysDisplaySubtitles: Boolean
): TrackPick {
    val global = pickTracks(
        streams = streams,
        preferredAudioLanguage = preferredAudioLanguage,
        preferredSubtitleLanguage = preferredSubtitleLanguage,
        deviceSubtitleLanguage = deviceSubtitleLanguage,
        alwaysDisplaySubtitles = alwaysDisplaySubtitles
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
            // Missed title must not reuse global.subtitleIndex (chosen under global audio).
            matchRememberedTrack(streams, MediaStreamType.SUBTITLE, memory.subtitle)
                ?: smartSubtitlesForSelectedAudio()
        memory.audio != null && audioIndex != global.audioIndex -> smartSubtitlesForSelectedAudio()
        else -> global.subtitleIndex
    }

    return TrackPick(audioIndex = audioIndex, subtitleIndex = subtitleIndex)
}

/**
 * Match a remembered track against [streams] of [type].
 * Language filter → exact title → conservative fuzzy (F1). Returns null on no clear winner.
 * [RememberedTrack.off] never matches a stream (caller treats as explicit Off).
 */
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

/**
 * W2: seed/update series when aligned; season-only when the pick diverges from series memory.
 * Requires a season id to write a season override; without one, always writes series.
 */
fun decideTrackMemoryWriteScope(
    seriesPreference: RememberedTrack?,
    pick: RememberedTrack,
    seasonId: String?
): TrackMemoryWriteScope {
    if (seriesPreference == null) return TrackMemoryWriteScope.SERIES
    if (rememberedTracksEquivalent(seriesPreference, pick)) return TrackMemoryWriteScope.SERIES
    if (seasonId.isNullOrBlank()) return TrackMemoryWriteScope.SERIES
    return TrackMemoryWriteScope.SEASON
}

/** Apply W2 write into a record (does not wipe series when writing season). */
fun applyTrackMemoryWrite(
    existing: SeriesTrackMemoryRecord?,
    seasonId: String?,
    kind: TrackMemoryKind,
    pick: RememberedTrack,
    scope: TrackMemoryWriteScope
): SeriesTrackMemoryRecord {
    val base = existing ?: SeriesTrackMemoryRecord()
    return when (scope) {
        TrackMemoryWriteScope.SERIES -> {
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

/** Title string stored for memory — prefer file [MediaStream.title], else OSD [MediaStream.displayTitle]. */
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

/**
 * F1: high-confidence fuzzy only. Strip bracket tags, then require near-equality or
 * strong containment with a unique winner. Never picks among similar dub names.
 */
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
