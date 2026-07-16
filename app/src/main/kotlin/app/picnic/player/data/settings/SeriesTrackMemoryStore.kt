package app.picnic.player.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.picnic.player.data.playback.RememberedTrack
import app.picnic.player.data.playback.SeriesTrackMemoryRecord
import app.picnic.player.data.playback.TrackMemoryKind
import app.picnic.player.data.playback.TrackMemoryWriteScope
import app.picnic.player.data.playback.applyTrackMemoryWrite
import app.picnic.player.data.playback.decideTrackMemoryWriteScope
import app.picnic.player.data.playback.resolveEffectiveMemory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Device-local series/season track memory (#15 stage 2).
 *
 * Storage keys: one Preferences string per series —
 * `playback.seriesTrackMemory.<seriesId>` → JSON [SeriesTrackMemoryRecord].
 * Does not write Jellyfin user configuration.
 */
@Singleton
class SeriesTrackMemoryStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {
    /** Effective memory for an episode after season → series hierarchy. */
    suspend fun effectiveMemory(seriesId: String, seasonId: String?) = resolveEffectiveMemory(read(seriesId), seasonId)

    suspend fun read(seriesId: String): SeriesTrackMemoryRecord? {
        val raw = dataStore.data.map { it[key(seriesId)] }.first() ?: return null
        return decodeRecord(raw)
    }

    /**
     * Persist an OSD pick (L1/W2). [seasonId] required for season overrides when the pick
     * diverges from existing series memory.
     *
     * Decode + merge + encode run inside a single [DataStore.edit] so concurrent audio and
     * subtitle writes cannot clobber each other.
     */
    suspend fun rememberOsdPick(
        seriesId: String,
        seasonId: String?,
        kind: TrackMemoryKind,
        pick: RememberedTrack
    ) {
        val prefsKey = key(seriesId)
        dataStore.edit { prefs ->
            val existing = prefs[prefsKey]?.let { decodeRecord(it) }
            val seriesPref = when (kind) {
                TrackMemoryKind.AUDIO -> existing?.audio
                TrackMemoryKind.SUBTITLE -> existing?.subtitle
            }
            val scope = decideTrackMemoryWriteScope(seriesPref, pick, seasonId)
            // Season override needs a season id; fall back to series if missing.
            val effectiveScope =
                if (scope == TrackMemoryWriteScope.SEASON && seasonId.isNullOrBlank()) {
                    TrackMemoryWriteScope.SERIES
                } else {
                    scope
                }
            val updated = applyTrackMemoryWrite(existing, seasonId, kind, pick, effectiveScope)
            prefs[prefsKey] = json.encodeToString(updated)
        }
    }

    private fun decodeRecord(raw: String): SeriesTrackMemoryRecord? = runCatching {
        json.decodeFromString<SeriesTrackMemoryRecord>(raw)
    }.getOrNull()

    private fun key(seriesId: String) = stringPreferencesKey("$KEY_PREFIX$seriesId")

    private companion object {
        const val KEY_PREFIX = "playback.seriesTrackMemory."
    }
}
