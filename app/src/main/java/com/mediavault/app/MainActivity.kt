package com.mediavault.app

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.mediavault.app.navigation.MediaVaultNavHost
import com.mediavault.app.ui.screens.player.LocalIsInPictureInPicture
import com.mediavault.app.ui.theme.MediaVaultTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val isInPictureInPicture = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // MediaVault only ships a light theme, so status/nav bar icons must stay dark
        // regardless of the system's own dark/light setting.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            MediaVaultTheme {
                CompositionLocalProvider(LocalIsInPictureInPicture provides isInPictureInPicture.value) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        MediaVaultNavHost()
                    }
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPictureInPicture.value = isInPictureInPictureMode
    }
}
