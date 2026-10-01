package com.linjing.shareku.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.linjing.shareku.AppSingletons
import com.linjing.shareku.ui.component.isLiquidGlassActive
import com.linjing.shareku.ui.component.liquidGlass
import com.linjing.shareku.ui.theme.LocalGlassBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 第二套布局：底部悬浮 Dock。
 *
 * 内容区用 HorizontalPager —— 支持左右跟手滑动、切换流畅；
 * 底部 Dock 有一个【液态玻璃滑块】指示当前页，并跟随手指实时移动；
 * 在 Dock 上【长按后左右拖动】可连续换页（按手指绝对位置映射，松手自动归位到最近页）。
 */
@Composable
fun DockLayout() {
    val pagerState = rememberPagerState(pageCount = { dockItems.size })
    val scope = rememberCoroutineScope()
    var showLog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val prefs = AppSingletons.preferencesManager
    val liquid = isLiquidGlassActive()
    val preRenderDone by prefs.liquidPreRenderDone.collectAsState(initial = true)
    val needPreRender = liquid && !preRenderDone

    // 预热状态：
    // - 首次启用液态玻璃 → 显式预渲染（逐页驱动首绘，把着色器编译/离屏层分配集中到这一刻）；
    // - 平时 → 启动后空闲时轻量预热一页。
    var warmPages by remember { mutableIntStateOf(1) }
    var preRendering by remember { mutableStateOf(false) }
    LaunchedEffect(needPreRender, liquid) {
        if (!liquid) return@LaunchedEffect
        if (needPreRender) {
            preRendering = true
            warmPages = dockItems.size
            delay(600)
            for (i in dockItems.indices) {
                pagerState.scrollToPage(i)
                delay(650)
            }
            pagerState.scrollToPage(0)
            delay(300)
            prefs.setLiquidPreRenderDone(true)
            warmPages = 2
            preRendering = false
        } else if (warmPages < 2) {
            delay(2500)
            if (pagerState.currentPage == 0 && !pagerState.isScrollInProgress) {
                warmPages = 2
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 页面切换不再做「玻璃降级」——那会让玻璃闪一下再回来。
        // 全界面液态玻璃：卡片/顶栏/Dock/按钮（毛玻璃模糊 + 折射 + 色散 + 高光 + 阴影）。
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // 内容区直接左右滑动即可换页（跟手）；预渲染期间锁住手势
            userScrollEnabled = !preRendering,
            // 预渲染时会开到全部页面，平时保持「相邻一页」的轻量策略
            beyondViewportPageCount = warmPages
        ) { p ->
            // 给底部悬浮 Dock 留出空间：避免内容滑到最底时被 Dock 遮住（如外观页的暗黑遮罩滑条）
            Box(Modifier.fillMaxSize().padding(bottom = 92.dp)) {
                when (p) {
                    0 -> HomeScreen(onNavigateToLog = { showLog = true })
                    1 -> ToolsScreen()
                    2 -> AppearanceScreen(onBack = {}, embedded = true)
                    3 -> AboutScreen(onBack = {}, onChangelog = {}, embedded = true)
                }
            }
        }

        // 拖动 Dock 时：按手指的绝对位置把 Pager 滚过去（界面 + 滑块一起跟手）
        val scrollJob = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }

        DockBar(
            pagerState = pagerState,
            enabled = !preRendering,
            onSelect = { i ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scrollJob[0]?.cancel()
                scope.launch { pagerState.animateScrollToPage(i) }
            },
            onScrollTo = { page, offset ->
                scrollJob[0]?.cancel()
                scrollJob[0] = scope.launch { pagerState.scrollToPage(page, offset) }
            },
            onSettle = { page ->
                // 松手时若停在两页中间，平滑归位到最近一页
                scrollJob[0]?.cancel()
                scrollJob[0] = scope.launch { pagerState.animateScrollToPage(page) }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // 日志页：Dock 布局下没有独立路由，用全屏层显示
        // 底部滑入滑出转场（布局内层，替代 Dialog：动画可控、无独立窗口闪变）
        AnimatedVisibility(
            visible = showLog,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(300, easing = DockNavEasing)
            ) + fadeIn(tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(300, easing = DockNavEasing)
            ) + fadeOut(tween(200))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                LogScreen(onBack = { showLog = false })
            }
        }
        // 返回键：日志层显示时关闭日志（此时的返回语义 = 关闭这一层）
        if (showLog) {
            BackHandler { showLog = false }
        }

        // 首次启用液态玻璃的「预渲染」引导：把一次性重活显式化，避免使用时被冷启动卡顿打扰
        if (preRendering) {
            com.linjing.shareku.ui.component.LiquidPreRenderOverlay()
        }
    }
}

