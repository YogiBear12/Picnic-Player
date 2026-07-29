package app.picnic.player.data.jellyfin

import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.media.NavImages
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

/**
 * Builds Jellyfin image URLs directly (stable REST shape). Done by hand rather
 * than via the SDK image API so the call sites stay simple and the URLs are
 * Coil-friendly. Sizing uses fillWidth so the server resizes server-side.
 */
object JellyfinImages {

    fun primary(session: UserSession, item: BaseItemDto, fillWidth: Int = 480): String? {
        val tag = item.imageTags?.get(ImageType.PRIMARY)
        return if (tag != null) {
            url(session, item.id.toString(), ImageType.PRIMARY, tag, fillWidth)
        } else {
            seriesPrimary(session, item, fillWidth)
        }
    }

    /**
     * Server-admin splashscreen art (Dashboard > Branding > Splashscreen). Public
     * endpoint, no auth and no image tag. Returns a URL unconditionally — the server
     * 404s when no splashscreen was ever uploaded, so callers must fall back on load
     * failure rather than trust the URL's existence.
     */
    fun splashscreen(session: UserSession, fillWidth: Int = 1920): String {
        val base = session.server.baseUrl.trimEnd('/')
        return "$base/Branding/Splashscreen?fillWidth=$fillWidth&quality=90"
    }

    /**
     * A Jellyfin user's avatar. Tag-guarded like item artwork so a replaced avatar lands on a
     * new URL instead of serving the cached copy; an untagged URL 404s when the user has no
     * avatar, which callers already treat as "draw the initial".
     */
    fun userPrimary(baseUrl: String, userId: String, tag: String?, fillWidth: Int = 256): String {
        val base = "${baseUrl.trimEnd('/')}/Users/$userId/Images/Primary?fillWidth=$fillWidth&quality=90"
        return if (tag != null) "$base&tag=$tag" else base
    }

    fun personPrimary(session: UserSession, personId: String, tag: String?, fillWidth: Int = 480): String? {
        if (tag == null) return null
        return url(session, personId, ImageType.PRIMARY, tag, fillWidth)
    }

    /**
     * Poster for portrait browse rows. Episodes use the series Primary when
     * available so a single-episode "recently added" shows the show poster, not
     * the episode still.
     */
    fun rowPoster(session: UserSession, item: BaseItemDto, fillWidth: Int = 480): String? {
        if (item.type == BaseItemKind.EPISODE) {
            seriesPrimary(session, item, fillWidth)?.let { return it }
        }
        return primary(session, item, fillWidth)
    }

    /**
     * Series poster for an episode. Resolved only from a real image tag (same rule as
     * [logo]): a guessed URL for a series with no poster 404s forever and — worse —
     * being non-null it short-circuits callers' fallback chains, so the episode's own
     * artwork never gets a chance.
     */
    fun seriesPrimary(session: UserSession, item: BaseItemDto, fillWidth: Int = 480): String? {
        val seriesId = item.seriesId?.toString() ?: return null
        val tag = item.seriesPrimaryImageTag ?: return null
        return url(session, seriesId, ImageType.PRIMARY, tag, fillWidth)
    }

    /** An episode's own still frame (its PRIMARY image), never a series/parent fallback. */
    fun episodeStill(session: UserSession, item: BaseItemDto, fillWidth: Int = 640): String? {
        if (item.type != BaseItemKind.EPISODE) return null
        val tag = item.imageTags?.get(ImageType.PRIMARY) ?: return null
        return url(session, item.id.toString(), ImageType.PRIMARY, tag, fillWidth)
    }

    fun thumb(session: UserSession, item: BaseItemDto, fillWidth: Int = 640): String? {
        item.imageTags?.get(ImageType.THUMB)?.let {
            return url(session, item.id.toString(), ImageType.THUMB, it, fillWidth)
        }
        // Episode → series Thumb, then the generic parent Thumb.
        item.seriesThumbImageTag?.let { tag ->
            item.seriesId?.let { return url(session, it.toString(), ImageType.THUMB, tag, fillWidth) }
        }
        item.parentThumbImageTag?.let { tag ->
            item.parentThumbItemId?.let {
                return url(session, it.toString(), ImageType.THUMB, tag, fillWidth)
            }
        }
        return null
    }

