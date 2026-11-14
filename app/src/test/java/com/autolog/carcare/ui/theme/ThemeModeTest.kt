package com.autolog.carcare.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    @Test
    fun isDarkThemePreset_onlyTrueForExplicitDarkThemes() {
        assertFalse(ThemeMode.SYSTEM.isDarkThemePreset)
        assertFalse(ThemeMode.LIGHT.isDarkThemePreset)

        assertTrue(ThemeMode.DARK.isDarkThemePreset)
        assertTrue(ThemeMode.DARK_AMOLED.isDarkThemePreset)
        assertTrue(ThemeMode.DARK_CARBON_RED.isDarkThemePreset)
        assertTrue(ThemeMode.DARK_EMERALD.isDarkThemePreset)
        assertTrue(ThemeMode.DARK_AMBER.isDarkThemePreset)
    }

    @Test
    fun themeModeCycle_visitsAllPresetsAndWrapsAround() {
        val all = ThemeMode.entries
        assertEquals(7, all.size)

        var idx = 0
        val visited = mutableListOf<ThemeMode>()
        repeat(all.size) {
            visited += all[idx]
            idx = (idx + 1) % all.size
        }

        assertEquals(all, visited)
        assertEquals(ThemeMode.SYSTEM, all[idx])
    }
}
