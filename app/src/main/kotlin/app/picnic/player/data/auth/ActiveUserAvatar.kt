package app.picnic.player.data.auth

import app.picnic.player.data.jellyfin.JellyfinImages
import app.picnic.player.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The signed-in user's avatar URL, resolved once per session and shared by every screen that
 * shows it (nav drawer, Settings) so they hit one cache entry instead of three.
 *
 * The public-user list is refetched when the session changes rather than read from the local
 * cache: the URL is only as fresh as the image tag in it, and a stale tag means a replaced
 * avatar never reaches the screen (Coil serves a cached URL forever — see #191).
 */
@Singleton
class ActiveUserAvatar @Inject constructor(
    private val authRepository: AuthRepository,
    @ApplicationScope scope: CoroutineScope
) {
    val url: StateFlow<String?> = authRepository.activeSessionFlow
        .map { session -> session?.let { resolve(it) } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private suspend fun resolve(session: UserSession): String {
        val users = runCatching { authRepository.publicUsers(session.server) }
            .getOrElse { authRepository.cachedPublicUsers(session.server.id) }
        val tag = users.firstOrNull { it.id == session.userId }?.primaryImageTag
        return JellyfinImages.userPrimary(session.server.baseUrl, session.userId, tag)
    }
}
