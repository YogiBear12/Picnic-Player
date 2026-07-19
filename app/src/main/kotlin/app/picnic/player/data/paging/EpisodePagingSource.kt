package app.picnic.player.data.paging

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemDto

private const val TAG = "EpisodePagingSource"

class EpisodePagingSource(
    private val api: ApiClient,
    private val ioDispatcher: CoroutineDispatcher,
    private val seriesId: String,
    private val seasonId: String,
    private val userId: String,
    private val onTotalRecordCount: (Int) -> Unit
) : PagingSource<Int, BaseItemDto>() {

    // The whole load runs on IO: the SDK deserializes the response on the calling context, and a
    // 50-episode page on the main thread is a visible jank spike (same root cause as the perf
    // pass's repository offloads).
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, BaseItemDto> = withContext(ioDispatcher) {
        val startIndex = params.key ?: 0
        try {
            val response = api.tvShowsApi.getEpisodes(
                seriesId = UUID.fromString(seriesId),
                seasonId = UUID.fromString(seasonId),
                userId = UUID.fromString(userId),
                startIndex = startIndex,
                limit = params.loadSize,
                fields = listOf(org.jellyfin.sdk.model.api.ItemFields.OVERVIEW)
            )
            val items = response.content.items ?: emptyList()

            // Report the total record count back so the UI can show "X episodes"
            response.content.totalRecordCount?.let { onTotalRecordCount(it) }

            LoadResult.Page(
                data = items,
                prevKey = if (startIndex == 0) null else maxOf(0, startIndex - params.loadSize),
                nextKey = if (items.isEmpty() || items.size < params.loadSize) null else startIndex + params.loadSize
            )
        } catch (e: Exception) {
            // Surface the real failure (e.g. a SerializationException naming the bad field) —
            // a swallowed error here renders as a silently blank episode pane.
            Log.e(TAG, "Episode page load failed (series=$seriesId season=$seasonId start=$startIndex)", e)
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, BaseItemDto>): Int? = state.anchorPosition?.let { pos ->
        val page = state.closestPageToPosition(pos)
        page?.prevKey?.plus(state.config.pageSize) ?: page?.nextKey?.minus(state.config.pageSize)
    }
}
