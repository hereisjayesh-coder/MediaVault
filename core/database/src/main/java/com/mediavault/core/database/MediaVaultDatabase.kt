package com.mediavault.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mediavault.core.database.dao.DownloadTaskDao
import com.mediavault.core.database.dao.MediaItemDao
import com.mediavault.core.database.entity.DownloadTaskEntity
import com.mediavault.core.database.entity.MediaItemEntity

const val MEDIAVAULT_DATABASE_NAME = "mediavault.db"

@Database(
    entities = [
        DownloadTaskEntity::class,
        MediaItemEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MediaVaultDatabase : RoomDatabase() {
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun mediaItemDao(): MediaItemDao
}
