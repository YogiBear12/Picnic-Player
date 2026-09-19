package app.picnic.player.ui.seerr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.common.PanelContentInset
import app.picnic.player.ui.common.PanelRowCornerRadius
import app.picnic.player.ui.common.PanelWidth

internal val SeerrPanelWidth = PanelWidth.Picker
internal val SeerrRowInnerPadding = 14.dp
internal val SeerrRowCornerRadius = PanelRowCornerRadius
internal val SeerrListViewportHeight = 200.dp

@Composable
internal fun SeerrPanelHeader(title: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PanelContentInset)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = SeerrRowInnerPadding)
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.14f))
        )
        Spacer(Modifier.height(12.dp))
    }
}
