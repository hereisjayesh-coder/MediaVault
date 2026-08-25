package com.mediavault.app.ui.screens.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.util.Rational
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import com.mediavault.app.R
import com.mediavault.app.ui.components.EmptyStateCard
import com.mediavault.app.ui.components.MediaDetailsDialog
import com.mediavault.app.ui.components.MediaVaultTopBar
import com.mediavault.app.ui.screens.home.formatDurationLabel
import com.mediavault.core.model.MediaTrackInfo
import com.mediavault.core.model.SubtitleTrackInfo

private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private const val DEFAULT_ASPECT_RATIO = 16f / 9f

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
    onBackToLibrary: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val isInPip = LocalIsInPictureInPicture.current

    DisposableEffect(Unit) {
        onDispose { viewModel.onScreenLeft() }
    }

    // Fullscreen also applies inside PiP's own tiny window — no system bars to fight there anyway.
    ApplyFullscreen(uiState.isFullscreen || isInPip)

    PlayerScreenContent(
        uiState = uiState,
        onBackToLibrary = onBackToLibrary,
        onPlayPauseToggled = viewModel::onPlayPauseToggled,
        onSeek = viewModel::onSeek,
        onSeekBy = viewModel::seekBy,
        onSpeedSelected = viewModel::onSpeedSelected,
        onAudioTrackSelected = viewModel::onAudioTrackSelected,
        onSubtitleTrackSelected = viewModel::onSubtitleTrackSelected,
        onFullscreenToggled = viewModel::onFullscreenToggled,
        onAttachSurface = viewModel::attachVideoSurface,
        onSurfaceTapped = viewModel::onSurfaceTapped,
        onLoopToggled = viewModel::onLoopToggled,
        onResizeModeSelected = viewModel::onResizeModeSelected,
        onDetailsToggled = viewModel::onDetailsToggled,
        onNext = viewModel::onNext,
        onPrevious = viewModel::onPrevious,
        onSleepTimerSelected = viewModel::onSleepTimerSelected,
    )
}

