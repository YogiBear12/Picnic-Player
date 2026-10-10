package app.picnic.player.data.media

/**
 * A card's backdrop pair: the full-width hero backdrop plus the small image the
 * ambient wash is extracted from. Built by `JellyfinImages.navImages` /
 * `SeerrImages.navImages` so every card derives the two URLs the same way.
 */
data class NavImages(val bgUrl: String?, val ambUrl: String?)
