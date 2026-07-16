package app.picnic.player.data.media

/**
 * The backdrop pair handed through navigation when a card opens a detail screen:
 * the full-width hero backdrop plus the small image the ambient wash is extracted
 * from. Built by `JellyfinImages.navImages` / `SeerrImages.navImages` so every
 * card click and backdrop publish derives the two URLs the same way.
 */
data class NavImages(val bgUrl: String?, val ambUrl: String?)
