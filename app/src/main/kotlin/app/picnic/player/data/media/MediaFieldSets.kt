package app.picnic.player.data.media

import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields

internal val BROWSE_FIELDS = listOf(
    ItemFields.OVERVIEW,
    ItemFields.GENRES,
    ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
    ItemFields.MEDIA_SOURCE_COUNT
)
internal val CONTINUE_FIELDS = BROWSE_FIELDS
internal val LATEST_FIELDS = BROWSE_FIELDS + ItemFields.CHILD_COUNT

internal val PERSON_ITEM_FIELDS = BROWSE_FIELDS +
    ItemFields.PEOPLE +
    ItemFields.PROVIDER_IDS +
    ItemFields.CHILD_COUNT

internal val GRID_FIELDS = BROWSE_FIELDS + ItemFields.SORT_NAME + ItemFields.CHILD_COUNT

internal val LEAD_EPISODE_FIELDS = listOf(ItemFields.MEDIA_STREAMS)

internal val NEXT_EPISODE_FIELDS = listOf(
    ItemFields.OVERVIEW,
    ItemFields.PRIMARY_IMAGE_ASPECT_RATIO
)

internal val IMAGE_TYPES = listOf(
    ImageType.PRIMARY,
    ImageType.BACKDROP,
    ImageType.THUMB,
    ImageType.LOGO
)
