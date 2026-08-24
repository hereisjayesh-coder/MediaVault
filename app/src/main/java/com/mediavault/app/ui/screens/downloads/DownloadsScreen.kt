package com.mediavault.app.ui.screens.downloads

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mediavault.app.R
import com.mediavault.app.ui.screens.common.PlaceholderScreen

@Composable
fun DownloadsScreen() {
    PlaceholderScreen(
        title = stringResource(R.string.downloads_title),
        icon = Icons.Default.Download,
        body = stringResource(R.string.downloads_placeholder_body),
    )
}
