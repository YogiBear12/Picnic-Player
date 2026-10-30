package app.picnic.player.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.picnic.player.ui.browse.BrowseHeroSummaryLineHeight
import app.picnic.player.ui.browse.HeroInfoTopGap
import app.picnic.player.ui.browse.HeroSummaryTopGap
import app.picnic.player.ui.browse.HeroTitleTopGap
import app.picnic.player.ui.browse.browseLayoutMetrics
import app.picnic.player.ui.common.FocusSeed
import app.picnic.player.ui.common.LogoOrFallback
import app.picnic.player.ui.common.focusSeed
import app.picnic.player.ui.common.rememberFocusSeed
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.theme.PicnicColors
import kotlinx.coroutines.delay

private val NextUpActionGap = 12.dp
private val NextUpColumnGap = 32.dp
private val NextUpButtonFill = Color(0xC0181E24)
private const val NextUpDetailsExtraWidth = 88f

@Composable
fun NextUpOverlay(
    item: NextUpItem,
    countdownSeconds: Int,
    onAutoplay: () -> Unit,
    onPlayNext: () -> Unit,
    inOutro: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true
) {
    var secondsLeft by remember(item) { mutableIntStateOf(countdownSeconds) }
    val nextFocus = rememberFocusSeed(key = item, enabled = active)

    LaunchedEffect(item, active) {
        if (!active) return@LaunchedEffect
        for (remaining in countdownSeconds downTo 1) {
            secondsLeft = remaining
            delay(1_000)
        }
        secondsLeft = 0
        onAutoplay()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    onBack()
                    true
                } else {
                    false
                }
            }
    ) {
        val metrics = browseLayoutMetrics(maxWidth, maxHeight)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(start = NextUpPlayerInset, end = NextUpPlayerInset, bottom = NextUpPlayerInset),
            horizontalArrangement = Arrangement.spacedBy(NextUpColumnGap),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(Modifier.weight(1f)) {
                NextUpDetails(
                    item,
                    metrics.logoHeight,
                    Modifier.width(metrics.heroContentWidth + metrics.sx(NextUpDetailsExtraWidth))
                )
            }
            NextUpActions(
                secondsLeft = secondsLeft,
                nextFocus = nextFocus,
                active = active,
                inOutro = inOutro,
                onPlayNext = onPlayNext,
                onBack = onBack
            )
        }
    }
}

@Composable
private fun NextUpDetails(item: NextUpItem, logoHeight: Dp, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        LogoOrFallback(
            url = item.logoUrl,
            contentDescription = item.logoName,
            alignment = Alignment.CenterStart,
            label = "next up logo item='${item.logoName}'",
            modifier = Modifier.height(logoHeight).fillMaxWidth()
        ) {
            Box(Modifier.height(logoHeight).fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
                Text(
                    text = item.logoName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (item.meta.isNotEmpty()) {
            Text(
                text = item.meta,
                color = PicnicColors.TextTertiary,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = HeroInfoTopGap)
            )
        }
        item.episodeLine?.let { episodeLine ->
            Text(
                text = episodeLine,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = HeroTitleTopGap)
            )
        }
        if (item.overview.isNotEmpty()) {
            Text(
                text = item.overview,
                color = PicnicColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = BrowseHeroSummaryLineHeight,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = HeroSummaryTopGap)
            )
        }
    }
}

@Composable
private fun NextUpActions(
    secondsLeft: Int,
    nextFocus: FocusSeed,
    active: Boolean,
    inOutro: Boolean,
    onPlayNext: () -> Unit,
    onBack: () -> Unit
) {
    val secondaryFocus = remember { FocusRequester() }
    LaunchedEffect(inOutro, active) {
        if (!inOutro && active) nextFocus.requester.requestFocusWhenAttached()
    }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = if (secondsLeft > 0) "Playing in $secondsLeft second${if (secondsLeft == 1) "" else "s"}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f)
        )
        Row(Modifier.width(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(NextUpActionGap)) {
            NextUpButton(
                label = if (inOutro) "Watch Credits" else "Back",
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(secondaryFocus)
                    .focusProperties {
                        canFocus = active
                        up = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                        left = FocusRequester.Cancel
                    }
            )
            NextUpButton(
                label = "Play Next",
                onClick = onPlayNext,
                modifier = Modifier
                    .weight(1f)
                    .focusSeed(nextFocus)
                    .focusProperties {
                        up = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                        left = secondaryFocus
                    }
            )
        }
    }
}

@Composable
private fun NextUpButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = NextUpButtonFill,
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
            pressedContainerColor = Color.White,
            pressedContentColor = Color.Black
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
    }
}
