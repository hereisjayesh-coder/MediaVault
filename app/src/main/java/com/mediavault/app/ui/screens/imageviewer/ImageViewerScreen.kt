package com.mediavault.app.ui.screens.imageviewer

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.mediavault.app.R
import com.mediavault.app.library.mimeTypeFor
import com.mediavault.core.database.entity.MediaItemEntity

/**
 * A deliberately small, dedicated preview for a downloaded image — never the video/audio
 * player (`PlayerScreen`/`PlayerViewModel`/`PlayerEngine` have no concept of a static image,
 * and nothing here reuses or touches them). Full-bleed image, title, back, and Share — see
 * `ImageViewerViewModel`'s own KDoc for why rename/delete/save-to-device aren't duplicated here.
 */
@Composable
fun ImageViewerScreen(
    viewModel: ImageViewerViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    ImageViewerContent(
        uiState = uiState,
        onBack = onBack,
        onShare = { item ->
            val uri = viewModel.shareUriFor(item) ?: return@ImageViewerContent
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeTypeFor(item)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(Intent.createChooser(intent, item.title)) }
        },
    )
}

@Composable
private fun ImageViewerContent(
    uiState: ImageViewerUiState,
    onBack: () -> Unit,
    onShare: (MediaItemEntity) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val item = uiState.item
        if (item != null) {
            AsyncImage(
                model = item.mediaUri,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        when {
            uiState.isLoading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )

            uiState.notFound -> Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.BrokenImage, contentDescription = null, tint = Color.White)
                Text(
                    text = stringResource(R.string.image_viewer_not_found),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.image_viewer_back), tint = Color.White)
            }
            Text(
                text = item?.title.orEmpty(),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (item != null) {
                IconButton(onClick = { onShare(item) }) {
                    Icon(Icons.Default.Share, contentDescription = stringResource(R.string.image_viewer_share), tint = Color.White)
                }
            }
        }
    }
}
