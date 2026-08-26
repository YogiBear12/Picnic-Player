package app.picnic.player.ui.browse

import app.picnic.player.ui.common.recentlyAddedDetail
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

internal const val TICKS_PER_MINUTE = 600_000_000L

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
    val years = when (item.type) {
        BaseItemKind.SERIES, BaseItemKind.SEASON -> formatSeriesYears(item)
        else -> item.productionYear?.toString().orEmpty()
    }
    val runtime = runtimeMinutes(item)?.let(::runtimeLabel).orEmpty()
    val detail = recentlyAddedDetail(item, seasonCount, runtime)
    return listOf(years, detail).filter { it.isNotEmpty() }.joinToString(" • ")
}

internal fun heroGenresLine(item: BaseItemDto): String = item.genres.orEmpty().take(3).joinToString(" • ")

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
