package com.autolog.carcare.ui.theme

import androidx.compose.ui.graphics.Color

enum class ThemeMode(
    val title: String,
    val subtitle: String,
    val previewBg: Color,
    val previewSurface: Color,
    val previewAccent: Color
) {
    SYSTEM(
        title = "System Default",
        subtitle = "Follows device light/dark settings",
        previewBg = Color(0xFF0B1120),
        previewSurface = Color(0xFF1E293B),
        previewAccent = Color(0xFF60A5FA)
    ),
    LIGHT(
        title = "Daylight Clean",
        subtitle = "Crisp, high-visibility light theme",
        previewBg = Color(0xFFF8FAFC),
        previewSurface = Color(0xFFFFFFFF),
        previewAccent = Color(0xFF1E3A8A)
    ),
    DARK(
        title = "Midnight Slate (Dark)",
        subtitle = "Deep midnight navy & electric blue",
        previewBg = Color(0xFF0B1120),
        previewSurface = Color(0xFF1E293B),
        previewAccent = Color(0xFF60A5FA)
    ),
    DARK_AMOLED(
        title = "AMOLED Pitch Black (Dark)",
        subtitle = "True #000000 OLED black with neon cyan",
        previewBg = Color(0xFF000000),
        previewSurface = Color(0xFF12141A),
        previewAccent = Color(0xFF22D3EE)
    ),
    DARK_CARBON_RED(
        title = "Carbon & Sport Red (Dark)",
        subtitle = "Matte carbon charcoal with racing crimson",
        previewBg = Color(0xFF0E0E11),
        previewSurface = Color(0xFF1B1B21),
        previewAccent = Color(0xFFFB7185)
    ),
    DARK_EMERALD(
        title = "Racing Green (Dark)",
        subtitle = "Deep forest night with emerald & gold",
        previewBg = Color(0xFF07130F),
        previewSurface = Color(0xFF11231D),
        previewAccent = Color(0xFF34D399)
    ),
    DARK_AMBER(
        title = "Cockpit Amber (Dark)",
        subtitle = "Classic night-driving warm amber & obsidian",
        previewBg = Color(0xFF120E0A),
        previewSurface = Color(0xFF211A14),
        previewAccent = Color(0xFFFBBF24)
    );

    val isDarkThemePreset: Boolean
        get() = this != LIGHT && this != SYSTEM
}
