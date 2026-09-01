package app.picnic.player.ui.seerr

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.graphics.vector.ImageVector
import app.picnic.player.data.seerr.SeerrIssueType

internal val SeerrIssueType.icon: ImageVector
    get() = when (this) {
        SeerrIssueType.VIDEO -> Icons.Default.Videocam
        SeerrIssueType.AUDIO -> Icons.Default.VolumeUp
        SeerrIssueType.SUBTITLE -> Icons.Default.ClosedCaption
        SeerrIssueType.OTHER -> Icons.Default.HelpOutline
    }
