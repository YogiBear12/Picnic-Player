package app.picnic.player.ui.browse

import app.picnic.player.data.media.PersonalLibrary
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType

sealed interface BrowseDest {
    val key: String

    data object Search : BrowseDest {
        override val key = "search"
    }

    data object Home : BrowseDest {
        override val key = "home"
    }

    data object Discover : BrowseDest {
        override val key = "discover"
    }

    data object Playlists : BrowseDest {
        override val key = "playlists"
    }

    data class Library(
        val id: UUID,
        val title: String,
        val kinds: List<BaseItemKind>,
        val collectionType: CollectionType? = null
    ) : BrowseDest {
        override val key = "lib:$id"

        val personalLibrary: PersonalLibrary?
            get() = PersonalLibrary(id, title).takeIf { collectionType == CollectionType.HOMEVIDEOS }
    }
}
