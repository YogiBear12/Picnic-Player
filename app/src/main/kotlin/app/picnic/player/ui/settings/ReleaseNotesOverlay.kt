package app.picnic.player.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.theme.PicnicColors
import kotlinx.coroutines.launch

private val PageBackground = Color(0xFF0E1114)

@Composable
internal fun ReleaseNotesOverlay(
    version: String,
    notes: String,
    onBack: () -> Unit
) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val scrollFr = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BackHandler(onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .background(PageBackground)
                .padding(horizontal = 64.dp, vertical = 48.dp)
        ) {
            Text(
                "What's new in $version",
                style = MaterialTheme.typography.headlineMedium,
                color = PicnicColors.OnDark
            )
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .focusRequester(scrollFr)
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val page = 240f
                        when (event.key) {
                            Key.DirectionDown -> {
                                scope.launch { scroll.animateScrollBy(page) }
                                true
                            }
                            Key.DirectionUp -> {
                                scope.launch { scroll.animateScrollBy(-page) }
                                true
                            }
                            else -> false
                        }
                    }
                    .focusable()
                    .verticalScroll(scroll)
                    .padding(24.dp)
            ) {
                MarkdownLite(notes)
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { scrollFr.requestFocus() } }
}

@Composable
internal fun MarkdownLite(source: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        source.lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.height(6.dp))
                line.startsWith("#") -> {
                    val level = line.takeWhile { it == '#' }.length
                    Text(
                        line.dropWhile { it == '#' }.trim(),
                        style = when (level) {
                            1 -> MaterialTheme.typography.titleLarge
                            2 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        },
                        fontWeight = FontWeight.SemiBold,
                        color = PicnicColors.OnDark,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    Row {
                        Box(
                            Modifier
                                .padding(top = 9.dp)
                                .width(5.dp)
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(PicnicColors.OnDarkMuted)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            boldAware(line.drop(2).trim()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = PicnicColors.OnDarkMuted
                        )
                    }
                }
                else -> Text(
                    boldAware(line),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PicnicColors.OnDarkMuted
                )
            }
        }
    }
}

private fun boldAware(text: String): AnnotatedString = buildAnnotatedString {
    val parts = text.split("**")
    parts.forEachIndexed { index, part ->
        if (index % 2 == 1 && index != parts.lastIndex) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Color.White)) {
                append(part)
            }
        } else if (index % 2 == 1) {
            append("**$part")
        } else {
            append(part)
        }
    }
}