    fun backdrop(session: UserSession, item: BaseItemDto, fillWidth: Int = 1280): String? {
        item.backdropImageTags?.firstOrNull()?.let {
            return url(session, item.id.toString(), ImageType.BACKDROP, it, fillWidth)
        }
        val parentId = item.parentBackdropItemId?.toString() ?: return null
        val parentTag = item.parentBackdropImageTags?.firstOrNull() ?: return null
        return url(session, parentId, ImageType.BACKDROP, parentTag, fillWidth)
    }

    /**
     * Item logo, resolved only from a real image tag so we never request a logo
     * the server doesn't have (a guessed series-logo URL 404s and leaves a gap
     * with no title fallback). Episodes carry the series logo via the Parent tags.
     */
    fun logo(session: UserSession, item: BaseItemDto, fillWidth: Int = 480): String? {
        item.imageTags?.get(ImageType.LOGO)?.let {
            return url(session, item.id.toString(), ImageType.LOGO, it, fillWidth)
        }
        item.parentLogoImageTag?.let { tag ->
            item.parentLogoItemId?.let {
                return url(session, it.toString(), ImageType.LOGO, tag, fillWidth)
            }
        }
        return null
    }

    /**
     * Small ambient-wash source image: the backdrop at wash size, else the poster.
     * The wash extractor only needs ~240px; anything larger wastes decode time.
     */
    fun ambient(session: UserSession, item: BaseItemDto): String? = backdrop(session, item, fillWidth = 240) ?: primary(session, item, fillWidth = 240)

    /** Hero backdrop + ambient-wash pair handed through navigation on card click. */
    fun navImages(session: UserSession, item: BaseItemDto): NavImages = NavImages(
        bgUrl = backdrop(session, item, fillWidth = 1280),
        ambUrl = ambient(session, item)
    )

    /**
     * BlurHashes for the images the card resolvers pick, mirroring their fallback order. The
     * server sends these inline with the item, so an accent taken from one costs no request at
     * all. Each is looked up by the same tag its URL is built from, so it stays correct when
     * artwork is replaced.
     */
    fun rowPosterBlurHash(item: BaseItemDto): String? {
        if (item.type == BaseItemKind.EPISODE) {
            blurHash(item, ImageType.PRIMARY, item.seriesPrimaryImageTag)?.let { return it }
        }
        return blurHash(item, ImageType.PRIMARY, item.imageTags?.get(ImageType.PRIMARY))
            ?: blurHash(item, ImageType.PRIMARY, item.seriesPrimaryImageTag)
    }

    fun thumbBlurHash(item: BaseItemDto): String? = blurHash(item, ImageType.THUMB, item.imageTags?.get(ImageType.THUMB))
        ?: blurHash(item, ImageType.THUMB, item.seriesThumbImageTag)
        ?: blurHash(item, ImageType.THUMB, item.parentThumbImageTag)

    fun backdropBlurHash(item: BaseItemDto): String? = blurHash(item, ImageType.BACKDROP, item.backdropImageTags?.firstOrNull())
        ?: blurHash(item, ImageType.BACKDROP, item.parentBackdropImageTags?.firstOrNull())

    private fun blurHash(item: BaseItemDto, type: ImageType, tag: String?): String? {
        if (tag == null) return null
        return item.imageBlurHashes?.get(type)?.get(tag)
    }

    private fun url(
        session: UserSession,
        itemId: String,
        type: ImageType,
        tag: String?,
        fillWidth: Int
    ): String {
        val base = session.server.baseUrl.trimEnd('/')
        val query = buildString {
            append("?fillWidth=").append(fillWidth)
            append("&quality=90")
            if (tag != null) append("&tag=").append(tag)
        }
        return "$base/Items/$itemId/Images/${type.serialName}$query"
    }
}
