package app.picnic.player.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.BuildConfig
import app.picnic.player.R
import app.picnic.player.ui.theme.PicnicColors

/**
 * Settings → About: the app identity (logo, name, version, tagline), the in-app
 * update row (#111) and the open-source licenses page. Mirrors the Account
 * panel's centred-header + [ActionRow] structure. The licenses browser itself
 * is a full-screen page hosted by [SettingsScreen] (not a dialog), reached via
 * [onOpenLicenses].
 *
 * The update row is stateful: "Check for updates" normally, "Install update"
 * once a newer release is known (startup check or manual). It is absent
 * entirely when no release host is configured (blank UPDATE_REPO).
 */
@Composable
internal fun AboutSettingsPanel(
    enterFr: FocusRequester,
    leftFocus: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
    updateViewModel: UpdateViewModel = hiltViewModel()
) {
    val phase by updateViewModel.phase.collectAsStateWithLifecycle()
    val updateAvailable by updateViewModel.updateAvailable.collectAsStateWithLifecycle()
    var showUpdateDialog by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.onFocusChanged { onFocusChanged(it.hasFocus) },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(bottom = 24.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier.size(140.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = PicnicColors.OnDark
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = PicnicColors.OnDarkMuted
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Built ${BuildConfig.BUILD_DATE}",
                style = MaterialTheme.typography.bodySmall,
                color = PicnicColors.OnDarkMuted
            )
        }
        if (updateViewModel.enabled) {
            ActionRow(
                label = if (updateAvailable) "Install update" else "Check for updates",
                value = (phase as? UpdateViewModel.Phase.Available)
                    ?.release?.version?.let { "v$it" } ?: "",
                leftFocus = leftFocus,
                focusRequester = enterFr,
                blockUp = true,
                onActivate = {
                    if (!updateAvailable) updateViewModel.check()
                    showUpdateDialog = true
                }
            )
        }
        ActionRow(
            label = stringResource(R.string.about_licenses_title),
            leftFocus = leftFocus,
            focusRequester = if (updateViewModel.enabled) null else enterFr,
            blockUp = !updateViewModel.enabled,
            blockDown = true,
            onActivate = onOpenLicenses
        )
    }

    if (showUpdateDialog) {
        UpdateDialog(
            phase = phase,
            onInstall = {
                (phase as? UpdateViewModel.Phase.Available)
                    ?.let { updateViewModel.downloadAndInstall(it.release) }
            },
            onDismiss = {
                updateViewModel.dismiss()
                showUpdateDialog = false
            }
        )
    }
}
