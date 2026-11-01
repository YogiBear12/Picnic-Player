package app.picnic.player.ui.watchtogether

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.ambient.BackdropSpec
import app.picnic.player.ui.ambient.rememberBackdrop
import app.picnic.player.ui.browse.BrowseHero
import app.picnic.player.ui.browse.DetailContentStartInset
import app.picnic.player.ui.browse.browseLayoutMetrics
import app.picnic.player.ui.common.ActionButtonRow
import app.picnic.player.ui.common.LocalImageUrls
import app.picnic.player.ui.common.PageAction
import app.picnic.player.ui.common.PlayFromStartLabel
import app.picnic.player.ui.common.panelGlass
import app.picnic.player.ui.common.rememberIdentityBrush
import app.picnic.player.ui.common.rememberRowFocusState
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.common.resumeLabel
import app.picnic.player.ui.detail.DetailBackdrop
import app.picnic.player.ui.theme.PicnicColors
import java.util.UUID

@Composable
fun LobbyScreen(
    groupId: String,
    onStart: (itemId: UUID, positionTicks: Long) -> Unit,
    onBack: () -> Unit,
    viewModel: LobbyViewModel = hiltViewModel<LobbyViewModel, LobbyViewModel.Factory>(
        creationCallback = { factory -> factory.create(groupId) }
    )
) {
    val group by viewModel.group.collectAsStateWithLifecycle()
    val item by viewModel.item.collectAsStateWithLifecycle()
    val start by rememberUpdatedState(onStart)
    val back by rememberUpdatedState(onBack)
    LaunchedEffect(viewModel) { viewModel.starts.collect { start(it.itemId, it.positionTicks) } }
    LaunchedEffect(viewModel) { viewModel.closed.collect { back() } }
    val leave = {
        viewModel.leave()
        onBack()
    }
    BackHandler(onBack = leave)

    val images = LocalImageUrls.current
    val nav = item?.let { images.navImages(it) }
    val frame = rememberBackdrop(BackdropSpec(backdropUrl = nav?.bgUrl, ambientUrl = nav?.ambUrl))
    val buttons = rememberRowFocusState()
    LaunchedEffect(Unit) { buttons.requesterAt(0).requestFocusWhenAttached(maxFrames = 20) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val metrics = browseLayoutMetrics(maxWidth, maxHeight)
        DetailBackdrop(frame, Modifier.fillMaxSize())
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = DetailContentStartInset, end = metrics.hInset, bottom = metrics.bottomInset),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(metrics.sx(32f))
        ) {
            Column(Modifier.weight(3f)) {
                BrowseHero(item = item, logoHeight = metrics.logoHeight, continueWatching = false, seasonCount = null)
                Spacer(Modifier.height(metrics.heroGap))
                val resumeTicks = group?.queue?.startTicks?.takeIf { it > 0 }
                ActionButtonRow(
                    actions = buildList {
                        if (resumeTicks != null) {
                            add(PageAction(resumeLabel(resumeTicks), Icons.Filled.PlayArrow, viewModel::play))
                            add(PageAction(PlayFromStartLabel, Icons.Filled.Replay, viewModel::playFromStart))
                        } else {
                            add(PageAction("Play", Icons.Filled.PlayArrow, viewModel::play))
                        }
                        add(PageAction("Leave", Icons.AutoMirrored.Filled.ExitToApp, leave))
                    },
                    focus = buttons,
                    onFocused = {}
                )
            }
            group?.let { RoomPanel(name = it.name, state = it.state.serialName, members = it.participants, modifier = Modifier.weight(2f)) }
        }
    }
}

@Composable
private fun RoomPanel(name: String, state: String, members: List<String>, modifier: Modifier = Modifier) {
    Column(modifier.panelGlass().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(name, style = MaterialTheme.typography.titleLarge, color = PicnicColors.OnDark, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(state, style = MaterialTheme.typography.bodyMedium, color = PicnicColors.OnDarkMuted)
        members.forEach { MemberRow(it) }
    }
}

@Composable
private fun MemberRow(name: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(rememberIdentityBrush(name.ifBlank { "?" })),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name.firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge, color = PicnicColors.OnDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
