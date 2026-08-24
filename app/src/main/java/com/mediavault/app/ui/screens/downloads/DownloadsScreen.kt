package com.mediavault.app.ui.screens.downloads

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mediavault.app.R
import com.mediavault.app.ui.screens.common.PlaceholderScreen

@Composable
fun DownloadsScreen() {
    PlaceholderScreen(
        title = stringResource(R.string.downloads_title),
        body = stringResource(R.string.downloads_placeholder_body),
    )
}
