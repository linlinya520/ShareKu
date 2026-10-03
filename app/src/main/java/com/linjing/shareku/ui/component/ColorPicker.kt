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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 取色面板（HSV / HSB 取色器）：SV 色板 + 色相条 + 明度条。全自绘，三套 UI 通用。
 *
 * ⚠️ 关键实现约定（修「圆点不跟手 / 松手回弹」）：
 * 面板**自己持有拖动中的草稿值**（draft），绘制与手势全部用草稿；
 * 只在「点按」或「拖动松手」时才通过 [onColorChange] 提交给外部。
 * 这样圆点永远跟着手指走，不会被外部异步回写（DataStore）拉回旧位置。
 */
@Composable
fun ColorPickerPanel(
    hue: Float,
    sat: Float,
    value: Float,
    onColorChange: (hue: Float, sat: Float, value: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // 拖动中的草稿（null = 没有拖动，以外部值为准）
    var draft by remember { mutableStateOf<FloatArray?>(null) }
    val h = draft?.get(0) ?: hue
    val s = draft?.get(1) ?: sat
    val v = draft?.get(2) ?: value

    // 外部值追平草稿后释放草稿（回到「外部为准」，用于重置 / HEX 输入等外部变更）
    LaunchedEffect(hue, sat, value) {
        val d = draft ?: return@LaunchedEffect
        if (abs(d[0] - hue) < 1.5f && abs(d[1] - sat) < 0.02f && abs(d[2] - value) < 0.02f) {
            draft = null
        }
    }

    val hueColor = remember(h) { Color(hsvToArgb(h, 1f, 1f)) }
    val pureColor = remember(h, s) { Color(hsvToArgb(h, s.coerceAtLeast(0.02f), 1f)) }

    val curOnChange = rememberUpdatedState(onColorChange)
    val curDraft = rememberUpdatedState<FloatArray?>(draft)
    val setColor: (Float, Float, Float, Boolean) -> Unit = { nh, ns, nv, commit ->
        draft = floatArrayOf(nh, ns, nv)
        if (commit) curOnChange.value(nh, ns, nv)
    }
    val commitCurrent: () -> Unit = {
        curDraft.value?.let { curOnChange.value(it[0], it[1], it[2]) }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ═══ SV 色板 ═══
        var box by remember { mutableStateOf(IntSize.Zero) }
        val curBox = rememberUpdatedState(box)
        val curH = rememberUpdatedState(h)
        val applySv: (Offset, Boolean) -> Unit = { pos, commit ->
            val size = curBox.value
            if (size.width > 0 && size.height > 0) {
                setColor(
                    curH.value,
                    (pos.x / size.width).coerceIn(0f, 1f),
                    (1f - pos.y / size.height).coerceIn(0.02f, 1f),
                    commit
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(168.dp)
                .clip(RoundedCornerShape(18.dp))
                .onSizeChanged { box = it }
                .pointerInput(Unit) {
                    detectTapGestures { applySv(it, true) }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { applySv(it, false) },
                        onDrag = { change, _ ->
                            change.consume()
                            applySv(change.position, false)
                        },
                        onDragEnd = { commitCurrent() },
                        onDragCancel = { commitCurrent() }
                    )
                }
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                // 指示环（位置 = 饱和度 × 明度）
                val x = s * size.width
                val y = (1f - v) * size.height
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

        // ═══ 色相条 ═══
        GradientBar(
            label = "色相",
            gradient = listOf(
                Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
                Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000)
            ),
            fraction = (h / 360f).coerceIn(0f, 1f),
            thumbColor = hueColor,
            labelText = "${h.roundToInt()}°",
            onPick = { f, commit -> setColor((f * 360f).coerceIn(0f, 360f), s, v, commit) },
            onCommit = commitCurrent
        )

        // ═══ 明度条（与色板纵轴联动）═══
        GradientBar(
            label = "明度",
            gradient = listOf(Color.Black, pureColor),
            fraction = v.coerceIn(0f, 1f),
            thumbColor = pureColor,
            labelText = "${(v * 100).roundToInt()}%",
            onPick = { f, commit -> setColor(h, s, f.coerceIn(0.02f, 1f), commit) },
            onCommit = commitCurrent
        )
    }
}

/** 渐变选择条：点按立即提交，拖动中只更新草稿、松手提交 */
@Composable
private fun GradientBar(
    label: String,
    gradient: List<Color>,
    fraction: Float,
    thumbColor: Color,
    labelText: String,
    onPick: (fraction: Float, commit: Boolean) -> Unit,
    onCommit: () -> Unit
) {
    val curPick = rememberUpdatedState(onPick)
    val curCommit = rememberUpdatedState(onCommit)

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
        val curW = rememberUpdatedState(w)
        val apply: (Offset, Boolean) -> Unit = { pos, commit ->
            val width = curW.value
            if (width > 0) curPick.value((pos.x / width).coerceIn(0f, 1f), commit)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .onSizeChanged { w = it.width }
                .pointerInput(Unit) { detectTapGestures { apply(it, true) } }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { apply(it, false) },
                        onDrag = { change, _ ->
                            change.consume()
                            apply(change.position, false)
                        },
                        onDragEnd = { curCommit.value() },
                        onDragCancel = { curCommit.value() }
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