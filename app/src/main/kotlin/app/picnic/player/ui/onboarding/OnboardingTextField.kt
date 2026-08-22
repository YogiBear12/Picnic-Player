@file:OptIn(ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.onboarding

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.common.rememberTvTextEntry
import app.picnic.player.ui.common.tvTextEntry
import app.picnic.player.ui.theme.PicnicColors
import kotlinx.coroutines.flow.drop

@Composable
fun OnboardingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
    onEditingChange: ((Boolean) -> Unit)? = null
) {
    val entry = rememberTvTextEntry()
    val editing = entry.editing
    val accent = MaterialTheme.colorScheme.primary

    val editingChanged by rememberUpdatedState(onEditingChange)
    LaunchedEffect(entry) {
        snapshotFlow { entry.editing }.drop(1).collect { editingChanged?.invoke(it) }
    }

    val finishEditing: () -> Unit = {
        entry.stop()
        onImeAction?.invoke()
    }

    val shape = RoundedCornerShape(6.dp)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = entry.readOnly,
        placeholder = { Text(placeholder, color = PicnicColors.OnDarkMuted) },
        singleLine = true,
        shape = shape,
        visualTransformation =
        if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = { finishEditing() },
            onDone = { finishEditing() }
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = if (editing) PicnicColors.Surface else PicnicColors.SurfaceVariant,
            unfocusedContainerColor = PicnicColors.Surface,
            focusedBorderColor = accent,
            unfocusedBorderColor = PicnicColors.OnDarkMuted,
            cursorColor = accent,
            focusedTextColor = PicnicColors.OnDark,
            unfocusedTextColor = PicnicColors.OnDark,
            focusedPlaceholderColor = PicnicColors.OnDarkMuted,
            unfocusedPlaceholderColor = PicnicColors.OnDarkMuted
        ),
        modifier = modifier
            .heightIn(min = 56.dp)
            .tvTextEntry(entry)
    )
}
