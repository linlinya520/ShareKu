package com.linjing.shareku.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.linjing.shareku.ui.theme.LocalGlassBackdrop
import com.linjing.shareku.ui.theme.LocalGlassTuning
import kotlin.math.abs
import kotlin.math.roundToInt

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
 * - 液态玻璃 → 玻璃胶囊轨道 + 液态玻璃拇指（跟手 / 速度形变 / 按压透镜 / 重力高光）
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
        LiquidSlider(value = value, onValueChange = onValueChange, valueRange = valueRange, modifier = modifier)
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
 * 液态玻璃滑条（重做）：40dp 玻璃胶囊外壳 + 7dp 细轨道 + 玻璃拇指。
 *
 * 视觉对齐 iOS / kyant backdrop demo 的做法：
 * 外壳玻璃几乎透明（只留折射与边缘高光），真正的"轨道"是内部细条
 * （已选段用主题主色、未选段低对比），拇指是独立玻璃圆。
 * 手势自持（点按 + 横向拖动），不依赖 Material3 的 interactionSource 转发。
 */
@Composable
private fun LiquidSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    val capsule = RoundedCornerShape(50)
    val density = LocalDensity.current
    val input = remember { SliderInput() }
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    input.value = value
    input.range = span

    var trackW by remember { mutableFloatStateOf(0f) }
    val sidePad = 14.dp
    val sidePadPx = with(density) { sidePad.toPx() }
    val thumbPx = with(density) { 26.dp.toPx() }
    val usable = (trackW - sidePadPx * 2 - thumbPx).coerceAtLeast(1f)

    val emit: (Float) -> Unit = { x ->
        if (trackW > 0f) {
            val inner = ((x - sidePadPx - thumbPx / 2f) / usable).coerceIn(0f, 1f)
            onValueChange(valueRange.start + inner * span)
        }
    }

    Box(
        modifier
            .height(40.dp)
            .clip(capsule)
            .liquidGlass(
                shape = capsule,
                surfaceAlpha = 0.10f,
                blurRadius = 5.dp,
                refractionHeight = 10.dp,
                refractionAmount = 20.dp
            )
            .onSizeChanged {
                trackW = it.width.toFloat()
                input.trackW = trackW
            }
            .pointerInput(span) {
                detectTapGestures { offset ->
                    input.hasFinger = true
                    input.fingerX = offset.x
                    emit(offset.x)
                }
            }
            .pointerInput(span) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        input.hasFinger = true
                        input.fingerX = offset.x
                        input.pressed = true
                        emit(offset.x)
                    },
                    onDragEnd = { input.pressed = false },
                    onDragCancel = { input.pressed = false },
                    onHorizontalDrag = { change, _ ->
                        input.hasFinger = true
                        input.fingerX = change.position.x
                        emit(change.position.x)
                    }
                )
            }
    ) {
        // 颜色在组合作用域取出（Canvas 的 DrawScope 内不能读 MaterialTheme）
        val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
        val activeColor = MaterialTheme.colorScheme.primary
        // 内部细轨道：未选段 + 已选段
        Canvas(Modifier.fillMaxSize().padding(horizontal = sidePad)) {
            val h = 7.dp.toPx()
            val r = h / 2f
            val cy = size.height / 2f
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, cy - r),
                size = Size(size.width, h),
                cornerRadius = CornerRadius(r, r)
            )
            val w = size.width * fraction
            if (w > 0f) {
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(0f, cy - r),
                    size = Size(w.coerceAtLeast(h), h),
                    cornerRadius = CornerRadius(r, r)
                )
            }
        }
        // 玻璃拇指：位置 = 内边距 + 进度 × 可用宽度
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = sidePad)
                .offset { IntOffset((fraction * usable).roundToInt(), 0) }
        ) {
            GlassSliderThumb(input = input)
        }
    }
}

/* ======================= 液态玻璃滑条拇指 · 跟手液滴手感 ======================= */

