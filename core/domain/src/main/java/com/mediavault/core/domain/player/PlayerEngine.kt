package com.mediavault.core.domain.player

import com.mediavault.core.model.MediaTrackInfo
import com.mediavault.core.model.SubtitleTrackInfo
import kotlinx.coroutines.flow.Flow

/**
 * Player-facing abstraction so screens and view models depend on this contract rather than
 * on Media3 (ExoPlayer) directly. Media3 is the initial and only planned implementation, but
 * nothing above this layer should reference `androidx.media3.*` types.
 */
interface PlayerEngine {

    fun prepare(mediaUri: String)

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setPlaybackSpeed(speed: Float)

    fun selectAudioTrack(trackId: String)
    fun selectSubtitleTrack(trackId: String?)

    fun observeState(): Flow<PlaybackState>

    /** Releases underlying player resources; the engine instance must not be reused after this. */
    fun release()
}

data class PlaybackState(
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long?,
    val bufferedPercentage: Int,
    val availableAudioTracks: List<MediaTrackInfo>,
    val availableSubtitleTracks: List<SubtitleTrackInfo>,
    val selectedAudioTrackId: String?,
    val selectedSubtitleTrackId: String?,
    val playbackSpeed: Float,
    /** Set when the underlying player hit a playback error (corrupt file, unsupported codec, ...); null otherwise. */
    val errorMessage: String? = null,
)
