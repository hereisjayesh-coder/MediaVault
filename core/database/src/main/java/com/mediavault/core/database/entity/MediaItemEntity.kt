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
    val isImported: Boolean,
    val sourceDownloadTaskId: String?,
    val lastPlaybackPositionMs: Long,
    val isFavorite: Boolean,
    val addedAtEpochMs: Long,
)
