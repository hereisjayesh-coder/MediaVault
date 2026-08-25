package com.mediavault.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.mediavault.app.R
import com.mediavault.app.ui.components.EmptyStateCard
import com.mediavault.app.ui.components.MediaVaultCard
import com.mediavault.app.ui.components.MediaVaultTopBar
import com.mediavault.app.ui.screens.home.formatDurationLabel
import com.mediavault.core.database.entity.MediaItemEntity
import com.mediavault.core.model.MediaType

/**
 * The Player *tab* landing screen — part of the normal five-tab layout (bottom nav stays
 * visible here). Shows what was last playing and lets the user reopen it; the actual immersive
 * playback experience only starts once they tap through to the dedicated `player/{id}` route.
 */
@Composable
fun PlayerHubScreen(
    viewModel: PlayerHubViewModel = hiltViewModel(),
    onOpenPlayer: (String) -> Unit = {},
    onOpenLibrary: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        MediaVaultTopBar(title = stringResource(R.string.player_hub_title))

        val item = uiState.item
        if (!uiState.isLoading && item == null) {
            EmptyStateCard(
                icon = Icons.Default.PlayArrow,
                title = stringResource(R.string.player_hub_empty_title),
                description = stringResource(R.string.player_hub_empty_body),
            )
            Button(onClick = onOpenLibrary) { Text(stringResource(R.string.player_hub_open_library)) }
        } else if (item != null) {
            ContinueWatchingCard(item = item, onClick = { onOpenPlayer(item.id) })
        }
    }
}

@Composable
private fun ContinueWatchingCard(item: MediaItemEntity, onClick: () -> Unit) {
    MediaVaultCard {
        Text(text = stringResource(R.string.player_hub_continue_watching), style = MaterialTheme.typography.labelLarge)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (item.thumbnailUrl != null) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = if (item.mediaType == MediaType.AUDIO) Icons.Default.Audiotrack else Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Text(text = item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)

        val durationMs = item.durationMs
        if (durationMs != null && durationMs > 0) {
            LinearProgressIndicator(
                progress = { (item.lastPlaybackPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            val remaining = ((durationMs - item.lastPlaybackPositionMs) / 1000).coerceAtLeast(0)
            Text(
                text = formatDurationLabel(remaining)?.let { "$it left" } ?: item.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.player_hub_resume))
        }
    }
}
