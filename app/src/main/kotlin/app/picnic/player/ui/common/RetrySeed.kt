package app.picnic.player.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Stable
class RetrySeed {
    var pending by mutableStateOf(false)
        private set

    fun arm() {
        pending = true
    }

    fun consume() {
        pending = false
    }
}

@Composable
fun rememberRetrySeed(): RetrySeed = remember { RetrySeed() }
