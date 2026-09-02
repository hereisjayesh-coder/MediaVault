package com.mediavault.app

import android.app.Application
import com.mediavault.app.download.MediaVaultDownloadEngine
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MediaVaultApplication : Application() {

    @Inject
    lateinit var downloadEngine: MediaVaultDownloadEngine

    override fun onCreate() {
        super.onCreate()
        downloadEngine.recoverAfterProcessDeath()
        // AppLockLifecycleObserver is registered on MainActivity's own Lifecycle instead — see its
        // KDoc for why.
    }
}
