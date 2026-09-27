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
import org.jellyfin.sdk.model.api.ItemFields

private const val TAG = "EpisodePagingSource"
private const val RefreshPageSpan = 3

internal fun alignedPageStart(index: Int, pageSize: Int): Int = index / pageSize * pageSize

internal fun refreshStart(anchor: Int, pageSize: Int): Int = (alignedPageStart(anchor, pageSize) - pageSize * (RefreshPageSpan / 2)).coerceAtLeast(0)

class EpisodePagingSource(
    private val api: ApiClient,
    private val ioDispatcher: CoroutineDispatcher,
    private val initialKey: Int,
    private val firstLoad: Boolean,
    private val seriesId: String,
    private val seasonId: String,
    private val userId: UUID,
    private val onTotalRecordCount: (Int) -> Unit
) : PagingSource<Int, BaseItemDto>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, BaseItemDto> = withContext(ioDispatcher) {
        val startIndex = params.key ?: 0
        val limit = if (params is LoadParams.Refresh && !firstLoad) params.loadSize * RefreshPageSpan else params.loadSize
        try {
            val response = api.tvShowsApi.getEpisodes(
                seriesId = UUID.fromString(seriesId),
                seasonId = UUID.fromString(seasonId),
                userId = userId,
                startIndex = startIndex,
                limit = limit,
                fields = listOf(ItemFields.OVERVIEW)
            )
            val items = response.content.items
            val totalRecordCount = response.content.totalRecordCount

            onTotalRecordCount(totalRecordCount)

            LoadResult.Page(
                data = items,
                prevKey = if (startIndex == 0) null else (startIndex - params.loadSize).coerceAtLeast(0),
                nextKey = if (items.isEmpty() || items.size < limit) null else startIndex + limit,
                itemsBefore = startIndex,
                itemsAfter = (totalRecordCount - startIndex - items.size).coerceAtLeast(0)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Episode page load failed (series=$seriesId season=$seasonId start=$startIndex)", e)
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, BaseItemDto>): Int? = state.anchorPosition?.let { refreshStart(it, state.config.pageSize) } ?: initialKey
}
