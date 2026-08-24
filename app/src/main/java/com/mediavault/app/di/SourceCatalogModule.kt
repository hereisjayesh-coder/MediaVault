package com.mediavault.app.di

import com.mediavault.core.domain.source.SourceCatalogRepository
import com.mediavault.core.extractor.ytdlp.YtDlpSourceCatalogRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SourceCatalogModule {

    @Binds
    @Singleton
    abstract fun bindSourceCatalogRepository(impl: YtDlpSourceCatalogRepository): SourceCatalogRepository
}
