package com.mediavault.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mediavault.core.model.DownloadStatus
import com.mediavault.core.model.MediaType

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey
    val id: String,
    /** Webpage URL to re-extract from — not a raw CDN URL, which may be short-lived/signed. */
    val sourceUrl: String,
    val title: String?,
    val sourceName: String?,
    val thumbnailUrl: String?,
    val mediaType: MediaType,
    val formatId: String?,
    /** File extension/container of the selected format, e.g. "mp4" — used to name the saved file. */
    val container: String?,
    /** SAF tree URI of the user-selected destination folder, chosen once up front. */
    val destinationTreeUri: String?,
    /** SAF file URI of the finished file — set only once [status] is COMPLETED. */
    val destinationUri: String?,
    /** Real filesystem path in app-private storage the bytes are/were written to mid-transfer. */
    val localCachePath: String?,
    val status: DownloadStatus,
    val bytesTransferred: Long,
    val totalBytes: Long?,
    /** Whether a paused copy of this task can safely continue from its byte offset. */
    val canResume: Boolean,
    val errorMessage: String?,
    /** The extractor-assigned id of the source media, for future dedup/"already downloaded" checks. */
    val sourceMediaId: String? = null,
    /** Set when this task came from a playlist item; groups tasks and preserves playlist order. */
    val playlistId: String? = null,
    val playlistItemIndex: Int? = null,
    /** Denormalized across every task in a playlist group — avoids a separate playlist table for just two display fields. */
    val playlistTitle: String? = null,
    val playlistThumbnailUrl: String? = null,
    /**
     * The quality every task in this playlist group was queued at (see `QualityDescriptor`
     * in core:domain), persisted per-task so format resolution can resume after process death
     * without needing to remember anything outside Room. Null for non-playlist tasks, which
     * already know their exact `formatId` up front and never need to re-resolve one.
     */
    val qualityResolutionLabel: String? = null,
    val qualityContainer: String? = null,
    val qualityHasVideo: Boolean? = null,
    val qualityHasAudio: Boolean? = null,
    /** Media duration, known from analysis — carried through to the completed `MediaItemEntity`. */
    val durationSeconds: Long? = null,
    /** The resolved format's resolution label (e.g. "1080p"), for Library display. Null for audio. */
    val resolutionLabel: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
