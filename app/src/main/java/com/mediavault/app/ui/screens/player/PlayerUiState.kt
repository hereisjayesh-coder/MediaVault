package com.mediavault.app.ui.screens.player

import com.mediavault.core.database.entity.MediaItemEntity
import com.mediavault.core.domain.player.PlaybackState

/** How the video surface fills its box — mirrors Media3 `PlayerView`'s own resize modes 1:1, see `PlayerScreen`. */
enum class VideoResizeMode {
    /** Whole frame visible, letterboxed if the container's aspect ratio doesn't match — default. */
    FIT,
    /** Stretches to fill the container exactly; may distort the picture. */
    FILL,
    /** Crops to fill the container with no letterboxing, aspect ratio preserved. */
    ZOOM,
    /** Locks the video to the container's full width and lets height follow the source's true aspect ratio. */
    ORIGINAL,
}

enum class SleepTimerOption(val minutes: Int?) {
    OFF(null),
    MIN_15(15),
    MIN_30(30),
    MIN_60(60),
    /** Pauses when the current item finishes instead of looping/auto-advancing, rather than firing after a fixed delay. */
    END_OF_MEDIA(null),
}

data class PlayerUiState(
    val isLoading: Boolean = true,
    val item: MediaItemEntity? = null,
    val playback: PlaybackState? = null,
    val isFullscreen: Boolean = false,
    val errorMessage: String? = null,
    val resizeMode: VideoResizeMode = VideoResizeMode.FIT,
    val sleepTimer: SleepTimerOption = SleepTimerOption.OFF,
    /** Only set for a fixed-duration timer, counting down for display — null when off or set to "end of media". */
    val sleepTimerRemainingMs: Long? = null,
    /** This item's playlist, in order, including itself — see `LibraryRepository.getPlaylistSiblings`. Empty for standalone media. */
    val playlistItems: List<MediaItemEntity> = emptyList(),
    val showDetails: Boolean = false,
    /** Auto-hidden a few seconds into fullscreen playback; always true outside fullscreen. */
    val controlsVisible: Boolean = true,
) {
    private val playlistIndex: Int get() = playlistItems.indexOfFirst { it.id == item?.id }
    val hasPrevious: Boolean get() = playlistItems.size > 1 && playlistIndex > 0
    val hasNext: Boolean get() = playlistItems.size > 1 && playlistIndex in 0 until playlistItems.lastIndex
}
