package com.mediavault.core.domain.download

import com.mediavault.core.model.DownloadStatus

/** Aggregate view of one playlist's [DownloadProgress] rows — see [List.toPlaylistProgressGroups]. */
data class PlaylistProgress(
    val playlistId: String,
    val playlistTitle: String?,
    val playlistThumbnailUrl: String?,
    val totalCount: Int,
    val completedCount: Int,
    val failedCount: Int,
    /** Not downloaded on purpose — cancelled by the user, or skipped as already-downloaded. */
    val skippedCount: Int,
    /** Not started yet: queued for format resolution, resolved and queued for transfer, or paused. */
    val queuedCount: Int,
    /** Currently downloading or being post-processed. */
    val activeCount: Int,
    /** Title of the item currently transferring, if any — for a "current item" display. */
    val currentItemTitle: String?,
) {
    val remainingCount: Int get() = queuedCount + activeCount
}

/**
 * Groups every task carrying a `playlistId` into one [PlaylistProgress] per playlist,
 * sorted by title. Tasks with no `playlistId` (ordinary single-item downloads) are excluded
 * — callers render those through the existing non-playlist sections unchanged.
 */
fun List<DownloadProgress>.toPlaylistProgressGroups(): List<PlaylistProgress> =
    filter { it.playlistId != null }
        .groupBy { it.playlistId!! }
        .map { (playlistId, tasks) ->
            val current = tasks.firstOrNull { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PROCESSING }
            PlaylistProgress(
                playlistId = playlistId,
                playlistTitle = tasks.firstNotNullOfOrNull { it.playlistTitle },
                playlistThumbnailUrl = tasks.firstNotNullOfOrNull { it.playlistThumbnailUrl },
                totalCount = tasks.size,
                completedCount = tasks.count { it.status == DownloadStatus.COMPLETED },
                failedCount = tasks.count { it.status == DownloadStatus.FAILED },
                skippedCount = tasks.count { it.status == DownloadStatus.CANCELLED },
                queuedCount = tasks.count {
                    it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.ANALYZING || it.status == DownloadStatus.PAUSED
                },
                activeCount = tasks.count { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PROCESSING },
                currentItemTitle = current?.title,
            )
        }
        .sortedBy { it.playlistTitle?.lowercase() }
