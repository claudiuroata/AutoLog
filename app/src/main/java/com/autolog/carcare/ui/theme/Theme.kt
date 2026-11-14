package com.autolog.carcare.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 1. Midnight Slate (Original Dark Theme)
private val MidnightSlateDarkScheme = darkColorScheme(
    primary = NavyAccent,                   // Vibrant Blue 400 (0xFF60A5FA)
    onPrimary = SlateMidnight,              // Dark Slate on Primary button
    primaryContainer = Color(0xFF1E3A8A),   // Navy 900
    onPrimaryContainer = Color(0xFFDBEAFE), // Light Blue 100
    secondary = TealAccent,                 // Teal 500
    onSecondary = SlateMidnight,
    secondaryContainer = Color(0xFF134E4A), // Teal 900
    onSecondaryContainer = Color(0xFFCCFBF1), // Teal 100
    tertiary = ColorService,                // Warm Orange
    onTertiary = SlateMidnight,
    background = SlateMidnight,             // Deep Charcoal Slate (0xFF0B1120)
    surface = SlateCardDark,                // Slate 800 (0xFF1E293B)
    surfaceVariant = SlateBorderDark,       // Slate 700 (0xFF334155)
    onBackground = Color(0xFFF1F5F9),       // Slate 100
    onSurface = Color(0xFFF8FAFC),          // Slate 50
    onSurfaceVariant = SlateTextSecondaryDark, // Slate 400 (0xFF94A3B8)
    outline = Color(0xFF475569),            // Slate 600
    outlineVariant = SlateBorderDark,       // Slate 700
    error = Color(0xFFF87171),              // Red 400
    onError = SlateMidnight
)

// 2. AMOLED Pitch Black (True #000000 OLED Dark Theme with Electric Cyan)
private val AmoledPitchBlackScheme = darkColorScheme(
    primary = Color(0xFF22D3EE),            // Electric Cyan 400
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF083344),   // Cyan 950
    onPrimaryContainer = Color(0xFFCFFAFE), // Cyan 100
    secondary = Color(0xFF38BDF8),          // Sky 400
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF0C4A6E),
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = Color(0xFFA78BFA),           // Violet 400
    onTertiary = Color(0xFF000000),
    background = Color(0xFF000000),         // True OLED Pitch Black
    surface = Color(0xFF101217),            // Subtle Elevated Dark Card
    surfaceVariant = Color(0xFF1E222B),     // Dark Border / Chip Surface
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF9CA3AF),   // Neutral Gray 400
    outline = Color(0xFF374151),
    outlineVariant = Color(0xFF1F2937),
    error = Color(0xFFF87171),
    onError = Color(0xFF000000)
)

// 3. Carbon & Sport Red (Matte Carbon Charcoal with Racing Crimson)
private val CarbonRedDarkScheme = darkColorScheme(
    primary = Color(0xFFFB7185),            // Sport Rose/Crimson 400
    onPrimary = Color(0xFF180408),
    primaryContainer = Color(0xFF4C0519),   // Deep Crimson 950
    onPrimaryContainer = Color(0xFFFFE4E6), // Rose 100
    secondary = Color(0xFFFBBF24),          // Racing Gold / Amber 400
    onSecondary = Color(0xFF181002),
    secondaryContainer = Color(0xFF451A03),
    onSecondaryContainer = Color(0xFFFEF3C7),
    tertiary = Color(0xFFF97316),
    onTertiary = Color(0xFF0E0E11),
    background = Color(0xFF0E0E11),         // Carbon Fiber Dark
    surface = Color(0xFF1A1A20),            // Matte Graphite Card
    surfaceVariant = Color(0xFF2A2A34),     // Gunmetal Surface Variant
    onBackground = Color(0xFFF4F4F5),       // Zinc 100
    onSurface = Color(0xFFFAFAFA),          // Zinc 50
    onSurfaceVariant = Color(0xFFA1A1AA),   // Zinc 400
    outline = Color(0xFF52525B),            // Zinc 600
    outlineVariant = Color(0xFF27272A),     // Zinc 800
    error = Color(0xFFF87171),
    onError = Color(0xFF0E0E11)
)

