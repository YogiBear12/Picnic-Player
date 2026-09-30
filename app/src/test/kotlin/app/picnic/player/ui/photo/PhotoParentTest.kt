package app.picnic.player.ui.photo

import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoParentTest {
    private val libraryId = UUID.randomUUID()

    private fun node(type: BaseItemKind) = BaseItemDto(id = UUID.randomUUID(), type = type)

    @Test
    fun photoParent_isTheFolderOrTheLibraryAtRoot() {
        val album = node(BaseItemKind.PHOTO_ALBUM)

        assertEquals(album.id, externalPhotoParent(libraryId, listOf(album)))
        assertEquals(libraryId, externalPhotoParent(libraryId, listOf(node(BaseItemKind.COLLECTION_FOLDER))))
        assertNull(externalPhotoParent(libraryId, emptyList()))
    }
}
