package app.picnic.player.data.media

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.picnic.player.data.auth.UserScope
import java.util.UUID
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
    suspend fun load(scope: UserScope, libraryId: UUID? = null): DiscoveryPlan? {
        val raw = dataStore.data.first()[key(scope, libraryId)] ?: return null
        return runCatching { json.decodeFromString<DiscoveryPlan>(raw) }.getOrNull()
    }

    suspend fun save(scope: UserScope, plan: DiscoveryPlan, libraryId: UUID? = null) {
        dataStore.edit { it[key(scope, libraryId)] = json.encodeToString(plan) }
    }

    private fun key(scope: UserScope, libraryId: UUID?): Preferences.Key<String> = stringPreferencesKey(scope.key(libraryId?.let { "$KEY_NAME.$it" } ?: KEY_NAME))

    private companion object {
        const val KEY_NAME = "home.discoveryPlan"
    }
}
