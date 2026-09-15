@file:OptIn(ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.seerr

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.picnic.player.data.seerr.SeerrCatalogItem
import app.picnic.player.data.seerr.SeerrMediaType
import app.picnic.player.data.seerr.formatSeerrSeriesYears
import app.picnic.player.ui.browse.BrowseHeroSummaryLineHeight
import app.picnic.player.ui.browse.HeroDetailsRowHeight
import app.picnic.player.ui.browse.heroBlockHeight
import app.picnic.player.ui.browse.runtimeLabel
import app.picnic.player.ui.common.SpecPillHeight
import java.util.Locale

private val HeroInfoTopGap = 14.dp
private val HeroSummaryTopGap = 6.dp
private val HeroGenresTopGap = 10.dp
private val StarGold = Color(0xFFF0B843)
private val CertLine = Color.White.copy(alpha = 0.30f)
private val CertInk = Color.White.copy(alpha = 0.88f)

/**
 * Seerr immersive / detail hero — same fixed [heroBlockHeight] geometry as
 * [app.picnic.player.ui.browse.BrowseHero] so Discover ↔ Seerr Detail don't jump.
 * Metadata line mirrors [app.picnic.player.ui.browse.HeroInfoLine] (bodySmall + gold star).
 */
@Composable
fun SeerrHero(
    item: SeerrCatalogItem?,
    logoHeight: Dp,
    modifier: Modifier = Modifier,
    summaryLines: Int = 3,
    onSummaryClick: (() -> Unit)? = null,
    summaryDown: (() -> FocusRequester)? = null,
    summaryFocusRequester: FocusRequester? = null
) {
    if (item == null) return
    Column(modifier = modifier.height(heroBlockHeight(logoHeight))) {
        Box(
            modifier = Modifier.height(logoHeight).fillMaxWidth(),
            contentAlignment = Alignment.BottomStart
        ) {
            Text(
                text = item.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }

        SeerrHeroInfoLine(
            item = item,
            modifier = Modifier.padding(top = HeroInfoTopGap)
        )

        item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
            val summaryText: @Composable (Modifier) -> Unit = { textModifier ->
                Text(
                    overview,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = BrowseHeroSummaryLineHeight,
                    maxLines = summaryLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = textModifier
                )
            }
            if (onSummaryClick == null) {
                summaryText(Modifier.padding(top = HeroSummaryTopGap).fillMaxWidth())
            } else {
                Surface(
                    onClick = onSummaryClick,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier
                        .padding(top = HeroSummaryTopGap)
                        .fillMaxWidth()
                        .then(
                            if (summaryFocusRequester != null) {
                                Modifier.focusRequester(summaryFocusRequester)
                            } else {
                                Modifier
                            }
                        )
                        .then(
                            if (summaryDown != null) {
                                Modifier.focusProperties { down = summaryDown() }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    summaryText(Modifier.fillMaxWidth())
                }
            }
        }

        val genres = item.genreNames.take(3).joinToString(" • ")
        if (genres.isNotEmpty()) {
            Text(
                genres,
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = HeroGenresTopGap)
            )
        }
    }
}

@Composable
private fun SeerrHeroInfoLine(
    item: SeerrCatalogItem,
    modifier: Modifier = Modifier
) {
    val meta = seerrMetadataLine(item)
    val rating = item.voteAverage?.takeIf { it > 0 }
    val certificate = item.certificate?.takeIf { it.isNotBlank() }
    if (meta.isEmpty() && rating == null && certificate == null) return

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
        if (rating != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = StarGold,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = String.format(Locale.US, "%.1f", rating),
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (certificate != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Transparent)
                    .border(1.dp, CertLine, RoundedCornerShape(6.dp))
                    .height(SpecPillHeight)
                    .padding(horizontal = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = certificate.uppercase(Locale.ROOT),
                    color = CertInk,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/**
 * Year / year-range + runtime or season count — mirrors [app.picnic.player.ui.browse.recentlyAddedMetadataLine].
 */
internal fun seerrMetadataLine(item: SeerrCatalogItem): String {
    val parts = mutableListOf<String>()
    when (item.mediaType) {
        SeerrMediaType.MOVIE -> {
            item.releaseDate?.take(4)?.takeIf { it.length == 4 }?.let { parts += it }
            item.runtimeMinutes?.let { parts += runtimeLabel(it) }
        }
        SeerrMediaType.TV -> {
            formatSeerrSeriesYears(item.releaseDate, item.lastAirDate, item.seriesStatus)
                .takeIf { it.isNotEmpty() }?.let { parts += it }
            val seasons = item.seasonCount
            if (seasons != null && seasons > 0) {
                parts += if (seasons == 1) "1 season" else "$seasons seasons"
            }
        }
    }
    return parts.joinToString(" • ")
}
