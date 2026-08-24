package com.mediavault.app.ui.screens.player

import com.mediavault.core.database.entity.MediaItemEntity
import com.mediavault.core.domain.player.PlaybackState

data class PlayerUiState(
    val isLoading: Boolean = true,
    val item: MediaItemEntity? = null,
    val playback: PlaybackState? = null,
    val isFullscreen: Boolean = false,
    val errorMessage: String? = null,
)
