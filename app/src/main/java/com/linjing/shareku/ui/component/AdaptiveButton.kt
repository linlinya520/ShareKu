package com.linjing.shareku.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.linjing.shareku.ui.theme.LocalUiStyle

/**
 * 自适应按钮：
 * - 液态玻璃风格 → 真玻璃（drawBackdrop 折射 + 模糊 + 边缘色散）
 * - Material / MIUI → 保持原有按钮（视觉零变化）
 *
 * 用于替换各页面里「谷歌风格」的 Button / FilledTonalButton。
 */
@Composable
fun AdaptiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    tonal: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    colors: ButtonColors = if (tonal) ButtonDefaults.filledTonalButtonColors()
        else ButtonDefaults.buttonColors(),
    content: @Composable RowScope.() -> Unit
) {
    val liquid = isLiquidGlassActive()
    if (liquid) {
        Box(
            modifier
                .clip(shape)
                .liquidGlass(
                    shape = shape,
                    surfaceAlpha = 0.18f,
                    blurRadius = 6.dp,
                    refractionHeight = 14.dp,
                    refractionAmount = 28.dp
                )
                .clickable(onClick = onClick)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(contentPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    } else if (tonal) {
        FilledTonalButton(onClick = onClick, modifier = modifier, shape = shape, colors = colors, content = content)
    } else {
        Button(onClick = onClick, modifier = modifier, shape = shape, colors = colors, content = content)
    }
}