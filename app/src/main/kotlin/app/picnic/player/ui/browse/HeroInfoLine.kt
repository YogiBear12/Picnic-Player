@file:OptIn(ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.R
import app.picnic.player.ui.common.SpecPill
import app.picnic.player.ui.common.SpecPillHeight
import app.picnic.player.ui.common.SpecPillInk
import app.picnic.player.util.LanguageDisplay
import app.picnic.player.util.audioSpatialLabel
import java.util.Locale
import kotlin.math.roundToInt
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.VideoRangeType

// ── Rail geometry ────────────────────────────────────────────────────────────
// Fixed (not screen-scaled) so DetailScreen can reserve the exact same height it
// renders — the rail grows the hero region *downward*, keeping the logo pinned.
internal val HeroSpecPillHeight = SpecPillHeight

/**
 * Fixed height for the details row. Its content varies by type (movies carry a cert pill +
 * rating chips ~20dp tall, episodes just the date text ~16dp), and the row centres its
 * children vertically — so without a constant height the centred text baseline drifts and
 * episodes read slightly higher than movies. Pin it to the tallest element (the pill).
 */
internal val HeroDetailsRowHeight = HeroSpecPillHeight

// ── Palette ──────────────────────────────────────────────────────────────────
private val CertLine = Color.White.copy(alpha = 0.30f)
private val StarGold = Color(0xFFF0B843)

// ── Data model ───────────────────────────────────────────────────────────────
internal data class HeroSpecs(
    val certificate: String?,
    val resolution: String?,
    val dynamicRange: String?,
    val atmos: Boolean,
    val audio: String?,
    val hasSubtitles: Boolean
) {
    /** Rail visibility — the certificate renders on the details line, not the rail. */
    val hasAny: Boolean =
        resolution != null || dynamicRange != null || atmos || audio != null || hasSubtitles
}

/**
 * Technical badges derived from an item's media streams. Only meaningful once the full item
 * (with `mediaStreams`) has loaded — Home browse items carry no streams, so the rail stays
 * empty there and the Home hero is unaffected. Series/seasons have no A/V streams either.
 */
internal fun heroSpecs(item: BaseItemDto, streamsOverride: List<MediaStream>? = null): HeroSpecs {
    val streams = item.mediaStreams.orEmpty().ifEmpty { streamsOverride.orEmpty() }
    val video = streams.filter { it.type == MediaStreamType.VIDEO }
    val audio = streams.filter { it.type == MediaStreamType.AUDIO }
    val subtitle = streams.filter { it.type == MediaStreamType.SUBTITLE }

    // The certificate is item metadata, not file metadata — series/seasons carry one even
    // though they have no streams. Showing it keeps the rail row present on series heroes,
    // so the summary doesn't jump vertically between movie and series focus.
    if (video.isEmpty() && audio.isEmpty()) {
        return HeroSpecs(
            certificate = item.officialRating?.takeIf { it.isNotBlank() },
            resolution = null,
            dynamicRange = null,
            atmos = false,
            audio = null,
            hasSubtitles = false
        )
    }

    return HeroSpecs(
        certificate = item.officialRating?.takeIf { it.isNotBlank() },
        resolution = resolutionLabel(video),
        dynamicRange = dynamicRange(video),
        atmos = hasAtmos(audio),
        audio = audioLabel(audio),
        hasSubtitles = subtitle.isNotEmpty()
    )
}

/** True when [heroSpecs] would render at least one pill — DetailScreen reserves rail height on this. */
internal fun heroHasSpecRail(item: BaseItemDto): Boolean = heroSpecs(item).hasAny

private const val UHD_MIN_WIDTH = 3200
private const val HD_MIN_WIDTH = 1280

private fun resolutionLabel(video: List<MediaStream>): String? {
    val width = video.mapNotNull { it.width }.maxOrNull() ?: return null
    return when {
        width >= UHD_MIN_WIDTH -> "4K"
        width >= HD_MIN_WIDTH -> "HD"
        else -> "SD"
    }
}

/** Highest range wins: Dolby Vision > HDR10+ > HDR10 > HLG > (SDR shows nothing). */
private fun dynamicRange(video: List<MediaStream>): String? {
    val types = video.mapNotNull { it.videoRangeType }
    return when {
        types.any { it.name.startsWith("DOVI") } -> "Dolby Vision"
        VideoRangeType.HDR10_PLUS in types -> "HDR10+"
        VideoRangeType.HDR10 in types -> "HDR10"
        VideoRangeType.HLG in types -> "HLG"
        // Older items may only carry the coarse videoRange flag.
        video.any { it.videoRange?.name == "HDR" } -> "HDR"
        else -> null
    }
}

private fun hasAtmos(audio: List<MediaStream>): Boolean {
    val primary = audio.firstOrNull { it.isDefault } ?: audio.firstOrNull() ?: return false
    return audioSpatialLabel(
        primary.audioSpatialFormat?.serialName,
        primary.profile,
        primary.displayTitle
    ) == "Atmos"
}

/** Language only — channel layout was pushing the pill onto a second line. */
private fun audioLabel(audio: List<MediaStream>): String? {
    if (audio.isEmpty()) return null
    val languages = audio.mapNotNull { it.language?.takeIf { l -> l.isNotBlank() } }.distinct()
    return when {
        languages.size >= 3 -> "Multi audio"
        languages.size == 2 -> "Dual audio"
        else -> {
            val primary = audio.firstOrNull { it.isDefault } ?: audio.first()
            primary.language
                ?.takeIf { it.isNotBlank() }
                ?.let { LanguageDisplay.name(it) }
                ?.takeIf { it != "Unknown" }
        }
    }
}

// ── UI ───────────────────────────────────────────────────────────────────────

/**
 * The hero details line: date/runtime, community star, critic mark, certificate pill. The
 * badge rail is rendered separately ([HeroBadgeRail]) so [BrowseHero] can place it below the
 * summary. Both Home and Detail render this so they can never drift.
 */
@Composable
internal fun HeroInfoLine(
    item: BaseItemDto,
    meta: String,
    specs: HeroSpecs,
    modifier: Modifier = Modifier
) {
    // Episodes: community/critic ratings belong to the series, not the episode — hide them.
    val isEpisode = item.type == BaseItemKind.EPISODE
    val community = item.communityRating.takeUnless { isEpisode }
    val critic = item.criticRating.takeUnless { isEpisode }

    // Fixed height so the centred text baseline is identical whether or not the row also
    // carries a pill/chips (otherwise episodes read higher than movies).
    Row(
        modifier = modifier.height(HeroDetailsRowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (meta.isNotEmpty()) {
            Text(
                meta,
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (community != null) {
            RatingChip {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = StarGold,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = String.format(Locale.US, "%.1f", community),
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (critic != null) {
            val fresh = critic >= 60f
            RatingChip {
                Icon(
                    painter = painterResource(
                        if (fresh) {
                            R.drawable.ic_rotten_tomatoes_fresh
                        } else {
                            R.drawable.ic_rotten_tomatoes_rotten
                        }
                    ),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "${critic.roundToInt()}%",
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        specs.certificate?.let {
            SpecPill(it, containerColor = Color.Transparent, borderColor = CertLine)
        }
    }
}

/**
 * Self-hiding rail of technical spec badges (resolution / dynamic range / Atmos / audio /
 * subtitles). Fixed pill height + a plain Row (not FlowRow) makes a second line impossible:
 * overflow clips instead of wrapping. Caller only composes this when [HeroSpecs.hasAny].
 */
@Composable
internal fun HeroBadgeRail(specs: HeroSpecs, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(HeroSpecPillHeight)
            .clipToBounds(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        specs.resolution?.let { SpecPill(it) }
        specs.dynamicRange?.let { SpecPill(it) }
        if (specs.atmos) SpecPill("Atmos")
        specs.audio?.let {
            SpecPill(it, leadingIcon = {
                Icon(
                    Icons.Filled.VolumeUp,
                    null,
                    tint = SpecPillInk,
                    modifier = Modifier.size(11.dp)
                )
            })
        }
        if (specs.hasSubtitles) {
            // Icon-only: the CC symbol alone says subtitles are available.
            SpecPill(text = null, leadingIcon = {
                Icon(
                    Icons.Filled.ClosedCaption,
                    null,
                    tint = SpecPillInk,
                    modifier = Modifier.size(12.dp)
                )
            })
        }
    }
}

@Composable
private fun RatingChip(content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) { content() }
}
