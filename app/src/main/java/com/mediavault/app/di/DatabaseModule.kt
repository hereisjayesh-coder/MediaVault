package com.mediavault.app.di

import android.content.Context
import androidx.room.Room
import com.mediavault.core.database.MEDIAVAULT_DATABASE_NAME
import com.mediavault.core.database.MIGRATION_1_2
import com.mediavault.core.database.MediaVaultDatabase
import com.mediavault.core.database.dao.DownloadTaskDao
import com.mediavault.core.database.dao.MediaItemDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMediaVaultDatabase(@ApplicationContext context: Context): MediaVaultDatabase =
        Room.databaseBuilder(context, MediaVaultDatabase::class.java, MEDIAVAULT_DATABASE_NAME)
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideDownloadTaskDao(database: MediaVaultDatabase): DownloadTaskDao =
        database.downloadTaskDao()

    @Provides
    fun provideMediaItemDao(database: MediaVaultDatabase): MediaItemDao =
        database.mediaItemDao()
}
