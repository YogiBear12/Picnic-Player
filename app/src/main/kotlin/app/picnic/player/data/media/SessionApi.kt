package app.picnic.player.data.media

import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.jellyfin.JellyfinFactory
import app.picnic.player.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient

@Singleton
class SessionApi @Inject constructor(
    private val jellyfin: JellyfinFactory,
    private val authRepository: AuthRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun session(): UserSession = authRepository.requireSession()

    suspend fun client(): ApiClient = session().let { jellyfin.api(it.server.baseUrl, it.accessToken) }

    suspend fun <T> onIo(block: suspend () -> T): T = withContext(ioDispatcher) { block() }
}
