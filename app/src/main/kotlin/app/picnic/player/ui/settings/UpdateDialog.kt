package app.picnic.player.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.BuildConfig
import app.picnic.player.ui.common.ActionButton
import app.picnic.player.ui.common.DialogCornerRadius
import app.picnic.player.ui.common.GlassRow
import app.picnic.player.ui.common.PanelWidth
import app.picnic.player.ui.common.PicnicDialog
import app.picnic.player.ui.common.panelSurface
import app.picnic.player.ui.settings.UpdateViewModel.Phase
import app.picnic.player.ui.theme.PicnicColors

private val ContentInset = 24.dp

@Composable
internal fun UpdateDialog(
    phase: Phase,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    val primaryFocus = remember { FocusRequester() }
    var showNotes by remember { mutableStateOf(false) }
    LaunchedEffect(phase::class, showNotes) {
        if (!showNotes) runCatching { primaryFocus.requestFocus() }
    }

    PicnicDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .panelSurface(width = PanelWidth.Form, corner = DialogCornerRadius)
                .padding(horizontal = ContentInset)
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

@Composable
private fun NotesPreview(
    notes: String,
    onOpen: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    GlassRow(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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
    ActionButton(
        label = label,
        onActivate = onActivate,
        focusRequester = focusRequester,
        modifier = Modifier.fillMaxWidth()
    )
}
