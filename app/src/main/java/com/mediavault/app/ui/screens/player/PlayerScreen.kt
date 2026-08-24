package com.mediavault.app.ui.screens.player

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import com.mediavault.app.R
import com.mediavault.app.ui.components.EmptyStateCard
import com.mediavault.app.ui.components.MediaVaultTopBar
import com.mediavault.app.ui.screens.home.formatDurationLabel
import com.mediavault.core.domain.player.PlaybackState
import com.mediavault.core.model.MediaTrackInfo
import com.mediavault.core.model.SubtitleTrackInfo

private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
    onBackToLibrary: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    DisposableEffect(Unit) {
        onDispose { viewModel.onScreenLeft() }
    }

    ApplyFullscreen(uiState.isFullscreen)

    PlayerScreenContent(
        uiState = uiState,
        onBackToLibrary = onBackToLibrary,
        onPlayPauseToggled = viewModel::onPlayPauseToggled,
        onSeek = viewModel::onSeek,
        onSpeedSelected = viewModel::onSpeedSelected,
        onAudioTrackSelected = viewModel::onAudioTrackSelected,
        onSubtitleTrackSelected = viewModel::onSubtitleTrackSelected,
        onFullscreenToggled = viewModel::onFullscreenToggled,
        onAttachSurface = viewModel::attachVideoSurface,
    )
}

/** Hides the system bars while the player is fullscreen, restoring them the moment it isn't. */
@Composable
private fun ApplyFullscreen(isFullscreen: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    DisposableEffect(isFullscreen) {
        val controller = WindowInsetsControllerCompat(window, view)
        if (isFullscreen) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun PlayerScreenContent(
    uiState: PlayerUiState,
    onBackToLibrary: () -> Unit,
    onPlayPauseToggled: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onAudioTrackSelected: (String) -> Unit,
    onSubtitleTrackSelected: (String?) -> Unit,
    onFullscreenToggled: () -> Unit,
    onAttachSurface: (PlayerView) -> Unit,
) {
    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val item = uiState.item
    if (item == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            MediaVaultTopBar(title = stringResource(R.string.player_title))
            val errorMessage = uiState.errorMessage
            if (errorMessage != null) {
                EmptyStateCard(icon = Icons.Default.Warning, title = stringResource(R.string.player_empty_title), description = errorMessage)
            } else {
                EmptyStateCard(
                    icon = Icons.Default.PlayArrow,
                    title = stringResource(R.string.player_empty_title),
                    description = stringResource(R.string.player_empty_body),
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (!uiState.isFullscreen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onBackToLibrary) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.player_back_to_library))
                }
                Text(text = item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.weight(1f))
            }
        }

        AndroidView(
            factory = { context -> PlayerView(context).apply { useController = false } },
            update = { playerView -> onAttachSurface(playerView) },
            // weight(1f) rather than fillMaxSize() even in fullscreen — the latter would let the
            // video surface claim every remaining pixel in this Column and push the controls
            // below it fully off-screen, making them unreachable while playing.
            modifier = if (uiState.isFullscreen) {
                Modifier.fillMaxWidth().weight(1f)
            } else {
                Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            },
        )

        val playback = uiState.playback
        val playbackErrorMessage = playback?.errorMessage
        if (playbackErrorMessage != null) {
            Text(
                text = playbackErrorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }

        PlayerControls(
            playback = playback,
            onPlayPauseToggled = onPlayPauseToggled,
            onSeek = onSeek,
            onSpeedSelected = onSpeedSelected,
            onAudioTrackSelected = onAudioTrackSelected,
            onSubtitleTrackSelected = onSubtitleTrackSelected,
            isFullscreen = uiState.isFullscreen,
            onFullscreenToggled = onFullscreenToggled,
        )
    }
}

