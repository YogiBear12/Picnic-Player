package app.picnic.player.ui.browse

import app.picnic.player.data.media.recentlyAddedDetail
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/** Jellyfin ticks (100ns) per minute: 10 000 ticks/ms × 1 000 ms/s × 60 s/min. */
internal const val TICKS_PER_MINUTE = 600_000_000L

/** The app's short date rendering for air/premiere dates, e.g. "4 Jun 2013". */
internal val ShortDateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

internal fun runtimeMinutes(item: BaseItemDto): Int? = item.runTimeTicks?.let { (it / TICKS_PER_MINUTE).toInt() }?.takeIf { it > 0 }

internal fun runtimeLabel(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

internal fun formatSeriesYears(item: BaseItemDto): String {
    val start = item.productionYear ?: item.premiereDate?.year
    if (start == null) return ""
    if (item.status.equals("Continuing", ignoreCase = true)) return "$start - Present"
    val endYear = item.endDate?.year
    if (item.status.equals("Ended", ignoreCase = true) || endYear != null) {
        if (endYear == null || endYear == start) return start.toString()
        return "$start - $endYear"
    }
    return start.toString()
}

/** Minutes left in an in-progress item, or null when unstarted/finished. */
internal fun minutesLeft(item: BaseItemDto): Int? {
    val runtime = item.runTimeTicks ?: return null
    val positionTicks = item.userData?.playbackPositionTicks ?: 0L
    if (positionTicks <= 0L) return null
    return ((runtime - positionTicks) / TICKS_PER_MINUTE).toInt().takeIf { it > 0 }
}

internal fun movieMetadataLine(item: BaseItemDto): String {
    val parts = mutableListOf<String>()
    item.productionYear?.let { parts += it.toString() }
    runtimeMinutes(item)?.let { parts += runtimeLabel(it) }
    return parts.joinToString(" • ")
}

internal fun recentlyAddedMetadataLine(item: BaseItemDto, seasonCount: Int?): String {
    val parts = mutableListOf<String>()
    when (item.type) {
        BaseItemKind.SERIES, BaseItemKind.SEASON -> formatSeriesYears(item).takeIf { it.isNotEmpty() }?.let { parts += it }
        else -> item.productionYear?.let { parts += it.toString() }
    }
    val runtime = runtimeMinutes(item)?.let(::runtimeLabel).orEmpty()
    parts += recentlyAddedDetail(item, seasonCount, runtime)
    return parts.filter { it.isNotEmpty() }.joinToString(" • ")
}

/** Genres get their own hero line (below the badge rail), not the details line. */
internal fun heroGenresLine(item: BaseItemDto): String = item.genres.orEmpty().take(3).joinToString(" • ")

/**
 * `{air date} • {runtime}`. The SxEy marker lives on the episode-title line ([BrowseHero]);
 * time remaining lives on the card badge, not here.
 */
internal fun episodeMetadataLine(item: BaseItemDto): String {
    val parts = mutableListOf<String>()
    val airDate = item.premiereDate?.format(ShortDateFormat)
    when {
        airDate != null -> parts += airDate
        item.productionYear != null -> parts += item.productionYear.toString()
    }

    runtimeMinutes(item)?.let { parts += runtimeLabel(it) }
    return parts.joinToString(" • ")
}
