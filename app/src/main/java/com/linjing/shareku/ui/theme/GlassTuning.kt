package com.linjing.shareku.ui.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * 液态玻璃质感档位（0 = 清澈，0.5 = 均衡，1 = 磨砂）。
 *
 * 所有倍率用于对组件既有玻璃参数做**相对缩放**——默认「均衡」档全部为 1.0，
 * 因此不改变现有视觉；向两端拖动时折射/模糊/表面透明度按比例增减（对齐 miuix
 * CLEAR / BALANCED / FROSTED 三档的配方）。
 */
data class GlassTuning(
    val density: Float,
    val surfaceAlphaScale: Float,
    val blurScale: Float,
    val refractionScale: Float,
    val lensScale: Float
) {
    val presetLabel: String
        get() = when {
            density < 0.34f -> "清澈"
            density < 0.68f -> "均衡"
            else -> "磨砂"
        }
}

/** 由 0..1 质感进度解析玻璃参数倍率（分段线性） */
fun resolveGlassTuning(density: Float): GlassTuning {
    val d = density.coerceIn(0f, 1f)
    fun seg(a: Float, b: Float, c: Float): Float =
        if (d <= 0.5f) a + (b - a) * (d / 0.5f) else b + (c - b) * ((d - 0.5f) / 0.5f)
    return GlassTuning(
        density = d,
        surfaceAlphaScale = seg(0.85f, 1f, 1.5f),
        blurScale = seg(0.7f, 1f, 1.6f),
        refractionScale = seg(1.2f, 1f, 0.65f),
        lensScale = seg(1.15f, 1f, 0.75f)
    )
}

/** 全局液态玻璃质感（由 LocalShareTheme 依据用户设置提供；默认均衡档） */
val LocalGlassTuning = compositionLocalOf { resolveGlassTuning(0.5f) }