package com.mediavault.app.ui.screens.downloads

import com.mediavault.core.domain.download.DownloadProgress
import com.mediavault.core.model.DownloadStatus

/**
 * Which section of the Downloads screen a task belongs in — the product's required distinction
 * between in-flight, queued, failed, cancelled, and completed work (a completed task's actual
 * *media* lives in the Library instead; this only ever categorizes the download-task record
 * itself). Pure and DAO/Compose-free, so the categorization is directly unit-testable, and kept
 * out of the composable so [DownloadTaskCard] doesn't grow into deciding both layout and status
 * grouping.
 */
enum class DownloadSection { ACTIVE, QUEUED, FAILED, CANCELLED, COMPLETED }

/**
 * Null for [DownloadStatus.ANALYZING] — that status only ever occurs on a playlist item (still
 * resolving its format), and playlist items never reach the plain, non-playlist task list this
 * groups; they're rendered under their own playlist group instead.
 */
fun DownloadStatus.toSection(): DownloadSection? = when (this) {
    // MERGING is still active work (a split video+audio download remuxing), not a fourth state
    // outside the visible sections — leaving it out here is what used to make an in-progress
    // split-stream download vanish from the list while it merged.
    DownloadStatus.DOWNLOADING, DownloadStatus.PROCESSING, DownloadStatus.MERGING -> DownloadSection.ACTIVE
    DownloadStatus.QUEUED, DownloadStatus.PAUSED -> DownloadSection.QUEUED
    DownloadStatus.FAILED -> DownloadSection.FAILED
    DownloadStatus.CANCELLED -> DownloadSection.CANCELLED
    DownloadStatus.COMPLETED -> DownloadSection.COMPLETED
    DownloadStatus.ANALYZING -> null
}

fun List<DownloadProgress>.groupBySection(): Map<DownloadSection, List<DownloadProgress>> =
    mapNotNull { task -> task.status.toSection()?.let { it to task } }
        .groupBy({ it.first }, { it.second })
