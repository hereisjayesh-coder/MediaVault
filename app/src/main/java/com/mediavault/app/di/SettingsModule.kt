package com.mediavault.app.di

import com.mediavault.app.settings.DataStoreThemeStore
import com.mediavault.app.settings.ThemeStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {

    @Binds
    @Singleton
    abstract fun bindThemeStore(impl: DataStoreThemeStore): ThemeStore
}
