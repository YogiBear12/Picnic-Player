package app.picnic.player.data.jellyfin

import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.media.NavImages
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

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

    fun userPrimary(baseUrl: String, userId: String, tag: String?, fillWidth: Int = 256): String {
        val base = "${baseUrl.trimEnd('/')}/Users/$userId/Images/Primary?fillWidth=$fillWidth&quality=90"
        return if (tag != null) "$base&tag=$tag" else base
    }

    fun personPrimary(session: UserSession, personId: String, tag: String?, fillWidth: Int = 480): String? {
        if (tag == null) return null
        return url(session, personId, ImageType.PRIMARY, tag, fillWidth)
    }

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

    fun episodeStill(session: UserSession, item: BaseItemDto, fillWidth: Int = 640): String? {
        if (item.type != BaseItemKind.EPISODE) return null
        val tag = item.imageTags?.get(ImageType.PRIMARY) ?: return null
        return url(session, item.id.toString(), ImageType.PRIMARY, tag, fillWidth)
    }

    fun thumb(session: UserSession, item: BaseItemDto, fillWidth: Int = 640): String? {
        item.imageTags?.get(ImageType.THUMB)?.let {
            return url(session, item.id.toString(), ImageType.THUMB, it, fillWidth)
        }
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

    fun ambient(session: UserSession, item: BaseItemDto): String? = backdrop(session, item, fillWidth = 240) ?: primary(session, item, fillWidth = 240)

    fun navImages(session: UserSession, item: BaseItemDto): NavImages = NavImages(
        bgUrl = backdrop(session, item, fillWidth = 1280),
        ambUrl = ambient(session, item)
    )

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

    fun trickplayTile(session: UserSession, itemId: UUID, width: Int, tileIndex: Int): String {
        val base = session.server.baseUrl.trimEnd('/')
        return "$base/Videos/$itemId/Trickplay/$width/$tileIndex.jpg?api_key=${session.accessToken}"
    }

    fun chapterImage(session: UserSession, itemId: UUID, index: Int, imageTag: String): String {
        val base = session.server.baseUrl.trimEnd('/')
        return "$base/Items/$itemId/Images/Chapter/$index?tag=$imageTag&api_key=${session.accessToken}"
    }

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
