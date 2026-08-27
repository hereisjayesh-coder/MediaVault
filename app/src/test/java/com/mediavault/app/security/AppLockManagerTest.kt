package com.mediavault.app.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockManagerTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts locked on launch when app lock is enabled`() {
        val manager = AppLockManager(FakeAppLockSettingsStore(AppLockSettings(appLockEnabled = true)))

        manager.initializeBlocking()

        assertTrue(manager.isLocked.value)
    }

    @Test
    fun `starts unlocked on launch when app lock is disabled`() {
        val manager = AppLockManager(FakeAppLockSettingsStore(AppLockSettings(appLockEnabled = false)))

        manager.initializeBlocking()

        assertFalse(manager.isLocked.value)
    }

    @Test
    fun `foregrounding immediately after backgrounding relocks when the timeout is immediate`() = runTest {
        val manager = AppLockManager(
            FakeAppLockSettingsStore(AppLockSettings(appLockEnabled = true, autoLockTimeout = AutoLockTimeout.IMMEDIATE)),
        )
        manager.unlock() // simulate an already-unlocked session

        manager.onAppBackgrounded()
        manager.onAppForegrounded()

        assertTrue(manager.isLocked.value)
    }

    @Test
    fun `foregrounding immediately after backgrounding stays unlocked when the timeout has not elapsed`() = runTest {
        val manager = AppLockManager(
            FakeAppLockSettingsStore(AppLockSettings(appLockEnabled = true, autoLockTimeout = AutoLockTimeout.FIVE_MINUTES)),
        )
        manager.unlock()

        manager.onAppBackgrounded()
        manager.onAppForegrounded()

        assertFalse(manager.isLocked.value)
    }

    @Test
    fun `background timeout is a no-op when app lock is disabled`() = runTest {
        val manager = AppLockManager(
            FakeAppLockSettingsStore(AppLockSettings(appLockEnabled = false, autoLockTimeout = AutoLockTimeout.IMMEDIATE)),
        )

        manager.onAppBackgrounded()
        manager.onAppForegrounded()

        assertFalse(manager.isLocked.value)
    }

    @Test
    fun `unlock clears both the locked state and any lockout`() {
        val manager = AppLockManager(FakeAppLockSettingsStore(AppLockSettings(appLockEnabled = true)))
        manager.initializeBlocking()
        repeat(5) { manager.recordFailedAttempt() }

        manager.unlock()

        assertFalse(manager.isLocked.value)
        assertEquals(0, manager.lockoutState.value.failedAttempts)
        assertEquals(0L, manager.remainingLockoutSeconds())
    }

    @Test
    fun `five consecutive failed attempts trigger a lockout, fewer do not`() {
        val manager = AppLockManager(FakeAppLockSettingsStore(AppLockSettings(appLockEnabled = true)))

        repeat(4) { manager.recordFailedAttempt() }
        assertEquals(0L, manager.remainingLockoutSeconds())

        manager.recordFailedAttempt()
        assertTrue(manager.remainingLockoutSeconds() > 0)
    }

    @Test
    fun `lockNow locks regardless of settings, for tests and manual lock alike`() {
        val manager = AppLockManager(FakeAppLockSettingsStore(AppLockSettings(appLockEnabled = true)))

        manager.lockNow()

        assertTrue(manager.isLocked.value)
    }
}
