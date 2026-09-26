package com.linjing.shareku.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
        if (showLog) {
            Dialog(
                onDismissRequest = { showLog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    LogScreen(onBack = { showLog = false })
                }
            }
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

    // 滑块位置：当前页 + 拖动进度 → 实时跟手
    val sliderPos by remember {
        derivedStateOf { pagerState.currentPage + pagerState.currentPageOffsetFraction }
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
        // 先在组合上下文取色：DrawScope 的绘制 lambda 里不允许调用 Composable
        val sliderTint = MaterialTheme.colorScheme.primary
        val sliderBackdrop = if (liquid && globalBackdrop != null) {
            rememberCombinedBackdrop(globalBackdrop, barBackdrop)
        } else null
        Box(
            Modifier
                .offset {
                    IntOffset(
                        x = kotlin.math.round(innerPad.toPx() + itemWidth.toPx() * sliderPos).toInt(),
                        y = 0
                    )
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