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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import app.picnic.player.ui.browse.browseLayoutMetrics
import app.picnic.player.ui.common.ActionButtonRow
import app.picnic.player.ui.common.LocalImageUrls
import app.picnic.player.ui.common.PageAction
import app.picnic.player.ui.common.PlayFromStartLabel
import app.picnic.player.ui.common.rememberIdentityBrush
import app.picnic.player.ui.common.rememberRowFocusState
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.common.resumeLabel
import app.picnic.player.ui.detail.DetailBackdrop
import app.picnic.player.ui.detail.RESTING_ACTIONS_BOTTOM
import app.picnic.player.ui.theme.PicnicColors
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
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
        if (queue != null) DetailBackdrop(frame, Modifier.fillMaxSize())
        group?.let {
            RoomHeader(it.name, Modifier.padding(start = metrics.hInset, top = metrics.sy(HEADER_TOP)))
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
                state = group?.state?.serialName.orEmpty(),
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
private val AvatarOverlap = 10.dp

@Composable
private fun RoomHeader(name: String, modifier: Modifier = Modifier) {
    Text(
        "Watch Together · $name",
        style = MaterialTheme.typography.labelLarge,
        color = PicnicColors.OnDarkMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
private fun PeopleLine(people: LobbyPeople, state: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(-AvatarOverlap)) {
            people.members.take(SHOWN_AVATARS).forEach { MemberAvatar(it) }
            val hidden = people.members.size - SHOWN_AVATARS
            if (hidden > 0) {
                AvatarCircle(Modifier.background(PicnicColors.SurfaceVariant)) {
                    Text("+$hidden", color = PicnicColors.OnDark, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(people.line, style = MaterialTheme.typography.bodyLarge, color = PicnicColors.OnDark, maxLines = 1)
        Text(" · $state", style = MaterialTheme.typography.bodyLarge, color = PicnicColors.OnDarkMuted, maxLines = 1)
    }
}

@Composable
private fun MemberAvatar(member: LobbyMember) {
    var pictureFailed by remember(member.pictureUrl) { mutableStateOf(false) }
    AvatarCircle(Modifier.background(rememberIdentityBrush(member.name.ifBlank { "?" }))) {
        if (member.pictureUrl == null || pictureFailed) {
            Text(
                member.name.firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium
            )
        }
        if (member.pictureUrl != null) {
            AsyncImage(
                model = member.pictureUrl,
                contentDescription = member.name,
                contentScale = ContentScale.Crop,
                onState = { pictureFailed = it is AsyncImagePainter.State.Error },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun AvatarCircle(fill: Modifier, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(AvatarSize)
            .border(2.dp, PicnicColors.Background, CircleShape)
            .clip(CircleShape)
            .then(fill),
        contentAlignment = Alignment.Center
    ) { content() }
}