/** Hides the system bars while the player is fullscreen (or in PiP), restoring them the moment it isn't. */
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
    onSeekBy: (Long) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onAudioTrackSelected: (String) -> Unit,
    onSubtitleTrackSelected: (String?) -> Unit,
    onFullscreenToggled: () -> Unit,
    onAttachSurface: (PlayerView) -> Unit,
    onSurfaceTapped: () -> Unit,
    onLoopToggled: () -> Unit,
    onResizeModeSelected: (VideoResizeMode) -> Unit,
    onDetailsToggled: (Boolean) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSleepTimerSelected: (SleepTimerOption) -> Unit,
) {
    // This route intentionally receives none of Scaffold's inset padding (see MediaVaultNavHost)
    // so it can react to its own fullscreen state instead of the Scaffold's, which doesn't pick
    // up this screen's imperative system-bar hide/show calls. Every branch below is therefore
    // responsible for its own status/navigation-bar-safe padding.
    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentAlignment = Alignment.Center) {
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
                .statusBarsPadding()
                .navigationBarsPadding()
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

    val isInPip = LocalIsInPictureInPicture.current
    val ratio = uiState.playback?.videoAspectRatio ?: DEFAULT_ASPECT_RATIO

    if (uiState.showDetails) {
        MediaDetailsDialog(item = item, onDismiss = { onDetailsToggled(false) })
    }

    // Only fullscreen/PiP claim the whole screen as a black media surface — the embedded
    // layout keeps the normal light background outside the video's own (black) bounds, so
    // there is never a stray black area beyond what the video itself actually occupies.
    val rootBackground = if (uiState.isFullscreen || isInPip) Color.Black else MaterialTheme.colorScheme.background
    // Fullscreen/PiP deliberately draw edge-to-edge with no inset padding — that's the entire
    // point of "true fullscreen." Embedded mode keeps real system bars visible, so its own
    // content must stay clear of them.
    val insetsModifier = if (uiState.isFullscreen || isInPip) Modifier else Modifier.statusBarsPadding().navigationBarsPadding()
    Box(Modifier.fillMaxSize().background(rootBackground).then(insetsModifier)) {
        Column(Modifier.fillMaxSize()) {
            if (!uiState.isFullscreen && !isInPip) {
                PlayerTopBar(title = item.title, onBackToLibrary = onBackToLibrary)
            }

            VideoArea(
                ratio = ratio,
                isFullscreen = uiState.isFullscreen,
                resizeMode = uiState.resizeMode,
                isInPip = isInPip,
                isLoadingFrame = uiState.playback == null,
                onAttachSurface = onAttachSurface,
                onSurfaceTapped = onSurfaceTapped,
            )

            val playbackErrorMessage = uiState.playback?.errorMessage
            if (!uiState.isFullscreen && playbackErrorMessage != null) {
                Text(
                    text = playbackErrorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }

            if (!uiState.isFullscreen && !isInPip) {
                PlayerControlsPanel(
                    uiState = uiState,
                    onPlayPauseToggled = onPlayPauseToggled,
                    onSeek = onSeek,
                    onSeekBy = onSeekBy,
                    onSpeedSelected = onSpeedSelected,
                    onAudioTrackSelected = onAudioTrackSelected,
                    onSubtitleTrackSelected = onSubtitleTrackSelected,
                    onFullscreenToggled = onFullscreenToggled,
                    onLoopToggled = onLoopToggled,
                    onResizeModeSelected = onResizeModeSelected,
                    onDetailsToggled = onDetailsToggled,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSleepTimerSelected = onSleepTimerSelected,
                    overlayStyle = false,
                )
            }
        }

        // Fullscreen: controls float over the video instead of pushing it into a smaller box,
        // and auto-hide a few seconds into playback — tap the video to bring them back. PiP
        // uses Media3's own built-in controller instead (see VideoSurface), never this overlay.
        if (uiState.isFullscreen && !isInPip) {
            AnimatedVisibility(
                visible = uiState.controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Box(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f))) {
                    PlayerTopBar(title = item.title, onBackToLibrary = onBackToLibrary, isOverlay = true)
                }
            }
            AnimatedVisibility(
                visible = uiState.controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomStart),
            ) {
                Box(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f))) {
                    PlayerControlsPanel(
                        uiState = uiState,
                        onPlayPauseToggled = onPlayPauseToggled,
                        onSeek = onSeek,
                        onSeekBy = onSeekBy,
                        onSpeedSelected = onSpeedSelected,
                        onAudioTrackSelected = onAudioTrackSelected,
                        onSubtitleTrackSelected = onSubtitleTrackSelected,
                        onFullscreenToggled = onFullscreenToggled,
                        onLoopToggled = onLoopToggled,
                        onResizeModeSelected = onResizeModeSelected,
                        onDetailsToggled = onDetailsToggled,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onSleepTimerSelected = onSleepTimerSelected,
                        overlayStyle = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerTopBar(title: String, onBackToLibrary: () -> Unit, isOverlay: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isOverlay) Modifier else Modifier.background(MaterialTheme.colorScheme.background))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onBackToLibrary) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.player_back_to_library),
                tint = if (isOverlay) Color.White else MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = if (isOverlay) Color.White else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Sizes the video surface to its real aspect ratio instead of a fixed box — see the player
 * milestone's layout requirement. Landscape/square content shrinks to a compact, width-driven
 * box (no reserved empty space below it); portrait content and fullscreen both use whatever
 * height is actually available, centered, so a 9:16 source never gets squeezed into a 16:9 box.
 */
@Composable
private fun ColumnScope.VideoArea(
    ratio: Float,
    isFullscreen: Boolean,
    resizeMode: VideoResizeMode,
    isInPip: Boolean,
    isLoadingFrame: Boolean,
    onAttachSurface: (PlayerView) -> Unit,
    onSurfaceTapped: () -> Unit,
) {
    val useAvailableHeight = isFullscreen || isInPip || ratio < 1f
    val tapModifier = if (isFullscreen && !isInPip) {
        Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onSurfaceTapped() }) }
    } else {
        Modifier
    }

    if (useAvailableHeight) {
        Box(
            modifier = Modifier.fillMaxWidth()
                .let { if (isFullscreen || isInPip) it.fillMaxSize() else it.weight(1f) }
                .background(Color.Black)
                .then(tapModifier),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val (width, height) = fitWithinBounds(maxWidth, maxHeight, ratio)
                VideoSurface(resizeMode, isInPip, onAttachSurface, Modifier.width(width).height(height))
                if (isLoadingFrame) CircularProgressIndicator(color = Color.White)
            }
        }
    } else {
        // Landscape/square, embedded: shrink to a compact, width-driven box — no space
        // reserved beyond what the video itself actually occupies. Black is scoped to this
        // exact box, never bleeding past the video's own bounds into the rest of the screen.
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(ratio).background(Color.Black).then(tapModifier),
            contentAlignment = Alignment.Center,
        ) {
            VideoSurface(resizeMode, isInPip, onAttachSurface, Modifier.fillMaxSize())
            if (isLoadingFrame) CircularProgressIndicator(color = Color.White)
        }
    }
}

