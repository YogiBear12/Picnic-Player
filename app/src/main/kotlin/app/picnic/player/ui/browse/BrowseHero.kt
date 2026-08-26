@file:OptIn(ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.picnic.player.ui.common.LocalImageUrls
import coil3.compose.AsyncImage
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

internal val BrowseHeroSummaryLineHeight = 20.sp

private val EpisodeTitleLineHeight = 16.dp

private val HeroInfoTopGap = 14.dp
private val HeroTitleTopGap = 4.dp
private val HeroSummaryTopGap = 6.dp
private val HeroRailTopGap = 10.dp
private val HeroGenresTopGap = 10.dp
private val HeroGenresLineHeight = 16.dp
private val HeroSummaryFocusInset = 8.dp

internal val HeroBadgeRailReserve = HeroRailTopGap + HeroSpecPillHeight

private fun Modifier.outsetHorizontally(inset: Dp) = layout { measurable, constraints ->
    val extra = inset.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth
        )
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-inset.roundToPx(), 0) }
}

@Composable
internal fun heroBlockHeight(logoHeight: Dp, reserveBadgeRail: Boolean = true): Dp {
    val summaryLine = with(LocalDensity.current) { BrowseHeroSummaryLineHeight.toDp() }
    val badgeRail = if (reserveBadgeRail) HeroBadgeRailReserve else 0.dp
    return logoHeight + HeroInfoTopGap + HeroDetailsRowHeight + HeroSummaryTopGap +
        summaryLine * 3 + badgeRail + HeroGenresTopGap + HeroGenresLineHeight
}

@Composable
internal fun heroRegionHeight(metrics: BrowseLayoutMetrics, screenHeight: Dp, blockHeight: Dp): Dp = screenHeight - metrics.rowsRegionHeight - heroBlockHeight(metrics.logoHeight) + blockHeight

@Composable
internal fun BrowseHero(
    item: BaseItemDto?,
    logoHeight: Dp,
    continueWatching: Boolean,
    seasonCount: Int?,
    modifier: Modifier = Modifier,
    summaryLines: Int? = null,
    onSummaryClick: (() -> Unit)? = null,
    summaryDown: (() -> FocusRequester)? = null,
    streamsOverride: List<org.jellyfin.sdk.model.api.MediaStream>? = null,
    blockHeight: Dp = heroBlockHeight(logoHeight)
) {
    if (item == null) return
    val isEpisode = item.type == BaseItemKind.EPISODE
    val isSeason = item.type == BaseItemKind.SEASON
    val specs = heroSpecs(item, streamsOverride)
    val isMovie = item.type == BaseItemKind.MOVIE
    val isSeries = item.type == BaseItemKind.SERIES
    val overviewLines = summaryLines ?: when {
        isEpisode -> 2
        isMovie || isSeries -> 3
        specs.hasAny -> 3
        else -> 4
    }
    Column(modifier = modifier.height(blockHeight)) {
        val logoUrl = LocalImageUrls.current.logo(item, fillWidth = 480)
        val headline = when {
            isEpisode || isSeason -> item.seriesName ?: item.name.orEmpty()
            else -> item.name.orEmpty()
        }
        if (logoUrl != null) {
            AsyncImage(
                model = logoUrl,
                contentDescription = headline,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                modifier = Modifier.height(logoHeight).fillMaxWidth()
            )
        } else {
            Box(
                modifier = Modifier.height(logoHeight).fillMaxWidth(),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(
                    text = headline,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        val meta = when {
            isEpisode -> episodeMetadataLine(item)
            continueWatching -> movieMetadataLine(item)
            else -> recentlyAddedMetadataLine(item, seasonCount)
        }
        HeroInfoLine(
            item = item,
            meta = meta,
            specs = specs,
            modifier = Modifier.padding(top = HeroInfoTopGap)
        )

        if (isEpisode) {
            val episodeLine = listOfNotNull(
                item.parentIndexNumber?.let { s ->
                    item.indexNumber?.let { e -> "S$s E$e" }
                },
                item.name
            ).joinToString(" • ")
            if (episodeLine.isNotEmpty()) {
                Box(
                    modifier = Modifier.padding(top = HeroTitleTopGap).height(EpisodeTitleLineHeight),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        episodeLine,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.wrapContentHeight(
                            align = Alignment.CenterVertically,
                            unbounded = true
                        )
                    )
                }
            }
        }

        item.overview?.let { overview ->
            val summaryText: @Composable (Modifier) -> Unit = { textModifier ->
                Text(
                    overview,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = BrowseHeroSummaryLineHeight,
                    maxLines = overviewLines,
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
                        .outsetHorizontally(HeroSummaryFocusInset)
                        .fillMaxWidth()
                        .then(
                            if (summaryDown != null) {
                                Modifier.focusProperties { down = summaryDown() }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    summaryText(Modifier.fillMaxWidth().padding(horizontal = HeroSummaryFocusInset))
                }
            }
        }

        if (specs.hasAny) {
            HeroBadgeRail(specs = specs, modifier = Modifier.padding(top = HeroRailTopGap))
        }

        if (!isEpisode) {
            val genres = heroGenresLine(item)
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
}
