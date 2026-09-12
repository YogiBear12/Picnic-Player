@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package app.picnic.player.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.TabRowScope
import androidx.tv.material3.Text
import app.picnic.player.data.seerr.SeerrIssueDisplay
import app.picnic.player.data.seerr.isOpen
import app.picnic.player.data.seerr.neighborIssueId
import app.picnic.player.data.seerr.replies
import app.picnic.player.data.seerr.reportBody
import app.picnic.player.ui.common.ContextMenuAction
import app.picnic.player.ui.common.ContextMenuPanel
import app.picnic.player.ui.common.CountPill
import app.picnic.player.ui.common.GlassRow
import app.picnic.player.ui.common.PicnicDialog
import app.picnic.player.ui.common.PillTab
import app.picnic.player.ui.common.PillTabRow
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.theme.PicnicColors

@Composable
internal fun IssuesScreen(
    seerrBaseUrl: String?,
    cacheImages: Boolean,
    onBack: () -> Unit,
    viewModel: IssuesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showResolved by rememberSaveable { mutableStateOf(false) }
    var pendingRestoreId by rememberSaveable { mutableStateOf<Int?>(null) }
    var restoreToken by rememberSaveable { mutableIntStateOf(0) }

    fun restoreFocusTo(issueId: Int?) {
        pendingRestoreId = issueId
        restoreToken++
    }

    val selected = state.selected
    val visible = state.issues.filter { it.issue.isOpen != showResolved }

    BackHandler {
        if (selected != null) viewModel.select(null) else onBack()
    }

    LaunchedEffect(Unit) { viewModel.load() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = SettingsSideInset, end = SettingsSideInset, top = SettingsSubPageTopInset)
    ) {
        when {
            selected != null -> IssueDetailPage(
                row = selected,
                seerrBaseUrl = seerrBaseUrl,
                cacheImages = cacheImages,
                busy = state.busyIssueId == selected.issue.id,
                onToggleResolved = {
                    restoreFocusTo(neighborIssueId(visible, selected.issue.id))
                    viewModel.setResolved(selected.issue.id, selected.issue.isOpen)
                },
                onDelete = {
                    restoreFocusTo(neighborIssueId(visible, selected.issue.id))
                    viewModel.delete(selected.issue.id)
                },
                onAddComment = { message -> viewModel.addComment(selected.issue.id, message) }
            )
            state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = PicnicColors.Accent)
            }
            else -> IssueListPage(
                issues = visible,
                showResolved = showResolved,
                openCount = state.issues.count { it.issue.isOpen },
                resolvedCount = state.issues.count { !it.issue.isOpen },
                error = state.error,
                seerrBaseUrl = seerrBaseUrl,
                cacheImages = cacheImages,
                restoreId = pendingRestoreId,
                restoreToken = restoreToken,
                onTab = { showResolved = it },
                onSelect = { row ->
                    pendingRestoreId = row.issue.id
                    viewModel.select(row)
                },
                onToggleResolved = { row ->
                    restoreFocusTo(neighborIssueId(visible, row.issue.id))
                    viewModel.setResolved(row.issue.id, row.issue.isOpen)
                },
                onDelete = { row ->
                    restoreFocusTo(neighborIssueId(visible, row.issue.id))
                    viewModel.delete(row.issue.id)
                }
            )
        }
    }
}

