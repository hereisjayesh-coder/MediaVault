package com.mediavault.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mediavault.core.model.DownloadStatus
import com.mediavault.core.model.MediaType

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey
    val id: String,
    val sourceUrl: String,
    val title: String?,
    val sourceName: String?,
    val mediaType: MediaType,
    val formatId: String?,
    val destinationUri: String?,
    val status: DownloadStatus,
    val bytesTransferred: Long,
    val totalBytes: Long?,
    val errorMessage: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
