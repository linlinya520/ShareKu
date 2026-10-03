package com.linjing.shareku.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.linjing.shareku.ui.theme.color.hsvToArgb
import kotlin.math.roundToInt

/**
 * 取色面板（自绘，三套 UI 通用）：HSV 色板 + 色相条 + 明度条。
 *
 * 交互与常见系统取色器一致：
 *  - 左上「一坨颜色」中拖动圆点 = 同时选饱和度(横)与明度(纵)；
 *  - 色相条 = 换颜色种类；明度条 = 整体调亮调暗（与色板纵轴联动）。
 *
 * 单一数据源：组件不保存状态，只把变化回调给调用方（hue/sat/value 由调用方持有）。
 */
@Composable
fun ColorPickerPanel(
    hue: Float,
    sat: Float,
    value: Float,
    onHueChange: (Float) -> Unit,
    onSatChange: (Float) -> Unit,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val hueColor = remember(hue) { Color(hsvToArgb(hue, 1f, 1f)) }
    val valueColor = remember(hue, sat, value) {
        Color(hsvToArgb(hue, sat.coerceAtLeast(0.02f), value))
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ── SV 色板 ──
        var boxSize by remember { mutableStateOf(IntSize.Zero) }
        val applySv: (Offset) -> Unit = { pos ->
            if (boxSize.width > 0 && boxSize.height > 0) {
                onSatChange((pos.x / boxSize.width).coerceIn(0f, 1f))
                onValueChange((1f - pos.y / boxSize.height).coerceIn(0f, 1f))
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(168.dp)
                .clip(RoundedCornerShape(18.dp))
                .onSizeChanged { boxSize = it }
                .pointerInput(Unit) { detectTapGestures { applySv(it) } }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { applySv(it) },
                        onDrag = { change, _ -> applySv(change.position) }
                    )
                }
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                // 选中指示环
                val x = sat * size.width
                val y = (1f - value) * size.height
                val r = 9.dp.toPx()
                val c = Offset(x, y)
                drawCircle(Color.White, radius = r, center = c, style = Stroke(width = 3.dp.toPx()))
                drawCircle(
                    Color.Black.copy(alpha = 0.35f),
                    radius = r + 2.dp.toPx(),
                    center = c,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        // ── 色相条 ──
        GradientBar(
            label = "色相",
            gradient = listOf(
                Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
                Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000)
            ),
            fraction = (hue / 360f).coerceIn(0f, 1f),
            thumbColor = hueColor,
            labelText = "${hue.roundToInt()}°",
            onPick = { onHueChange((it * 360f).coerceIn(0f, 360f)) }
        )

        // ── 明度条（与色板纵轴联动）──
        GradientBar(
            label = "明度",
            gradient = listOf(Color.Black, valueColor),
            fraction = value.coerceIn(0f, 1f),
            thumbColor = valueColor,
            labelText = "${(value * 100).roundToInt()}%",
            onPick = { onValueChange(it.coerceIn(0.02f, 1f)) }
        )
    }
}

/** 渐变选择条：点按 / 拖动取值（0..1） */
@Composable
private fun GradientBar(
    label: String,
    gradient: List<Color>,
    fraction: Float,
    thumbColor: Color,
    labelText: String,
    onPick: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                labelText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(6.dp))
        var w by remember { mutableStateOf(0) }
        val apply: (Offset) -> Unit = { pos ->
            if (w > 0) onPick((pos.x / w).coerceIn(0f, 1f))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .onSizeChanged { w = it.width }
                .pointerInput(Unit) { detectTapGestures { apply(it) } }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { apply(it) },
                        onDrag = { change, _ -> apply(change.position) }
                    )
                }
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawRect(Brush.horizontalGradient(gradient))
                val pad = 10.dp.toPx()
                val cx = (fraction * size.width).coerceIn(pad, (size.width - pad).coerceAtLeast(pad))
                val cy = size.height / 2f
                drawCircle(Color.White, radius = 9.dp.toPx(), center = Offset(cx, cy))
                drawCircle(thumbColor, radius = 6.dp.toPx(), center = Offset(cx, cy))
            }
        }
    }
}