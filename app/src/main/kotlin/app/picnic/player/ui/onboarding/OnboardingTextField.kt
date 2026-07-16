@file:OptIn(ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.picnic.player.ui.theme.PicnicColors

/**
 * TV text field: two-layer control. Idle is a focusable TV
 * [Surface] shell — **not** a TextField — so D-pad traversal never pops the IME.
 * Select opens the editable field; Done/Next or focus loss returns to the shell.
 *
 * Callers that swap sibling chrome on focus (e.g. login method rail) should honor
 * [onEditingChange] and ignore transient focus escapes while [editing] is true —
 * disposing the shell briefly drops focus before the TextField attaches.
 */
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

    fun setEditing(value: Boolean) {
        if (editing == value) return
        editing = value
        onEditingChange?.invoke(value)
    }

    if (editing) {
        val focus = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        var armed by remember { mutableStateOf(false) }
        val accent = MaterialTheme.colorScheme.primary
        LaunchedEffect(Unit) {
            runCatching { focus.requestFocus() }
            keyboard?.show()
            armed = true
        }
        val finishEditing: () -> Unit = {
            setEditing(false)
            keyboard?.hide()
            onImeAction?.invoke()
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = PicnicColors.OnDarkMuted) },
            singleLine = true,
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
                focusedBorderColor = accent,
                unfocusedBorderColor = PicnicColors.OnDarkMuted,
                cursorColor = accent,
                focusedTextColor = PicnicColors.OnDark,
                unfocusedTextColor = PicnicColors.OnDark,
                focusedPlaceholderColor = PicnicColors.OnDarkMuted,
                unfocusedPlaceholderColor = PicnicColors.OnDarkMuted
            ),
            modifier = modifier
                .focusRequester(focus)
                .onFocusChanged { f ->
                    // Leaving the field (e.g. Back closed the IME, or D-pad away) returns to idle.
                    if (!f.isFocused && armed) {
                        setEditing(false)
                        keyboard?.hide()
                    }
                }
        )
    } else {
        val shape = RoundedCornerShape(6.dp)
        val accent = MaterialTheme.colorScheme.primary
        Surface(
            onClick = { setEditing(true) },
            shape = ClickableSurfaceDefaults.shape(shape),
            // No focus scale: the shell must not grow then snap back to size when
            // it swaps to the editable field / the keyboard opens.
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = PicnicColors.Surface,
                contentColor = PicnicColors.OnDark,
                focusedContainerColor = PicnicColors.SurfaceVariant,
                focusedContentColor = PicnicColors.OnDark
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, PicnicColors.OnDarkMuted), shape = shape),
                focusedBorder = Border(BorderStroke(2.dp, accent), shape = shape)
            ),
            modifier = modifier
        ) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val display = when {
                    value.isEmpty() -> placeholder
                    password -> "•".repeat(value.length)
                    else -> value
                }
                Text(display, color = if (value.isEmpty()) PicnicColors.OnDarkMuted else PicnicColors.OnDark)
            }
        }
    }
}