/** 速度 → 横向拉伸系数（px/s → 比例）：约 1300px/s 达到封顶 */
private const val STRETCH_PER_VELOCITY = 0.00015f
/** 最大拉伸比例（对齐 BiliPai 指示器封顶 20%） */
private const val MAX_STRETCH = 0.2f
/** 拉伸弹簧（跟随 + 回弹） */
private const val STRETCH_K = 520f
private const val STRETCH_C = 36f
/** 形变抖动弹簧（按压 / 松手果冻感） */
private const val WOBBLE_K = 420f
private const val WOBBLE_C = 12f

/** 滑条 → 拇指物理的输入通道（普通对象：协程内每帧读最新值） */
private class SliderInput {
    var trackW = 0f       // 轨道宽度（px）
    var value = 0f        // 当前值
    var range = 1f        // 值域跨度
    var fingerX = 0f      // 手指 x（px，观察坐标）
    var hasFinger = false // 是否已捕获到手指位置
    var pressed by mutableStateOf(false)   // 按压状态（State：驱动重组与帧循环）
}

/** 拇指节点逐帧物理暂存（普通 var：避免每帧重组） */
private class SliderPhysics {
    var lastValue = Float.NaN
    var lastFinger = Float.NaN
    var lastT = 0L
    var dt = 1f / 60f
    var vSmooth = 0f      // 平滑后的“手指”速度（px/s）
    var stretchVel = 0f
    var wobbleVel = 0f

    fun reset() {
        lastValue = Float.NaN
        lastFinger = Float.NaN
        lastT = 0L
        vSmooth = 0f
        stretchVel = 0f
        wobbleVel = 0f
    }

    /** 推进时钟，返回本帧 dt（秒，已钳制） */
    fun tick(t: Long): Float {
        dt = if (lastT == 0L) 1f / 60f
        else ((t - lastT) / 1_000_000_000f).coerceIn(1f / 240f, 0.033f)
        lastT = t
        return dt
    }

    /** 由数值变化率推算手指速度（px/s）：备用通道 */
    fun valueVelocity(value: Float, pxPerUnit: Float): Float {
        if (lastValue.isNaN()) {
            lastValue = value
            return 0f
        }
        val v = (value - lastValue) * pxPerUnit / dt
        lastValue = value
        return v
    }

    /** 手指速度（px/s）：主通道（直接来自指针事件，不依赖任何库内部实现） */
    fun fingerVelocity(x: Float): Float {
        if (lastFinger.isNaN()) {
            lastFinger = x
            return 0f
        }
        val v = (x - lastFinger) / dt
        lastFinger = x
        return v
    }
}

/**
 * 液态玻璃滑条拇指（对齐 BiliPai 指示器手感）：
 * - 跟手：不做位置滞后拖尾（实测滞后模型不可感知）
 * - 速度形变：拖动越快横向拉伸越明显（封顶 20%），停止即弹簧回缩
 * - 按压透镜：按下时折射 + 深度 + 色散按进度增强
 * - 重力高光：一抹柔光跟随设备倾斜方向（3° 量化）
 * - 松手：欠阻尼弹簧 → 果冻回弹
 */
