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
    /**
     * Selects an embedded subtitle track, or disables subtitles entirely when [trackId] is
     * null. Only ever chooses among tracks already muxed into the source file — there is no
     * external subtitle file (`.srt`/`.vtt`) support yet. [MergeRequest][com.mediavault.core.domain.processing.MergeRequest]-style
     * loading of an external subtitle as an additional [androidx.media3.common.MediaItem]
     * subtitle configuration would be the natural extension point if that's ever added; nothing
     * in this contract (id-based track selection) would need to change for callers.
     */
    fun selectSubtitleTrack(trackId: String?)

    /** REPEAT_MODE_ONE-equivalent: replays the current item instead of stopping at the end. */
    fun setLooping(enabled: Boolean)

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
    /**
     * Decoded video's true display width/height ratio (rotation and pixel aspect already
     * applied), so the UI can size the playback surface to the source's real shape instead of
     * assuming 16:9. Null for audio-only media or before the first frame's size is known.
     */
    val videoAspectRatio: Float? = null,
    val isLooping: Boolean = false,
    /** True once playback has run to the end (and isn't looping) — lets the UI offer "replay" instead of a dead Pause icon. */
    val isEnded: Boolean = false,
)
