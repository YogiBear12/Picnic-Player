package app.picnic.player.data.media

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.picnic.player.data.auth.UserScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
internal class DiscoveryStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {
    suspend fun load(scope: UserScope): DiscoveryPlan? {
        val raw = dataStore.data.first()[key(scope)] ?: return null
        return runCatching { json.decodeFromString<DiscoveryPlan>(raw) }.getOrNull()
    }

    suspend fun save(scope: UserScope, plan: DiscoveryPlan) {
        dataStore.edit { it[key(scope)] = json.encodeToString(plan) }
    }

    private fun key(scope: UserScope): Preferences.Key<String> = stringPreferencesKey(scope.key(KEY_NAME))

    private companion object {
        const val KEY_NAME = "home.discoveryPlan"
    }
}
