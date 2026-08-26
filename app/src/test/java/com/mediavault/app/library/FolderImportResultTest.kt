package com.mediavault.app.library

import com.mediavault.core.database.entity.MediaItemEntity
import com.mediavault.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderImportResultTest {

    private fun entity(id: String) = MediaItemEntity(
        id = id,
        title = "Item $id",
        mediaUri = "content://provider/$id",
        mediaType = MediaType.VIDEO,
        durationMs = null,
        sizeBytes = null,
        container = "mp4",
        isImported = true,
        sourceDownloadTaskId = null,
        lastPlaybackPositionMs = 0,
        isFavorite = false,
        addedAtEpochMs = 0L,
    )

    @Test
    fun `an empty folder with nothing skipped reports no media files`() {
        val result = FolderImportResult(imported = emptyList(), skippedCount = 0)
        assertEquals("That folder has no media files.", result.summaryMessage())
    }

    @Test
    fun `a folder of only unsupported files reports the skipped count, not a false success`() {
        val result = FolderImportResult(imported = emptyList(), skippedCount = 3)
        assertEquals("No supported media found (3 file(s) skipped).", result.summaryMessage())
    }

    @Test
    fun `a fully successful import reports only the imported count`() {
        val result = FolderImportResult(imported = listOf(entity("a"), entity("b")), skippedCount = 0)
        assertEquals("Imported 2 item(s).", result.summaryMessage())
    }

    @Test
    fun `a partially successful import reports both counts`() {
        val result = FolderImportResult(imported = listOf(entity("a")), skippedCount = 2)
        assertEquals("Imported 1 item(s), skipped 2 unsupported file(s).", result.summaryMessage())
    }
}
