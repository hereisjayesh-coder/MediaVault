package com.mediavault.app

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mediavault.app.navigation.MediaVaultNavHost
import com.mediavault.app.security.AppLockLifecycleObserver
import com.mediavault.app.security.AppLockManager
import com.mediavault.app.security.AppLockSettingsStore
import com.mediavault.app.settings.ThemeStore
import com.mediavault.app.settings.ThemeViewModel
import com.mediavault.app.settings.resolveIsDark
import com.mediavault.app.ui.screens.lock.AppLockScreen
import com.mediavault.app.ui.screens.player.LocalIsInPictureInPicture
import com.mediavault.app.ui.theme.BackgroundDark
import com.mediavault.app.ui.theme.BackgroundLight
import com.mediavault.app.ui.theme.MediaVaultTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var themeStore: ThemeStore
    @Inject lateinit var appLockManager: AppLockManager
    @Inject lateinit var appLockSettingsStore: AppLockSettingsStore
    @Inject lateinit var appLockLifecycleObserver: AppLockLifecycleObserver

    private val isInPictureInPicture = mutableStateOf(false)

    // POST_NOTIFICATIONS is a runtime permission on API 33+ (Android 13/Tiramisu) — a manifest
    // declaration alone leaves it ungranted, and the download-progress notification simply never
    // shows (silently, no crash: the foreground service and downloads still run correctly, only
    // the user-visible progress notification is suppressed by the OS) unless this is requested.
    // Below API 33 the permission is granted at install time and this launcher is just never
    // triggered. A denial is not re-prompted here — Android itself only allows one system prompt
    // per install unless the user later grants it manually from system settings, and downloads
    // remaining fully functional without it makes forcing the issue unnecessary.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

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
        // Same "decide before first frame" pattern as the theme read above — whether App Lock is
        // enabled decides whether the very first Compose frame already renders locked.
        appLockManager.initializeBlocking()
        // See AppLockLifecycleObserver's KDoc for why this Activity's own Lifecycle (not
        // ProcessLifecycleOwner) is the correct, debounce-free source for lock/unlock signals.
        lifecycle.addObserver(appLockLifecycleObserver)

        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeMode by themeViewModel.themeMode.collectAsState()
            val isDark = themeMode.resolveIsDark(isSystemInDarkTheme())

            // Re-applies whenever the resolved theme changes at runtime (the user switches it in
            // Settings, or — in SYSTEM mode — the OS theme changes) so the system bar icon color
            // never goes stale relative to the app's own background, not just at cold start.
            LaunchedEffect(isDark) { applyWindowChrome(isDark) }

            val appLockSettings by appLockSettingsStore.settings.collectAsStateWithLifecycle(initialValue = null)
            // FLAG_SECURE is tied to the App Lock toggle itself (not just to isLocked) — Library/
            // Downloads/Player show private media whenever App Lock is on, whether or not the
            // screen happens to be locked right now, so screenshot/recents protection applies to
            // the same "I've opted into privacy" scope as the lock feature, not narrower.
            LaunchedEffect(appLockSettings?.appLockEnabled) {
                val enabled = appLockSettings?.appLockEnabled ?: return@LaunchedEffect
                if (enabled) {
                    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            val isLocked by appLockManager.isLocked.collectAsStateWithLifecycle()

            MediaVaultTheme(themeMode = themeMode) {
                CompositionLocalProvider(LocalIsInPictureInPicture provides isInPictureInPicture.value) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            MediaVaultNavHost()
                            // Kept as a sibling overlay (not a replacement for the NavHost) so
                            // Library/Player/etc. stay composed underneath while locked — Player
                            // in particular must not be disposed here, since its own onDispose
                            // path pauses playback in a way that would break resume-after-unlock.
                            // PlayerViewModel separately pauses playback the moment isLocked
                            // becomes true, so nothing plays silently behind this opaque screen.
                            if (isLocked) {
                                AppLockScreen(biometricEnabledSetting = appLockSettings?.biometricEnabled ?: false)
                            }
                        }
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
