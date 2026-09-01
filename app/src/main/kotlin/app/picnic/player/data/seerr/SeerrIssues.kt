package app.picnic.player.data.seerr

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

fun canViewSeerrIssues(user: SeerrUser?): Boolean = user != null && listOf(
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

fun seerrIssueMessage(type: SeerrIssueType, reason: String?): String = reason?.trim()?.takeIf { it.isNotEmpty() } ?: type.label
