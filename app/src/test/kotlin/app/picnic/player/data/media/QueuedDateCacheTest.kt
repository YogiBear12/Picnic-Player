package app.picnic.player.data.media

import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueuedDateCacheTest {

    private fun cache() = QueuedDateCache(LibraryChangeBus(), CoroutineScope(Dispatchers.Unconfined))

    private val date = LocalDateTime.of(2026, 8, 20, 20, 0)

    @Test
    fun storedDate_isReadBack() {
        val cache = cache()
        val episode = UUID.randomUUID()
        cache.put(episode, UUID.randomUUID(), date)
        assertEquals(date, cache.get(episode))
    }

    @Test
    fun playingAnEpisode_forgetsEveryEpisodeOfThatSeries() {
        val cache = cache()
        val series = UUID.randomUUID()
        val queued = UUID.randomUUID()
        val untouched = UUID.randomUUID()
        cache.put(queued, series, date)
        cache.put(untouched, UUID.randomUUID(), date)

        cache.forget(UUID.randomUUID().toString(), series.toString())

        assertNull(cache.get(queued))
        assertEquals(date, cache.get(untouched))
    }

    @Test
    fun markingASeriesWatched_forgetsItWhenTheSeriesArrivesAsTheItemId() {
        val cache = cache()
        val series = UUID.randomUUID()
        val queued = UUID.randomUUID()
        cache.put(queued, series, date)

        cache.forget(series.toString(), null)

        assertNull(cache.get(queued))
    }

    @Test
    fun playingTheQueuedEpisodeItself_forgetsIt() {
        val cache = cache()
        val queued = UUID.randomUUID()
        cache.put(queued, UUID.randomUUID(), date)

        cache.forget(queued.toString(), null)

        assertNull(cache.get(queued))
    }
}
