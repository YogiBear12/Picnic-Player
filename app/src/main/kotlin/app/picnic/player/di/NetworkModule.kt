package app.picnic.player.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * The app's one base [OkHttpClient].
 *
 * Constructing a client eagerly builds an SSLContext — measured at ~400ms on TV hardware — so each
 * additional client pays that again, on whichever thread happened to create it. Callers needing
 * their own timeouts or cookie jar derive from this with `newBuilder()`, which carries the
 * connection pool, dispatcher and SSL factory over instead of building new ones.
 *
 * Inject it as [dagger.Lazy] so the cost lands on the first network call, off the main thread,
 * rather than during injection.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient()
}
