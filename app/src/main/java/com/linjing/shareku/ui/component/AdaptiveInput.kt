package com.linjing.shareku.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.linjing.shareku.ui.theme.LocalGlassBackdrop

/**
 * 自适应输入框：
 * - 液态玻璃 → 真玻璃容器 + BasicTextField（壁纸透出、边缘折射）
 * - Material / MIUI → 原 OutlinedTextField（视觉零变化）
 */
@Composable
fun AdaptiveTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    if (isLiquidGlassActive()) {
        Box(
            modifier
                .clip(shape)
                .liquidGlass(
                    shape = shape,
                    surfaceAlpha = 0.16f,
                    blurRadius = 6.dp,
                    refractionHeight = 12.dp,
                    refractionAmount = 24.dp
                )
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) {
                                val hint = placeholder ?: label
                                if (hint != null) {
                                    Text(
                                        hint,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            innerTextField()
                        }
                    }
                )
                if (trailingIcon != null) trailingIcon()
            }
        }
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            singleLine = singleLine,
            shape = shape,
            label = label?.let { { Text(it) } },
            placeholder = placeholder?.let { { Text(it) } },
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            trailingIcon = trailingIcon
        )
    }
}

/**
 * 自适应滑条：
 * - 液态玻璃 → 玻璃胶囊轨道 + 液态玻璃拇指（按压/未按压两种特效）
 * - Material / MIUI → 原 Slider
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    colors: SliderColors = SliderDefaults.colors()
) {
    if (isLiquidGlassActive()) {
        val capsule = RoundedCornerShape(50)
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        Box(
            modifier
                .clip(capsule)
                .liquidGlass(
                    shape = capsule,
                    surfaceAlpha = 0.12f,
                    blurRadius = 6.dp,
                    refractionHeight = 12.dp,
                    refractionAmount = 26.dp
                )
        ) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                interactionSource = interactionSource,
                colors = SliderDefaults.colors(
                    thumbColor = Color.Transparent,
                    activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                ),
                thumb = { GlassSliderThumb(pressed = pressed) },
                modifier = Modifier.padding(horizontal = 10.dp)
            )
        }
    } else {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = colors,
            modifier = modifier
        )
    }
}

/**
 * 液态玻璃滑条拇指（苹果风「两种效果」）：
 * - 未按压：清透小玻璃球，弱高光、浅阴影
 * - 按压中：放大 + 更亮的高光 + 更深的投影 + 液滴鼓包折射（depthEffect）+ 边缘色散
 */
@Composable
private fun GlassSliderThumb(pressed: Boolean) {
    val backdrop = LocalGlassBackdrop.current ?: return
    val progress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 550f),
        label = "glassThumbPress"
    )
    val scale = lerp(1f, 1.3f, progress)
    val lensH = lerp(9f, 16f, progress).dp
    val lensA = lerp(15f, 30f, progress).dp
    val surfaceA = lerp(0.16f, 0.30f, progress)
    val hlWidth = lerp(0.6f, 1.2f, progress).dp
    val hlAlpha = lerp(0.5f, 1f, progress)
    val shadowEl = lerp(4f, 14f, progress).dp
    Box(
        Modifier
            .size(26.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation = shadowEl, shape = CircleShape, clip = false)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur(3.dp.toPx())
                    lens(
                        refractionHeight = lensH.toPx(),
                        refractionAmount = lensA.toPx(),
                        depthEffect = pressed,
                        chromaticAberration = true
                    )
                },
                highlight = { Highlight(width = hlWidth, blurRadius = lerp(0.8f, 2f, progress).dp, alpha = hlAlpha) },
                onDrawSurface = {
                    drawRect(Color.White.copy(alpha = surfaceA))
                }
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = lerp(0.25f, 0.8f, progress)),
                shape = CircleShape
            )
    )
}