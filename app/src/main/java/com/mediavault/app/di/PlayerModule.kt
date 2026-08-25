package com.mediavault.app.di

import com.mediavault.app.player.AudioPreferenceProvider
import com.mediavault.app.player.AudioPreferenceStore
import com.mediavault.app.player.LastPlayedProvider
import com.mediavault.app.player.LastPlayedStore
import com.mediavault.app.player.Media3PlayerEngineFactory
import com.mediavault.app.player.PlayerEngineFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {

    @Binds
    @Singleton
    abstract fun bindPlayerEngineFactory(impl: Media3PlayerEngineFactory): PlayerEngineFactory

    @Binds
    @Singleton
    abstract fun bindLastPlayedProvider(impl: LastPlayedStore): LastPlayedProvider

    @Binds
    @Singleton
    abstract fun bindAudioPreferenceProvider(impl: AudioPreferenceStore): AudioPreferenceProvider
}
