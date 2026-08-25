package com.mediavault.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mediavault.core.database.entity.MediaItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MediaItemEntity)

    @Update
    suspend fun update(item: MediaItemEntity)

    @Delete
    suspend fun delete(item: MediaItemEntity)

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getById(id: String): MediaItemEntity?

    @Query("SELECT * FROM media_items ORDER BY addedAtEpochMs DESC")
    fun observeAll(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE isFavorite = 1 ORDER BY addedAtEpochMs DESC")
    fun observeFavorites(): Flow<List<MediaItemEntity>>

    /** Used to resolve a playlist's sibling Library items from their download tasks' ids — see `LibraryRepository.getPlaylistSiblings`. */
    @Query("SELECT * FROM media_items WHERE sourceDownloadTaskId IN (:taskIds)")
    suspend fun getBySourceDownloadTaskIds(taskIds: List<String>): List<MediaItemEntity>
}
