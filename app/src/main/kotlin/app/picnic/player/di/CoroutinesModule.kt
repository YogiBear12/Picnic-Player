package app.picnic.player.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Application-lifetime scope for fire-and-forget work that must outlive a screen
 *  (e.g. the final "playback stopped" report sent as the player tears down). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * The dispatcher the data layer runs network + deserialization work on. Repository
 * calls resume the SDK's continuation here (not on the caller's dispatcher), so
 * kotlinx.serialization parsing of large Jellyfin responses never lands on the main
 * thread — the direct cause of cold-start frame drops and the "slideshow" spinners.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
