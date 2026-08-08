@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.text.Cue
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.jellyfin.JellyfinImages
import app.picnic.player.data.settings.SettingsStore
import app.picnic.player.data.settings.SubtitleAppearance
import app.picnic.player.data.settings.SubtitleAppearanceEditor
import app.picnic.player.data.settings.SubtitleArea
import app.picnic.player.data.settings.SubtitleBackground
import app.picnic.player.playback.BlackBars
import app.picnic.player.playback.applyTo
import app.picnic.player.playback.subtitleBottomPaddingFraction
import app.picnic.player.ui.grid.OceanAmbientBackground
import app.picnic.player.ui.theme.PicnicColors
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SubtitleAppearanceViewModel @Inject constructor(
    store: SettingsStore,
    private val editor: SubtitleAppearanceEditor,
    authRepository: AuthRepository
) : ViewModel() {

    val appearance: StateFlow<SubtitleAppearance> = store.settings
        .map { it.subtitleAppearance }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SubtitleAppearance())

    /** Server splashscreen URL for the screen background; null with no active session.
     *  The URL may 404 (no splashscreen configured) — the screen falls back to ocean. */
    val splashUrl: StateFlow<String?> = authRepository.activeSessionFlow
        .map { session -> session?.let { JellyfinImages.splashscreen(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun cycleSize(forward: Boolean) = viewModelScope.launch { editor.cycleSize(forward) }

    fun cycleColour(forward: Boolean) = viewModelScope.launch { editor.cycleColour(forward) }

    fun cycleBackground(forward: Boolean) = viewModelScope.launch { editor.cycleBackground(forward) }

    fun cycleBackgroundFill(forward: Boolean) = viewModelScope.launch { editor.cycleBackgroundFill(forward) }

    fun cycleArea(forward: Boolean) = viewModelScope.launch { editor.cycleArea(forward) }

    fun stepInset(forward: Boolean) = viewModelScope.launch { editor.stepInset(forward) }
}

private val PanelGlassFill = Color(0xF2181E24)
private val PanelCornerRadius = 20.dp
private val PanelWidth = 380.dp
private val PanelInsetHorizontal = 12.dp
private val PanelInsetVertical = 16.dp
private val RowInnerPadding = 12.dp
private val RowSpacing = 1.dp
private val RowCornerRadius = 10.dp
private val PageInset = 40.dp
private val MinHeaderHeight = 40.dp
private const val ScopeAspect = 2.39f

/**
 * Screen background: server splashscreen art layered over the ocean wash. The splash
 * URL 404s when no splashscreen is configured on the server — [AsyncImage] then draws
 * nothing and the ocean shows through. A dark scrim keeps the option card (left) and
 * preview cue (bottom) legible over bright artwork.
 */
@Composable
private fun SubtitleBackground(splashUrl: String?, modifier: Modifier = Modifier) {
    Box(modifier) {
        OceanAmbientBackground(Modifier.fillMaxSize())

        if (splashUrl != null) {
            var loaded by remember(splashUrl) { mutableStateOf(false) }
            val imageAlpha by animateFloatAsState(
                targetValue = if (loaded) 1f else 0f,
                animationSpec = tween(600),
                label = "splashFade"
            )
            val context = LocalContext.current
            // The Branding endpoint takes no image tag (supplying one returns an empty 304), so a
            // cached copy would outlive every splashscreen the admin swaps in. This screen is
            // rare enough that refetching per visit beats showing artwork the server replaced.
            val request = remember(splashUrl) {
                ImageRequest.Builder(context)
                    .data(splashUrl)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // Only Success flips the fade in; Error leaves alpha at 0 so the ocean
                // base stays the background.
                onState = { state -> if (state is AsyncImagePainter.State.Success) loaded = true },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = imageAlpha }
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 0.55f),
                            0.45f to Color.Black.copy(alpha = 0.15f),
                            0.7f to Color.Transparent
                        )
                    )
                    drawRect(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.6f)
                        )
                    )
                }
        )
    }
}

/**
 * Full-screen subtitle appearance page (#54): option rows on the left, and a
 * live preview cue drawn over the whole screen exactly where playback puts
 * subtitles — same [SubtitleView], same [applyTo], same bottom padding — so
 * position and true size are never misrepresented by a scaled-down preview
 * window. Selecting a row cycles to its next value.
 */
