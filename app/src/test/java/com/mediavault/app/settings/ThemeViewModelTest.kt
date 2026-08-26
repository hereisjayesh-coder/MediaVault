package com.mediavault.app.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var fakeStore: FakeThemeStore
    private lateinit var viewModel: ThemeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        fakeStore = FakeThemeStore(initial = ThemeMode.SYSTEM)
        viewModel = ThemeViewModel(fakeStore)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts with the store's persisted value, not just the ViewModel's own default`() = runTest {
        val storeWithDarkAlreadyPersisted = FakeThemeStore(initial = ThemeMode.DARK)
        val freshViewModel = ThemeViewModel(storeWithDarkAlreadyPersisted)

        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ThemeMode.DARK, freshViewModel.themeMode.value)
    }

    @Test
    fun `setting a theme mode persists it to the store and updates the exposed state`() = runTest {
        viewModel.setThemeMode(ThemeMode.DARK)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)
        assertEquals(ThemeMode.DARK, fakeStore.currentThemeMode())
    }

    @Test
    fun `reflects a change made through another instance sharing the same store`() = runTest {
        dispatcher.scheduler.advanceUntilIdle()

        fakeStore.setThemeMode(ThemeMode.LIGHT)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ThemeMode.LIGHT, viewModel.themeMode.value)
    }
}
