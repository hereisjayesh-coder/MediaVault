package com.mediavault.app.di

import com.mediavault.app.util.AndroidDeviceStatusProvider
import com.mediavault.app.util.DeviceStatusProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UtilModule {

    @Binds
    @Singleton
    abstract fun bindDeviceStatusProvider(impl: AndroidDeviceStatusProvider): DeviceStatusProvider
}
