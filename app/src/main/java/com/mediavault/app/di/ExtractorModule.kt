package com.mediavault.app.di

import com.mediavault.core.domain.extractor.ExtractorEngine
import com.mediavault.core.extractor.ytdlp.YtDlpExtractorEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExtractorModule {

    @Binds
    @Singleton
    abstract fun bindExtractorEngine(impl: YtDlpExtractorEngine): ExtractorEngine
}
