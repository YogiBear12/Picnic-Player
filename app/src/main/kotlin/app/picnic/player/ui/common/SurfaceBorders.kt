package app.picnic.player.ui.common

import androidx.compose.runtime.Composable
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceBorder
import androidx.tv.material3.ClickableSurfaceDefaults

@Composable
fun flatSurfaceBorder(): ClickableSurfaceBorder = ClickableSurfaceDefaults.border(focusedDisabledBorder = Border.None)
