package com.mediavault.app.di

import com.mediavault.app.library.AndroidLibraryRepository
import com.mediavault.app.library.LibraryRepository
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
}
