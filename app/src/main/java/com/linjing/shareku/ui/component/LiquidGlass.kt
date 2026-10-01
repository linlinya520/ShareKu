package com.linjing.shareku.ui.component

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.isRuntimeShaderSupported
import com.linjing.shareku.ui.theme.LocalGlassBackdrop
import com.linjing.shareku.ui.theme.LocalGlassTuning
import com.linjing.shareku.ui.theme.LocalUiStyle

/** 当前是否处于「液态玻璃」风格且玻璃源可用 */
@Composable
fun isLiquidGlassActive(): Boolean =
    LocalUiStyle.current == "liquid" && LocalGlassBackdrop.current != null

/**
 * 液态玻璃表面：真实折射 + 模糊 + 饱和度增强 + 边缘色散。
 *
 * 设计依据（钻研 Kyant0/backdrop + QWEA0/Liquid-Glass-Android 的透镜管线）：
 * 1. 折射/高光**必须有细节可采样**——纯色或平滑渐变上完全看不出玻璃感，
 *    所以 backdrop 源必须是「有内容的背景层」（本项目 = 全局壁纸）。
 * 2. 效果顺序固定为 colorFilter → blur → lens。
 * 3. `chromaticAberration` 打开才有边缘光谱条纹（玻璃质感的关键，别关）。
 * 4. 表面色 alpha 要低（0.08~0.2）。之前用 0.4 太不透明，所以看着像塑料片。
 * 5. highlight / shadow / innerShadow 用库的默认值（自带边缘高光与投影）。
 *
 * @param surfaceAlpha 玻璃表面不透明度，越低越通透
 * @param blurRadius 背景模糊半径（QWEA0 参考值 4dp）
 * @param refractionHeight 贴边折射位移（≡ QWEA0 的 refractionHeight）
 * @param refractionAmount 折射影响范围（≡ QWEA0 的 bevelWidth）
 * @param dispersion 是否启用边缘色散（内部 7 次采样，较贵；数量多的元素建议关）
 * @param plain 省成本模式：不挂库默认 Highlight / Shadow（各自是独立离屏层 + 逐帧录制），
 *   由调用方自绘轻量高光/阴影——适合数量多的元素（如卡片）。
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(24.dp),
    surfaceAlpha: Float = 0.12f,
    blurRadius: Dp = 6.dp,
    refractionHeight: Dp = 18.dp,
    refractionAmount: Dp = 34.dp,
    dispersion: Boolean = true,
    plain: Boolean = false,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this
    val backdrop = LocalGlassBackdrop.current ?: return this
    if (LocalUiStyle.current != "liquid") return this

    val tuning = LocalGlassTuning.current
    val cornerShape = shape as? CornerBasedShape
    val canRefract = isRuntimeShaderSupported() && cornerShape != null
    val surfaceColor = MaterialTheme.colorScheme.surface
    val effSurfaceAlpha = (surfaceAlpha * tuning.surfaceAlphaScale).coerceIn(0f, 0.6f)
    val effBlurRadius = blurRadius * tuning.blurScale
    val effRefractionHeight = refractionHeight * tuning.refractionScale
    val effRefractionAmount = refractionAmount * tuning.refractionScale
    val effects: BackdropEffectScope.() -> Unit = {
        // 顺序：colorFilter → blur → lens
        vibrancy()
        blur(effBlurRadius.toPx(), TileMode.Clamp)
        if (canRefract) {
            val cornerRadius = cornerShape!!.topStart.toPx(size, this)
            val height = minOf(effRefractionHeight.toPx(), cornerRadius)
            val amount = minOf(effRefractionAmount.toPx(), size.minDimension * 0.5f)
            if (height > 0f && amount > 0f) {
                // chromaticAberration = dispersion → 边缘光谱色散（玻璃感核心）
                lens(height, amount, chromaticAberration = dispersion)
            }
        }
    }
    val onSurface: DrawScope.() -> Unit = {
        if (effSurfaceAlpha > 0f) {
            drawRect(surfaceColor.copy(alpha = effSurfaceAlpha))
        }
    }

    if (plain) {
        // 省成本模式：不挂库默认 Highlight / Shadow（各自都是独立离屏层 + 逐帧录制）
        return this.drawPlainBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = effects,
            onDrawSurface = onSurface
        )
    }
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = effects,
        onDrawSurface = onSurface
    )
}
