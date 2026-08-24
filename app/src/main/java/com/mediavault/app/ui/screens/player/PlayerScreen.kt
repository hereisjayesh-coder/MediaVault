package com.mediavault.app.ui.screens.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mediavault.app.R
import com.mediavault.app.ui.screens.common.PlaceholderScreen

@Composable
fun PlayerScreen() {
    PlaceholderScreen(
        title = stringResource(R.string.player_title),
        body = stringResource(R.string.player_placeholder_body),
    )
}
