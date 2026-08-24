package com.mediavault.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mediavault.app.navigation.MediaVaultNavHost
import com.mediavault.app.ui.theme.MediaVaultTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediaVaultTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MediaVaultNavHost()
                }
            }
        }
    }
}
