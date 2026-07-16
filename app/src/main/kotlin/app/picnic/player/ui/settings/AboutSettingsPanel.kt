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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.BuildConfig
import app.picnic.player.R
import app.picnic.player.ui.theme.PicnicColors

/**
 * Settings → About: the app identity (logo, name, version, tagline) with a
 * single action opening the open-source licenses page. Mirrors the Account
 * panel's centred-header + [ActionRow] structure. The licenses browser itself
 * is a full-screen page hosted by [SettingsScreen] (not a dialog), reached via
 * [onOpenLicenses].
 */
@Composable
internal fun AboutSettingsPanel(
    enterFr: FocusRequester,
    leftFocus: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        ActionRow(
            label = stringResource(R.string.about_licenses_title),
            leftFocus = leftFocus,
            focusRequester = enterFr,
            blockUp = true,
            blockDown = true,
            onActivate = onOpenLicenses
        )
    }
}
