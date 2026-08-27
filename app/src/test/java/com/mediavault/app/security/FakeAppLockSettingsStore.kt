package com.mediavault.app.security

import kotlinx.coroutines.flow.MutableStateFlow

class FakeAppLockSettingsStore(initial: AppLockSettings = AppLockSettings()) : AppLockSettingsStore {
    private val state = MutableStateFlow(initial)

    override val settings = state

    override suspend fun currentSettings(): AppLockSettings = state.value

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        state.value = state.value.copy(appLockEnabled = enabled)
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        state.value = state.value.copy(biometricEnabled = enabled)
    }

    override suspend fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        state.value = state.value.copy(autoLockTimeout = timeout)
    }
}
