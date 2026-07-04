package com.linjing.shareku.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.linjing.shareku.ui.theme.color.PaletteStyle
import com.linjing.shareku.ui.theme.color.createDynamicScheme
import com.linjing.shareku.ui.theme.color.toComposeColorScheme

private fun generateColorScheme(paletteStyle: PaletteStyle, darkTheme: Boolean): ColorScheme {
    val seedArgb = 0xFF6750A4.toInt()
    return createDynamicScheme(seedArgb, paletteStyle, darkTheme).toComposeColorScheme()
}

// Fallback colors (should never be hit since generateColorScheme always works)
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF), onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B), onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC), onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458), onSecondaryContainer = Color(0xFFE8DEF8),
    surface = Color(0xFF141218), onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F), onSurfaceVariant = Color(0xFFCAC4D0),
    tertiary = Color(0xFFEFB8C8), onTertiary = Color(0xFF492532),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005)
)
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF), onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8), onSecondaryContainer = Color(0xFF1D192B),
    surface = Color(0xFFFFFBFE), onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC), onSurfaceVariant = Color(0xFF49454F),
    tertiary = Color(0xFF7D5260), onTertiary = Color(0xFFFFFFFF),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF)
)

@Composable
fun LocalShareTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    paletteStyle: PaletteStyle = PaletteStyle.TONAL_SPOT,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = remember(dynamicColor, paletteStyle, darkTheme) {
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                // Dynamic colors are handled below with context
                null
            }
            darkTheme -> generateColorScheme(paletteStyle, darkTheme = true)
            else -> generateColorScheme(paletteStyle, darkTheme = false)
        }
    } ?: run {
        // Dynamic colors need context, so can't be in remember
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (darkTheme) DarkColorScheme else LightColorScheme
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}