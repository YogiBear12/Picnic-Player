@file:OptIn(ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.darkColorScheme
import app.picnic.player.ui.grid.OceanAmbientBackground

/**
 * App theme (Compose for TV). Dark, cinematic, TV-first. Colours come from the
 * Picnic brand palette ([PicnicColors]) — neutral white accent for focus chrome;
 * dynamic artwork colour is applied per-screen (browse cards, ambient washes).
 */
@Composable
fun PicnicTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = PicnicColors.Accent,
        onPrimary = PicnicColors.Background,
        primaryContainer = PicnicColors.Teal,
        onPrimaryContainer = PicnicColors.OnDark,
        secondary = PicnicColors.Teal,
        onSecondary = PicnicColors.OnDark,
        background = PicnicColors.Background,
        onBackground = PicnicColors.OnDark,
        surface = PicnicColors.Surface,
        onSurface = PicnicColors.OnDark,
        surfaceVariant = PicnicColors.SurfaceVariant,
        onSurfaceVariant = PicnicColors.OnDarkMuted,
        border = PicnicColors.Accent,
        error = PicnicColors.Error
    )
    MaterialTheme(colorScheme = colors) {
        Box(Modifier.fillMaxSize()) {
            OceanAmbientBackground(Modifier.fillMaxSize())
            Surface(
                modifier = Modifier.fillMaxSize(),
                colors = SurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = colors.onBackground
                )
            ) {
                content()
            }
        }
    }
}
