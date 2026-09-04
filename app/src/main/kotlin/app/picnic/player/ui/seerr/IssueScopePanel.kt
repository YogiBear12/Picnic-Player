package app.picnic.player.ui.seerr

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.picnic.player.data.seerr.SEERR_ISSUE_ALL
import app.picnic.player.data.seerr.SeerrIssueSeason
import app.picnic.player.ui.common.panelSurface
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.theme.PicnicColors

private const val SEERR_ISSUE_SEASON_HEADER = -1

private data class IssueScopeRow(
    val season: Int,
    val episode: Int,
    val label: String,
    val indented: Boolean,
    val trailingIcon: ImageVector?,
    val onClick: () -> Unit
)

@Composable
internal fun IssueScopePanel(
    seasons: List<SeerrIssueSeason>,
    loading: Boolean,
    selectedSeason: Int,
    selectedEpisode: Int,
    onSelect: (season: Int, episode: Int) -> Unit
) {
    var expandedSeason by remember(selectedSeason) { mutableStateOf(selectedSeason.takeIf { it > 0 }) }
    val seedFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val rows = buildList {
        add(
            IssueScopeRow(
                season = SEERR_ISSUE_ALL,
                episode = SEERR_ISSUE_ALL,
                label = "All seasons",
                indented = false,
                trailingIcon = null,
                onClick = { onSelect(SEERR_ISSUE_ALL, SEERR_ISSUE_ALL) }
            )
        )
        seasons.forEach { season ->
            val expanded = expandedSeason == season.seasonNumber
            add(
                IssueScopeRow(
                    season = season.seasonNumber,
                    episode = SEERR_ISSUE_SEASON_HEADER,
                    label = "Season ${season.seasonNumber}",
                    indented = false,
                    trailingIcon = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    onClick = { expandedSeason = if (expanded) null else season.seasonNumber }
                )
            )
            if (!expanded) return@forEach
            add(
                IssueScopeRow(
                    season = season.seasonNumber,
                    episode = SEERR_ISSUE_ALL,
                    label = "All episodes",
                    indented = true,
                    trailingIcon = null,
                    onClick = { onSelect(season.seasonNumber, SEERR_ISSUE_ALL) }
                )
            )
            season.episodes.forEach { episode ->
                add(
                    IssueScopeRow(
                        season = season.seasonNumber,
                        episode = episode,
                        label = "Episode $episode",
                        indented = true,
                        trailingIcon = null,
                        onClick = { onSelect(season.seasonNumber, episode) }
                    )
                )
            }
        }
    }

    val seedRow = rows.firstOrNull {
        it.season == selectedSeason && it.episode == selectedEpisode
    } ?: rows.first()

    Column(
        modifier = Modifier
            .panelSurface(SeerrPanelWidth, SeerrPanelCornerRadius)
            .focusGroup()
    ) {
        SeerrPanelHeader(title = "What is affected?")
        if (loading) {
            Box(
                modifier = Modifier
                    .height(SeerrListViewportHeight)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PicnicColors.Accent)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .height(SeerrListViewportHeight)
                    .fillMaxWidth()
                    .focusGroup(),
                contentPadding = PaddingValues(horizontal = SeerrContentInset),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(rows, key = { it.season to it.episode }) { row ->
                    IssueScopeRowItem(
                        row = row,
                        focusRequester = if (row === seedRow) seedFocus else null
                    )
                }
            }
        }
    }

    LaunchedEffect(loading) {
        if (loading) return@LaunchedEffect
        listState.scrollToItem(rows.indexOf(seedRow).coerceAtLeast(0))
        seedFocus.requestFocusWhenAttached(maxFrames = 20)
    }
}

@Composable
private fun IssueScopeRowItem(
    row: IssueScopeRow,
    focusRequester: FocusRequester?
) {
    Surface(
        onClick = row.onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(SeerrRowCornerRadius)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier.padding(
                start = SeerrRowInnerPadding + if (row.indented) 18.dp else 0.dp,
                end = SeerrRowInnerPadding,
                top = 9.dp,
                bottom = 9.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = row.label,
                color = Color.White.copy(alpha = if (row.indented) 0.78f else 0.92f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            row.trailingIcon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.White.copy(alpha = 0.55f)
                )
            }
        }
    }
}
