package app.picnic.player.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import app.picnic.player.data.settings.settingsKeyMigration
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json

// Migrations run here rather than at app start so DataStore applies them before any
// consumer's first read — no one can observe a half-migrated file.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "picnic",
    produceMigrations = { listOf(settingsKeyMigration()) }
)

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.dataStore

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
