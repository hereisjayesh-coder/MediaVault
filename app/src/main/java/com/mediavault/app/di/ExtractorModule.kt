package com.mediavault.app.di

import com.mediavault.app.extractor.CompositeExtractorEngine
import com.mediavault.core.domain.extractor.ExtractorEngine
import com.mediavault.core.extractor.instaloader.InstaloaderExtractorEngine
import com.mediavault.core.extractor.ytdlp.YtDlpExtractorEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * The only file that wires concrete [ExtractorEngine] backends together — the rest of the app
 * depends on [ExtractorEngine] (bound here to [CompositeExtractorEngine]) and never sees
 * [YtDlpExtractorEngine]/[InstaloaderExtractorEngine] directly. Adding a third backend means
 * implementing [ExtractorEngine] in its own module and adding one more `@IntoSet` binding here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ExtractorModule {

    @Binds
    @IntoSet
    abstract fun bindYtDlpExtractorEngine(impl: YtDlpExtractorEngine): ExtractorEngine

    @Binds
    @IntoSet
    abstract fun bindInstaloaderExtractorEngine(impl: InstaloaderExtractorEngine): ExtractorEngine

    @Binds
    @Singleton
    abstract fun bindExtractorEngine(impl: CompositeExtractorEngine): ExtractorEngine
}