// 4. British Racing Green (Deep Forest Night with Emerald & Gold)
private val EmeraldRacingDarkScheme = darkColorScheme(
    primary = Color(0xFF34D399),            // Emerald 400
    onPrimary = Color(0xFF041E15),
    primaryContainer = Color(0xFF064E3B),   // Emerald 900
    onPrimaryContainer = Color(0xFFD1FAE5), // Emerald 100
    secondary = Color(0xFFFBBF24),          // Classic Gold Accent
    onSecondary = Color(0xFF1C1302),
    secondaryContainer = Color(0xFF451A03),
    onSecondaryContainer = Color(0xFFFEF3C7),
    tertiary = Color(0xFF2DD4BF),           // Teal 400
    onTertiary = Color(0xFF041E15),
    background = Color(0xFF07130F),         // Deep Forest Night
    surface = Color(0xFF11231D),            // British Racing Green Card
    surfaceVariant = Color(0xFF1D382F),     // Forest Surface Variant
    onBackground = Color(0xFFECFDF5),
    onSurface = Color(0xFFF0FDF4),
    onSurfaceVariant = Color(0xFF86EFAC).copy(alpha = 0.72f),
    outline = Color(0xFF2D5A4C),
    outlineVariant = Color(0xFF1B3B30),
    error = Color(0xFFF87171),
    onError = Color(0xFF07130F)
)

// 5. Cockpit Amber (Classic Night-Driving Warm Amber & Obsidian)
private val CockpitAmberDarkScheme = darkColorScheme(
    primary = Color(0xFFFBBF24),            // Warm Cockpit Amber 400
    onPrimary = Color(0xFF1C1204),
    primaryContainer = Color(0xFF451A03),   // Deep Bronze / Amber 950
    onPrimaryContainer = Color(0xFFFEF3C7), // Amber 100
    secondary = Color(0xFFFB923C),          // Sunset Orange 400
    onSecondary = Color(0xFF1C0E04),
    secondaryContainer = Color(0xFF431407),
    onSecondaryContainer = Color(0xFFFFEDD5),
    tertiary = Color(0xFFF59E0B),
    onTertiary = Color(0xFF120E0A),
    background = Color(0xFF120E0A),         // Warm Obsidian Background
    surface = Color(0xFF211A14),            // Espresso / Bronze Card
    surfaceVariant = Color(0xFF332920),     // Warm Umber Surface Variant
    onBackground = Color(0xFFFEF3C7).copy(alpha = 0.95f),
    onSurface = Color(0xFFFFFBEB),
    onSurfaceVariant = Color(0xFFD6C7B2),
    outline = Color(0xFF574739),
    outlineVariant = Color(0xFF332920),
    error = Color(0xFFF87171),
    onError = Color(0xFF120E0A)
)

private val LightColorScheme = lightColorScheme(
    primary = NavyPrimary,              // Automotive Navy 900 (0xFF1E3A8A)
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE), // Soft Blue 100
    onPrimaryContainer = NavyPrimary,
    secondary = NavySecondary,          // Blue 600 (0xFF2563EB)
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0369A1),
    tertiary = TealDark,
    onTertiary = Color.White,
    background = SlateLightBg,          // Crisp Slate 50 (0xFFF8FAFC)
    surface = SlateCardLight,           // Pure White (0xFFFFFFFF)
    surfaceVariant = Color(0xFFF1F5F9), // Slate 100
    onBackground = SlateDark,           // Slate 900 (0xFF0F172A)
    onSurface = SlateDark,
    onSurfaceVariant = SlateTextSecondaryLight, // Slate 500 (0xFF64748B)
    outline = Color(0xFFCBD5E1),        // Slate 300
    outlineVariant = SlateBorderLight,  // Slate 200
    error = Color(0xFFDC2626),
    onError = Color.White
)

@Composable
fun AutoLogTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK,
        ThemeMode.DARK_AMOLED,
        ThemeMode.DARK_CARBON_RED,
        ThemeMode.DARK_EMERALD,
        ThemeMode.DARK_AMBER -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        themeMode == ThemeMode.LIGHT -> LightColorScheme
        themeMode == ThemeMode.DARK -> MidnightSlateDarkScheme
        themeMode == ThemeMode.DARK_AMOLED -> AmoledPitchBlackScheme
        themeMode == ThemeMode.DARK_CARBON_RED -> CarbonRedDarkScheme
        themeMode == ThemeMode.DARK_EMERALD -> EmeraldRacingDarkScheme
        themeMode == ThemeMode.DARK_AMBER -> CockpitAmberDarkScheme
        themeMode == ThemeMode.SYSTEM -> if (systemDark) MidnightSlateDarkScheme else LightColorScheme
        else -> MidnightSlateDarkScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
