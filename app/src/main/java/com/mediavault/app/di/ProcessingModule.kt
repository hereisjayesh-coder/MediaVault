package com.mediavault.app.di

import com.mediavault.app.processing.FFmpegMediaProcessor
import com.mediavault.core.domain.processing.MediaProcessor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProcessingModule {

    @Binds
    @Singleton
    abstract fun bindMediaProcessor(impl: FFmpegMediaProcessor): MediaProcessor
}