@Composable
private fun VideoSurface(
    resizeMode: VideoResizeMode,
    isInPip: Boolean,
    onAttachSurface: (PlayerView) -> Unit,
    modifier: Modifier,
) {
    AndroidView(
        factory = { context -> PlayerView(context).apply { useController = false } },
        update = { playerView ->
            onAttachSurface(playerView)
            // PiP's window is too small for Compose's own controls to be usable — hand
            // control to Media3's built-in minimal controller for the duration of PiP only.
            playerView.useController = isInPip
            playerView.resizeMode = resizeMode.toMedia3ResizeMode()
        },
        modifier = modifier,
    )
}

/** Fits a box of the given aspect [ratio] inside [maxWidth]x[maxHeight], preserving it (letterbox/pillarbox as needed). */
private fun fitWithinBounds(maxWidth: Dp, maxHeight: Dp, ratio: Float): Pair<Dp, Dp> {
    if (ratio <= 0f) return maxWidth to maxHeight
    val heightAtFullWidth = maxWidth / ratio
    return if (heightAtFullWidth <= maxHeight) {
        maxWidth to heightAtFullWidth
    } else {
        (maxHeight * ratio) to maxHeight
    }
}

private fun VideoResizeMode.toMedia3ResizeMode(): Int = when (this) {
    VideoResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    VideoResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    VideoResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    VideoResizeMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
}

@Composable
private fun PlayerControlsPanel(
    uiState: PlayerUiState,
    onPlayPauseToggled: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onAudioTrackSelected: (String) -> Unit,
    onSubtitleTrackSelected: (String?) -> Unit,
    onFullscreenToggled: () -> Unit,
    onLoopToggled: () -> Unit,
    onResizeModeSelected: (VideoResizeMode) -> Unit,
    onDetailsToggled: (Boolean) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSleepTimerSelected: (SleepTimerOption) -> Unit,
    overlayStyle: Boolean,
) {
    val playback = uiState.playback
    var isDragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }

    val durationMs = (playback?.durationMs ?: 0L).coerceAtLeast(1L).toFloat()
    val livePositionMs = playback?.positionMs?.toFloat() ?: 0f
    val shownPositionMs = if (isDragging) dragPositionMs else livePositionMs
    val textColor = if (overlayStyle) Color.White else MaterialTheme.colorScheme.onSurface
    val subtleTextColor = if (overlayStyle) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (overlayStyle) Modifier else Modifier.background(MaterialTheme.colorScheme.background))
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Slider(
            value = shownPositionMs.coerceIn(0f, durationMs),
            valueRange = 0f..durationMs,
            onValueChange = { isDragging = true; dragPositionMs = it },
            onValueChangeFinished = { onSeek(dragPositionMs.toLong()); isDragging = false },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = formatDurationLabel((shownPositionMs / 1000).toLong()) ?: "0:00", color = subtleTextColor, style = MaterialTheme.typography.labelMedium)
            val sleepRemaining = uiState.sleepTimerRemainingMs
            if (sleepRemaining != null) {
                Text(
                    text = stringResource(R.string.player_sleep_timer_remaining, formatDurationLabel(sleepRemaining / 1000) ?: "0:00"),
                    color = subtleTextColor,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(text = formatDurationLabel((durationMs / 1000).toLong()) ?: "0:00", color = subtleTextColor, style = MaterialTheme.typography.labelMedium)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (uiState.hasPrevious) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.player_previous), tint = textColor)
                }
            }
            IconButton(onClick = { onSeekBy(-10_000L) }) {
                Icon(Icons.Default.Replay10, contentDescription = stringResource(R.string.player_seek_back_10), tint = textColor)
            }
            IconButton(
                onClick = onPlayPauseToggled,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = textColor),
            ) {
                Icon(
                    imageVector = if (playback?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                )
            }
            IconButton(onClick = { onSeekBy(10_000L) }) {
                Icon(Icons.Default.Forward10, contentDescription = stringResource(R.string.player_seek_forward_10), tint = textColor)
            }
            if (uiState.hasNext) {
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.player_next), tint = textColor)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val audioTracks = playback?.availableAudioTracks.orEmpty()
            val subtitleTracks = playback?.availableSubtitleTracks.orEmpty()
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpeedMenu(playback?.playbackSpeed ?: 1f, onSpeedSelected, textColor)
                if (audioTracks.size > 1) {
                    AudioTrackMenu(audioTracks, playback?.selectedAudioTrackId, onAudioTrackSelected, textColor)
                }
                if (subtitleTracks.isNotEmpty()) {
                    SubtitleMenu(subtitleTracks, playback?.selectedSubtitleTrackId, onSubtitleTrackSelected, textColor)
                }
                ResizeModeMenu(uiState.resizeMode, onResizeModeSelected, textColor)
                IconButton(onClick = onLoopToggled) {
                    Icon(
                        Icons.Default.Loop,
                        contentDescription = stringResource(R.string.player_loop),
                        tint = if (playback?.isLooping == true) MaterialTheme.colorScheme.primary else textColor,
                    )
                }
                SleepTimerMenu(uiState.sleepTimer, onSleepTimerSelected, textColor)
                PipButton(playback?.videoAspectRatio ?: DEFAULT_ASPECT_RATIO, textColor)
                IconButton(onClick = { onDetailsToggled(true) }) {
                    Icon(Icons.Default.Info, contentDescription = stringResource(R.string.player_details), tint = textColor)
                }
            }
            IconButton(onClick = onFullscreenToggled) {
                Icon(
                    imageVector = if (uiState.isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = stringResource(if (uiState.isFullscreen) R.string.player_exit_fullscreen else R.string.player_fullscreen),
                    tint = textColor,
                )
            }
        }
    }
}

