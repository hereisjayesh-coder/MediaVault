package com.mediavault.app.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mediavault.app.R
import com.mediavault.app.ui.screens.common.PlaceholderScreen

@Composable
fun LibraryScreen() {
    PlaceholderScreen(
        title = stringResource(R.string.library_title),
        body = stringResource(R.string.library_placeholder_body),
    )
}
