package com.mediavault.app.ui.screens.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.util.Rational
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private const val DEFAULT_ASPECT_RATIO = 16f / 9f
private const val SEEK_FEEDBACK_DURATION_MS = 650L

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
    // Landscape/square content rotates the device to landscape on entering fullscreen, like
    // mainstream video apps; portrait content never forces a rotation (it already uses the
    // available height in whatever orientation the device is already in — see VideoArea).
    val ratio = uiState.playback?.videoAspectRatio ?: DEFAULT_ASPECT_RATIO
    ApplyLandscapeLock(uiState.isFullscreen && !isInPip && ratio >= 1f)

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
        onLongPressSpeedEngaged = viewModel::onLongPressSpeedEngaged,
        onLongPressSpeedReleased = viewModel::onLongPressSpeedReleased,
    )
}

/**
 * Locks the device to landscape while [lockToLandscape] is true, restoring free rotation the
 * moment it isn't — e.g. exiting fullscreen, or switching to a portrait item. Safe against
 * Activity recreation: `MainActivity` already declares `orientation` in `android:configChanges`
 * for Picture-in-Picture, so this never tears down/recreates the player session.
 */
@Composable
private fun ApplyLandscapeLock(lockToLandscape: Boolean) {
    val view = LocalView.current
    val activity = view.context as? Activity ?: return
    DisposableEffect(lockToLandscape) {
        activity.requestedOrientation = if (lockToLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
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
    onLongPressSpeedEngaged: () -> Unit,
    onLongPressSpeedReleased: () -> Unit,
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
                onSeekBy = onSeekBy,
                onLongPressSpeedEngaged = onLongPressSpeedEngaged,
                onLongPressSpeedReleased = onLongPressSpeedReleased,
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
        // The video itself stays true edge-to-edge; only these control surfaces are inset-safe
        // (statusBars/navigationBars/displayCutout) so buttons never sit under a notch, punch
        // hole, or gesture-nav area in either portrait or landscape.
        if (uiState.isFullscreen && !isInPip) {
            AnimatedVisibility(
                visible = uiState.controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Box(
                    Modifier.fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .systemBarsPadding()
                        .displayCutoutPadding(),
                ) {
                    PlayerTopBar(title = item.title, onBackToLibrary = onBackToLibrary, isOverlay = true)
                }
            }
            AnimatedVisibility(
                visible = uiState.controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomStart),
            ) {
                Box(
                    Modifier.fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .systemBarsPadding()
                        .displayCutoutPadding(),
                ) {
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
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

/** One resolved seek gesture to show transient feedback for; `nonce` forces a fresh auto-hide timer even if the same [deltaMs] fires twice in a row. */
private data class SeekFeedback(val deltaMs: Long, val nonce: Long)

/**
 * Sizes the video surface to its real aspect ratio instead of a fixed box — see the player
 * milestone's layout requirement. Landscape/square content shrinks to a compact, width-driven
 * box (no reserved empty space below it); portrait content and fullscreen both use whatever
 * height is actually available, centered, so a 9:16 source never gets squeezed into a 16:9 box.
 *
 * Also owns the player's gesture contract: a single tap anywhere only shows/hides the fullscreen
 * overlay controls — it never seeks. Double-tapping the left/right third seeks -10s/+10s, triple
 * seeks -30s/+30s (both with transient on-screen feedback), and holding anywhere plays at 2x
 * until released, restoring the exact speed from before. One `awaitEachGesture` per touch cycle
 * — never a separate tap/double-tap/long-press detector — so counting additional taps toward a
 * double/triple only ever happens after ruling out a long-press on the first touch, and a real
 * long-press can never also be misread as a stray tap underneath it. See [resolveTapAction] for
 * the actual (unit-tested) tap-count-and-zone decision table this only feeds timing/position into.
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
    onSeekBy: (Long) -> Unit,
    onLongPressSpeedEngaged: () -> Unit,
    onLongPressSpeedReleased: () -> Unit,
) {
    val useAvailableHeight = isFullscreen || isInPip || ratio < 1f
    var seekFeedback by remember { mutableStateOf<SeekFeedback?>(null) }
    var isSpeedBoosting by remember { mutableStateOf(false) }

    LaunchedEffect(seekFeedback?.nonce) {
        if (seekFeedback != null) {
            delay(SEEK_FEEDBACK_DURATION_MS)
            seekFeedback = null
        }
    }

    val gestureModifier = if (!isInPip) {
        // Unit key: these callbacks are stable method references bound to the same ViewModel
        // for this screen's whole lifetime, so there's nothing to re-key on — and re-keying on
        // the lambdas themselves would restart (and lose) any gesture already in flight every
        // time playback position recomposes this composable (every ~500ms while playing).
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val firstDown = awaitFirstDown(requireUnconsumed = false)
                val zone = tapZoneFor(firstDown.position.x, size.width.toFloat())
                val downTimeMs = System.currentTimeMillis()
                val longPressTimeoutMs = viewConfiguration.longPressTimeoutMillis
                // Races the up-event against the long-press threshold. `withTimeoutOrNull`
                // returning null is ambiguous by itself (timed out vs. waitForUpOrCancellation
                // legitimately returning null on a cancelled gesture) — elapsed wall-clock time
                // disambiguates the two below.
                val firstUp = withTimeoutOrNull(longPressTimeoutMs) { waitForUpOrCancellation() }
                val heldPastLongPressThreshold = firstUp == null && (System.currentTimeMillis() - downTimeMs) >= longPressTimeoutMs

                if (heldPastLongPressThreshold) {
                    isSpeedBoosting = true
                    onLongPressSpeedEngaged()
                    waitForUpOrCancellation()
                    isSpeedBoosting = false
                    onLongPressSpeedReleased()
                    return@awaitEachGesture
                }
                // Cancelled before either the up event or the long-press threshold (e.g. a second
                // pointer came down) — deliberately not treated as any gesture.
                if (firstUp == null) return@awaitEachGesture

                // A clean tap landed. Count how many more follow in the same third within the
                // platform's own double-tap window before committing, so a 2nd/3rd tap can
                // upgrade a plain toggle into a 10s/30s seek instead of acting the instant the
                // finger first lifts. Caps at 3 — nothing beyond triple-tap is defined. A next
                // tap landing in a *different* third, or held past the long-press threshold
                // itself, ends the count where it stands rather than trying to also start a
                // second overlapping gesture out of the same touch stream.
                var tapCount = 1
                val doubleTapTimeoutMs = viewConfiguration.doubleTapTimeoutMillis
                while (tapCount < 3) {
                    val nextDown = withTimeoutOrNull(doubleTapTimeoutMs) { awaitFirstDown(requireUnconsumed = false) } ?: break
                    if (tapZoneFor(nextDown.position.x, size.width.toFloat()) != zone) break
                    val nextUp = withTimeoutOrNull(longPressTimeoutMs) { waitForUpOrCancellation() } ?: break
                    tapCount++
                }

                when (val action = resolveTapAction(zone, tapCount)) {
                    PlayerTapAction.ToggleControls -> onSurfaceTapped()
                    is PlayerTapAction.SeekBy -> {
                        onSeekBy(action.deltaMs)
                        seekFeedback = SeekFeedback(action.deltaMs, System.nanoTime())
                    }
                }
            }
        }
    } else {
        Modifier
    }

    if (useAvailableHeight) {
        Box(
            modifier = Modifier.fillMaxWidth()
                .let { if (isFullscreen || isInPip) it.fillMaxSize() else it.weight(1f) }
                .background(Color.Black)
                .then(gestureModifier),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val (width, height) = fitWithinBounds(maxWidth, maxHeight, ratio)
                VideoSurface(resizeMode, isInPip, onAttachSurface, Modifier.width(width).height(height))
                if (isLoadingFrame) CircularProgressIndicator(color = Color.White)
            }
            SeekFeedbackOverlay(seekFeedback?.deltaMs)
            SpeedBoostOverlay(isSpeedBoosting)
        }
    } else {
        // Landscape/square, embedded: shrink to a compact, width-driven box — no space
        // reserved beyond what the video itself actually occupies. Black is scoped to this
        // exact box, never bleeding past the video's own bounds into the rest of the screen.
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(ratio).background(Color.Black).then(gestureModifier),
            contentAlignment = Alignment.Center,
        ) {
            VideoSurface(resizeMode, isInPip, onAttachSurface, Modifier.fillMaxSize())
            if (isLoadingFrame) CircularProgressIndicator(color = Color.White)
            SeekFeedbackOverlay(seekFeedback?.deltaMs)
            SpeedBoostOverlay(isSpeedBoosting)
        }
    }
}

/** [deltaMs] is signed — negative shows a "back" bubble on the left, positive a "forward" bubble on the right. Same Replay10/Forward10 icons for both the 10s and 30s cases (there's no bundled "30" variant of these icons) — the templated text label carries the actual magnitude. */
@Composable
private fun BoxScope.SeekFeedbackOverlay(deltaMs: Long?) {
    val isBack = (deltaMs ?: 0L) < 0L
    AnimatedVisibility(
        visible = deltaMs != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(if (isBack) Alignment.CenterStart else Alignment.CenterEnd),
    ) {
        Surface(
            modifier = Modifier.padding(32.dp),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.6f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = if (isBack) Icons.Default.Replay10 else Icons.Default.Forward10,
                    contentDescription = null,
                    tint = Color.White,
                )
                val seconds = kotlin.math.abs(deltaMs ?: 0L) / 1000
                Text(
                    text = stringResource(if (isBack) R.string.player_seek_feedback_back else R.string.player_seek_feedback_forward, seconds),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.SpeedBoostOverlay(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
    ) {
        Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.6f)) {
            Text(
                text = stringResource(R.string.player_speed_boost_active),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
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
        factory = { context ->
            // Plain default (SurfaceView) construction. A prior attempt at forcing
            // surface_type="texture_view" via a themed ContextThemeWrapper (to fix a cosmetic
            // pop during the Library<->Player nav transition) turned out to silently break real
            // frame rendering instead — verified live on a Pixel 7a: the video area rendered
            // solid black in both embedded and fullscreen layouts, on multiple different videos
            // (including a colorful non-black source), while playback/audio/position continued
            // normally underneath. Correct video rendering matters far more than that cosmetic
            // pop, so this reverts to the known-working default rather than trying to patch the
            // TextureView path further. See PROJECT_MASTER.md's decision log for the full story.
            PlayerView(context).apply { useController = false }
        },
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
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
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

/**
 * One selectable row shared by every player popup menu: label on the left, a trailing check
 * icon (not appended text) when selected, consistent padding/typography throughout.
 */
@Composable
private fun MenuCheckItem(text: String, isSelected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        trailingIcon = if (isSelected) {
            { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
        onClick = onClick,
    )
}

/**
 * A trigger [IconButton] plus its [DropdownMenu], wrapped together in one [Box] — the anchor
 * `DropdownMenu`/`Popup` positions itself against is the composable that hosts it, so trigger
 * and menu must share one parent for the popup to open next to the actual button pressed
 * rather than at a fixed screen corner. `DropdownMenu` already animates in/out (fade + scale)
 * and already clamps itself inside the screen bounds — both built into Material3, not added here.
 */
@Composable
private fun PopupMenuButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    menuContent: @Composable ColumnScope.(collapse: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(icon, contentDescription = contentDescription, tint = tint)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            menuContent { expanded = false }
        }
    }
}

@Composable
private fun SpeedMenu(currentSpeed: Float, onSpeedSelected: (Float) -> Unit, tint: Color) {
    PopupMenuButton(Icons.Default.Speed, stringResource(R.string.player_speed), tint) { collapse ->
        PLAYBACK_SPEEDS.forEach { speed ->
            MenuCheckItem(
                text = "${speed}x",
                isSelected = speed == currentSpeed,
                onClick = { collapse(); onSpeedSelected(speed) },
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
    PopupMenuButton(Icons.Default.Audiotrack, stringResource(R.string.player_audio_track), tint) { collapse ->
        tracks.forEachIndexed { index, track ->
            MenuCheckItem(
                text = trackLabel(track.label, track.languageCode, index),
                isSelected = track.id == selectedTrackId,
                onClick = { collapse(); onTrackSelected(track.id) },
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
    PopupMenuButton(Icons.Default.ClosedCaption, stringResource(R.string.player_subtitles), tint) { collapse ->
        MenuCheckItem(
            text = stringResource(R.string.player_subtitles_off),
            isSelected = selectedTrackId == null,
            onClick = { collapse(); onTrackSelected(null) },
        )
        tracks.forEachIndexed { index, track ->
            MenuCheckItem(
                text = trackLabel(track.label, track.languageCode, index),
                isSelected = track.id == selectedTrackId,
                onClick = { collapse(); onTrackSelected(track.id) },
            )
        }
    }
}

@Composable
private fun ResizeModeMenu(current: VideoResizeMode, onSelected: (VideoResizeMode) -> Unit, tint: Color) {
    PopupMenuButton(Icons.Default.AspectRatio, stringResource(R.string.player_aspect_ratio), tint) { collapse ->
        val labels = mapOf(
            VideoResizeMode.FIT to R.string.player_resize_fit,
            VideoResizeMode.FILL to R.string.player_resize_fill,
            VideoResizeMode.ZOOM to R.string.player_resize_zoom,
            VideoResizeMode.ORIGINAL to R.string.player_resize_original,
        )
        labels.forEach { (mode, labelRes) ->
            MenuCheckItem(
                text = stringResource(labelRes),
                isSelected = mode == current,
                onClick = { collapse(); onSelected(mode) },
            )
        }
    }
}

@Composable
private fun SleepTimerMenu(current: SleepTimerOption, onSelected: (SleepTimerOption) -> Unit, tint: Color) {
    val activeTint = if (current != SleepTimerOption.OFF) MaterialTheme.colorScheme.primary else tint
    PopupMenuButton(Icons.Default.Bedtime, stringResource(R.string.player_sleep_timer), activeTint) { collapse ->
        val labels = mapOf(
            SleepTimerOption.OFF to R.string.player_sleep_timer_off,
            SleepTimerOption.MIN_15 to R.string.player_sleep_timer_15,
            SleepTimerOption.MIN_30 to R.string.player_sleep_timer_30,
            SleepTimerOption.MIN_60 to R.string.player_sleep_timer_60,
            SleepTimerOption.END_OF_MEDIA to R.string.player_sleep_timer_end_of_media,
        )
        labels.forEach { (option, labelRes) ->
            MenuCheckItem(
                text = stringResource(labelRes),
                isSelected = option == current,
                onClick = { collapse(); onSelected(option) },
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