@Composable
private fun GlassSliderThumb(input: SliderInput) {
    val backdrop = LocalGlassBackdrop.current ?: return
    val pressed = input.pressed
    val density = LocalDensity.current
    val thumbW = with(density) { 26.dp.toPx() }
    val tuning = LocalGlassTuning.current
    val phys = remember { SliderPhysics() }
    val tilt = rememberQuantizedDeviceTilt()

    // 速度形变（>0 横向拉长）与形变抖动（>0 横向压扁）
    var stretch by remember { mutableFloatStateOf(0f) }
    var wobble by remember { mutableFloatStateOf(0f) }
    var engaged by remember { mutableStateOf(false) }

    val progress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 550f),
        label = "glassThumbPress"
    )

    // —— 拇指物理帧循环：拖拽「速度形变」、松手「果冻回弹」 ——
    LaunchedEffect(pressed) {
        if (pressed) {
            engaged = true
            phys.reset()
            wobble = 0.08f
            while (true) {
                withFrameNanos { t ->
                    val dt = phys.tick(t)
                    val pxPerUnit = ((input.trackW - thumbW) / input.range).coerceAtLeast(1f)
                    val vRaw = if (input.hasFinger) phys.fingerVelocity(input.fingerX)
                    else phys.valueVelocity(input.value, pxPerUnit)
                    phys.vSmooth += (vRaw - phys.vSmooth) * (dt * 20f).coerceAtMost(1f)
                    if (abs(phys.vSmooth) < 6f) phys.vSmooth = 0f
                    // 目标形变：速度越快拉伸越明显（跟手；不做滞后拖尾）
                    val target = (abs(phys.vSmooth) * STRETCH_PER_VELOCITY).coerceIn(0f, MAX_STRETCH)
                    phys.stretchVel += (target - stretch) * STRETCH_K * dt
                    phys.stretchVel *= (1f - STRETCH_C * dt).coerceIn(0f, 1f)
                    stretch += phys.stretchVel * dt
                    phys.wobbleVel += (0f - wobble) * WOBBLE_K * dt
                    phys.wobbleVel *= (1f - WOBBLE_C * dt).coerceIn(0f, 1f)
                    wobble += phys.wobbleVel * dt
                }
            }
        } else {
            if (!engaged) return@LaunchedEffect
            engaged = false
            wobble = -0.13f
            var elapsed = 0f
            while (elapsed < 1.2f) {
                withFrameNanos { t ->
                    val dt = phys.tick(t)
                    elapsed += dt
                    phys.stretchVel += (0f - stretch) * STRETCH_K * dt
                    phys.stretchVel *= (1f - STRETCH_C * dt).coerceIn(0f, 1f)
                    stretch += phys.stretchVel * dt
                    phys.wobbleVel += (0f - wobble) * WOBBLE_K * dt
                    phys.wobbleVel *= (1f - WOBBLE_C * dt).coerceIn(0f, 1f)
                    wobble += phys.wobbleVel * dt
                }
                if (abs(stretch) < 0.004f && abs(phys.stretchVel) < 0.06f &&
                    abs(wobble) < 0.006f && abs(phys.wobbleVel) < 0.1f) break
            }
            stretch = 0f
            wobble = 0f
        }
    }

    val lensH = (lerp(9f, 16f, progress) * tuning.lensScale).dp
    val lensA = (lerp(15f, 30f, progress) * tuning.lensScale).dp
    val surfaceA = (lerp(0.16f, 0.32f, progress) * tuning.surfaceAlphaScale).coerceIn(0f, 0.6f)
    val hlWidth = lerp(0.6f, 1.2f, progress).dp
    val hlAlpha = lerp(0.5f, 1f, progress)
    val shadowEl = lerp(4f, 14f, progress).dp
    Box(
        Modifier
            .size(26.dp)
            .graphicsLayer {
                // 跟手：无位置滞后；只做速度形变 + 抖动（欠阻尼回弹）
                val ps = lerp(1f, 1.3f, progress)
                val w = wobble
                val s = stretch
                scaleX = ps * (1f + w + s)
                scaleY = ps * (1f - w * 0.7f - s * 0.5f)
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
    ) {
        // 重力高光：跟随设备倾斜的一抹柔光（球体内裁切；3° 量化防高频重组）
        Canvas(Modifier.matchParentSize()) {
            val r = size.minDimension / 2f
            val c = center
            val g = tilt.value
            val off = Offset(c.x + g.first * r * 0.42f, c.y + g.second * r * 0.42f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.30f + 0.14f * progress),
                        Color.White.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = off,
                    radius = r * 0.95f
                ),
                radius = r,
                center = c
            )
        }
    }
}