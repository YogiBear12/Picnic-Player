package app.picnic.player.data.seerr

import app.picnic.player.text.countLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class SeerrIssueType(val id: Int, val label: String) {
    VIDEO(1, "Video"),
    AUDIO(2, "Audio"),
    SUBTITLE(3, "Subtitle"),
    OTHER(4, "Other")
}

fun seerrIssueReasons(type: SeerrIssueType, subject: String): List<String> = when (type) {
    SeerrIssueType.VIDEO -> listOf("Wrong $subject", "Low quality", "Out of sync")
    SeerrIssueType.AUDIO -> listOf("Wrong language", "Out of sync", "Bad audio")
    SeerrIssueType.SUBTITLE -> listOf("Missing subtitles", "Bad subtitles", "Out of sync")
    SeerrIssueType.OTHER -> emptyList()
}

fun canCreateSeerrIssue(user: SeerrUser?): Boolean = user != null && SeerrPermission.has(user.permissions, SeerrPermission.CREATE_ISSUES)

fun canViewSeerrIssues(user: SeerrUser?): Boolean = user != null &&
    listOf(
        SeerrPermission.CREATE_ISSUES,
        SeerrPermission.VIEW_ISSUES,
        SeerrPermission.MANAGE_ISSUES
    ).any { SeerrPermission.has(user.permissions, it) }

data class SeerrIssueSeason(
    val seasonNumber: Int,
    val episodes: List<Int>
)

const val SEERR_ISSUE_ALL = 0

fun seerrIssueSeasons(episodesBySeason: Map<Int, List<Int>>): List<SeerrIssueSeason> = episodesBySeason
    .filterKeys { it > 0 }
    .toSortedMap()
    .map { (season, episodes) ->
        SeerrIssueSeason(season, episodes.filter { it > 0 }.distinct().sorted())
    }
    .filter { it.episodes.isNotEmpty() }

fun seerrIssueMessage(type: SeerrIssueType, reason: String?, extra: String? = null): String {
    val headline = reason?.trim()?.takeIf { it.isNotEmpty() } ?: type.label
    val detail = extra?.trim()?.takeIf { it.isNotEmpty() } ?: return headline
    return "$headline\n\n$detail"
}

data class SeerrIssueDisplay(
    val issue: SeerrIssue,
    val title: String,
    val posterPath: String?,
    val yearLabel: String? = null
)

fun neighborIssueId(issues: List<SeerrIssueDisplay>, removedId: Int): Int? {
    val index = issues.indexOfFirst { it.issue.id == removedId }
    if (index < 0) return null
    return issues.getOrNull(index + 1)?.issue?.id ?: issues.getOrNull(index - 1)?.issue?.id
}

val SeerrIssue.isOpen: Boolean get() = status != SeerrIssueStatus.RESOLVED

val SeerrIssue.reportBody: String? get() = comments.firstOrNull()?.message?.takeIf { it.isNotBlank() }

val SeerrIssue.replies: List<SeerrIssueComment> get() = comments.drop(1)

fun seerrIssueScopeLabel(issue: SeerrIssue): String? = when {
    issue.problemSeason > SEERR_ISSUE_ALL && issue.problemEpisode > SEERR_ISSUE_ALL ->
        "S${issue.problemSeason} E${issue.problemEpisode}"
    issue.problemSeason > SEERR_ISSUE_ALL -> "Season ${issue.problemSeason}"
    issue.media?.resolvedType == SeerrMediaType.TV -> "All seasons"
    else -> null
}

fun seerrCommentAuthor(comment: SeerrIssueComment): String = comment.user?.displayLabel() ?: "Seerr"

private fun SeerrUser.displayLabel(): String? = displayName?.takeIf { it.isNotBlank() }
    ?: username?.takeIf { it.isNotBlank() }
    ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() }

fun seerrIssueTypeSentence(code: Int?): String {
    val label = SeerrIssueType.entries.firstOrNull { it.id == code }?.label ?: SeerrIssueType.OTHER.label
    return "$label issue"
}

private val SeerrDateFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

fun seerrRelativeTime(iso: String?, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String? {
    val instant = iso?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
    val minutes = ChronoUnit.MINUTES.between(instant, now)
    val hours = ChronoUnit.HOURS.between(instant, now)
    val days = ChronoUnit.DAYS.between(instant, now)
    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> "${countLabel(minutes.toInt(), "minute")} ago"
        hours < 24L -> "${countLabel(hours.toInt(), "hour")} ago"
        days < 7L -> "${countLabel(days.toInt(), "day")} ago"
        else -> SeerrDateFormat.withZone(zone).format(instant)
    }
}

fun seerrAvatarUrl(seerrBaseUrl: String?, avatar: String?): String? {
    val path = avatar?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (path.startsWith("http://") || path.startsWith("https://")) return path
    val base = seerrBaseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return null
    return base + if (path.startsWith("/")) path else "/$path"
}
