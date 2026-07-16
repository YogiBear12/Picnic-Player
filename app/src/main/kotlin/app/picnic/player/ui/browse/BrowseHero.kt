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
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.jellyfin.JellyfinImages
import coil3.compose.AsyncImage
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/** Summary line height — detail screen derives its extra-line offset from this. */
internal val BrowseHeroSummaryLineHeight = 20.sp

/**
 * Layout height reserved for the episode-title line. Kept tight so per-title font
 * metrics can't grow the box and shift the top-packed hero on focus change. The Text
 * may paint descenders slightly outside this slot into [HeroSummaryTopGap] (unbounded
 * wrap) — layout contribution stays fixed.
 */
private val EpisodeTitleLineHeight = 16.dp

// Vertical rhythm of the info block (gaps between elements). Tightened for compactness; kept
// deliberately separate from [heroBlockHeight] so adjusting them tightens the content without
// moving the logo off its anchor.
private val HeroInfoTopGap = 14.dp // logo → details line
private val HeroTitleTopGap = 4.dp // details line → episode title
private val HeroSummaryTopGap = 6.dp // (episode title / details) → summary
private val HeroRailTopGap = 10.dp // summary → badge rail
private val HeroGenresTopGap = 10.dp // summary / rail → genres

/**
 * Reserved height of the info block, sized to the tallest (movie) variant. The block is a
 * fixed-height, bottom-anchored Column with its content top-packed, so the logo and details
 * line sit at a constant Y for every card (movie / series / episode); shorter variants — and
 * tighter inter-element gaps — leave their slack as a larger bottom gap rather than floating
 * the logo. Held independent of the render gaps above (fixed remainder below the logo) so
 * tightening those doesn't drop the logo.
 */
@Composable
internal fun heroBlockHeight(logoHeight: Dp): Dp {
    val summaryLine = with(LocalDensity.current) { BrowseHeroSummaryLineHeight.toDp() }
    // 96dp ≈ details row + badge rail + genre line + their gaps (the movie stack below the
    // logo, excluding the 3-line summary).
    return logoHeight + 96.dp + summaryLine * 3
}

@Composable
internal fun BrowseHero(
    item: BaseItemDto?,
    session: UserSession,
    logoHeight: Dp,
    continueWatching: Boolean,
    seasonCount: Int?,
    modifier: Modifier = Modifier,
    summaryLines: Int? = null,
    // Detail screen: summary becomes a focusable element opening the full-text dialog.
    // Rendering stays geometry-identical to the plain text so Home → Detail doesn't shift.
    onSummaryClick: (() -> Unit)? = null,
    // D-pad down from the focused summary; detail hands focus back to the last-used button.
    summaryDown: (() -> FocusRequester)? = null,
    /** Series items carry no streams — the lead episode's are borrowed for the badge rail. */
    streamsOverride: List<org.jellyfin.sdk.model.api.MediaStream>? = null
) {
    if (item == null) return
    val isEpisode = item.type == BaseItemKind.EPISODE
    val isSeason = item.type == BaseItemKind.SEASON
    val specs = heroSpecs(item, streamsOverride)
    val isMovie = item.type == BaseItemKind.MOVIE
    val isSeries = item.type == BaseItemKind.SERIES
    // Summary length fills for what the variant is missing (best-effort — the block's bottom
    // gap absorbs the rest): episodes carry a title line so keep 2; a series with no badge
    // rail grows to 4 to backfill the missing rail; otherwise 3.
    val overviewLines = summaryLines ?: when {
        isEpisode -> 2
        isMovie || isSeries -> 3 // Lock to 3 to prevent layout jump during lazy badge load
        specs.hasAny -> 3
        else -> 4
    }
    // Fixed height + top-packed content: logo/details always land at the same Y.
    Column(modifier = modifier.height(heroBlockHeight(logoHeight))) {
        val logoUrl = JellyfinImages.logo(session, item, fillWidth = 480)
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
            // Constrained to the logo box so the details line below stays at the same Y as it
            // does for items that have a logo image.
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
        // Details line (certificate, date/runtime, rating chips) then the badge rail. Renders
        // on both the Home and Detail heroes (same block), so the two can never drift; the
        // rail only appears when the item actually carries A/V streams.
        HeroInfoLine(
            item = item,
            meta = meta,
            specs = specs,
            modifier = Modifier.padding(top = HeroInfoTopGap)
        )

        if (isEpisode) {
            // "S1 E5 • Episode Title" — the SxEy marker lives here, not on the details line.
            val episodeLine = listOfNotNull(
                item.parentIndexNumber?.let { s ->
                    item.indexNumber?.let { e -> "S$s E$e" }
                },
                item.name
            ).joinToString(" • ")
            if (episodeLine.isNotEmpty()) {
                // Fixed LAYOUT height so glyph metrics can't grow the slot and nudge the
                // top-packed hero. Text measures unbounded and paints descenders into the
                // summary gap below — Box does not clip, so nothing shifts in the column.
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
            // Shrink-wrapped (no reserved height): a short summary lets the badge rail and
            // genres below move up, with the freed space falling to the block's bottom gap.
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
                // Detail: the summary is a focusable surface opening the full-text dialog. The
                // highlight wraps the text and keeps the plain variant's origin, so the text
                // itself doesn't move between Home and Detail.
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

        // Badge rail now sits below the summary. It collapses entirely when the item has no
        // badges (a rail-less series), so the genres move up into that space.
        if (specs.hasAny) {
            HeroBadgeRail(specs = specs, modifier = Modifier.padding(top = HeroRailTopGap))
        }

        // Genres close the block — movies and shows only, episodes don't carry useful genres.
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
