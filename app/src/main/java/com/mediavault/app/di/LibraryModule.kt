package com.mediavault.app.di

import com.mediavault.app.library.AndroidLibraryRepository
import com.mediavault.app.library.AndroidMediaImportRepository
import com.mediavault.app.library.AndroidMediaMetadataProbe
import com.mediavault.app.library.LibraryRepository
import com.mediavault.app.library.MediaImportRepository
import com.mediavault.app.library.MediaMetadataProbe
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryModule {

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: AndroidLibraryRepository): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindMediaImportRepository(impl: AndroidMediaImportRepository): MediaImportRepository

    @Binds
    @Singleton
    abstract fun bindMediaMetadataProbe(impl: AndroidMediaMetadataProbe): MediaMetadataProbe
}
