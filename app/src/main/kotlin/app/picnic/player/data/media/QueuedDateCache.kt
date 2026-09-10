package app.picnic.player.data.media

import app.picnic.player.di.ApplicationScope
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Singleton
class QueuedDateCache @Inject constructor(
    changeBus: LibraryChangeBus,
    @ApplicationScope scope: CoroutineScope
) {
    private data class Held(val seriesId: UUID, val date: LocalDateTime, val storedAt: Long)

    private val held = ConcurrentHashMap<UUID, Held>()

    init {
        scope.launch {
            changeBus.changes().collect { change ->
                when (change) {
                    is LibraryChange.ItemUpdated -> forget(change.itemId, change.seriesId)
                    LibraryChange.LibraryContentChanged -> held.clear()
                }
            }
        }
    }

    fun get(episodeId: UUID): LocalDateTime? {
        val entry = held[episodeId] ?: return null
        if (System.currentTimeMillis() - entry.storedAt > LIFETIME_MS) {
            held.remove(episodeId)
            return null
        }
        return entry.date
    }

    fun put(episodeId: UUID, seriesId: UUID, date: LocalDateTime) {
        if (held.size >= MAX_ENTRIES) {
            held.entries.minByOrNull { it.value.storedAt }?.let { held.remove(it.key) }
        }
        held[episodeId] = Held(seriesId, date, System.currentTimeMillis())
    }

    internal fun forget(itemId: String, seriesId: String?) {
        held.entries.removeAll { (episodeId, entry) ->
            val series = entry.seriesId.toString()
            series == seriesId || series == itemId || episodeId.toString() == itemId
        }
    }

    private companion object {
        const val LIFETIME_MS = 2 * 60 * 60 * 1000L
        const val MAX_ENTRIES = 200
    }
}
