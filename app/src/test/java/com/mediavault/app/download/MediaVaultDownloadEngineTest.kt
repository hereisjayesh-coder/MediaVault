package com.mediavault.app.download

import com.mediavault.core.database.entity.DownloadTaskEntity
import com.mediavault.core.domain.download.PlaylistDownloadItem
import com.mediavault.core.domain.download.PlaylistDownloadRequest
import com.mediavault.core.domain.download.QualityDescriptor
import com.mediavault.core.model.DownloadStatus
import com.mediavault.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pure, DAO/coroutine-free decision logic extracted from [MediaVaultDownloadEngine]:
 * playlist task creation, order preservation, duplicate detection, retry-status decisions, and
 * process-recovery grouping. The engine's actual async orchestration (queue processing, real
 * downloads) runs on its own long-lived `Dispatchers.Default` scope and touches Android/SAF
 * APIs — as with the rest of this engine (see PROJECT_MASTER.md), that part is verified via
 * real-device testing and code review rather than JVM unit tests.
 */
class MediaVaultDownloadEngineTest {

    private val descriptor = QualityDescriptor(resolutionLabel = "1080p", container = "mp4", hasVideo = true, hasAudio = true)

    private fun request(
        items: List<PlaylistDownloadItem>,
        skipAlreadyDownloaded: Boolean = true,
    ) = PlaylistDownloadRequest(
        playlistId = "playlist-1",
        playlistTitle = "My Playlist",
        playlistThumbnailUrl = "https://example.com/thumb.jpg",
        sourceName = "Youtube",
        qualityDescriptor = descriptor,
        skipAlreadyDownloaded = skipAlreadyDownloaded,
        items = items,
    )

    private fun item(id: String, index: Int) =
        PlaylistDownloadItem(sourceUrl = "https://example.com/$id", sourceMediaId = id, itemIndex = index, title = "Item $id", thumbnailUrl = null)

    @Test
    fun `every selected item becomes its own task in playlist order`() {
        val items = listOf(item("a", 1), item("b", 2), item("c", 3))

        val tasks = buildPlaylistTaskEntities(request(items), alreadyDownloadedSourceMediaIds = emptySet(), nowMs = 1_000L)

        assertEquals(3, tasks.size)
        assertEquals(listOf(1, 2, 3), tasks.map { it.playlistItemIndex })
        assertEquals(listOf("a", "b", "c"), tasks.map { it.sourceMediaId })
        // Distinct createdAtEpochMs, strictly increasing — this is what preserves order once
        // the real queue picks tasks by createdAtEpochMs ASC.
        assertTrue(tasks.zipWithNext().all { (a, b) -> a.createdAtEpochMs < b.createdAtEpochMs })
    }

    @Test
    fun `every task carries the playlist group and chosen quality`() {
        val tasks = buildPlaylistTaskEntities(request(listOf(item("a", 1))), emptySet(), 1_000L)

        val task = tasks.single()
        assertEquals("playlist-1", task.playlistId)
        assertEquals("My Playlist", task.playlistTitle)
        assertEquals("https://example.com/thumb.jpg", task.playlistThumbnailUrl)
        assertEquals("1080p", task.qualityResolutionLabel)
        assertEquals("mp4", task.qualityContainer)
        assertEquals(true, task.qualityHasVideo)
        assertEquals(true, task.qualityHasAudio)
        assertEquals(false, task.qualityRequiresProcessing)
        assertNull(task.qualityAudioLanguageCode)
        assertEquals(DownloadStatus.ANALYZING, task.status)
    }

    @Test
    fun `a merge-required quality persists requiresProcessing and the paired audio language per task`() {
        val mergeDescriptor = QualityDescriptor(
            resolutionLabel = "1080p",
            container = "mp4",
            hasVideo = true,
            hasAudio = true,
            requiresProcessing = true,
            audioLanguageCode = "en",
        )
        val playlistRequest = request(listOf(item("a", 1))).copy(qualityDescriptor = mergeDescriptor)

        val task = buildPlaylistTaskEntities(playlistRequest, emptySet(), 1_000L).single()

        assertEquals(true, task.qualityRequiresProcessing)
        assertEquals("en", task.qualityAudioLanguageCode)
    }

    @Test
    fun `an item already downloaded is marked cancelled instead of queued for download`() {
        val items = listOf(item("a", 1), item("b", 2))

        val tasks = buildPlaylistTaskEntities(request(items), alreadyDownloadedSourceMediaIds = setOf("a"), nowMs = 1_000L)

        val taskA = tasks.first { it.sourceMediaId == "a" }
        val taskB = tasks.first { it.sourceMediaId == "b" }
        assertEquals(DownloadStatus.CANCELLED, taskA.status)
        assertEquals("Already downloaded — skipped", taskA.errorMessage)
        assertEquals(DownloadStatus.ANALYZING, taskB.status)
        assertNull(taskB.errorMessage)
    }

