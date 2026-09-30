package app.picnic.player.data.media

import java.util.UUID
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.serializer.UUIDSerializer

@Serializable
data class PersonalLibrary(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val name: String
)

@Serializable
data class FolderContext(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val sort: GridSortSpec
)

val FOLDER_CHILD_KINDS = listOf(
    BaseItemKind.FOLDER,
    BaseItemKind.PHOTO_ALBUM,
    BaseItemKind.PHOTO,
    BaseItemKind.VIDEO
)

fun BaseItemDto.isFolderContainer(): Boolean = isFolder == true ||
    type == BaseItemKind.FOLDER ||
    type == BaseItemKind.PHOTO_ALBUM
