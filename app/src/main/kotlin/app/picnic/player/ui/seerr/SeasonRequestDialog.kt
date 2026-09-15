package app.picnic.player.ui.seerr

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.SelectableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.picnic.player.data.seerr.SeerrMediaStatus
import app.picnic.player.data.seerr.SeerrSeasonAvailability
import app.picnic.player.data.seerr.SeerrSeasonPickItem
import app.picnic.player.data.seerr.seasonLibraryBadgeLabel
import app.picnic.player.ui.common.PanelContentInset
import app.picnic.player.ui.common.PicnicDialog
import app.picnic.player.ui.common.focusSeed
import app.picnic.player.ui.common.panelSurface
import app.picnic.player.ui.common.rememberFocusSeed
import app.picnic.player.ui.common.requestFocusWhenAttached

private val SeasonAvailableGreen = Color(0xFF6BCB77)

@Composable
internal fun SeasonRequestDialog(
    seasons: List<SeerrSeasonPickItem>,
    onConfirm: (List<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    PicnicDialog(onDismiss = onDismiss) {
        SeasonRequestPanel(seasons = seasons, onConfirm = onConfirm, onDismiss = onDismiss)
    }
}

@Composable
internal fun SeasonRequestPanel(
    seasons: List<SeerrSeasonPickItem>,
    onConfirm: (List<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    val selectableNumbers = remember(seasons) {
        seasons.filter { it.selectable }.map { it.seasonNumber }.toSet()
    }
    var selected by remember(seasons) { mutableStateOf(selectableNumbers) }
    val listState = rememberLazyListState()
    val lastListFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    val seedIndex = if (seasons.isNotEmpty()) 0 else -1
    val seed = rememberFocusSeed(seedIndex, enabled = seedIndex >= 0)
    val lastFocusableIndex = seasons.lastIndex
    val canConfirm = selected.isNotEmpty()
    val upFromConfirm = when {
        lastFocusableIndex < 0 -> FocusRequester.Default
        seedIndex == lastFocusableIndex -> seed.requester
        else -> lastListFocus
    }

    Column(
        modifier = Modifier
            .panelSurface(SeerrPanelWidth)
            .focusGroup()
    ) {
        SeerrPanelHeader(title = "Select seasons")
        LazyColumn(
            state = listState,
            modifier = Modifier
                .height(SeerrListViewportHeight)
                .fillMaxWidth()
                .focusGroup(),
            contentPadding = PaddingValues(horizontal = PanelContentInset),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(seasons, key = { _, item -> item.seasonNumber }) { index, item ->
                val focusModifier = when {
                    index == seedIndex -> Modifier.focusSeed(seed)
                    index == lastFocusableIndex -> Modifier.focusRequester(lastListFocus)
                    else -> Modifier
                }
                SeasonPickRow(
                    item = item,
                    selected = item.seasonNumber in selected,
                    onToggle = {
                        if (!item.selectable) return@SeasonPickRow
                        selected = if (item.seasonNumber in selected) {
                            selected - item.seasonNumber
                        } else {
                            selected + item.seasonNumber
                        }
                    },
                    modifier = focusModifier,
                    downTarget = when {
                        index == lastFocusableIndex && canConfirm -> confirmFocus
                        else -> null
                    }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SeerrActionButton(
            title = "Request",
            onClick = {
                if (!canConfirm) return@SeerrActionButton
                onConfirm(selected.sorted())
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PanelContentInset)
                .focusRequester(confirmFocus)
                .focusProperties {
                    up = upFromConfirm
                    canFocus = canConfirm
                }
        )
    }
    LaunchedEffect(seedIndex) {
        if (seedIndex >= 0) {
            listState.scrollToItem(seedIndex)
        } else if (canConfirm) {
            confirmFocus.requestFocusWhenAttached()
        }
    }
}

@Composable
private fun SeasonPickRow(
    item: SeerrSeasonPickItem,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    downTarget: FocusRequester?
) {
    val titleAlpha = if (item.selectable) 0.92f else 0.55f
    val rowModifier = modifier
        .fillMaxWidth()
        .then(
            if (downTarget != null) {
                Modifier.focusProperties { down = downTarget }
            } else {
                Modifier
            }
        )

    Surface(
        selected = item.selectable && selected,
        onClick = onToggle,
        modifier = rowModifier,
        shape = SelectableSurfaceDefaults.shape(
            shape = RoundedCornerShape(SeerrRowCornerRadius)
        ),
        scale = SelectableSurfaceDefaults.scale(focusedScale = 1f),
        colors = SelectableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.15f),
            selectedContainerColor = Color.Transparent,
            focusedSelectedContainerColor = Color.White.copy(alpha = 0.15f)
        )
    ) {
        SeasonPickRowContent(
            seasonNumber = item.seasonNumber,
            titleAlpha = titleAlpha,
            availability = item.availability,
            mediaStatus = item.mediaStatus,
            selected = selected
        )
    }
}

@Composable
private fun SeasonPickRowContent(
    seasonNumber: Int,
    titleAlpha: Float,
    availability: SeerrSeasonAvailability,
    mediaStatus: Int?,
    selected: Boolean
) {
    Row(
        modifier = Modifier.padding(horizontal = SeerrRowInnerPadding, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Season $seasonNumber",
            color = Color.White.copy(alpha = titleAlpha),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        when (availability) {
            SeerrSeasonAvailability.Available -> {
                val label = seasonLibraryBadgeLabel(mediaStatus)
                if (label != null) {
                    val (contentColor, containerColor) = when (mediaStatus) {
                        SeerrMediaStatus.AVAILABLE ->
                            SeasonAvailableGreen to
                                SeasonAvailableGreen.copy(alpha = 0.18f)
                        else -> Color.White.copy(alpha = 0.55f) to
                            Color.White.copy(alpha = 0.10f)
                    }
                    SeasonStatusBadge(
                        text = label,
                        contentColor = contentColor,
                        containerColor = containerColor
                    )
                }
            }
            SeerrSeasonAvailability.Requested -> SeasonStatusBadge(
                text = "Requested",
                contentColor = Color.White.copy(alpha = 0.55f),
                containerColor = Color.White.copy(alpha = 0.10f)
            )
            SeerrSeasonAvailability.Selectable -> SeasonSelectionIndicator(selected = selected)
        }
    }
}

@Composable
private fun SeasonSelectionIndicator(selected: Boolean) {
    if (selected) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = Color.White.copy(alpha = 0.85f)
        )
    } else {
        Box(
            modifier = Modifier
                .size(24.dp)
                .border(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.45f),
                    shape = CircleShape
                )
        )
    }
}

@Composable
private fun SeasonStatusBadge(
    text: String,
    contentColor: Color,
    containerColor: Color
) {
    Text(
        text = text,
        color = contentColor,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
