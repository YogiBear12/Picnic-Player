package app.picnic.player.ui.seerr

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.picnic.player.data.seerr.SEERR_ISSUE_ALL
import app.picnic.player.data.seerr.SeerrIssueSeason
import app.picnic.player.data.seerr.SeerrIssueType
import app.picnic.player.data.seerr.seerrIssueMessage
import app.picnic.player.data.seerr.seerrIssueReasons
import app.picnic.player.ui.common.ContextMenuAction
import app.picnic.player.ui.common.ContextMenuPanel
import app.picnic.player.ui.common.rememberContextMenuFocus
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

private enum class ReportStep {
    SCOPE,
    TYPE,
    REASON,
    FREE_TEXT,
    DETAIL
}

@Composable
internal fun ReportIssuePanel(
    item: BaseItemDto,
    target: IssueReportTarget,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    val picksScope = item.type == BaseItemKind.SERIES
    val isEpisode = item.type == BaseItemKind.EPISODE

    var step by remember { mutableStateOf(if (picksScope) ReportStep.SCOPE else ReportStep.TYPE) }
    var type by remember { mutableStateOf(SeerrIssueType.OTHER) }
    var message by remember { mutableStateOf("") }
    var extraText by remember { mutableStateOf("") }
    var season by remember { mutableIntStateOf(if (isEpisode) item.parentIndexNumber ?: SEERR_ISSUE_ALL else SEERR_ISSUE_ALL) }
    var episode by remember { mutableIntStateOf(if (isEpisode) item.indexNumber ?: SEERR_ISSUE_ALL else SEERR_ISSUE_ALL) }
    val typeFocus = rememberContextMenuFocus()
    val reasonFocus = rememberContextMenuFocus()
    var seasons by remember { mutableStateOf<List<SeerrIssueSeason>>(emptyList()) }
    var seasonsLoading by remember { mutableStateOf(picksScope) }

    fun submit() {
        target.reporter.report(item, type, seerrIssueMessage(type, message, extraText), season, episode)
        onDone()
    }

    fun afterReason() {
        if (type == SeerrIssueType.OTHER) submit() else step = ReportStep.DETAIL
    }

    BackHandler {
        step = when (step) {
            ReportStep.SCOPE -> return@BackHandler onCancel()
            ReportStep.TYPE -> if (picksScope) ReportStep.SCOPE else return@BackHandler onCancel()
            ReportStep.REASON, ReportStep.FREE_TEXT -> ReportStep.TYPE
            ReportStep.DETAIL -> ReportStep.REASON
        }
    }

    LaunchedEffect(Unit) {
        if (!picksScope) return@LaunchedEffect
        seasons = target.reporter.seasons(item.id)
        seasonsLoading = false
    }

    when (step) {
        ReportStep.TYPE -> ContextMenuPanel(
            actions = SeerrIssueType.entries.map { candidate ->
                ContextMenuAction(
                    label = candidate.label,
                    icon = candidate.icon,
                    onClick = {
                        type = candidate
                        step = if (candidate == SeerrIssueType.OTHER) ReportStep.FREE_TEXT else ReportStep.REASON
                    }
                )
            },
            openedByLongPress = false,
            focus = typeFocus
        )
        ReportStep.REASON -> ContextMenuPanel(
            actions = seerrIssueReasons(type, target.subject).map { preset ->
                ContextMenuAction(
                    label = preset,
                    icon = type.icon,
                    onClick = {
                        message = preset
                        afterReason()
                    }
                )
            },
            openedByLongPress = false,
            focus = reasonFocus
        )
        ReportStep.FREE_TEXT -> SeerrTextEntryPanel(
            title = "Describe the issue",
            placeholder = "What is wrong?",
            submitLabel = "Report",
            text = message,
            onTextChange = { message = it },
            onSubmit = { afterReason() }
        )
        ReportStep.SCOPE -> IssueScopePanel(
            seasons = seasons,
            loading = seasonsLoading,
            selectedSeason = season,
            selectedEpisode = episode,
            onSelect = { pickedSeason, pickedEpisode ->
                season = pickedSeason
                episode = pickedEpisode
                step = ReportStep.TYPE
            }
        )
        ReportStep.DETAIL -> SeerrTextEntryPanel(
            title = "Additional comments",
            placeholder = "Anything else? (optional)",
            submitLabel = "Send report",
            text = extraText,
            onTextChange = { extraText = it },
            onSubmit = { submit() },
            optional = true
        )
    }
}
