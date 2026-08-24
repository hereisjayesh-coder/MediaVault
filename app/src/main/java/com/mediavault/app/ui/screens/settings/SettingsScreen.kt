package com.mediavault.app.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mediavault.app.R
import com.mediavault.app.ui.screens.common.PlaceholderScreen

@Composable
fun SettingsScreen() {
    PlaceholderScreen(
        title = stringResource(R.string.settings_title),
        icon = Icons.Default.Settings,
        body = stringResource(R.string.settings_placeholder_body),
    )
}
