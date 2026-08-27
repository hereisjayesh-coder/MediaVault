package com.mediavault.app

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.mediavault.app.download.MediaVaultDownloadEngine
import com.mediavault.app.security.AppLockLifecycleObserver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MediaVaultApplication : Application() {

    @Inject
    lateinit var downloadEngine: MediaVaultDownloadEngine

    @Inject
    lateinit var appLockLifecycleObserver: AppLockLifecycleObserver

    override fun onCreate() {
        super.onCreate()
        downloadEngine.recoverAfterProcessDeath()
        // The app's only foreground/background signal — see AppLockLifecycleObserver's KDoc for
        // why ProcessLifecycleOwner (not a per-Activity callback) is the right source for it.
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLockLifecycleObserver)
    }
}