private val dockItems: List<Triple<String, ImageVector, Int>> = listOf(
    Triple("主页", Icons.Default.Home, 0),
    Triple("工具", Icons.Default.Build, 1),
    Triple("外观", Icons.Default.Palette, 2),
    Triple("关于", Icons.Default.Info, 3)
)

/* ─── 液态水珠滑块物理（Dock 指示器） ─── */

/** 统一转场缓动（快进慢出，与 NavHost / 窗口动画一致） */
private val DockNavEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** 弹簧刚度（格/s²·格）：越大越跟手、越小滞后越明显 */
private const val DOCK_SLIDER_K = 900f
/** 阻尼（1/s）：越小越"糯"（拖尾越明显、回弹越多） */
private const val DOCK_SLIDER_C = 40f

/** Dock 滑块逐帧物理暂存（普通 var：避免每帧重组） */
private class DockSliderPhysics {
    var actual = Float.NaN  // 显示位置（格）
    var vel = 0f            // 速度（格/s）
    var smoothV = 0f        // 平滑速度（格/s，驱动拉伸）
    var dir = 1f            // 运动方向（1=右，-1=左）
    var stretch = 0f        // 平滑后的拉伸量
    var lastT = 0L

    fun reset(target: Float) {
        actual = target
        vel = 0f
        smoothV = 0f
        dir = 1f
        stretch = 0f
        lastT = 0L
    }
}

