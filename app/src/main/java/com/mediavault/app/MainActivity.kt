package com.mediavault.app

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.hilt.navigation.compose.hiltViewModel
import com.mediavault.app.navigation.MediaVaultNavHost
import com.mediavault.app.settings.ThemeStore
import com.mediavault.app.settings.ThemeViewModel
import com.mediavault.app.settings.resolveIsDark
import com.mediavault.app.ui.screens.player.LocalIsInPictureInPicture
import com.mediavault.app.ui.theme.BackgroundDark
import com.mediavault.app.ui.theme.BackgroundLight
import com.mediavault.app.ui.theme.MediaVaultTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var themeStore: ThemeStore

    private val isInPictureInPicture = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Resolves the persisted theme synchronously (a single small local-disk read, same
        // pattern as other DataStore-backed stores in this app) so the very first frame — the
        // window background and system bar icon color, both set before setContent below — never
        // flashes the wrong theme while waiting for an async read to complete. `values-night/
        // themes.xml` already covers the pre-Kotlin native frame for the common "follow system"
        // case; this covers the moment right after that for every case, including an explicit
        // Light/Dark override that disagrees with the system setting.
        val systemInDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val startupIsDark = runBlocking { themeStore.currentThemeMode() }.resolveIsDark(systemInDark)
        applyWindowChrome(startupIsDark)

        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeMode by themeViewModel.themeMode.collectAsState()
            val isDark = themeMode.resolveIsDark(isSystemInDarkTheme())

            // Re-applies whenever the resolved theme changes at runtime (the user switches it in
            // Settings, or — in SYSTEM mode — the OS theme changes) so the system bar icon color
            // never goes stale relative to the app's own background, not just at cold start.
            LaunchedEffect(isDark) { applyWindowChrome(isDark) }

            MediaVaultTheme(themeMode = themeMode) {
                CompositionLocalProvider(LocalIsInPictureInPicture provides isInPictureInPicture.value) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        MediaVaultNavHost()
                    }
                }
            }
        }
    }

    private fun applyWindowChrome(isDark: Boolean) {
        window.setBackgroundDrawable(ColorDrawable(if (isDark) BackgroundDark.toArgb() else BackgroundLight.toArgb()))
        enableEdgeToEdge(
            statusBarStyle = if (isDark) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            },
            navigationBarStyle = if (isDark) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            },
        )
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPictureInPicture.value = isInPictureInPictureMode
    }
}
