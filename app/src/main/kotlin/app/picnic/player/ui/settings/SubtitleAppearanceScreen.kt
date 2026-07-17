@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.ui.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.text.Cue
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.settings.SettingsStore
import app.picnic.player.data.settings.SubtitleAppearance
import app.picnic.player.data.settings.SubtitleBackgroundStyle
import app.picnic.player.data.settings.SubtitleColour
import app.picnic.player.data.settings.SubtitleSize
import app.picnic.player.playback.applyTo
import app.picnic.player.ui.theme.PicnicColors
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SubtitleAppearanceViewModel @Inject constructor(
    private val store: SettingsStore
) : ViewModel() {

    val appearance: StateFlow<SubtitleAppearance> = store.settings
        .map { it.subtitleAppearance }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SubtitleAppearance())

    fun cycleSize() = viewModelScope.launch {
        store.setSubtitleSize(appearance.value.size.next())
    }

    fun cycleColour() = viewModelScope.launch {
        store.setSubtitleColour(appearance.value.colour.next())
    }

    fun toggleBackground() = viewModelScope.launch {
        store.setSubtitleBackground(!appearance.value.background)
    }

    fun cycleBackgroundStyle() = viewModelScope.launch {
        store.setSubtitleBackgroundStyle(appearance.value.backgroundStyle.next())
    }

    private inline fun <reified T : Enum<T>> T.next(): T = enumValues<T>().let { it[(ordinal + 1) % it.size] }
}

private fun SubtitleSize.display(): String = when (this) {
    SubtitleSize.SMALLER -> "Smaller"
    SubtitleSize.STANDARD -> "Standard"
    SubtitleSize.LARGER -> "Larger"
}

private fun SubtitleColour.display(): String = when (this) {
    SubtitleColour.WHITE -> "White"
    SubtitleColour.YELLOW -> "Yellow"
    SubtitleColour.CYAN -> "Cyan"
    SubtitleColour.GREEN -> "Green"
}

private fun SubtitleBackgroundStyle.display(): String = when (this) {
    SubtitleBackgroundStyle.TRANSLUCENT -> "Translucent"
    SubtitleBackgroundStyle.SOLID -> "Solid"
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
    BackHandler(onBack = onBack)

    val firstRowFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstRowFocus.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
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

            Column(
                modifier = Modifier.width(380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppearanceRow(
                    label = "Size",
                    value = appearance.size.display(),
                    onActivate = viewModel::cycleSize,
                    modifier = Modifier
                        .focusRequester(firstRowFocus)
                        .focusProperties { up = FocusRequester.Cancel }
                )
                AppearanceRow(
                    label = "Colour",
                    value = appearance.colour.display(),
                    onActivate = viewModel::cycleColour
                )
                AppearanceRow(
                    label = "Background",
                    value = if (appearance.background) "On" else "Off",
                    onActivate = viewModel::toggleBackground
                )
                // Stays mounted while background is off (removing a focused row
                // disposes the focused node — see OnboardingTextField / #130);
                // it just mutes and ignores Select.
                AppearanceRow(
                    label = "Background style",
                    value = appearance.backgroundStyle.display(),
                    enabled = appearance.background,
                    onActivate = viewModel::cycleBackgroundStyle,
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
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (focused) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f)
            )
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.DirectionRight -> {
                        if (enabled) onActivate()
                        true
                    }
                    else -> false
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) PicnicColors.OnDark else PicnicColors.OnDarkMuted,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) PicnicColors.Cyan else PicnicColors.OnDarkMuted
        )
    }
}
