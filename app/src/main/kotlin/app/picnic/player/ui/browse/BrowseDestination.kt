package app.picnic.player.ui.browse

import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * A top-level browse destination shown in the side navigation drawer. Search and Home are
 * fixed; libraries are built dynamically from the server's video libraries.
 */
sealed interface BrowseDest {
    /** Stable identity — drives saved selection and per-destination focus requesters. */
    val key: String

    data object Search : BrowseDest {
        override val key = "search"
    }

    data object Home : BrowseDest {
        override val key = "home"
    }

    /** Seerr Discover — only published when linked and Show Discover is on. */
    data object Discover : BrowseDest {
        override val key = "discover"
    }

    /**
     * One Jellyfin video library. [kinds] is the item types its grid queries
     * (movies → MOVIE, shows → SERIES, mixed → both).
     */
    data class Library(
        val id: UUID,
        val title: String,
        val kinds: List<BaseItemKind>
    ) : BrowseDest {
        override val key = "lib:$id"
    }
}
