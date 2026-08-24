package com.mediavault.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mediavault.core.model.MediaType

/**
 * A playable item in the local library, whether it arrived via download or was imported
 * from an on-device folder scan.
 */
@Entity(tableName = "media_items")
data class MediaItemEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val mediaUri: String,
    val mediaType: MediaType,
    val durationMs: Long?,
    val sizeBytes: Long?,
    val container: String?,
    /** e.g. "1080p" — null for audio-only items. */
    val resolutionLabel: String? = null,
    /** Carried from the source so the Library can show a thumbnail without generating one locally. */
    val thumbnailUrl: String? = null,
    val isImported: Boolean,
    val sourceDownloadTaskId: String?,
    val lastPlaybackPositionMs: Long,
    val isFavorite: Boolean,
    val addedAtEpochMs: Long,
)
