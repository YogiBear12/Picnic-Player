package app.picnic.player.ui.browse

import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * A top-level browse destination shown in the side navigation drawer. Search and Home are
 * fixed; libraries (+ Discover when Seerr is linked) are customisable via pin/reorder (#88).
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

    /** Seerr Discover — published when Seerr is linked; pin/unpin like a library (#88). */
    data object Discover : BrowseDest {
        override val key = "discover"
    }

    /** The user's playlists — published when the server has a playlists view; pin/unpin like a library. */
    data object Playlists : BrowseDest {
        override val key = "playlists"
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
