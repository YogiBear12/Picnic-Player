@file:OptIn(ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.theme.PicnicColors

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
    var editing by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val accent = MaterialTheme.colorScheme.primary

    fun setEditing(next: Boolean) {
        if (editing == next) return
        editing = next
        onEditingChange?.invoke(next)
    }

    LaunchedEffect(editing) { if (editing) keyboard?.show() else keyboard?.hide() }
    BackHandler(enabled = editing) { setEditing(false) }

    val finishEditing: () -> Unit = {
        setEditing(false)
        onImeAction?.invoke()
    }

    val shape = RoundedCornerShape(6.dp)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = !editing,
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
            .onFocusChanged { if (!it.isFocused) setEditing(false) }
            .onPreviewKeyEvent { event ->
                if (editing) return@onPreviewKeyEvent false
                val direction = when (event.key) {
                    Key.DirectionLeft -> FocusDirection.Left
                    Key.DirectionRight -> FocusDirection.Right
                    Key.DirectionUp -> FocusDirection.Up
                    Key.DirectionDown -> FocusDirection.Down
                    else -> null
                }
                val select = event.key == Key.DirectionCenter || event.key == Key.Enter
                when {
                    direction != null -> {
                        if (event.type == KeyEventType.KeyDown) focusManager.moveFocus(direction)
                        true
                    }
                    select -> {
                        if (event.type == KeyEventType.KeyUp) setEditing(true)
                        true
                    }
                    else -> false
                }
            }
    )
}
