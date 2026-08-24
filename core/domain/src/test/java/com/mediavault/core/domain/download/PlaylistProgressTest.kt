package com.mediavault.core.domain.download

import com.mediavault.core.model.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistProgressTest {

    private fun progress(
        taskId: String,
        status: DownloadStatus,
        playlistId: String?,
        playlistTitle: String? = "My Playlist",
        playlistThumbnailUrl: String? = "https://example.com/thumb.jpg",
        title: String? = taskId,
    ) = DownloadProgress(
        taskId = taskId,
        title = title,
        sourceName = null,
        thumbnailUrl = null,
        status = status,
        bytesTransferred = 0,
        totalBytes = null,
        throughputBytesPerSecond = null,
        etaSeconds = null,
        canResume = false,
        errorMessage = null,
        destinationUri = null,
        createdAtEpochMs = 0,
        playlistId = playlistId,
        playlistItemIndex = null,
        playlistTitle = playlistTitle,
        playlistThumbnailUrl = playlistThumbnailUrl,
    )

    @Test
    fun `non-playlist tasks are excluded from playlist groups`() {
        val tasks = listOf(progress("1", DownloadStatus.COMPLETED, playlistId = null))

        assertTrue(tasks.toPlaylistProgressGroups().isEmpty())
    }

    @Test
    fun `counts completed failed skipped and remaining independently`() {
        val tasks = listOf(
            progress("1", DownloadStatus.COMPLETED, "p1"),
            progress("2", DownloadStatus.COMPLETED, "p1"),
            progress("3", DownloadStatus.FAILED, "p1"),
            progress("4", DownloadStatus.CANCELLED, "p1"),
            progress("5", DownloadStatus.QUEUED, "p1"),
            progress("6", DownloadStatus.ANALYZING, "p1"),
            progress("7", DownloadStatus.DOWNLOADING, "p1"),
        )

        val group = tasks.toPlaylistProgressGroups().single()

        assertEquals(7, group.totalCount)
        assertEquals(2, group.completedCount)
        assertEquals(1, group.failedCount)
        assertEquals(1, group.skippedCount)
        assertEquals(2, group.queuedCount) // QUEUED + ANALYZING
        assertEquals(1, group.activeCount)
        assertEquals(3, group.remainingCount) // queuedCount + activeCount
    }

    @Test
    fun `current item title comes from the actively downloading task`() {
        val tasks = listOf(
            progress("1", DownloadStatus.QUEUED, "p1", title = "Next up"),
            progress("2", DownloadStatus.DOWNLOADING, "p1", title = "Now playing"),
        )

        assertEquals("Now playing", tasks.toPlaylistProgressGroups().single().currentItemTitle)
    }

    @Test
    fun `current item title is null when nothing is actively transferring`() {
        val tasks = listOf(progress("1", DownloadStatus.QUEUED, "p1"))

        assertNull(tasks.toPlaylistProgressGroups().single().currentItemTitle)
    }

    @Test
    fun `multiple playlists are grouped separately and sorted by title`() {
        val tasks = listOf(
            progress("1", DownloadStatus.QUEUED, "p2", playlistTitle = "Zebra"),
            progress("2", DownloadStatus.QUEUED, "p1", playlistTitle = "Apple"),
        )

        val groups = tasks.toPlaylistProgressGroups()

        assertEquals(listOf("Apple", "Zebra"), groups.map { it.playlistTitle })
    }

    @Test
    fun `playlist title and thumbnail are recovered even if one row is missing them`() {
        val tasks = listOf(
            progress("1", DownloadStatus.QUEUED, "p1", playlistTitle = null, playlistThumbnailUrl = null),
            progress("2", DownloadStatus.QUEUED, "p1", playlistTitle = "Real Title", playlistThumbnailUrl = "https://example.com/t.jpg"),
        )

        val group = tasks.toPlaylistProgressGroups().single()

        assertEquals("Real Title", group.playlistTitle)
        assertEquals("https://example.com/t.jpg", group.playlistThumbnailUrl)
    }
}
