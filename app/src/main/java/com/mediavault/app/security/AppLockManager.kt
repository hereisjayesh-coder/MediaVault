package com.mediavault.app.security

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking

/**
 * The single, centralized source of truth for whether MediaVault is currently locked — every
 * other piece of App Lock (the lock screen, [com.mediavault.app.ui.screens.player.PlayerViewModel]'s
 * pause-on-lock, [com.mediavault.app.MainActivity]'s overlay) reads [isLocked] rather than
 * deriving its own notion of lock state. Deliberately holds no Android lifecycle/Compose
 * dependency itself — [com.mediavault.app.security.AppLockLifecycleObserver] is the only caller
 * of [onAppForegrounded]/[onAppBackgrounded], keeping this class a plain, directly-unit-testable
 * state machine.
 */
@Singleton
class AppLockManager @Inject constructor(
    private val settingsStore: AppLockSettingsStore,
) {
    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _lockoutState = MutableStateFlow(LockoutState())
    val lockoutState: StateFlow<LockoutState> = _lockoutState.asStateFlow()

    /** Set on background, cleared on the next foreground check — null means "not currently backgrounded." */
    private var backgroundedAtMs: Long? = null

    /**
     * Called once from `MainActivity.onCreate()`, mirroring the app's existing `runBlocking`
     * synchronous-startup-read pattern (see `MainActivity`'s theme resolution) — decides whether
     * the very first Compose frame should already render locked, satisfying "lock on app launch."
     * A fresh process always re-runs this (nothing survives process death to skip it), which is
     * what makes process recreation naturally correct: there is no persisted "already unlocked"
     * flag that could leak across a process restart.
     */
    fun initializeBlocking() {
        val settings = runBlocking { settingsStore.currentSettings() }
        if (settings.appLockEnabled) _isLocked.value = true
    }

    /** Call from a foreground lifecycle signal (e.g. `ProcessLifecycleOwner` `ON_START`). */
    suspend fun onAppForegrounded() {
        val backgroundedAt = backgroundedAtMs
        backgroundedAtMs = null
        val settings = settingsStore.currentSettings()
        if (!settings.appLockEnabled) return
        // First-ever foreground of this process: initializeBlocking() already made the correct
        // launch decision, so there's nothing to re-derive from a background timestamp here.
        if (backgroundedAt == null) return
        val elapsedSeconds = (System.currentTimeMillis() - backgroundedAt) / 1000
        if (elapsedSeconds >= settings.autoLockTimeout.seconds) {
            _isLocked.value = true
        }
    }

    /** Call from a background lifecycle signal (e.g. `ProcessLifecycleOwner` `ON_STOP`). */
    suspend fun onAppBackgrounded() {
        if (!settingsStore.currentSettings().appLockEnabled) return
        backgroundedAtMs = System.currentTimeMillis()
    }

    fun unlock() {
        _isLocked.value = false
        _lockoutState.value = LockoutState()
    }

    /** Immediate manual lock — not currently wired to any UI action, but keeps the state machine's surface complete and is what tests use to simulate "just became locked." */
    fun lockNow() {
        _isLocked.value = true
    }

    /** Simple fixed-window rate limit: after [MAX_ATTEMPTS_BEFORE_LOCKOUT] consecutive wrong PINs, block further attempts for [LOCKOUT_DURATION_MS]. Resets on [unlock]. */
    fun recordFailedAttempt() {
        val current = _lockoutState.value
        val attempts = current.failedAttempts + 1
        val lockedUntil = if (attempts >= MAX_ATTEMPTS_BEFORE_LOCKOUT) {
            System.currentTimeMillis() + LOCKOUT_DURATION_MS
        } else {
            current.lockedUntilEpochMs
        }
        _lockoutState.value = LockoutState(failedAttempts = attempts, lockedUntilEpochMs = lockedUntil)
    }

    fun remainingLockoutSeconds(): Long {
        val until = _lockoutState.value.lockedUntilEpochMs ?: return 0L
        return ((until - System.currentTimeMillis()) / 1000).coerceAtLeast(0L)
    }

    data class LockoutState(val failedAttempts: Int = 0, val lockedUntilEpochMs: Long? = null)

    private companion object {
        const val MAX_ATTEMPTS_BEFORE_LOCKOUT = 5
        const val LOCKOUT_DURATION_MS = 30_000L
    }
}
