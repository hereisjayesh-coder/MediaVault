package com.mediavault.app.ui.screens.downloads

import com.mediavault.core.domain.download.DownloadProgress
import com.mediavault.core.model.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure coverage of which Downloads-screen section each task status renders under. */
class DownloadSectionsTest {

    private fun task(status: DownloadStatus) = DownloadProgress(
        taskId = "t-$status",
        title = "Task",
        sourceName = null,
        thumbnailUrl = null,
        status = status,
        bytesTransferred = 0L,
        totalBytes = null,
        throughputBytesPerSecond = null,
        etaSeconds = null,
        canResume = false,
        errorMessage = null,
        destinationUri = null,
        createdAtEpochMs = 0L,
    )

    @Test
    fun `downloading, processing, and merging are all Active — a merging task must not disappear from the list`() {
        assertEquals(DownloadSection.ACTIVE, DownloadStatus.DOWNLOADING.toSection())
        assertEquals(DownloadSection.ACTIVE, DownloadStatus.PROCESSING.toSection())
        assertEquals(DownloadSection.ACTIVE, DownloadStatus.MERGING.toSection())
    }

    @Test
    fun `queued and paused are Queued`() {
        assertEquals(DownloadSection.QUEUED, DownloadStatus.QUEUED.toSection())
        assertEquals(DownloadSection.QUEUED, DownloadStatus.PAUSED.toSection())
    }

    @Test
    fun `failed and cancelled are distinct sections, not merged together`() {
        assertEquals(DownloadSection.FAILED, DownloadStatus.FAILED.toSection())
        assertEquals(DownloadSection.CANCELLED, DownloadStatus.CANCELLED.toSection())
    }

    @Test
    fun `completed is its own section`() {
        assertEquals(DownloadSection.COMPLETED, DownloadStatus.COMPLETED.toSection())
    }

    @Test
    fun `analyzing has no section — it never reaches the non-playlist task list`() {
        assertNull(DownloadStatus.ANALYZING.toSection())
    }

    @Test
    fun `grouping splits a mixed task list into the right sections, dropping analyzing entries`() {
        val tasks = listOf(
            task(DownloadStatus.DOWNLOADING),
            task(DownloadStatus.MERGING),
            task(DownloadStatus.QUEUED),
            task(DownloadStatus.FAILED),
            task(DownloadStatus.CANCELLED),
            task(DownloadStatus.COMPLETED),
            task(DownloadStatus.ANALYZING),
        )

        val grouped = tasks.groupBySection()

        assertEquals(2, grouped[DownloadSection.ACTIVE]?.size)
        assertEquals(1, grouped[DownloadSection.QUEUED]?.size)
        assertEquals(1, grouped[DownloadSection.FAILED]?.size)
        assertEquals(1, grouped[DownloadSection.CANCELLED]?.size)
        assertEquals(1, grouped[DownloadSection.COMPLETED]?.size)
        assertEquals(6, grouped.values.sumOf { it.size })
    }
}
