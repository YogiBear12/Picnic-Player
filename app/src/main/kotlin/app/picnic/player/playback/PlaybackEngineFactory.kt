package app.picnic.player.playback

import android.content.Context
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * Builds a [PlaybackEngine] per viewing, holding the pieces that outlive one — the app context and
 * the shared HTTP client. Injected rather than constructed at the call site so a playback engine
 * is never built with a private client: each one costs an SSLContext (~400ms on TV hardware) on
 * whichever thread happens to create it, which for the player is the main thread.
 */
@Singleton
class PlaybackEngineFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: Lazy<OkHttpClient>
) {
    fun create(): PlaybackEngine = PlaybackEngine(context, httpClient.get())
}
