package com.mediavault.app.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.mediavault.core.domain.player.PlaybackState
import com.mediavault.core.domain.player.PlayerEngine
import com.mediavault.core.model.MediaTrackInfo
import com.mediavault.core.model.SubtitleTrackInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Creates a fresh [PlayerEngine] per playback session — see [PlayerEngine] for why the app core never references this directly. */
interface PlayerEngineFactory {
    fun create(): PlayerEngine
}

class Media3PlayerEngineFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlayerEngineFactory {
    override fun create(): PlayerEngine = Media3PlayerEngine(context)
}

/**
 * [PlayerEngine] backed by Media3's [ExoPlayer]. Must be created and used from a single thread
 * with a [android.os.Looper] (the main thread, in practice) — ExoPlayer itself requires this.
 * Owned exclusively by [com.mediavault.app.ui.screens.player.PlayerViewModel] for the lifetime
 * of one playback session; never shared or held as a singleton.
 */
class Media3PlayerEngine(context: Context) : PlayerEngine {

    private val player: ExoPlayer = ExoPlayer.Builder(context).build()

    /**
     * Escape hatch for the one thing [PlayerEngine] can't abstract away: a `PlayerView` needs a
     * real [androidx.media3.common.Player] to render video frames onto. Only the Compose screen
     * (never [com.mediavault.app.ui.screens.player.PlayerViewModel]'s own logic) touches this —
     * the ViewModel still drives playback purely through the [PlayerEngine] contract above.
     */
    val rawPlayer: Player get() = player

    override fun prepare(mediaUri: String) {
        player.setMediaItem(MediaItem.fromUri(mediaUri))
        player.prepare()
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    override fun setPlaybackSpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed)
    }

    override fun selectAudioTrack(trackId: String) {
        applyOverride(C.TRACK_TYPE_AUDIO, trackId)
    }

    override fun selectSubtitleTrack(trackId: String?) {
        if (trackId == null) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .build()
        } else {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
            applyOverride(C.TRACK_TYPE_TEXT, trackId)
        }
    }

    override fun setLooping(enabled: Boolean) {
        player.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    private fun applyOverride(trackType: Int, trackId: String) {
        val (groupIndex, trackIndex) = trackId.split(":").map { it.toInt() }
        val group = player.currentTracks.groups.filter { it.type == trackType }.getOrNull(groupIndex) ?: return
        val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex))
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(override)
            .build()
    }

    override fun observeState(): Flow<PlaybackState> = callbackFlow {
        var lastError: String? = null

        fun snapshot(): PlaybackState {
            val tracks = player.currentTracks
            val videoSize = player.videoSize
            val aspectRatio = if (videoSize.width > 0 && videoSize.height > 0) {
                (videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height
            } else {
                null
            }
            return PlaybackState(
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition,
                durationMs = player.duration.takeIf { it != C.TIME_UNSET },
                bufferedPercentage = player.bufferedPercentage,
                availableAudioTracks = tracks.toTrackInfos(C.TRACK_TYPE_AUDIO),
                availableSubtitleTracks = tracks.toSubtitleInfos(),
                selectedAudioTrackId = tracks.selectedTrackId(C.TRACK_TYPE_AUDIO),
                selectedSubtitleTrackId = tracks.selectedTrackId(C.TRACK_TYPE_TEXT),
                playbackSpeed = player.playbackParameters.speed,
                errorMessage = lastError,
                videoAspectRatio = aspectRatio,
                isLooping = player.repeatMode == Player.REPEAT_MODE_ONE,
                isEnded = player.playbackState == Player.STATE_ENDED,
            )
        }

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                trySend(snapshot())
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                trySend(snapshot())
            }

            override fun onTracksChanged(tracks: Tracks) {
                trySend(snapshot())
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                trySend(snapshot())
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                trySend(snapshot())
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                trySend(snapshot())
            }

            override fun onPlayerError(error: PlaybackException) {
                lastError = error.message ?: "Playback failed."
                trySend(snapshot())
            }
        }
        player.addListener(listener)
        trySend(snapshot())

        // Position/buffer updates have no dedicated callback — a light poll while playing keeps
        // the seek bar moving without spamming updates while paused.
        val pollJob = launch {
            while (isActive) {
                if (player.isPlaying) trySend(snapshot())
                delay(500)
            }
        }

        awaitClose {
            pollJob.cancel()
            player.removeListener(listener)
        }
    }

    override fun release() {
        player.release()
    }
}

private fun Tracks.toTrackInfos(trackType: Int): List<MediaTrackInfo> =
    groups.filter { it.type == trackType }.flatMapIndexed { groupIndex, group ->
        (0 until group.length).map { trackIndex ->
            val format = group.getTrackFormat(trackIndex)
            MediaTrackInfo(
                id = "$groupIndex:$trackIndex",
                languageCode = format.language,
                label = format.label,
                isDefault = (format.selectionFlags and C.SELECTION_FLAG_DEFAULT) != 0,
            )
        }
    }

private fun Tracks.toSubtitleInfos(): List<SubtitleTrackInfo> =
    groups.filter { it.type == C.TRACK_TYPE_TEXT }.flatMapIndexed { groupIndex, group ->
        (0 until group.length).map { trackIndex ->
            val format = group.getTrackFormat(trackIndex)
            SubtitleTrackInfo(
                id = "$groupIndex:$trackIndex",
                languageCode = format.language,
                label = format.label,
                isForced = (format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0,
            )
        }
    }

private fun Tracks.selectedTrackId(trackType: Int): String? {
    groups.filter { it.type == trackType }.forEachIndexed { groupIndex, group ->
        for (trackIndex in 0 until group.length) {
            if (group.isTrackSelected(trackIndex)) return "$groupIndex:$trackIndex"
        }
    }
    return null
}
