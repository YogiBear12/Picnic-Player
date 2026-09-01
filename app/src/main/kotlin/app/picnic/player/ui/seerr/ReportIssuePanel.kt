package app.picnic.player.ui.seerr

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import app.picnic.player.data.seerr.SEERR_ISSUE_ALL
import app.picnic.player.data.seerr.SeerrIssueSeason
import app.picnic.player.data.seerr.SeerrIssueType
import app.picnic.player.data.seerr.seerrIssueMessage
import app.picnic.player.data.seerr.seerrIssueReasons
import app.picnic.player.ui.common.ContextMenuAction
import app.picnic.player.ui.common.ContextMenuPanel
import app.picnic.player.ui.common.DialogTextField
import app.picnic.player.ui.common.panelSurface
import app.picnic.player.ui.common.requestFocusWhenAttached
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
internal fun ReportIssuePanel(
    item: BaseItemDto,
    target: IssueReportTarget,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    val subject = target.subject
    val picksScope = item.type == BaseItemKind.SERIES

    var type by remember { mutableStateOf<SeerrIssueType?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var freeText by remember { mutableStateOf("") }
    var pickingScope by remember { mutableStateOf(false) }
    var seasons by remember { mutableStateOf<List<SeerrIssueSeason>>(emptyList()) }
    var seasonsLoading by remember { mutableStateOf(false) }

    val chosenType = type
    val writingFreeText = chosenType == SeerrIssueType.OTHER && !pickingScope

    fun submit(text: String, season: Int, episode: Int) {
        val selected = chosenType ?: return
        target.reporter.report(item, selected, seerrIssueMessage(selected, text), season, episode)
        onDone()
    }

    fun continueWith(selected: SeerrIssueType, text: String) {
        if (picksScope) {
            type = selected
            message = text
            pickingScope = true
        } else {
            target.reporter.report(
                item,
                selected,
                seerrIssueMessage(selected, text),
                item.parentIndexNumber ?: SEERR_ISSUE_ALL,
                item.indexNumber ?: SEERR_ISSUE_ALL
            )
            onDone()
        }
    }

    BackHandler {
        when {
            pickingScope -> pickingScope = false
            chosenType != null -> type = null
            else -> onCancel()
        }
    }

    LaunchedEffect(pickingScope) {
        if (!pickingScope || seasons.isNotEmpty()) return@LaunchedEffect
        seasonsLoading = true
        seasons = target.reporter.seasons(item.id)
        seasonsLoading = false
    }

    when {
        pickingScope -> IssueScopePanel(
            seasons = seasons,
            loading = seasonsLoading,
            onSelect = { season, episode -> submit(message.orEmpty(), season, episode) }
        )
        writingFreeText -> FreeTextPanel(
            text = freeText,
            onTextChange = { freeText = it },
            onSubmit = { continueWith(SeerrIssueType.OTHER, freeText) }
        )
        chosenType != null -> ContextMenuPanel(
            actions = seerrIssueReasons(chosenType, subject).map { preset ->
                ContextMenuAction(
                    label = preset,
                    icon = chosenType.icon,
                    onClick = { continueWith(chosenType, preset) }
                )
            },
            openedByLongPress = false
        )
        else -> ContextMenuPanel(
            actions = SeerrIssueType.entries.map { candidate ->
                ContextMenuAction(
                    label = candidate.label,
                    icon = candidate.icon,
                    onClick = { type = candidate }
                )
            },
            openedByLongPress = false
        )
    }
}

@Composable
private fun FreeTextPanel(
    text: String,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val fieldFocus = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .panelSurface(SeerrPanelWidth, SeerrPanelCornerRadius)
            .focusGroup()
    ) {
        SeerrPanelHeader(title = "Describe the issue")
        Column(modifier = Modifier.padding(horizontal = SeerrContentInset)) {
            DialogTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = "What is wrong?",
                modifier = Modifier.focusRequester(fieldFocus),
                onImeAction = { if (text.isNotBlank()) onSubmit() }
            )
            Spacer(Modifier.height(12.dp))
            SeerrActionButton(
                title = "Report",
                onClick = { if (text.isNotBlank()) onSubmit() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    LaunchedEffect(Unit) {
        fieldFocus.requestFocusWhenAttached(maxFrames = 20)
    }
}
