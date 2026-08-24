package com.mediavault.app.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mediavault.app.R
import com.mediavault.app.ui.screens.common.PlaceholderScreen

@Composable
fun HomeScreen() {
    PlaceholderScreen(
        title = stringResource(R.string.home_title),
        body = stringResource(R.string.home_placeholder_body),
    )
}
