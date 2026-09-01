package app.picnic.player.ui.seerr

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.media.MediaRepository
import app.picnic.player.data.seerr.SeerrIssueSeason
import app.picnic.player.data.seerr.SeerrIssueType
import app.picnic.player.data.seerr.SeerrLinkState
import app.picnic.player.data.seerr.SeerrRepository
import app.picnic.player.data.seerr.canCreateSeerrIssue
import app.picnic.player.data.seerr.seerrIssueSeasons
import app.picnic.player.data.seerr.tmdbIdFromProviderIds
import app.picnic.player.data.socket.ServerMessageBus
import app.picnic.player.data.socket.ServerNotice
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

val LocalIssueReporter = staticCompositionLocalOf<IssueReportViewModel> {
    error("LocalIssueReporter not provided")
}

data class IssueReportTarget(
    val reporter: IssueReportViewModel,
    val subject: String
)

@Composable
fun rememberIssueReporter(item: BaseItemDto?): IssueReportTarget? {
    val reporter = LocalIssueReporter.current
    val canReport by reporter.canCreateIssues.collectAsStateWithLifecycle()
    if (!canReport || item == null) return null
    val subject = issueSubjectOrNull(item) ?: return null
    return IssueReportTarget(reporter, subject)
}

private fun issueSubjectOrNull(item: BaseItemDto): String? = when (item.type) {
    BaseItemKind.MOVIE -> "movie"
    BaseItemKind.SERIES -> "show"
    BaseItemKind.EPISODE -> "episode"
    else -> null
}

@HiltViewModel
class IssueReportViewModel @Inject constructor(
    private val seerrRepository: SeerrRepository,
    private val mediaRepository: MediaRepository,
    private val serverMessageBus: ServerMessageBus
) : ViewModel() {
    private val seasonCache = ConcurrentHashMap<UUID, List<SeerrIssueSeason>>()

    val canCreateIssues: StateFlow<Boolean> = seerrRepository.state
        .map { it.linkState == SeerrLinkState.Linked && canCreateSeerrIssue(it.user) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    suspend fun seasons(seriesId: UUID): List<SeerrIssueSeason> {
        seasonCache[seriesId]?.let { return it }
        val seasons = seerrIssueSeasons(mediaRepository.episodeNumbersBySeason(seriesId))
        if (seasons.isNotEmpty()) seasonCache[seriesId] = seasons
        return seasons
    }

    fun report(
        item: BaseItemDto,
        type: SeerrIssueType,
        message: String,
        season: Int,
        episode: Int
    ) {
        viewModelScope.launch {
            val notice = runCatching {
                val seriesId = item.seriesId
                val tmdbId = tmdbIdFromProviderIds(mediaRepository.item(seriesId ?: item.id).providerIds)
                    ?: error("No TMDB id for ${item.id}")
                seerrRepository.reportIssue(
                    tmdbId = tmdbId,
                    isTv = seriesId != null || item.type == BaseItemKind.SERIES,
                    type = type,
                    message = message,
                    problemSeason = season,
                    problemEpisode = episode
                )
            }.fold({ "Issue reported" }, { "Could not report issue" })
            serverMessageBus.emit(ServerNotice(text = notice))
        }
    }
}
