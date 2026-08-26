package com.mediavault.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mediavault.app.R
import com.mediavault.app.ui.components.EmptyStateCard
import com.mediavault.app.ui.components.MediaThumbnail
import com.mediavault.app.ui.components.MediaVaultTopBar
import com.mediavault.app.ui.components.SectionLabel
import com.mediavault.app.ui.screens.home.formatDurationLabel
import com.mediavault.core.database.entity.MediaItemEntity

/**
 * The Player *tab* landing screen — part of the normal five-tab layout (bottom nav stays
 * visible here). A real, multi-item watch-history experience: everything currently in progress
 * under Continue Watching, everything else ever played under Recently Watched, both ordered
 * most-recent-first. Tapping any row opens the actual immersive playback experience at the
 * dedicated `player/{id}` route.
 */
@Composable
fun PlayerHubScreen(
    viewModel: PlayerHubViewModel = hiltViewModel(),
    onOpenPlayer: (String) -> Unit = {},
    onOpenLibrary: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    PlayerHubScreenContent(uiState = uiState, onOpenPlayer = onOpenPlayer, onOpenLibrary = onOpenLibrary)
}

@Composable
private fun PlayerHubScreenContent(
    uiState: PlayerHubUiState,
    onOpenPlayer: (String) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MediaVaultTopBar(title = stringResource(R.string.player_hub_title)) }

        when {
            // A real loading placeholder instead of a blank gap — this is what used to make an
            // empty Column suddenly pop in a Card once state arrived, right after the nav
            // transition, reading as an inconsistent "something appeared out of nowhere" glitch.
            uiState.isLoading -> item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.isEmpty -> {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.PlayArrow,
                        title = stringResource(R.string.player_hub_empty_title),
                        description = stringResource(R.string.player_hub_empty_body),
                    )
                }
                item { Button(onClick = onOpenLibrary) { Text(stringResource(R.string.player_hub_open_library)) } }
            }

            else -> {
                if (uiState.continueWatching.isNotEmpty()) {
                    item { SectionLabel(text = stringResource(R.string.player_hub_continue_watching)) }
                    items(uiState.continueWatching, key = { "continue-${it.id}" }) { item ->
                        WatchHistoryCard(item = item, onClick = { onOpenPlayer(item.id) })
                    }
                }
                if (uiState.recentlyWatched.isNotEmpty()) {
                    item { SectionLabel(text = stringResource(R.string.player_hub_recently_watched)) }
                    items(uiState.recentlyWatched, key = { "recent-${it.id}" }) { item ->
                        WatchHistoryCard(item = item, onClick = { onOpenPlayer(item.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchHistoryCard(item: MediaItemEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                MediaThumbnail(thumbnailUrl = item.thumbnailUrl, mediaType = item.mediaType, width = 96.dp)
                val durationMs = item.durationMs
                if (durationMs != null && durationMs > 0) {
                    LinearProgressIndicator(
                        progress = { (item.lastPlaybackPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                Text(
                    text = watchHistorySubtitle(item),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.player_hub_resume),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private const val FINISHED_FRACTION = 0.95f

/** "12m left" while in progress, the full duration once finished — never a negative/zero-second remainder from a stale position past the real end. */
@Composable
private fun watchHistorySubtitle(item: MediaItemEntity): String {
    val durationMs = item.durationMs
    if (durationMs == null || durationMs <= 0) return ""
    val isFinished = item.lastPlaybackPositionMs >= durationMs * FINISHED_FRACTION
    return if (isFinished) {
        formatDurationLabel(durationMs / 1000) ?: ""
    } else {
        val remainingSeconds = ((durationMs - item.lastPlaybackPositionMs) / 1000).coerceAtLeast(0)
        formatDurationLabel(remainingSeconds)?.let { stringResource(R.string.player_hub_time_left, it) } ?: ""
    }
}
