package app.picnic.player.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.theme.PicnicColors

@Composable
fun DialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {}
) {
    val entry = rememberTvTextEntry()
    val focusManager = LocalFocusManager.current

    val finish: () -> Unit = {
        entry.stop()
        if (imeAction == ImeAction.Next) focusManager.moveFocus(FocusDirection.Next)
        onImeAction()
    }

    Column(modifier = Modifier.fillMaxWidth().then(modifier)) {
        label?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = PicnicColors.OnDarkMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassRow(entry.focused)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = PicnicColors.OnDarkMuted
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                readOnly = entry.readOnly,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                cursorBrush = SolidColor(Color.White),
                visualTransformation =
                if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (password) KeyboardType.Password else keyboardType,
                    imeAction = imeAction
                ),
                keyboardActions = KeyboardActions(
                    onDone = { finish() },
                    onGo = { finish() },
                    onNext = { finish() },
                    onSearch = { finish() },
                    onSend = { finish() }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .tvTextEntry(entry)
            )
        }
    }
}
