package app.picnic.player.ui.library

import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderPathTest {
    private fun node(name: String, type: BaseItemKind) = BaseItemDto(id = UUID.randomUUID(), name = name, type = type)

    @Test
    fun path_runsFromBelowTheLibraryToTheFolder() {
        val ancestors = listOf(
            node("2024", BaseItemKind.FOLDER),
            node("Trips", BaseItemKind.PHOTO_ALBUM),
            node("Family raw", BaseItemKind.COLLECTION_FOLDER),
            node("Media", BaseItemKind.AGGREGATE_FOLDER)
        )

        assertEquals(listOf("Trips", "2024", "Beach"), folderPath("Beach", ancestors))
    }

    @Test
    fun path_atTheLibraryRootIsJustTheFolder() {
        assertEquals(listOf("Beach"), folderPath("Beach", listOf(node("Root", BaseItemKind.COLLECTION_FOLDER))))
    }
}
