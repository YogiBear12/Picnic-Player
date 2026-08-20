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
