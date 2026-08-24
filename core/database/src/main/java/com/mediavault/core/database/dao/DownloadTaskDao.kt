package com.mediavault.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mediavault.core.database.entity.DownloadTaskEntity
import com.mediavault.core.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: DownloadTaskEntity)

    @Update
    suspend fun update(task: DownloadTaskEntity)

    @Delete
    suspend fun delete(task: DownloadTaskEntity)

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun getById(id: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    fun observeById(id: String): Flow<DownloadTaskEntity?>

    @Query("SELECT * FROM download_tasks ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE status IN (:statuses) ORDER BY createdAtEpochMs ASC")
    suspend fun getByStatuses(statuses: List<DownloadStatus>): List<DownloadTaskEntity>

    /** Used on app start: anything still marked DOWNLOADING/PROCESSING was interrupted by process death. */
    @Query("UPDATE download_tasks SET status = :newStatus, updatedAtEpochMs = :nowMs WHERE status IN (:fromStatuses)")
    suspend fun reassignStatus(fromStatuses: List<DownloadStatus>, newStatus: DownloadStatus, nowMs: Long)
}
