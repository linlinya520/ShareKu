package com.linjing.shareku.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.isRuntimeShaderSupported
import com.linjing.shareku.AppSingletons
import com.linjing.shareku.ui.component.WallpaperBackground
import com.linjing.shareku.ui.theme.color.PaletteStyle
import com.linjing.shareku.ui.theme.color.createDynamicScheme
import com.linjing.shareku.ui.theme.color.toComposeColorScheme
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

/** 全局 UI 风格（"material" | "miuix"），组件层据此条件渲染 */
val LocalUiStyle = staticCompositionLocalOf { "material" }

/**液态玻璃背景源（仅 liquid 风格下非空）；玻璃组件用 drawBackdrop 取用 */
val LocalGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

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
    uiStyle: String = "material", // "material" | "miuix"
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

    val wallpaperSource by AppSingletons.preferencesManager.wallpaperSource.collectAsState(initial = "default")
    val glassDensity by AppSingletons.preferencesManager.glassDensity.collectAsState(initial = 0.5f)
    val wallpaperEnabled = wallpaperSource != "none"
    val isLiquid = uiStyle == "liquid" && isRuntimeShaderSupported()
    // 动态系统壁纸透出：窗口后由系统绘制当前壁纸（含视频/动态壁纸），App 层留空透出
    val liveSystemWallpaper = wallpaperSource == "system" && com.linjing.shareku.ui.component.isLiveSystemWallpaper(view.context)
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            if (liveSystemWallpaper) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                // 窗口自身背景必须透明，否则会盖住壁纸层
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                window.statusBarColor = android.graphics.Color.TRANSPARENT
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                window.statusBarColor = colorScheme.surface.toArgb()
            }
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    // 液态玻璃下把「表面系」颜色改成半透明——否则深色模式的卡片/输入框/对话框
    // 仍是接近纯黑的不透明块，壁纸完全透不出来（「大片纯黑背景」的根因）。
    val effectiveScheme = if (wallpaperEnabled) {
        if (isLiquid) {
            colorScheme.copy(
                background = Color.Transparent,
                surface = colorScheme.surface.copy(alpha = 0.42f),
                surfaceVariant = colorScheme.surfaceVariant.copy(alpha = 0.36f),
                surfaceContainer = colorScheme.surfaceContainer.copy(alpha = 0.40f),
                surfaceContainerLow = colorScheme.surfaceContainerLow.copy(alpha = 0.38f),
                surfaceContainerLowest = colorScheme.surfaceContainerLowest.copy(alpha = 0.34f),
                surfaceContainerHigh = colorScheme.surfaceContainerHigh.copy(alpha = 0.44f),
                surfaceContainerHighest = colorScheme.surfaceContainerHighest.copy(alpha = 0.46f),
                surfaceDim = colorScheme.surfaceDim.copy(alpha = 0.40f),
                surfaceBright = colorScheme.surfaceBright.copy(alpha = 0.40f)
            )
        } else {
            colorScheme.copy(background = Color.Transparent)
        }
    } else colorScheme

    // 玻璃折射源：把「背景层（壁纸）」录进 backdrop。
    // ⚠️ 折射/高光必须有细节可采样——纯色或平滑渐变上完全看不出玻璃感
    // （QWEA0 原话：refraction is invisible over a flat colour or a smooth gradient）。
    // ⚠️ 玻璃组件必须画在这个录制层之外，否则 backdrop 自引用 → RenderThread 无限递归崩溃。
    val glassBackdrop = rememberLayerBackdrop {
        drawRect(colorScheme.background)
        drawContent()
    }

    Box(Modifier.fillMaxSize()) {
        // ① 背景层：壁纸（liquid 下同时作为玻璃的折射源）
        Box(
            Modifier
                .fillMaxSize()
                .then(if (isLiquid) Modifier.layerBackdrop(glassBackdrop) else Modifier)
        ) {
            if (wallpaperEnabled) {
                WallpaperBackground(fallbackColor = colorScheme.background, darkTheme = darkTheme)
            }
        }

        // ② 前景层：玻璃组件在此引用上面的「壁纸背景层」
        CompositionLocalProvider(
            LocalUiStyle provides uiStyle,
            // 液态玻璃质感档位（清澈/均衡/磨砂）→ 对各玻璃组件的参数做相对缩放
            LocalGlassTuning provides resolveGlassTuning(glassDensity),
            // 动态壁纸透出模式下玻璃无法采样系统壁纸层 → 不提供 backdrop（组件自动降级为非玻璃材质）
            LocalGlassBackdrop provides if (isLiquid && !liveSystemWallpaper) glassBackdrop else null
        ) {
        if (uiStyle == "miuix") {
            // MIUI 风格：ThemeController 以当前主题主色为 keyColor 生成 miuix 配色（对齐参考实现 Spec2021）
            val miuixController = remember(darkTheme, colorScheme.primary) {
                ThemeController(
                    colorSchemeMode = if (darkTheme) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
                    keyColor = colorScheme.primary,
                    paletteStyle = ThemePaletteStyle.TonalSpot,
                    colorSpec = ThemeColorSpec.Spec2021
                )
            }
            MiuixTheme(
                controller = miuixController
            ) {
                MaterialTheme(
                    colorScheme = effectiveScheme,
                    typography = Typography,
                    content = content
                )
            }
        } else {
            // material / liquid：liquid 的玻璃效果完全由组件层 drawBackdrop 承担，
            // 主题层只负责提供「壁纸背景层」作为折射源（见 LocalGlassBackdrop）。
            MaterialTheme(
                colorScheme = effectiveScheme,
                typography = Typography,
                content = content
            )
        }
        }
    }
}