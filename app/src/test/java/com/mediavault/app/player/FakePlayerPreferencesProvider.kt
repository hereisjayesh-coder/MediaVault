package com.mediavault.app.player

import kotlinx.coroutines.flow.MutableStateFlow

class FakePlayerPreferencesProvider(initial: PlayerPreferences = PlayerPreferences()) : PlayerPreferencesProvider {
    private val state = MutableStateFlow(initial)

    override val preferences = state

    override suspend fun currentPreferences(): PlayerPreferences = state.value

    override suspend fun setDefaultPlaybackSpeed(speed: Float) {
        state.value = state.value.copy(defaultPlaybackSpeed = speed)
    }

    override suspend fun setResumePlaybackEnabled(enabled: Boolean) {
        state.value = state.value.copy(resumePlaybackEnabled = enabled)
    }

    override suspend fun setAutoFullscreenLandscape(enabled: Boolean) {
        state.value = state.value.copy(autoFullscreenLandscape = enabled)
    }

    override suspend fun setAutoEnterPip(enabled: Boolean) {
        state.value = state.value.copy(autoEnterPip = enabled)
    }

    override suspend fun setAutoAdvancePlaylist(enabled: Boolean) {
        state.value = state.value.copy(autoAdvancePlaylist = enabled)
    }
}
