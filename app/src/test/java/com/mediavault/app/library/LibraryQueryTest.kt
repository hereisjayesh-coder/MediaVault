package com.mediavault.app.library

import com.mediavault.core.database.entity.MediaItemEntity
import com.mediavault.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryQueryTest {

    private fun item(
        id: String,
        title: String,
        sizeBytes: Long? = null,
        addedAtEpochMs: Long = 0L,
        isImported: Boolean = false,
        sourceDownloadTaskId: String? = null,
    ) = MediaItemEntity(
        id = id,
        title = title,
        mediaUri = "file:///private/media/$id.mp4",
        mediaType = MediaType.VIDEO,
        durationMs = null,
        sizeBytes = sizeBytes,
        container = "mp4",
        isImported = isImported,
        sourceDownloadTaskId = sourceDownloadTaskId,
        lastPlaybackPositionMs = 0,
        isFavorite = false,
        addedAtEpochMs = addedAtEpochMs,
    )

    // --- Search -----------------------------------------------------------------------

    @Test
    fun `blank query returns every item`() {
        val items = listOf(item("1", "Alpha"), item("2", "Beta"))

        assertEquals(2, items.filterAndSort("", LibrarySortOrder.RECENT).size)
    }

    @Test
    fun `search matches title case-insensitively`() {
        val items = listOf(item("1", "My Vacation Video"), item("2", "Other Clip"))

        val result = items.filterAndSort("vacation", LibrarySortOrder.RECENT)

        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test
    fun `search with no matches returns an empty list`() {
        val items = listOf(item("1", "Alpha"))

        assertTrue(items.filterAndSort("zzz", LibrarySortOrder.RECENT).isEmpty())
    }

    // --- Sort -------------------------------------------------------------------------

    @Test
    fun `RECENT sorts by added time, newest first`() {
        val items = listOf(item("old", "A", addedAtEpochMs = 100L), item("new", "B", addedAtEpochMs = 200L))

        val result = items.filterAndSort("", LibrarySortOrder.RECENT)

        assertEquals(listOf("new", "old"), result.map { it.id })
    }

    @Test
    fun `NAME sorts alphabetically, case-insensitive`() {
        val items = listOf(item("1", "zebra"), item("2", "Apple"))

        val result = items.filterAndSort("", LibrarySortOrder.NAME)

        assertEquals(listOf("2", "1"), result.map { it.id })
    }

    @Test
    fun `SIZE sorts largest first, treating unknown size as zero`() {
        val items = listOf(item("small", "A", sizeBytes = 100L), item("unknown", "B", sizeBytes = null), item("large", "C", sizeBytes = 900L))

        val result = items.filterAndSort("", LibrarySortOrder.SIZE)

        assertEquals(listOf("large", "small", "unknown"), result.map { it.id })
    }

    // --- Missing-file detection ---------------------------------------------------------

    @Test
    fun `isMissing reflects the injected existence check`() {
        val present = item("1", "A")
        val gone = item("2", "B")

        assertFalse(present.isMissing { uri -> uri == "file:///private/media/1.mp4" })
        assertTrue(gone.isMissing { uri -> uri == "file:///private/media/1.mp4" })
    }

    // --- Delete safety (never delete a file MediaVault doesn't own) --------------------

    @Test
    fun `a MediaVault-managed download's underlying file can be deleted`() {
        assertTrue(item("1", "A", isImported = false).canDeleteUnderlyingFile())
    }

    @Test
    fun `an imported item's underlying document can never be deleted by Library removal`() {
        assertFalse(item("1", "A", isImported = true).canDeleteUnderlyingFile())
    }

    // --- Origin classification -----------------------------------------------------------

    @Test
    fun `a plain download is classified as DOWNLOADED`() {
        val downloaded = item("1", "A", isImported = false, sourceDownloadTaskId = "task-1")
        assertEquals(MediaOrigin.DOWNLOADED, downloaded.origin())
    }

    @Test
    fun `a download later saved to Gallery is classified as SAVED_TO_GALLERY, not IMPORTED`() {
        val savedToGallery = item("1", "A", isImported = true, sourceDownloadTaskId = "task-1")
        assertEquals(MediaOrigin.SAVED_TO_GALLERY, savedToGallery.origin())
    }

    @Test
    fun `a file imported from outside MediaVault is classified as IMPORTED`() {
        val imported = item("1", "A", isImported = true, sourceDownloadTaskId = null)
        assertEquals(MediaOrigin.IMPORTED, imported.origin())
    }

    // --- Export file naming ---------------------------------------------------------------

    @Test
    fun `exportFileName combines the sanitized title with the container extension`() {
        val messyTitle = item("1", "My/Video?").copy(container = "mp4")
        assertEquals("My_Video_.mp4", exportFileName(messyTitle))
    }

    @Test
    fun `exportFileName falls back to bin when the container is unknown`() {
        val noContainer = item("1", "Clip").copy(container = null)
        assertEquals("Clip.bin", exportFileName(noContainer))
    }
}