@Composable
private fun PlayerControls(
    playback: PlaybackState?,
    onPlayPauseToggled: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onAudioTrackSelected: (String) -> Unit,
    onSubtitleTrackSelected: (String?) -> Unit,
    isFullscreen: Boolean,
    onFullscreenToggled: () -> Unit,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }

    val durationMs = (playback?.durationMs ?: 0L).coerceAtLeast(1L).toFloat()
    val livePositionMs = playback?.positionMs?.toFloat() ?: 0f
    val shownPositionMs = if (isDragging) dragPositionMs else livePositionMs

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isFullscreen) Color.Black else MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Slider(
            value = shownPositionMs.coerceIn(0f, durationMs),
            valueRange = 0f..durationMs,
            onValueChange = { isDragging = true; dragPositionMs = it },
            onValueChangeFinished = { onSeek(dragPositionMs.toLong()); isDragging = false },
        )

        val textColor = if (isFullscreen) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = formatDurationLabel((shownPositionMs / 1000).toLong()) ?: "0:00", color = textColor, style = MaterialTheme.typography.labelMedium)
            Text(text = formatDurationLabel((durationMs / 1000).toLong()) ?: "0:00", color = textColor, style = MaterialTheme.typography.labelMedium)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPlayPauseToggled) {
                Icon(
                    imageVector = if (playback?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (isFullscreen) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }

            val audioTracks = playback?.availableAudioTracks.orEmpty()
            val subtitleTracks = playback?.availableSubtitleTracks.orEmpty()
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpeedMenu(playback?.playbackSpeed ?: 1f, onSpeedSelected, isFullscreen)
                if (audioTracks.size > 1) {
                    AudioTrackMenu(audioTracks, playback?.selectedAudioTrackId, onAudioTrackSelected, isFullscreen)
                }
                if (subtitleTracks.isNotEmpty()) {
                    SubtitleMenu(subtitleTracks, playback?.selectedSubtitleTrackId, onSubtitleTrackSelected, isFullscreen)
                }
                IconButton(onClick = onFullscreenToggled) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = stringResource(if (isFullscreen) R.string.player_exit_fullscreen else R.string.player_fullscreen),
                        tint = if (isFullscreen) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedMenu(currentSpeed: Float, onSpeedSelected: (Float) -> Unit, isFullscreen: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.Speed, contentDescription = stringResource(R.string.player_speed), tint = if (isFullscreen) Color.White else MaterialTheme.colorScheme.onSurface)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        PLAYBACK_SPEEDS.forEach { speed ->
            DropdownMenuItem(
                text = { Text("${speed}x" + if (speed == currentSpeed) " ✓" else "") },
                onClick = { expanded = false; onSpeedSelected(speed) },
            )
        }
    }
}

@Composable
private fun AudioTrackMenu(
    tracks: List<MediaTrackInfo>,
    selectedTrackId: String?,
    onTrackSelected: (String) -> Unit,
    isFullscreen: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.Audiotrack, contentDescription = stringResource(R.string.player_audio_track), tint = if (isFullscreen) Color.White else MaterialTheme.colorScheme.onSurface)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        tracks.forEachIndexed { index, track ->
            DropdownMenuItem(
                text = { Text(trackLabel(track.label, track.languageCode, index) + if (track.id == selectedTrackId) " ✓" else "") },
                onClick = { expanded = false; onTrackSelected(track.id) },
            )
        }
    }
}

@Composable
private fun SubtitleMenu(
    tracks: List<SubtitleTrackInfo>,
    selectedTrackId: String?,
    onTrackSelected: (String?) -> Unit,
    isFullscreen: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.ClosedCaption, contentDescription = stringResource(R.string.player_subtitles), tint = if (isFullscreen) Color.White else MaterialTheme.colorScheme.onSurface)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.player_subtitles_off) + if (selectedTrackId == null) " ✓" else "") },
            onClick = { expanded = false; onTrackSelected(null) },
        )
        tracks.forEachIndexed { index, track ->
            DropdownMenuItem(
                text = { Text(trackLabel(track.label, track.languageCode, index) + if (track.id == selectedTrackId) " ✓" else "") },
                onClick = { expanded = false; onTrackSelected(track.id) },
            )
        }
    }
}

/** Never guesses a language — falls back to a plain index label when the source gave us no metadata. */
@Composable
private fun trackLabel(label: String?, languageCode: String?, index: Int): String =
    label ?: languageCode ?: stringResource(R.string.player_track_generic, index + 1)
