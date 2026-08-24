package com.mediavault.app.di

import com.mediavault.app.download.AndroidDownloadForegroundServiceStarter
import com.mediavault.app.download.DownloadForegroundServiceStarter
import com.mediavault.app.download.MediaVaultDownloadEngine
import com.mediavault.core.domain.download.DownloadEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadModule {

    @Binds
    @Singleton
    abstract fun bindDownloadEngine(impl: MediaVaultDownloadEngine): DownloadEngine

    @Binds
    @Singleton
    abstract fun bindDownloadForegroundServiceStarter(impl: AndroidDownloadForegroundServiceStarter): DownloadForegroundServiceStarter
}
