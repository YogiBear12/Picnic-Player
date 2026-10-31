package app.picnic.player.ui.common

import android.util.Log
import app.picnic.player.data.auth.UserScope
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.media.DISCOVERY_FIELDS
import app.picnic.player.data.media.DISCOVERY_PAGE_SIZE
import app.picnic.player.data.media.DiscoveryCandidate
import app.picnic.player.data.media.DiscoveryPlan
import app.picnic.player.data.media.DiscoveryScope
import app.picnic.player.data.media.HomeDiscovery
import app.picnic.player.data.media.HomeRow
import app.picnic.player.data.media.MediaRepository
import app.picnic.player.data.media.nextDiscoveryBatch
import app.picnic.player.data.media.pendingDiscoveryCandidates
import app.picnic.player.data.media.retryDiscovery
import app.picnic.player.data.media.shouldRenewDiscovery
import app.picnic.player.data.media.shouldShow
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto

data class PendingDiscoveryRow(val key: String, val title: String)

// The host owns the screen state; it must rebuild it from rows and pending on every onChanged.
internal class DiscoveryFeed(
    private val scope: CoroutineScope,
    private val discovery: HomeDiscovery,
    private val mediaRepository: MediaRepository,
    private val target: DiscoveryScope,
    private val focusedInFeed: () -> Boolean,
    private val awaitHostRows: suspend () -> Unit,
    private val onChanged: () -> Unit,
    private val onRowsPublished: (List<HomeRow>) -> Unit,
    private val onPageAppended: (List<HomeRow>) -> Unit
) {
    var rows: List<HomeRow> = emptyList()
        private set
    var paused = false
        private set

    private var job: Job? = null
    private var plan: DiscoveryPlan? = null
    private var nextPlan: DiscoveryPlan? = null
    private val shown = mutableListOf<DiscoveryCandidate>()
    private val unpublishedChanges = mutableSetOf<String>()
    private var publishIndex = 0
    private val pager = RowPager(scope, ::appendPage)
    private val profile = target.profile

    val pending: List<PendingDiscoveryRow>
        get() = if (paused) {
            emptyList()
        } else {
            pendingDiscoveryCandidates(plan?.candidates.orEmpty().drop(publishIndex), shown, profile)
                .map { PendingDiscoveryRow(it.rowKey, it.title) }
        }

    fun resume() {
        paused = false
    }

    fun onFocused(row: HomeRow, item: BaseItemDto) {
        pager.onFocused(row.key, row.items.indexOfFirst { it.id == item.id }, row.items.size)
    }

    /** Changes that land while a batch is loading are patched once that batch publishes. */
    fun markChanged(itemIds: Set<String>) {
        if (job?.isActive == true) unpublishedChanges += itemIds
    }

    fun request(session: UserSession) {
        if (job?.isActive == true || paused) return
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val today = LocalDate.now()
                val current = plan
                if (current == null || shouldRenewDiscovery(current, today, focusedInFeed())) {
                    val fresh = nextPlan?.takeIf { it.date == today.toString() }
                        ?: retryDiscovery { discovery.plan(session.scope, session.userUuid, today, target) }
                    if (current != null && focusedInFeed()) {
                        nextPlan = fresh
                        return@launch
                    }
                    nextPlan = null
                    plan = fresh
                    rows = emptyList()
                    pager.clear()
                    shown.clear()
                    publishIndex = 0
                    onChanged()
                }
                if (nextPlan == null) fill(session.scope)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                paused = true
                onChanged()
                Log.w(TAG, "Discovery load failed; retry on resume", failure)
            }
        }.also { it.start() }
    }

    suspend fun patch(changedIds: Set<String>) {
        try {
            val fresh = mediaRepository.refreshChanged(rows.flatMap { it.items }, changedIds, DISCOVERY_FIELDS)
            if (fresh.isEmpty()) return
            rows = rows.map { row -> row.copy(items = row.items.map { fresh[it.id] ?: it }) }
            onChanged()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(TAG, "Discovery card refresh failed", failure)
        }
    }

    private fun appendPage(key: String, page: List<BaseItemDto>) {
        if (page.isEmpty()) return
        rows = rows.map { row -> if (row.key == key) row.copy(items = row.items + page) else row }
        onChanged()
        onPageAppended(rows.filter { it.key == key })
    }

    private suspend fun fill(userScope: UserScope) = try {
        publish(userScope)
    } finally {
        unpublishedChanges.clear()
    }

    private suspend fun publish(userScope: UserScope) = coroutineScope {
        val initialPlan = plan ?: return@coroutineScope
        val remaining = initialPlan.candidates.drop(publishIndex)
        if (nextDiscoveryBatch(remaining, shown, BATCH_SIZE, profile).isEmpty()) return@coroutineScope
        val loader = discovery.loader(this, initialPlan, remaining, target, awaitHostRows)
        while (true) {
            val current = plan ?: return@coroutineScope
            val batch = nextDiscoveryBatch(current.candidates.drop(publishIndex), shown, BATCH_SIZE, profile)
            if (batch.isEmpty()) return@coroutineScope
            val eligible = batch.filter { shouldShow(it, shown, profile) }
            val filled = if (eligible.isEmpty()) {
                emptyList()
            } else {
                val (latest, firstPages) = discovery.fill(userScope, current, eligible, rows.map { it.items }, loader, target)
                plan = latest
                firstPages
            }
            publishIndex += batch.size
            val savedIds = plan?.candidates.orEmpty().associate { it.key to it.itemIds.orEmpty() }
            val added = eligible.zip(filled).mapNotNull { (candidate, items) ->
                if (items.isEmpty()) return@mapNotNull null
                shown += candidate
                val ids = savedIds[candidate.key].orEmpty().map(UUID::fromString)
                pager.add(candidate.rowKey, RowCursor(DISCOVERY_PAGE_SIZE, next = DISCOVERY_PAGE_SIZE, ids = ids)) { cursor ->
                    loadOrderedPage(cursor, { ids }) { mediaRepository.itemsOrThrow(it, DISCOVERY_FIELDS) }
                }
                HomeRow(candidate.title, items, continueWatching = false, key = candidate.rowKey)
            }
            rows = rows + added
            onChanged()
            if (added.isNotEmpty()) {
                onRowsPublished(added)
                val stale = added.flatMap { row -> row.items.map { it.id.toString() } }.filter { it in unpublishedChanges }.toSet()
                if (stale.isNotEmpty()) scope.launch { patch(stale) }
            }
        }
    }

    private companion object {
        const val TAG = "DiscoveryFeed"
        const val BATCH_SIZE = 4
    }
}

private val DiscoveryCandidate.rowKey: String get() = "discovery-$key"
