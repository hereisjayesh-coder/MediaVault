package com.mediavault.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `LIGHT always resolves to not dark, regardless of system setting`() {
        assertFalse(ThemeMode.LIGHT.resolveIsDark(systemInDark = true))
        assertFalse(ThemeMode.LIGHT.resolveIsDark(systemInDark = false))
    }

    @Test
    fun `DARK always resolves to dark, regardless of system setting`() {
        assertTrue(ThemeMode.DARK.resolveIsDark(systemInDark = true))
        assertTrue(ThemeMode.DARK.resolveIsDark(systemInDark = false))
    }

    @Test
    fun `SYSTEM follows whatever the system setting is`() {
        assertTrue(ThemeMode.SYSTEM.resolveIsDark(systemInDark = true))
        assertFalse(ThemeMode.SYSTEM.resolveIsDark(systemInDark = false))
    }

    @Test
    fun `every enum entry has a name that round-trips through valueOf`() {
        ThemeMode.entries.forEach { mode -> assertEquals(mode, ThemeMode.valueOf(mode.name)) }
    }
}
