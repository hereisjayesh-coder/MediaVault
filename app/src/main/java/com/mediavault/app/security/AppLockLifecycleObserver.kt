package com.mediavault.app.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.launch

/**
 * The app's only foreground/background signal — registered once on `MainActivity`'s own
 * `Lifecycle` from its `onCreate()`. This app is single-Activity and declares `configChanges` for
 * rotation/multi-window/screen-size in the manifest, so `MainActivity` is never torn down and
 * recreated by those transitions — its own `ON_START`/`ON_STOP` are exactly as reliable a
 * whole-app foreground/background signal as `ProcessLifecycleOwner`'s would be, and critically
 * still does NOT fire `ON_STOP` while Picture-in-Picture keeps the window visible (PiP only pauses
 * the Activity, it doesn't stop it) — so entering PiP never triggers the background-timeout path,
 * matching "don't interrupt playback that's still visibly running."
 *
 * Deliberately *not* `ProcessLifecycleOwner`: it collapses a fast enough background/foreground
 * round trip (e.g. a quick recents-switcher flick, under its own ~700ms grace window) into no
 * `ON_STOP`/`ON_START` dispatch at all, on the theory that the process was never really
 * backgrounded — which silently skips this observer entirely and leaves the app unlocked through
 * that round trip even with "Lock after: Immediately" selected. `MainActivity`'s own `Lifecycle`
 * has no such debounce: every real backgrounding is reported, every time.
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
