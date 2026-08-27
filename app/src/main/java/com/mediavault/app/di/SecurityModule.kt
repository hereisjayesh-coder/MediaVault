package com.mediavault.app.di

import com.mediavault.app.security.AppLockSettingsStore
import com.mediavault.app.security.DataStoreAppLockSettingsStore
import com.mediavault.app.security.EncryptedPinCredentialStore
import com.mediavault.app.security.PinCredentialStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindAppLockSettingsStore(impl: DataStoreAppLockSettingsStore): AppLockSettingsStore

    @Binds
    @Singleton
    abstract fun bindPinCredentialStore(impl: EncryptedPinCredentialStore): PinCredentialStore
}
