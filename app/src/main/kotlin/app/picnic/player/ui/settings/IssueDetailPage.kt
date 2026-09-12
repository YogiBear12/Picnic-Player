package app.picnic.player.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.seerr.SeerrIssueComment
import app.picnic.player.data.seerr.SeerrIssueDisplay
import app.picnic.player.data.seerr.isOpen
import app.picnic.player.data.seerr.replies
import app.picnic.player.data.seerr.reportBody
import app.picnic.player.data.seerr.seerrAvatarUrl
import app.picnic.player.data.seerr.seerrCommentAuthor
import app.picnic.player.data.seerr.seerrIssueScopeLabel
import app.picnic.player.data.seerr.seerrIssueTypeSentence
import app.picnic.player.data.seerr.seerrRelativeTime
import app.picnic.player.ui.common.ActionButton
import app.picnic.player.ui.common.ArtworkImage
import app.picnic.player.ui.common.GlassRow
import app.picnic.player.ui.common.GlassRowFocusedFill
import app.picnic.player.ui.common.GlassRowIdleFill
import app.picnic.player.ui.common.PicnicDialog
import app.picnic.player.ui.common.ScrollableTextDialog
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.seerr.SeerrTextEntryPanel
import app.picnic.player.ui.theme.PicnicColors

private const val DetailPaneWeight = 1.3f
private const val CommentsPaneWeight = 1f

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun IssueDetailPage(
    row: SeerrIssueDisplay,
    seerrBaseUrl: String?,
    cacheImages: Boolean,
    busy: Boolean,
    onToggleResolved: () -> Unit,
    onDelete: () -> Unit,
    onAddComment: (String) -> Unit
) {
    val resolveFr = remember { FocusRequester() }
    val deleteFr = remember { FocusRequester() }
    var lastActionFr by remember { mutableStateOf(resolveFr) }
    val comments = row.issue.replies
    var expandedMessage by remember { mutableStateOf<String?>(null) }
    var composing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val commentsState = rememberLazyListState()
    val lastCommentFr = remember { FocusRequester() }

    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) commentsState.scrollToItem(comments.lastIndex)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(48.dp),
        modifier = Modifier.fillMaxSize().padding(bottom = SettingsBottomInset)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.weight(DetailPaneWeight)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                IssueThumb(
                    row = row,
                    seerrBaseUrl = seerrBaseUrl,
                    cacheImages = cacheImages,
                    width = DetailThumbWidth,
                    height = DetailThumbHeight
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        row.yearLabel?.let { "${row.title} ($it)" } ?: row.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = PicnicColors.OnDark,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(open = row.issue.isOpen)
                        MetaChip(text = seerrIssueTypeSentence(row.issue.issueType))
                        seerrIssueScopeLabel(row.issue)?.let { MetaChip(text = it) }
                    }
                    seerrRelativeTime(row.issue.createdAt)?.let { opened ->
                        MetaLine(label = "Opened", value = opened)
                    }
                    seerrRelativeTime(row.issue.updatedAt)
                        ?.takeIf { row.issue.updatedAt != row.issue.createdAt }
                        ?.let { updated -> MetaLine(label = "Updated", value = updated) }
                }
            }
            row.issue.reportBody?.let { message ->
                ReportCard(
                    message = message,
                    downTarget = lastActionFr,
                    onOpen = { expandedMessage = message }
                )
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(
                    label = if (row.issue.isOpen) "Mark as resolved" else "Reopen",
                    onActivate = onToggleResolved,
                    focusRequester = resolveFr,
                    busy = busy,
                    modifier = Modifier.onFocusChanged { if (it.isFocused) lastActionFr = resolveFr }
                )
                ActionButton(
                    label = "Delete",
                    onActivate = onDelete,
                    focusRequester = deleteFr,
                    busy = busy,
                    modifier = Modifier.onFocusChanged { if (it.isFocused) lastActionFr = deleteFr }
                )
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(CommentsPaneWeight)
                .fillMaxHeight()
        ) {
            Text(
                if (comments.isEmpty()) "Comments" else "Comments (${comments.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = PicnicColors.OnDark
            )
            if (comments.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Text(
                        "No comments yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PicnicColors.OnDarkMuted
                    )
                }
            }
            LazyColumn(
                state = commentsState,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
                modifier = Modifier
                    .then(if (comments.isEmpty()) Modifier else Modifier.weight(1f))
                    .focusRestorer { lastCommentFr }
                    .focusGroup()
            ) {
                itemsIndexed(comments, key = { index, comment -> comment.id ?: -(index + 1) }) { index, comment ->
                    CommentBubble(
                        comment = comment,
                        byReporter = comment.user?.id != null && comment.user.id == row.issue.createdBy?.id,
                        seerrBaseUrl = seerrBaseUrl,
                        focusRequester = if (index == comments.lastIndex) lastCommentFr else null,
                        blockUp = index == 0
                    )
                }
            }
            ComposerBox(busy = busy, onClick = { composing = true })
        }
    }

    expandedMessage?.let { message ->
        ScrollableTextDialog(text = message, onDismiss = { expandedMessage = null })
    }

    if (composing) {
        BackHandler { composing = false }
        PicnicDialog(onDismiss = { composing = false }) {
            SeerrTextEntryPanel(
                title = "Add a comment",
                placeholder = "Write a comment",
                submitLabel = "Send",
                text = draft,
                onTextChange = { draft = it },
                onSubmit = {
                    if (!busy) {
                        onAddComment(draft)
                        draft = ""
                        composing = false
                    }
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        resolveFr.requestFocusWhenAttached()
    }
}

@Composable
private fun ReportCard(message: String, downTarget: FocusRequester, onOpen: () -> Unit) {
    GlassRow(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .focusProperties { down = downTarget }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                "Reported",
                style = MaterialTheme.typography.labelMedium,
                color = PicnicColors.OnDarkMuted
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = PicnicColors.OnDark,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CommentBubble(
    comment: SeerrIssueComment,
    byReporter: Boolean,
    seerrBaseUrl: String?,
    focusRequester: FocusRequester?,
    blockUp: Boolean
) {
    var focused by remember { mutableStateOf(false) }
    val shape = if (byReporter) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }
    val fill = when {
        focused -> GlassRowFocusedFill
        byReporter -> PicnicColors.Cyan.copy(alpha = 0.16f)
        else -> GlassRowIdleFill
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties {
                right = FocusRequester.Cancel
                if (blockUp) up = FocusRequester.Cancel
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
    ) {
        CommentAvatar(comment = comment, seerrBaseUrl = seerrBaseUrl)
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(fill)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    seerrCommentAuthor(comment),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PicnicColors.OnDark
                )
                seerrRelativeTime(comment.createdAt)?.let { date ->
                    Text(
                        date,
                        style = MaterialTheme.typography.labelSmall,
                        color = PicnicColors.OnDarkMuted
                    )
                }
            }
            Text(
                comment.message.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = PicnicColors.OnDark
            )
        }
    }
}

@Composable
private fun CommentAvatar(comment: SeerrIssueComment, seerrBaseUrl: String?) {
    val url = remember(comment.user?.avatar, seerrBaseUrl) {
        seerrAvatarUrl(seerrBaseUrl, comment.user?.avatar)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(PicnicColors.Cyan.copy(alpha = 0.20f))
    ) {
        if (url != null) {
            ArtworkImage(
                url = url,
                contentDescription = null,
                label = "seerr avatar",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                seerrCommentAuthor(comment).take(1).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = PicnicColors.OnDark
            )
        }
    }
}

@Composable
private fun ComposerBox(busy: Boolean, onClick: () -> Unit) {
    GlassRow(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier
            .fillMaxWidth()
            .focusProperties {
                right = FocusRequester.Cancel
                down = FocusRequester.Cancel
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text(
                if (busy) "Sending…" else "Write a comment",
                style = MaterialTheme.typography.bodyMedium,
                color = PicnicColors.OnDarkMuted
            )
        }
    }
}
