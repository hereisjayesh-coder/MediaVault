package com.mediavault.app.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.launch

/**
 * The app's only foreground/background signal — registered once on `ProcessLifecycleOwner` from
 * `MediaVaultApplication.onCreate()`. `ProcessLifecycleOwner` fires `ON_START`/`ON_STOP` for the
 * whole process (not per-Activity), which is exactly right for this single-Activity app, and
 * critically does NOT fire `ON_STOP` while Picture-in-Picture keeps the window visible — so
 * entering PiP never triggers the background-timeout path, matching "don't interrupt playback
 * that's still visibly running."
 */
@Singleton
class AppLockLifecycleObserver @Inject constructor(
    private val appLockManager: AppLockManager,
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        owner.lifecycleScope.launch { appLockManager.onAppForegrounded() }
    }

    override fun onStop(owner: LifecycleOwner) {
        owner.lifecycleScope.launch { appLockManager.onAppBackgrounded() }
    }
}