@Composable
private fun IssueListPage(
    issues: List<SeerrIssueDisplay>,
    showResolved: Boolean,
    openCount: Int,
    resolvedCount: Int,
    error: String?,
    seerrBaseUrl: String?,
    cacheImages: Boolean,
    restoreId: Int?,
    restoreToken: Int,
    onTab: (Boolean) -> Unit,
    onSelect: (SeerrIssueDisplay) -> Unit,
    onToggleResolved: (SeerrIssueDisplay) -> Unit,
    onDelete: (SeerrIssueDisplay) -> Unit
) {
    val listState = rememberLazyListState()
    var menuRow by remember { mutableStateOf<SeerrIssueDisplay?>(null) }
    val restoreRowFr = remember { FocusRequester() }
    val openTabFr = remember { FocusRequester() }
    val resolvedTabFr = remember { FocusRequester() }
    val activeTabFr = if (showResolved) resolvedTabFr else openTabFr

    Column {
        Text(
            "Issues",
            style = MaterialTheme.typography.headlineMedium,
            color = PicnicColors.OnDark
        )
        Spacer(Modifier.height(20.dp))
        PillTabRow(
            selectedIndex = if (showResolved) 1 else 0,
            activeTabFocus = activeTabFr
        ) {
            IssueTab(
                label = "Open",
                count = openCount,
                accent = OpenAccent,
                selected = !showResolved,
                focusRequester = openTabFr,
                onSelect = { onTab(false) }
            )
            IssueTab(
                label = "Resolved",
                count = resolvedCount,
                accent = ResolvedAccent,
                selected = showResolved,
                focusRequester = resolvedTabFr,
                onSelect = { onTab(true) }
            )
        }
        Spacer(Modifier.height(20.dp))
        when {
            error != null -> Text(error, color = PicnicColors.OnDarkMuted)
            issues.isEmpty() -> Text(
                if (showResolved) "No resolved issues." else "No open issues.",
                style = MaterialTheme.typography.bodyLarge,
                color = PicnicColors.OnDarkMuted
            )
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = SettingsBottomInset),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(issues, key = { _, row -> row.issue.id }) { index, row ->
                    IssueListCard(
                        row = row,
                        seerrBaseUrl = seerrBaseUrl,
                        cacheImages = cacheImages,
                        rowFocus = if (row.issue.id == restoreId) restoreRowFr else null,
                        upTarget = if (index == 0) activeTabFr else null,
                        blockDown = index == issues.lastIndex,
                        onActivate = { onSelect(row) },
                        onLongPress = { menuRow = row }
                    )
                }
            }
        }
    }

    menuRow?.let { row ->
        PicnicDialog(onDismiss = { menuRow = null }) {
            ContextMenuPanel(
                actions = listOf(
                    ContextMenuAction(
                        label = if (row.issue.isOpen) "Mark as resolved" else "Reopen",
                        icon = if (row.issue.isOpen) Icons.Default.CheckCircle else Icons.Default.Replay,
                        onClick = {
                            onToggleResolved(row)
                            menuRow = null
                        }
                    ),
                    ContextMenuAction(
                        label = "Delete",
                        icon = Icons.Default.Delete,
                        onClick = {
                            onDelete(row)
                            menuRow = null
                        }
                    )
                )
            )
        }
    }

    LaunchedEffect(restoreToken) {
        val restoreIndex = issues.indexOfFirst { it.issue.id == restoreId }
        if (restoreIndex < 0) {
            activeTabFr.requestFocusWhenAttached(maxFrames = 20)
            return@LaunchedEffect
        }
        if (restoreRowFr.requestFocusWhenAttached(maxFrames = 20)) return@LaunchedEffect
        listState.scrollToItem(restoreIndex)
        restoreRowFr.requestFocusWhenAttached(maxFrames = 20)
    }
}

@Composable
private fun TabRowScope.IssueTab(
    label: String,
    count: Int,
    accent: Color,
    selected: Boolean,
    focusRequester: FocusRequester,
    onSelect: () -> Unit
) {
    PillTab(selected = selected, focusRequester = focusRequester, onSelect = onSelect) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        CountPill(count = count, accent = accent)
    }
}

@Composable
private fun IssueListCard(
    row: SeerrIssueDisplay,
    seerrBaseUrl: String?,
    cacheImages: Boolean,
    rowFocus: FocusRequester?,
    upTarget: FocusRequester?,
    blockDown: Boolean,
    onActivate: () -> Unit,
    onLongPress: () -> Unit
) {
    val message = row.issue.reportBody

    GlassRow(
        onClick = onActivate,
        onLongClick = onLongPress,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (rowFocus != null) Modifier.focusRequester(rowFocus) else Modifier)
            .focusProperties {
                right = FocusRequester.Cancel
                if (upTarget != null) up = upTarget
                if (blockDown) down = FocusRequester.Cancel
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IssueThumb(
                row = row,
                seerrBaseUrl = seerrBaseUrl,
                cacheImages = cacheImages,
                width = ThumbWidth,
                height = ThumbHeight
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        row.yearLabel?.let { "${row.title} ($it)" } ?: row.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = PicnicColors.OnDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    StatusPill(open = row.issue.isOpen)
                }
                Text(
                    issueMetaLine(row, row.issue.replies.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = PicnicColors.OnDarkMuted
                )
                if (message != null) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = PicnicColors.OnDark.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
