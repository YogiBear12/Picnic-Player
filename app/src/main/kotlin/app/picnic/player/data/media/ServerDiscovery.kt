package app.picnic.player.data.media

import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.ServerConnection
import app.picnic.player.di.ApplicationScope
import app.picnic.player.di.IoDispatcher
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App-scoped LAN server discovery. Started during splash/bootstrap so the first
 * UDP replies can already be on screen when server entry appears. Servers are
 * published **as they arrive** (not after the full scan timeout).
 */
@Singleton
class ServerDiscovery @Inject constructor(
    private val authRepository: AuthRepository,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val _servers = MutableStateFlow<List<ServerConnection>>(emptyList())
    val servers: StateFlow<List<ServerConnection>> = _servers.asStateFlow()

    private val _discovering = MutableStateFlow(false)
    val discovering: StateFlow<Boolean> = _discovering.asStateFlow()

    private val started = AtomicBoolean(false)

    /** Idempotent: kicks off discovery once for the process. */
    fun ensureStarted() {
        if (!started.compareAndSet(false, true)) return
        _discovering.value = true
        scope.launch {
            try {
                withContext(ioDispatcher) {
                    authRepository.discoverServers().collect { server ->
                        _servers.update { current ->
                            if (current.any { it.id == server.id || it.baseUrl == server.baseUrl }) {
                                current
                            } else {
                                current + server
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
                // Keep any servers already found; discovering clears in finally.
            } finally {
                _discovering.value = false
            }
        }
    }
}
