package app.picnic.player.ui.ambient

import android.graphics.Canvas
import coil3.Image
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SharedFlightsTest {
    @Test
    fun twoWaitersShareOneFetch() = runTest {
        val result = CompletableDeferred<Image?>()
        val image = FakeArtworkImage()
        var fetches = 0
        val flights = SharedFlights(backgroundScope) {
            fetches++
            result.await()
        }
        val first = async { flights.load("a") }
        val second = async { flights.load("a") }
        runCurrent()

        assertEquals(1, fetches)
        result.complete(image)
        assertSame(image, first.await())
        assertSame(image, second.await())
    }

    @Test
    fun completedSuccessIsRemovedAndThirdCallFetchesAgain() = runTest {
        val result = CompletableDeferred<Image?>()
        val image = FakeArtworkImage()
        var fetches = 0
        val flights = SharedFlights(backgroundScope) {
            fetches++
            result.await()
        }
        val first = async { flights.load("a") }
        val second = async { flights.load("a") }
        runCurrent()
        result.complete(image)
        first.await()
        second.await()

        assertSame(image, flights.load("a"))
        assertEquals(2, fetches)
    }

    @Test
    fun completedFailureIsRemoved() = runTest {
        var fetches = 0
        val flights = SharedFlights(backgroundScope) {
            fetches++
            null
        }

        assertNull(flights.load("a"))
        assertNull(flights.load("a"))
        assertEquals(2, fetches)
    }

    @Test
    fun canceledWaiterReleasesAfterLockContention() = runTest {
        var fetchCanceled = false
        val flights = SharedFlights(backgroundScope) {
            try {
                awaitCancellation()
            } finally {
                fetchCanceled = true
            }
        }
        val waiter = async { flights.load("a") }
        runCurrent()
        // Hold the real private lock to exercise cancellation during waiter cleanup.
        val lock = SharedFlights::class.java.getDeclaredField("lock").apply { isAccessible = true }
            .get(flights) as Mutex
        val releaseLock = CompletableDeferred<Unit>()
        val holder = launch { lock.withLock { releaseLock.await() } }
        runCurrent()
        assertTrue(lock.isLocked)

        waiter.cancel()
        runCurrent()
        assertFalse(waiter.isCompleted)
        assertFalse(fetchCanceled)
        releaseLock.complete(Unit)
        holder.join()
        waiter.join()
        runCurrent()

        assertTrue(fetchCanceled)
    }

    @Test
    fun cancelingOneWaiterKeepsFetchAliveForOther() = runTest {
        val result = CompletableDeferred<Image?>()
        val image = FakeArtworkImage()
        var fetchCanceled = false
        var fetches = 0
        val flights = SharedFlights(backgroundScope) {
            fetches++
            try {
                result.await()
            } finally {
                fetchCanceled = !result.isCompleted
            }
        }
        val first = async { flights.load("a") }
        val second = async { flights.load("a") }
        runCurrent()

        first.cancel()
        first.join()
        runCurrent()
        assertFalse(fetchCanceled)
        assertFalse(second.isCompleted)
        assertEquals(1, fetches)
        result.complete(image)
        assertSame(image, second.await())
    }

    @Test
    fun cancelingLastWaiterCancelsFetchAndRemovesFlight() = runTest {
        var fetches = 0
        var cancellations = 0
        val flights = SharedFlights(backgroundScope) {
            fetches++
            try {
                awaitCancellation()
            } finally {
                cancellations++
            }
        }
        val first = async { flights.load("a") }
        val second = async { flights.load("a") }
        runCurrent()
        first.cancel()
        first.join()
        runCurrent()
        assertEquals(0, cancellations)

        second.cancel()
        second.join()
        runCurrent()
        assertEquals(1, cancellations)

        val third = async { flights.load("a") }
        runCurrent()
        assertEquals(2, fetches)
        third.cancel()
        third.join()
        runCurrent()
        assertEquals(2, cancellations)
    }

    @Test
    fun concurrencyNeverExceedsTwo() = runTest {
        val release = CompletableDeferred<Unit>()
        var active = 0
        var peak = 0
        var fetches = 0
        val flights = SharedFlights(backgroundScope) {
            fetches++
            active++
            peak = maxOf(peak, active)
            try {
                release.await()
                FakeArtworkImage()
            } finally {
                active--
            }
        }
        val waiters = List(6) { index -> async { flights.load("art-$index") } }
        runCurrent()

        assertEquals(2, active)
        assertEquals(2, fetches)
        release.complete(Unit)
        waiters.awaitAll()
        assertEquals(6, fetches)
        assertEquals(2, peak)
        assertEquals(0, active)
    }
}

internal class FakeArtworkImage : Image {
    override val size = 4L
    override val width = 1
    override val height = 1
    override val shareable = true

    override fun draw(canvas: Canvas) = Unit
}
