package app.picnic.player.ui.seerr

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import app.picnic.player.ui.common.DialogTextField
import app.picnic.player.ui.common.panelSurface
import app.picnic.player.ui.common.requestFocusWhenAttached

@Composable
internal fun SeerrTextEntryPanel(
    title: String,
    placeholder: String,
    submitLabel: String,
    text: String,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    optional: Boolean = false
) {
    val fieldFocus = remember { FocusRequester() }
    val submitFocus = remember { FocusRequester() }
    val submit = { if (optional || text.isNotBlank()) onSubmit() }

    Column(
        modifier = Modifier
            .panelSurface(SeerrPanelWidth, SeerrPanelCornerRadius)
            .focusGroup()
    ) {
        SeerrPanelHeader(title = title)
        Column(modifier = Modifier.padding(horizontal = SeerrContentInset)) {
            DialogTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = placeholder,
                modifier = Modifier.focusRequester(fieldFocus),
                onImeAction = submit
            )
            Spacer(Modifier.height(12.dp))
            SeerrActionButton(
                title = submitLabel,
                onClick = submit,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(submitFocus)
            )
        }
    }

    LaunchedEffect(Unit) {
        if (optional) {
            submitFocus.requestFocusWhenAttached(maxFrames = 20)
        } else {
            fieldFocus.requestFocusWhenAttached(maxFrames = 20)
        }
    }
}
