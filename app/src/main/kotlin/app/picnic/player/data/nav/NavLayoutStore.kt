package app.picnic.player.data.nav

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists [NavLayout] per Jellyfin (serverId, userId). On first resolve for a
 * profile, migrates the legacy Seerr `show_discover` boolean into Discover's
 * pin state and deletes that pref.
 */
@Singleton
class NavLayoutStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {

    /**
     * Load + reconcile against [availableIds], persist the result, and one-shot
     * migrate/clear `seerr.show_discover` when creating the first layout.
     */
    suspend fun resolve(
        serverId: String,
        userId: String,
        availableIds: List<String>
    ): NavLayout {
        val key = layoutKey(serverId, userId)
        val raw = dataStore.data.map { it[stringPreferencesKey(key)] }.first()
        val saved = raw?.let { runCatching { json.decodeFromString<NavLayout>(it) }.getOrNull() }
        val legacyDiscoverPinned = if (saved == null) {
            dataStore.data.map {
                it[booleanPreferencesKey(showDiscoverKey(serverId, userId))]
            }.first() ?: true
        } else {
            true
        }
        val resolved = NavLayoutResolver.reconcile(availableIds, saved, legacyDiscoverPinned)
        persist(serverId, userId, resolved)
        if (saved == null) {
            dataStore.edit { it.remove(booleanPreferencesKey(showDiscoverKey(serverId, userId))) }
        }
        return resolved
    }

    suspend fun save(serverId: String, userId: String, layout: NavLayout) {
        persist(serverId, userId, layout)
    }

    suspend fun clear(serverId: String, userId: String) {
        dataStore.edit {
            it.remove(stringPreferencesKey(layoutKey(serverId, userId)))
            it.remove(booleanPreferencesKey(showDiscoverKey(serverId, userId)))
        }
    }

    private suspend fun persist(serverId: String, userId: String, layout: NavLayout) {
        dataStore.edit {
            it[stringPreferencesKey(layoutKey(serverId, userId))] = json.encodeToString(layout)
        }
    }

    private fun layoutKey(serverId: String, userId: String) = "nav.layout.$serverId.$userId"

    private fun showDiscoverKey(serverId: String, userId: String) = "seerr.show_discover.$serverId.$userId"
}