@Composable
private fun SpeedMenu(currentSpeed: Float, onSpeedSelected: (Float) -> Unit, tint: Color) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.Speed, contentDescription = stringResource(R.string.player_speed), tint = tint)
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
    tint: Color,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.Audiotrack, contentDescription = stringResource(R.string.player_audio_track), tint = tint)
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
    tint: Color,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.ClosedCaption, contentDescription = stringResource(R.string.player_subtitles), tint = tint)
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

@Composable
private fun ResizeModeMenu(current: VideoResizeMode, onSelected: (VideoResizeMode) -> Unit, tint: Color) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.AspectRatio, contentDescription = stringResource(R.string.player_aspect_ratio), tint = tint)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        val labels = mapOf(
            VideoResizeMode.FIT to R.string.player_resize_fit,
            VideoResizeMode.FILL to R.string.player_resize_fill,
            VideoResizeMode.ZOOM to R.string.player_resize_zoom,
            VideoResizeMode.ORIGINAL to R.string.player_resize_original,
        )
        labels.forEach { (mode, labelRes) ->
            DropdownMenuItem(
                text = { Text(stringResource(labelRes) + if (mode == current) " ✓" else "") },
                onClick = { expanded = false; onSelected(mode) },
            )
        }
    }
}

@Composable
private fun SleepTimerMenu(current: SleepTimerOption, onSelected: (SleepTimerOption) -> Unit, tint: Color) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(
            Icons.Default.Bedtime,
            contentDescription = stringResource(R.string.player_sleep_timer),
            tint = if (current != SleepTimerOption.OFF) MaterialTheme.colorScheme.primary else tint,
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        val labels = mapOf(
            SleepTimerOption.OFF to R.string.player_sleep_timer_off,
            SleepTimerOption.MIN_10 to R.string.player_sleep_timer_10,
            SleepTimerOption.MIN_30 to R.string.player_sleep_timer_30,
            SleepTimerOption.MIN_60 to R.string.player_sleep_timer_60,
            SleepTimerOption.END_OF_MEDIA to R.string.player_sleep_timer_end_of_media,
        )
        labels.forEach { (option, labelRes) ->
            DropdownMenuItem(
                text = { Text(stringResource(labelRes) + if (option == current) " ✓" else "") },
                onClick = { expanded = false; onSelected(option) },
            )
        }
    }
}

@Composable
private fun PipButton(videoAspectRatio: Float, tint: Color) {
    val view = LocalView.current
    val activity = view.context as? Activity
    IconButton(
        onClick = {
            activity?.enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(clampedPipRatio(videoAspectRatio)).build(),
            )
        },
    ) {
        Icon(Icons.Default.PictureInPictureAlt, contentDescription = stringResource(R.string.player_picture_in_picture), tint = tint)
    }
}

/** Android requires a PiP aspect ratio between 1:2.39 and 2.39:1; anything outside that range is rejected at the OS level. */
private fun clampedPipRatio(ratio: Float): Rational {
    val clamped = ratio.coerceIn(1f / 2.39f, 2.39f)
    return Rational((clamped * 1000).toInt(), 1000)
}

/** Never guesses a language — falls back to a plain index label when the source gave us no metadata. */
@Composable
private fun trackLabel(label: String?, languageCode: String?, index: Int): String =
    label ?: languageCode ?: stringResource(R.string.player_track_generic, index + 1)
