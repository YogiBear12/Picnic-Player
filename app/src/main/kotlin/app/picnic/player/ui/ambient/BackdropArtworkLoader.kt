package app.picnic.player.ui.ambient

import android.content.Context
import coil3.Image
import coil3.imageLoader
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.Size
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private const val MAX_CONCURRENT_BACKDROPS = 2

interface ArtworkSource {
    fun peek(url: String): Image?
    suspend fun load(url: String): Image?
}

@Singleton
class BackdropArtworkLoader @Inject constructor(
    @ApplicationContext private val context: Context
) : ArtworkSource {
    private val flights = SharedFlights { url ->
        (context.imageLoader.execute(backdropRequest(context, url)) as? SuccessResult)?.image
    }

    override fun peek(url: String): Image? = context.imageLoader.memoryCache?.get(MemoryCache.Key(url))?.image

    override suspend fun load(url: String): Image? = flights.load(url)
}

private fun backdropRequest(context: Context, url: String) = ImageRequest.Builder(context)
    .data(url)
    .memoryCacheKey(url)
    .size(Size.ORIGINAL)
    .scale(Scale.FIT)
    .precision(Precision.INEXACT)
    .build()

internal class SharedFlights(
    private val scope: CoroutineScope,
    private val fetch: suspend (String) -> Image?
) {
    constructor(fetch: suspend (String) -> Image?) : this(
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
        fetch
    )

    private class Flight(val deferred: Deferred<Image?>, var waiters: Int)

    private val lock = Mutex()
    private val inFlight = mutableMapOf<String, Flight>()
    private val gate = Semaphore(MAX_CONCURRENT_BACKDROPS)

    suspend fun load(url: String): Image? {
        val flight = lock.withLock {
            inFlight.getOrPut(url) {
                Flight(scope.async { gate.withPermit { fetch(url) } }, 0)
            }.also { it.waiters++ }
        }
        try {
            return flight.deferred.await()
        } finally {
            // A canceled waiter must release its share even when the lock is held.
            withContext(NonCancellable) {
                lock.withLock {
                    flight.waiters--
                    if (flight.waiters == 0) {
                        if (inFlight[url] === flight) inFlight.remove(url)
                        if (!flight.deferred.isCompleted) flight.deferred.cancel()
                    }
                }
            }
        }
    }
}
