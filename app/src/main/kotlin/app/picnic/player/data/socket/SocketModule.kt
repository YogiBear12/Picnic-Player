package app.picnic.player.data.socket

import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.UserSession
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

/** The reactive active-session signal, so consumers bind the flow, not the repo. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ActiveSession

@Module
@InstallIn(SingletonComponent::class)
abstract class SocketModule {
    @Binds
    @Singleton
    abstract fun sessionSocketFactory(impl: JellyfinSessionSocketFactory): SessionSocketFactory

    @Binds
    @Singleton
    abstract fun appForegroundState(impl: ProcessAppForegroundState): AppForegroundState

    companion object {
        @Provides
        @ActiveSession
        fun activeSession(authRepository: AuthRepository): StateFlow<UserSession?> = authRepository.activeSessionFlow
    }
}
