package app.picnic.player.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.BuildConfig
import app.picnic.player.ui.settings.UpdateViewModel.Phase
import app.picnic.player.ui.theme.PicnicColors

private val DialogGlassFill = Color(0xEA181E24)
private val PanelWidth = 420.dp
private val PanelCornerRadius = 20.dp
private val ContentInset = 24.dp

/**
 * Update flow dialog (#111): one glass panel whose content follows
 * [UpdateViewModel.Phase] — checking spinner, up-to-date, release notes +
 * install offer, download progress, or failure. [onDismiss] is the only exit;
 * the phase machine itself never closes the dialog so outcomes stay readable.
 */
@Composable
internal fun UpdateDialog(
    phase: Phase,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    val primaryFocus = remember { FocusRequester() }
    var showNotes by remember { mutableStateOf(false) }
    // Keyed on the phase class: refocus the primary button when the content
    // changes, but not while the notes overlay sits on top.
    LaunchedEffect(phase::class, showNotes) {
        if (!showNotes) runCatching { primaryFocus.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .width(PanelWidth)
                .shadow(8.dp, RoundedCornerShape(PanelCornerRadius))
                .clip(RoundedCornerShape(PanelCornerRadius))
                .background(DialogGlassFill)
                .padding(ContentInset)
                .focusGroup()
        ) {
            when (phase) {
                Phase.Idle, Phase.Checking -> {
                    Header("Checking for updates")
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = PicnicColors.Cyan)
                        Spacer(Modifier.width(16.dp))
                        Text("Contacting the release server…", color = PicnicColors.OnDarkMuted)
                    }
                    Spacer(Modifier.height(20.dp))
                    DialogButton("Cancel", primaryFocus, onDismiss)
                }
                Phase.UpToDate -> {
                    Header("You're up to date")
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Version ${BuildConfig.VERSION_NAME} is the latest release.",
                        color = PicnicColors.OnDarkMuted
                    )
                    Spacer(Modifier.height(20.dp))
                    DialogButton("Close", primaryFocus, onDismiss)
                }
                is Phase.Available -> {
                    Header("Update available")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${BuildConfig.VERSION_NAME} → ${phase.release.version}",
                        color = PicnicColors.Cyan,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (phase.release.notes.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        // Focusable preview: Select opens the full-screen scrollable
                        // notes overlay with markdown rendering.
                        NotesPreview(
                            notes = phase.release.notes,
                            onOpen = { showNotes = true }
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    DialogButton("Download and install", primaryFocus, onInstall)
                    Spacer(Modifier.height(8.dp))
                    DialogButton("Later", null, onDismiss)

                    if (showNotes) {
                        ReleaseNotesOverlay(
                            version = "v${phase.release.version}",
                            notes = phase.release.notes,
                            onBack = { showNotes = false }
                        )
                    }
                }
                is Phase.Downloading -> {
                    Header("Downloading update")
                    Spacer(Modifier.height(16.dp))
                    val fraction = phase.progress.fraction
                    if (fraction != null) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            color = PicnicColors.Cyan,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${(fraction * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = PicnicColors.OnDarkMuted
                        )
                    } else {
                        LinearProgressIndicator(
                            color = PicnicColors.Cyan,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is Phase.ReadyToInstall -> {
                    Header("Starting installer")
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Follow the system prompt to finish updating.",
                        color = PicnicColors.OnDarkMuted
                    )
                    Spacer(Modifier.height(20.dp))
                    DialogButton("Close", primaryFocus, onDismiss)
                }
                is Phase.Failed -> {
                    Header("Update failed")
                    Spacer(Modifier.height(12.dp))
                    Text(phase.message, color = PicnicColors.Error)
                    Spacer(Modifier.height(20.dp))
                    DialogButton("Close", primaryFocus, onDismiss)
                }
            }
        }
    }
}

/**
 * Truncated release-notes block; focusable, Select opens [ReleaseNotesOverlay].
 * Focus chrome mirrors [ActionRow] so it reads as one interactive family.
 */
@Composable
private fun NotesPreview(
    notes: String,
    onOpen: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (focused) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f)
            )
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter -> {
                        onOpen()
                        true
                    }
                    else -> false
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(12.dp)
    ) {
        Text(
            notes,
            style = MaterialTheme.typography.bodySmall,
            color = PicnicColors.OnDarkMuted,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Select to read full notes",
            style = MaterialTheme.typography.labelSmall,
            color = if (focused) PicnicColors.Cyan else PicnicColors.OnDarkMuted
        )
    }
}

@Composable
private fun Header(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = PicnicColors.OnDark
    )
}

@Composable
private fun DialogButton(
    label: String,
    focusRequester: FocusRequester?,
    onActivate: () -> Unit
) {
    ActionRow(
        label = label,
        leftFocus = null,
        focusRequester = focusRequester,
        onActivate = onActivate
    )
}
