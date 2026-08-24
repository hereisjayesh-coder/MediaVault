package com.mediavault.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import com.mediavault.app.R

enum class MediaVaultDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    HOME("home", R.string.nav_home, Icons.Default.Home),
    DOWNLOADS("downloads", R.string.nav_downloads, Icons.Default.Download),
    LIBRARY("library", R.string.nav_library, Icons.Default.VideoLibrary),
    PLAYER("player", R.string.nav_player, Icons.Default.PlayArrow),
    SETTINGS("settings", R.string.nav_settings, Icons.Default.Settings),
}
