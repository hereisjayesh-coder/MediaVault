package com.mediavault.app.di

import com.mediavault.app.storage.DownloadDestinationProvider
import com.mediavault.app.storage.DownloadDestinationStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {

    @Binds
    @Singleton
    abstract fun bindDownloadDestinationProvider(impl: DownloadDestinationStore): DownloadDestinationProvider
}
