package app.picnic.player.ui.watchtogether

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.ambient.BackdropSpec
import app.picnic.player.ui.ambient.rememberBackdrop
import app.picnic.player.ui.browse.BrowseHero
import app.picnic.player.ui.browse.browseLayoutMetrics
import app.picnic.player.ui.common.ActionButtonRow
import app.picnic.player.ui.common.LocalImageUrls
import app.picnic.player.ui.common.PageAction
import app.picnic.player.ui.common.PlayFromStartLabel
import app.picnic.player.ui.common.ServerBrandingImage
import app.picnic.player.ui.common.UserAvatar
import app.picnic.player.ui.common.rememberRowFocusState
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.common.resumeLabel
import app.picnic.player.ui.detail.DetailBackdrop
import app.picnic.player.ui.detail.RESTING_ACTIONS_BOTTOM
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
    DisposableEffect(viewModel) { onDispose(viewModel::onScreenGone) }
    val leave = {
        viewModel.leave()
        onBack()
    }
    BackHandler(onBack = leave)

    val people by viewModel.people.collectAsStateWithLifecycle()
    val queue = group?.queue
    val images = LocalImageUrls.current
    val nav = item?.let { images.navImages(it) }
    val frame = rememberBackdrop(BackdropSpec(backdropUrl = nav?.bgUrl, ambientUrl = nav?.ambUrl))
    val buttons = rememberRowFocusState()
    LaunchedEffect(Unit) { buttons.requesterAt(0).requestFocusWhenAttached(maxFrames = 20) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val metrics = browseLayoutMetrics(maxWidth, maxHeight)
        // With no movie queued there is no art, and DetailBackdrop would paint the app's ocean wash over.
        if (queue != null) {
            DetailBackdrop(frame, Modifier.fillMaxSize())
        } else {
            viewModel.brandingUrl?.let { ServerBrandingImage(it) }
            Box(Modifier.fillMaxSize().background(BrandingScrim))
        }
        group?.let {
            RoomHeader(watchPartyName(it.name), Modifier.padding(start = metrics.hInset, top = metrics.sy(HEADER_TOP)))
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = metrics.hInset, bottom = metrics.sy(RESTING_ACTIONS_BOTTOM))
        ) {
            if (queue != null) {
                BrowseHero(
                    item = item,
                    logoHeight = metrics.logoHeight,
                    continueWatching = false,
                    seasonCount = null,
                    modifier = Modifier.width(metrics.heroContentWidth)
                )
            } else {
                Text(NoMovieNotice, style = MaterialTheme.typography.titleMedium, color = PicnicColors.OnDark)
            }
            Spacer(Modifier.height(metrics.heroGap))
            val resumeTicks = queue?.startTicks?.takeIf { it > 0 }
            ActionButtonRow(
                actions = buildList {
                    when {
                        queue == null -> Unit
                        resumeTicks != null -> {
                            add(PageAction(resumeLabel(resumeTicks), Icons.Filled.PlayArrow, viewModel::play))
                            add(PageAction(PlayFromStartLabel, Icons.Filled.Replay, viewModel::playFromStart))
                        }
                        else -> add(PageAction("Play", Icons.Filled.PlayArrow, viewModel::play))
                    }
                    add(PageAction("Leave", Icons.AutoMirrored.Filled.ExitToApp, leave))
                },
                focus = buttons,
                onFocused = {}
            )
        }
        people?.let {
            PeopleLine(
                people = it,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .height(metrics.sy(RESTING_ACTIONS_BOTTOM))
                    .padding(start = metrics.hInset)
            )
        }
    }
}

private const val HEADER_TOP = 27f
private const val SHOWN_AVATARS = 3
private const val NoMovieNotice = "Waiting for someone to pick a movie"
private val AvatarSize = 36.dp
private val BrandingScrim = Brush.horizontalGradient(
    0f to PicnicColors.Background.copy(alpha = 0.9f),
    0.45f to PicnicColors.Background.copy(alpha = 0.6f),
    1f to PicnicColors.Background.copy(alpha = 0.35f)
)
private val AvatarOverlap = 10.dp
private val AvatarRing = 2.dp

@Composable
private fun RoomHeader(name: String, modifier: Modifier = Modifier) {
    Text(
        name,
        style = MaterialTheme.typography.headlineMedium,
        color = PicnicColors.OnDark,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
private fun PeopleLine(people: LobbyPeople, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(-AvatarOverlap)) {
            people.members.take(SHOWN_AVATARS).forEach {
                UserAvatar(it.name, it.pictureUrl, AvatarSize, modifier = Modifier.border(AvatarRing, PicnicColors.Background, CircleShape))
            }
            val hidden = people.members.size - SHOWN_AVATARS
            if (hidden > 0) {
                Box(
                    Modifier
                        .size(AvatarSize)
                        .border(AvatarRing, PicnicColors.Background, CircleShape)
                        .clip(CircleShape)
                        .background(PicnicColors.SurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+$hidden", color = PicnicColors.OnDark, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(people.line, style = MaterialTheme.typography.bodyLarge, color = PicnicColors.OnDark, maxLines = 1)
    }
}