@Composable
private fun DockBar(
    pagerState: PagerState,
    onSelect: (Int) -> Unit,
    onScrollTo: (Int, Float) -> Unit,
    onSettle: (Int) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val capsule = RoundedCornerShape(50)
    val liquid = isLiquidGlassActive()
    val globalBackdrop = LocalGlassBackdrop.current
    val haptic = LocalHapticFeedback.current
    // 动画开关：Dock 滑块动效（关 = 直接跟手，无滞后 / 拉伸）
    val dockMotion by AppSingletons.preferencesManager.dockSliderMotion.collectAsState(initial = true)

    // 滑块位置：当前页 + 拖动进度 → 实时跟手（这是物理系统的"目标位置"）
    val sliderPos by remember {
        derivedStateOf { pagerState.currentPage + pagerState.currentPageOffsetFraction }
    }

    // ═══ 液态水珠物理：滑块"粘性跟随" + 运动方向拉伸 ═══
    // 拖动时滑块被"拽着走"：滞后跟随（越拖得快、落后越多）、朝运动方向拉长、纵向微压扁；
    // 停止/松手后以欠阻尼弹簧追上并回弹到位 —— 液滴质感。
    val sliderVis = remember { mutableFloatStateOf(0f) }      // 显示位置（格）
    val sliderStretch = remember { mutableFloatStateOf(0f) }  // 拉伸量（0..0.30）
    val sliderOrigin = remember { mutableFloatStateOf(0f) }   // 拉伸原点（0=左固定，1=右固定）
    val sliderPhys = remember { DockSliderPhysics() }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { t ->
                val target = sliderPos
                val ph = sliderPhys
                if (ph.actual.isNaN()) ph.reset(target)
                val dt = if (ph.lastT == 0L) 1f / 60f
                else ((t - ph.lastT) / 1_000_000_000f).coerceIn(1f / 240f, 1f / 30f)
                ph.lastT = t
                if (!dockMotion) {
                    // 开关关闭：直接对齐（无滞后 / 拉伸 / 回弹）
                    ph.actual = target
                    ph.vel = 0f; ph.smoothV = 0f; ph.stretch = 0f
                } else {
                    val lag = target - ph.actual
                    // 二阶弹簧（欠阻尼）：滞后跟随 + 追逐回弹
                    ph.vel += (lag * DOCK_SLIDER_K - ph.vel * DOCK_SLIDER_C) * dt
                    ph.actual += ph.vel * dt
                    ph.smoothV += (ph.vel - ph.smoothV) * (dt * 12f).coerceAtMost(1f)
                    if (kotlin.math.abs(ph.vel) > 0.15f) ph.dir = if (ph.vel > 0f) 1f else -1f
                    // 拉伸 = 滞后量 + 速度共同驱动（液滴被拖拽拉长；收敛力度）
                    val st = (kotlin.math.abs(lag) * 0.18f + kotlin.math.abs(ph.smoothV) * 0.04f)
                        .coerceAtMost(0.16f)
                    ph.stretch += (st - ph.stretch) * (dt * 16f).coerceAtMost(1f)
                    // 静止收敛：精确对齐（消除浮点残余）
                    if (!pagerState.isScrollInProgress &&
                        kotlin.math.abs(lag) < 0.002f && kotlin.math.abs(ph.vel) < 0.03f
                    ) {
                        ph.actual = target; ph.vel = 0f; ph.smoothV = 0f; ph.stretch = 0f
                    }
                }
                sliderVis.floatValue = ph.actual
                sliderStretch.floatValue = ph.stretch
                val ox = if (ph.dir > 0f) 0f else 1f
                if (ox != sliderOrigin.floatValue) sliderOrigin.floatValue = ox
            }
        }
    }

    BoxWithConstraints(
        modifier
            .padding(start = 20.dp, end = 20.dp, bottom = 26.dp)
            // 固定高度！否则滑块 fillMaxHeight 会跟着父级的满屏约束把 Dock 撑满整屏
            .height(60.dp)
            .pointerInput(Unit, enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val longPressMs = viewConfiguration.longPressTimeoutMillis
                    var longPressed = false
                    val startTime = System.currentTimeMillis()
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    var lastTarget = -1f

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break

                        if (!longPressed && System.currentTimeMillis() - startTime >= longPressMs) {
                            longPressed = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }

                        if (longPressed) {
                            // 滑块 / 界面都跟随【手指的绝对位置】：
                            // 第 i 个图标中心 ≈ 第 i 页（格子间线性插值），
                            // 而不是「滑一点点就翻页」的位移百分比。
                            val fraction = (change.position.x / width).coerceIn(0f, 1f)
                            val n = dockItems.size
                            val target = (fraction * n - 0.5f).coerceIn(0f, (n - 1).toFloat())
                            if (kotlin.math.abs(target - lastTarget) > 0.02f) {
                                lastTarget = target
                                // scrollToPage 要求 offset ∈ [-0.5, 0.5]：
                                // 取【最近的整页 + 残余偏移】，绝不能直接传 [0, 1)（否则抛异常）
                                val nearest = target.toInt()
                                    .let { p -> if (target - p > 0.5f) p + 1 else p }
                                    .coerceIn(0, dockItems.lastIndex)
                                val offset = (target - nearest).coerceIn(-0.5f, 0.5f)
                                onScrollTo(nearest, offset)
                            }
                            change.consume()
                        }
                    }

                    // 松手：停在两页中间时平滑归位到最近一页
                    if (longPressed && lastTarget >= 0f) {
                        val nearest = lastTarget.toInt()
                            .let { p -> if (lastTarget - p > 0.5f) p + 1 else p }
                            .coerceIn(0, dockItems.lastIndex)
                        onSettle(nearest)
                    }
                }
            }
    ) {
        val innerPad = 5.dp
        val itemWidth = (maxWidth - innerPad * 2) / dockItems.size

        // ① Dock 玻璃板（同时把自身录制进 barBackdrop，供滑块采样）
        val barBackdrop = rememberLayerBackdrop()
        Box(
            Modifier
                .matchParentSize()
                .clip(capsule)
                .then(if (liquid) Modifier.layerBackdrop(barBackdrop) else Modifier)
                .then(
                    if (liquid) {
                        Modifier.liquidGlass(
                            shape = capsule,
                            surfaceAlpha = 0.16f,
                            blurRadius = 8.dp,
                            refractionHeight = 14.dp,
                            refractionAmount = 30.dp
                        )
                    } else {
                        Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, capsule)
                    }
                )
        )

        // ② 选中滑块（画在图标【下层】：图标永远清晰，不被折射吞掉）
        // - 采样「全局壁纸 + Dock 玻璃板」→ 滑块内部 = 毛玻璃底 + 扭曲折射 + 色散（两种玻璃融合）
        // - depthEffect = true：折射方向叠加径向分量 → 液滴鼓包感
        // - chromaticAberration = true：边缘色散（彩虹条纹）
        // - 液态水珠物理：滞后跟随（sliderVis）+ 方向拉伸（sliderStretch）
        // 先在组合上下文取色：DrawScope 的绘制 lambda 里不允许调用 Composable
        val sliderTint = MaterialTheme.colorScheme.primary
        val sliderBackdrop = if (liquid && globalBackdrop != null) {
            rememberCombinedBackdrop(globalBackdrop, barBackdrop)
        } else null
        // 外层裁剪容器：液滴拉伸/超调时不越过 Dock 边界
        Box(Modifier.matchParentSize().clip(capsule)) {
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            x = kotlin.math.round(innerPad.toPx() + itemWidth.toPx() * sliderVis.floatValue).toInt(),
                            y = 0
                        )
                    }
                    .graphicsLayer {
                        // 液滴被拖拽时：朝运动方向拉长、纵向微压扁（体积感）
                        val s = sliderStretch.floatValue
                        scaleX = 1f + s
                        scaleY = 1f - s * 0.4f
                        transformOrigin = TransformOrigin(sliderOrigin.floatValue, 0.5f)
                    }
                    .width(itemWidth)
                    .fillMaxHeight()
                    .padding(horizontal = 3.dp, vertical = innerPad)
                    .clip(capsule)
                    .then(
                        if (sliderBackdrop != null) {
                            Modifier.drawBackdrop(
                                backdrop = sliderBackdrop,
                                shape = { capsule },
                                effects = {
                                    vibrancy()
                                    blur(1.dp.toPx())
                                    lens(
                                        refractionHeight = 18.dp.toPx(),
                                        refractionAmount = 30.dp.toPx(),
                                        depthEffect = true,
                                        chromaticAberration = true
                                    )
                                },
                                highlight = { Highlight(width = 1.dp, blurRadius = 2.dp) },
                                onDrawSurface = {
                                    drawRect(sliderTint.copy(alpha = 0.12f))
                                }
                            )
                        } else {
                            Modifier.background(
                                sliderTint.copy(alpha = 0.22f),
                                capsule
                            )
                        }
                    )
            )
        }

        // ③ 图标行（最上层：图标永远清晰锐利）
        Row(
            Modifier.fillMaxSize().padding(innerPad),
            verticalAlignment = Alignment.CenterVertically
        ) {
            dockItems.forEach { (label, icon, index) ->
                val active = pagerState.currentPage == index
                val tint = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant

                Box(
                    Modifier
                        .weight(1f)
                        .clip(capsule)
                        .clickable { onSelect(index) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp), tint = tint)
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            color = tint
                        )
                    }
                }
            }
        }
    }
}