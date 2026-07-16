package app.picnic.player.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import java.util.UUID
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemDto

class EpisodePagingSource(
    private val api: ApiClient,
    private val seriesId: String,
    private val seasonId: String,
    private val userId: String,
    private val onTotalRecordCount: (Int) -> Unit
) : PagingSource<Int, BaseItemDto>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, BaseItemDto> {
        val startIndex = params.key ?: 0
        return try {
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
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, BaseItemDto>): Int? = state.anchorPosition?.let { pos ->
        val page = state.closestPageToPosition(pos)
        page?.prevKey?.plus(state.config.pageSize) ?: page?.nextKey?.minus(state.config.pageSize)
    }
}