    @Test
    fun `an empty already-downloaded set leaves every item queued for analysis`() {
        // The caller only populates alreadyDownloadedSourceMediaIds when skipAlreadyDownloaded
        // is set — with it off, every item reaches here as ANALYZING regardless of history.
        val tasks = buildPlaylistTaskEntities(
            request(listOf(item("a", 1)), skipAlreadyDownloaded = false),
            alreadyDownloadedSourceMediaIds = emptySet(),
            nowMs = 1_000L,
        )

        assertEquals(DownloadStatus.ANALYZING, tasks.single().status)
    }

    // --- Retry decisions ---------------------------------------------------------------

    private fun sampleTask(
        status: DownloadStatus,
        playlistId: String? = null,
        formatId: String? = "f1",
        audioFormatId: String? = null,
    ) = DownloadTaskEntity(
        id = "t1",
        sourceUrl = "https://example.com/a",
        title = "A",
        sourceName = null,
        thumbnailUrl = null,
        mediaType = MediaType.VIDEO,
        formatId = formatId,
        audioFormatId = audioFormatId,
        container = "mp4",
        destinationTreeUri = "content://tree/x",
        destinationUri = null,
        localCachePath = null,
        status = status,
        bytesTransferred = 0,
        totalBytes = null,
        canResume = false,
        errorMessage = null,
        playlistId = playlistId,
        createdAtEpochMs = 0,
        updatedAtEpochMs = 0,
    )

    @Test
    fun `a failed playlist item with no resolved format yet retries into ANALYZING`() {
        val task = sampleTask(DownloadStatus.FAILED, playlistId = "p1", formatId = null)

        assertEquals(DownloadStatus.ANALYZING, task.retryNextStatusOrNull())
    }

    @Test
    fun `a failed task that already resolved a format retries straight into QUEUED`() {
        val task = sampleTask(DownloadStatus.FAILED, playlistId = "p1", formatId = "f1")

        assertEquals(DownloadStatus.QUEUED, task.retryNextStatusOrNull())
    }

    @Test
    fun `a failed non-playlist task retries into QUEUED`() {
        val task = sampleTask(DownloadStatus.FAILED, playlistId = null)

        assertEquals(DownloadStatus.QUEUED, task.retryNextStatusOrNull())
    }

    @Test
    fun `a cancelled (including skipped-duplicate) task can be retried too`() {
        val task = sampleTask(DownloadStatus.CANCELLED, playlistId = "p1", formatId = null)

        assertEquals(DownloadStatus.ANALYZING, task.retryNextStatusOrNull())
    }

    @Test
    fun `a task that is not failed or cancelled is not retryable`() {
        assertNull(sampleTask(DownloadStatus.COMPLETED).retryNextStatusOrNull())
        assertNull(sampleTask(DownloadStatus.DOWNLOADING).retryNextStatusOrNull())
        assertNull(sampleTask(DownloadStatus.QUEUED).retryNextStatusOrNull())
    }

    @Test
    fun `a failed split video+audio task retries straight into QUEUED, same as any other direct task`() {
        // Not a playlist task, so retryNextStatusOrNull() only looks at playlistId/formatId —
        // audioFormatId being set doesn't change which status a retry lands on. The engine's
        // runSplitStreamDownload then re-downloads both streams and re-merges from QUEUED.
        val task = sampleTask(DownloadStatus.FAILED, playlistId = null, formatId = "v1", audioFormatId = "a1")

        assertEquals(DownloadStatus.QUEUED, task.retryNextStatusOrNull())
    }

    @Test
    fun `a task stuck MERGING when the process died is not retryable until it's paused`() {
        // MERGING isn't FAILED or CANCELLED — retryNextStatusOrNull() correctly refuses it;
        // recoverAfterProcessDeath() is what reassigns a stuck MERGING task to PAUSED first.
        assertNull(sampleTask(DownloadStatus.MERGING, formatId = "v1", audioFormatId = "a1").retryNextStatusOrNull())
    }

    // --- Process-death recovery ---------------------------------------------------------

    @Test
    fun `process recovery finds every playlist with a stuck ANALYZING task`() {
        val tasks = listOf(
            sampleTask(DownloadStatus.ANALYZING, playlistId = "p1"),
            sampleTask(DownloadStatus.ANALYZING, playlistId = "p1"),
            sampleTask(DownloadStatus.ANALYZING, playlistId = "p2"),
            sampleTask(DownloadStatus.QUEUED, playlistId = "p3"),
            sampleTask(DownloadStatus.ANALYZING, playlistId = null),
        )

        val stuck = tasks.playlistIdsNeedingResolution()

        assertEquals(setOf("p1", "p2"), stuck.toSet())
    }

