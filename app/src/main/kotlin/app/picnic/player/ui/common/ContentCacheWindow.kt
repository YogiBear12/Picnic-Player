@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.picnic.player.ui.common

import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.ui.unit.dp

internal val ContentCacheWindow = LazyLayoutCacheWindow(ahead = 1200.dp, behind = 400.dp)
