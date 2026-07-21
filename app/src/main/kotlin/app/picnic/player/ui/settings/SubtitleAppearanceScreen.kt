@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import app.picnic.player.data.settings.SubtitleBackgroundFill
import app.picnic.player.data.settings.SubtitleBackgroundStyle
import app.picnic.player.data.settings.SubtitleColour
import app.picnic.player.data.settings.SubtitleSize
import app.picnic.player.playback.applyTo
import app.picnic.player.ui.grid.OceanAmbientBackground
import app.picnic.player.ui.theme.PicnicColors
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SubtitleAppearanceViewModel @Inject constructor(
    private val store: SettingsStore,
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

    fun cycleSize(forward: Boolean) = viewModelScope.launch {
        store.setSubtitleSize(appearance.value.size.step(forward))
    }

    fun cycleColour(forward: Boolean) = viewModelScope.launch {
        store.setSubtitleColour(appearance.value.colour.step(forward))
    }

    // Two-state toggle — direction is irrelevant, both chevrons flip it.
    fun toggleBackground(forward: Boolean) = viewModelScope.launch {
        store.setSubtitleBackground(!appearance.value.background)
    }

    fun cycleBackgroundFill(forward: Boolean) = viewModelScope.launch {
        store.setSubtitleBackgroundFill(appearance.value.backgroundFill.step(forward))
    }

    fun cycleBackgroundStyle(forward: Boolean) = viewModelScope.launch {
        store.setSubtitleBackgroundStyle(appearance.value.backgroundStyle.step(forward))
    }

    private inline fun <reified T : Enum<T>> T.step(forward: Boolean): T = enumValues<T>().let { it[(ordinal + (if (forward) 1 else -1) + it.size) % it.size] }
}

private fun SubtitleSize.display(): String = when (this) {
    SubtitleSize.SMALLER -> "Smaller"
    SubtitleSize.SMALL -> "Small"
    SubtitleSize.STANDARD -> "Standard"
    SubtitleSize.LARGE -> "Large"
    SubtitleSize.LARGER -> "Larger"
}

private fun SubtitleColour.display(): String = when (this) {
    SubtitleColour.WHITE -> "White"
    SubtitleColour.YELLOW -> "Yellow"
    SubtitleColour.CYAN -> "Cyan"
    SubtitleColour.GREEN -> "Green"
}

private fun SubtitleBackgroundFill.display(): String = when (this) {
    SubtitleBackgroundFill.TRANSLUCENT -> "Translucent"
    SubtitleBackgroundFill.SOLID -> "Solid"
}

private fun SubtitleBackgroundStyle.display(): String = when (this) {
    SubtitleBackgroundStyle.BOXED -> "Boxed"
    SubtitleBackgroundStyle.WRAPPED -> "Wrapped"
}

// Shared floating-panel language (matches GridFilterPanel / PlayerSettingsPanel).
private val PanelGlassFill = Color(0xF2181E24)
private val PanelCornerRadius = 20.dp
private val ContentInset = 12.dp
private val RowInnerPadding = 12.dp
private val RowCornerRadius = 8.dp

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
            AsyncImage(
                model = splashUrl,
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

    Box(Modifier.fillMaxSize()) {
        // Server splashscreen art, ocean wash showing through on 404/failure.
        SubtitleBackground(splashUrl, Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 64.dp, vertical = 48.dp)
        ) {
            Text(
                "Subtitle appearance",
                style = MaterialTheme.typography.headlineMedium,
                color = PicnicColors.OnDark
            )
            Spacer(Modifier.height(32.dp))

            // Options float in a glass card so they stay legible over the artwork.
            Column(
                modifier = Modifier
                    .width(380.dp)
                    .clip(RoundedCornerShape(PanelCornerRadius))
                    .background(PanelGlassFill)
                    .padding(horizontal = ContentInset, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
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
                    value = if (appearance.background) "On" else "Off",
                    onStep = viewModel::toggleBackground
                )
                // Fill and style rows stay mounted while background is off (removing a
                // focused row disposes the focused node — see OnboardingTextField / #130);
                // they just mute and ignore Left/Right/Select.
                AppearanceRow(
                    label = "Background style",
                    value = appearance.backgroundStyle.display(),
                    enabled = appearance.background,
                    onStep = viewModel::cycleBackgroundStyle
                )
                AppearanceRow(
                    label = "Background fill",
                    value = appearance.backgroundFill.display(),
                    enabled = appearance.background,
                    onStep = viewModel::cycleBackgroundFill,
                    modifier = Modifier.focusProperties { down = FocusRequester.Cancel }
                )
            }
        }

        // Preview cue over the full screen — playback-true position and size.
        AndroidView(
            factory = { context -> SubtitleView(context) },
            update = { view ->
                appearance.applyTo(view)
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
            .padding(horizontal = RowInnerPadding, vertical = 9.dp),
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
