package app.picnic.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.seerr.SeerrImages
import app.picnic.player.data.seerr.SeerrIssueDisplay
import app.picnic.player.data.seerr.seerrIssueScopeLabel
import app.picnic.player.data.seerr.seerrIssueTypeSentence
import app.picnic.player.data.seerr.seerrRelativeTime
import app.picnic.player.text.countLabel
import app.picnic.player.ui.common.ArtworkImage
import app.picnic.player.ui.common.GlassRowIdleFill
import app.picnic.player.ui.common.Pill
import app.picnic.player.ui.theme.PicnicColors

internal val ThumbWidth = 48.dp
internal val ThumbHeight = 72.dp
internal val DetailThumbWidth = 132.dp
internal val DetailThumbHeight = 198.dp
internal val OpenAccent = PicnicColors.Warning
internal val ResolvedAccent = PicnicColors.Success

private val MetaLabelWidth = 84.dp

@Composable
internal fun StatusPill(open: Boolean) {
    val accent = if (open) OpenAccent else ResolvedAccent
    Pill(
        text = if (open) "Open" else "Resolved",
        color = accent,
        fill = accent.copy(alpha = 0.16f),
        horizontal = 10.dp,
        vertical = 4.dp
    )
}

@Composable
internal fun MetaChip(text: String) {
    Pill(text, PicnicColors.OnDark.copy(alpha = 0.85f), GlassRowIdleFill, horizontal = 10.dp, vertical = 4.dp)
}

@Composable
internal fun MetaLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = PicnicColors.OnDarkMuted,
            modifier = Modifier.width(MetaLabelWidth)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = PicnicColors.OnDarkMuted
        )
    }
}

@Composable
internal fun IssueThumb(
    row: SeerrIssueDisplay,
    seerrBaseUrl: String?,
    cacheImages: Boolean,
    width: Dp,
    height: Dp
) {
    val url = remember(row.posterPath, seerrBaseUrl, cacheImages) {
        SeerrImages.poster(seerrBaseUrl, row.posterPath, cacheImages, size = "w342")
    }
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        if (url != null) {
            ArtworkImage(
                url = url,
                contentDescription = row.title,
                label = "seerr issue '${row.title}'",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

internal fun issueMetaLine(row: SeerrIssueDisplay, commentCount: Int): String = listOfNotNull(
    seerrIssueScopeLabel(row.issue),
    seerrIssueTypeSentence(row.issue.issueType),
    seerrRelativeTime(row.issue.createdAt)?.let { "Opened $it" },
    seerrRelativeTime(row.issue.updatedAt)
        ?.takeIf { row.issue.updatedAt != row.issue.createdAt }
        ?.let { "Updated $it" },
    countLabel(commentCount, "comment").takeIf { commentCount > 0 }
).joinToString(" · ")
