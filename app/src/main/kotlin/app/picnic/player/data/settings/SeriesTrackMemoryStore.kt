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

@Singleton
class SeriesTrackMemoryStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {
    suspend fun effectiveMemory(itemId: String, seasonId: String?) = resolveEffectiveMemory(read(itemId), seasonId)

    suspend fun read(itemId: String): SeriesTrackMemoryRecord? {
        val raw = dataStore.data.map { it[key(itemId)] }.first() ?: return null
        return decodeRecord(raw)
    }

    suspend fun rememberOsdPick(
        itemId: String,
        seasonId: String?,
        kind: TrackMemoryKind,
        pick: RememberedTrack
    ) {
        val prefsKey = key(itemId)
        dataStore.edit { prefs ->
            val existing = prefs[prefsKey]?.let { decodeRecord(it) }
            val seriesPref = when (kind) {
                TrackMemoryKind.AUDIO -> existing?.audio
                TrackMemoryKind.SUBTITLE -> existing?.subtitle
            }
            val scope = decideTrackMemoryWriteScope(seriesPref, pick, seasonId)
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

    private fun key(itemId: String) = stringPreferencesKey("$KEY_PREFIX$itemId")

    private companion object {
        const val KEY_PREFIX = "playback.seriesTrackMemory."
    }
}