    @Test
    fun `process recovery finds nothing when no playlist is mid-resolution`() {
        val tasks = listOf(sampleTask(DownloadStatus.DOWNLOADING, playlistId = "p1"), sampleTask(DownloadStatus.COMPLETED, playlistId = "p2"))

        assertTrue(tasks.playlistIdsNeedingResolution().isEmpty())
    }

    // --- Completed download -> Library insertion ------------------------------------------

    @Test
    fun `a completed task maps to a library item carrying its real metadata`() {
        val task = sampleTask(DownloadStatus.COMPLETED, formatId = "f1").copy(
            title = "My Video",
            container = "mp4",
            bytesTransferred = 12_345L,
            durationSeconds = 125,
            resolutionLabel = "1080p",
            thumbnailUrl = "https://example.com/thumb.jpg",
        )

        val item = buildMediaItemEntity(task, mediaUri = "file:///private/media/My Video.mp4", id = "media-1", nowMs = 999L)

        assertEquals("media-1", item.id)
        assertEquals("My Video", item.title)
        assertEquals("file:///private/media/My Video.mp4", item.mediaUri)
        assertEquals(MediaType.VIDEO, item.mediaType)
        assertEquals(125_000L, item.durationMs)
        assertEquals(12_345L, item.sizeBytes)
        assertEquals("mp4", item.container)
        assertEquals("1080p", item.resolutionLabel)
        assertEquals("https://example.com/thumb.jpg", item.thumbnailUrl)
        assertEquals("t1", item.sourceDownloadTaskId)
        assertEquals(0L, item.lastPlaybackPositionMs)
        assertEquals(999L, item.addedAtEpochMs)
    }

    @Test
    fun `a task with no known duration maps to a null duration, never a guess`() {
        val task = sampleTask(DownloadStatus.COMPLETED).copy(durationSeconds = null)

        val item = buildMediaItemEntity(task, mediaUri = "file:///private/media/x.mp4", id = "media-1", nowMs = 0L)

        assertNull(item.durationMs)
    }

    @Test
    fun `an untitled task falls back to a generic title, never a blank one`() {
        val task = sampleTask(DownloadStatus.COMPLETED).copy(title = null)

        val item = buildMediaItemEntity(task, mediaUri = "file:///private/media/x.mp4", id = "media-1", nowMs = 0L)

        assertEquals("Untitled", item.title)
    }

    // --- Remove eligibility --------------------------------------------------------------

    @Test
    fun `a completed, failed, or cancelled task can be removed`() {
        assertTrue(sampleTask(DownloadStatus.COMPLETED).isRemovable())
        assertTrue(sampleTask(DownloadStatus.FAILED).isRemovable())
        assertTrue(sampleTask(DownloadStatus.CANCELLED).isRemovable())
    }

    @Test
    fun `an active or queued task can never be removed out from under itself`() {
        assertTrue(!sampleTask(DownloadStatus.DOWNLOADING).isRemovable())
        assertTrue(!sampleTask(DownloadStatus.PROCESSING).isRemovable())
        assertTrue(!sampleTask(DownloadStatus.MERGING).isRemovable())
        assertTrue(!sampleTask(DownloadStatus.QUEUED).isRemovable())
        assertTrue(!sampleTask(DownloadStatus.PAUSED).isRemovable())
    }

    // --- Engine routing hint -------------------------------------------------------------

    @Test
    fun `an Instagram image task hints instaloader, since yt-dlp would also claim the URL`() {
        val task = sampleTask(DownloadStatus.ANALYZING).copy(
            sourceUrl = "https://instagram.com/p/shortcode/",
            mediaType = MediaType.IMAGE,
        )

        assertEquals("instaloader", task.preferredEngineIdOrNull())
    }

    @Test
    fun `a Reddit image task hints nothing — only yt-dlp's canHandle ever claims that URL`() {
        // Regression guard: this used to unconditionally hint "instaloader" for every
        // MediaType.IMAGE task, which would have wrongly forced a Reddit image download
        // through Instaloader (which doesn't even recognize reddit.com URLs) instead of the
        // yt-dlp backend that actually resolved it.
        val task = sampleTask(DownloadStatus.ANALYZING).copy(
            sourceUrl = "https://www.reddit.com/r/pics/comments/1w0mfi4/sunrise_on_lake_ontario/",
            mediaType = MediaType.IMAGE,
        )

        assertNull(task.preferredEngineIdOrNull())
    }

    @Test
    fun `a non-image task never hints an image-only backend`() {
        val task = sampleTask(DownloadStatus.ANALYZING).copy(
            sourceUrl = "https://instagram.com/reel/shortcode/",
            mediaType = MediaType.VIDEO,
        )

        assertNull(task.preferredEngineIdOrNull())
    }
}