@Composable
internal fun SubtitleAppearanceScreen(
    onBack: () -> Unit,
    viewModel: SubtitleAppearanceViewModel = hiltViewModel()
) {
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val splashUrl by viewModel.splashUrl.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    val firstRowFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstRowFocus.requestFocus() } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Screen and Image place a cue identically with no video behind them, so the preview
        // borrows a scope frame to sit against.
        val barFraction = scopeBarFraction(maxWidth / maxHeight)
        val barHeight = maxHeight * barFraction

        // Server splashscreen art, ocean wash showing through on 404/failure.
        SubtitleBackground(splashUrl, Modifier.fillMaxSize())
        ScopeBars(barHeight)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = PageInset, vertical = 24.dp)
        ) {
            // Held to the bar's height so the title sits inside the letterbox rather than
            // straddling its edge; the floor covers displays wide enough to leave no bars.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(maxOf(barHeight - 24.dp, MinHeaderHeight)),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "Subtitle appearance",
                    style = MaterialTheme.typography.headlineMedium,
                    color = PicnicColors.OnDark
                )
            }
            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .width(PanelWidth)
                    .clip(RoundedCornerShape(PanelCornerRadius))
                    .background(PanelGlassFill)
                    .padding(horizontal = PanelInsetHorizontal, vertical = PanelInsetVertical),
                verticalArrangement = Arrangement.spacedBy(RowSpacing)
            ) {
                AppearanceRow(
                    label = "Size",
                    value = appearance.size.display(),
                    onStep = viewModel::cycleSize,
                    modifier = Modifier
                        .focusRequester(firstRowFocus)
                        .focusProperties { up = FocusRequester.Cancel }
                )
                AppearanceRow(
                    label = "Color",
                    value = appearance.colour.display(),
                    onStep = viewModel::cycleColour
                )
                AppearanceRow(
                    label = "Background",
                    value = appearance.background.display(),
                    onStep = viewModel::cycleBackground
                )
                // The fill row stays mounted while the background is off (removing a focused row
                // disposes the focused node — see OnboardingTextField / #130); it only mutes.
                AppearanceRow(
                    label = "Background fill",
                    value = appearance.backgroundFill.display(),
                    enabled = appearance.background != SubtitleBackground.OFF,
                    onStep = viewModel::cycleBackgroundFill
                )
                AppearanceRow(
                    label = "Subtitle area",
                    value = appearance.area.display(),
                    onStep = viewModel::cycleArea
                )
                AppearanceRow(
                    label = "Subtitle offset",
                    value = appearance.insetDisplay(),
                    onStep = viewModel::stepInset,
                    modifier = Modifier.focusProperties { down = FocusRequester.Cancel }
                )
            }
        }

        // Preview cue over the full screen — playback-true position and size.
        AndroidView(
            factory = { context -> SubtitleView(context) },
            update = { view ->
                appearance.applyTo(view, previewBottomPaddingFraction(appearance, barFraction))
                view.setCues(
                    listOf(
                        Cue.Builder()
                            .setText("This is what subtitles will look like\nwhile you are watching")
                            .build()
                    )
                )
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun AppearanceRow(
    label: String,
    value: String,
    onStep: (forward: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var focused by remember { mutableStateOf(false) }
    val labelColor = when {
        !enabled && focused -> Color.Black.copy(alpha = 0.4f)
        !enabled -> PicnicColors.OnDarkMuted
        focused -> Color.Black
        else -> Color.White.copy(alpha = 0.92f)
    }
    val valueColor = when {
        !enabled && focused -> Color.Black.copy(alpha = 0.4f)
        !enabled -> PicnicColors.OnDarkMuted
        focused -> Color.Black.copy(alpha = 0.72f)
        else -> PicnicColors.Cyan
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RowCornerRadius))
            .background(if (focused) Color.White else Color.Transparent)
            // Left/Right step the value in place — never move focus out of the row.
            .focusProperties {
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            }
            // Left = previous, Right/Select = next (a stepper, values wrap).
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.DirectionRight -> {
                        if (enabled) onStep(true)
                        true
                    }
                    Key.DirectionLeft -> {
                        if (enabled) onStep(false)
                        true
                    }
                    else -> false
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = RowInnerPadding, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = labelColor,
            modifier = Modifier.weight(1f)
        )
        // Stepper: ‹ value › — always rendered (muted when disabled) so the value never
        // shifts as the row enables/disables.
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.ChevronLeft,
                contentDescription = null,
                tint = valueColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor
            )
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = valueColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun BoxScope.ScopeBars(height: Dp) {
    if (height <= 0.dp) return
    Box(Modifier.fillMaxWidth().height(height).align(Alignment.TopCenter).background(Color.Black))
    Box(Modifier.fillMaxWidth().height(height).align(Alignment.BottomCenter).background(Color.Black))
}

/** Bars a 2.39:1 picture leaves in a container, as a fraction of its height. */
private fun scopeBarFraction(containerAspect: Float): Float = ((1f - containerAspect / ScopeAspect) / 2f).coerceIn(0f, 0.4f)

/**
 * Image reads the frame as a letterboxed video rect, Automatic as a full rect with the bars baked
 * in. Both land the cue in the same place, which is the point being previewed.
 */
private fun previewBottomPaddingFraction(appearance: SubtitleAppearance, barFraction: Float): Float = subtitleBottomPaddingFraction(
    appearance.area,
    appearance.insetPercent,
    if (appearance.area == SubtitleArea.IMAGE) 1f - 2f * barFraction else 1f,
    if (appearance.area == SubtitleArea.AUTOMATIC) BlackBars(barFraction, barFraction) else BlackBars.None
)
