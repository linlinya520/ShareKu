package com.linjing.shareku.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset

/**
 * 动画规格 — 从 OriginUI (VigourOS 16) 系统桌面反编译提取
 *
 * 提取源: 全局动效_2.5.6.5 / 系统桌面_16.0.0.716 / 系统界面_16.0.7.251
 *
 * 核心发现:
 * - 触感动画用 pathInterpolator(0.25,0.45,0.3,1.0) — 轻柔先慢后匀
 * - 弹窗用双阶段: 0.6→1.01 过冲 (200ms) + 1.01→0.99 回弹 (200ms)
 * - 底部菜单用 18px 过冲: 100%→-18 (250ms) + -18→18 归位 (300ms)
 * - Fragment 转场: scale 0.85→1.0 (300ms) + 13ms 淡入
 */
object ShareKuAnimationSpecs {
    // ══════ 按压/卡片缩放 (OriginUI pathInterpolator 0.25,0.45,0.3,1.0) ══════
    const val PressDamping = 0.45f        // OriginUI: 轻柔先慢后匀 → 0.45
    const val PressStiffness = 500f       // 比系统默认更灵敏

    val springFloat: SpringSpec<Float> = spring(
        dampingRatio = PressDamping,
        stiffness = PressStiffness
    )
    val springDp: SpringSpec<Dp> = spring(
        dampingRatio = PressDamping,
        stiffness = PressStiffness
    )

    // ══════ QR/弹窗展开 (OriginUI dialog enter: 双阶段过冲) ══════
    // Phase1: 0.6→1.01 with pathInterpolator(0.14,0.89,0.56,1.0)
    // Phase2: 1.01→0.99 with pathInterpolator(0.13,0.0,0.33,1.0)
    const val ExpandDamping = 0.38f       // 更明显的过冲感
    const val ExpandStiffness = 600f      // 更快响应

    val springExpand: SpringSpec<Float> = spring(
        dampingRatio = ExpandDamping,
        stiffness = ExpandStiffness
    )
    val springExpandOffset = spring<IntOffset>(
        dampingRatio = ExpandDamping,
        stiffness = ExpandStiffness
    )

    // ══════ 滑动/菜单滑入 (OriginUI bottom sheet: 100%→-18→+18) ══════
    // translate with pathInterpolator(0.2,0.7,0.6,1.0) + pathInterpolator(0.19,0.0,0.33,1.0)
    const val SlideDamping = 0.35f        // 过冲18px → 强回弹
    const val SlideStiffness = 400f       // 跟手但不过快

    val springSlide: SpringSpec<Float> = spring(
        dampingRatio = SlideDamping,
        stiffness = SlideStiffness
    )

    // ══════ Fragment转场缩放 (OriginUI: 0.85→1.0 300ms) ══════
    // fast_out_extra_slow_in: M0,0 C0.05,0 0.133,0.06 0.167,0.4 C0.208,0.82 0.25,1 1,1
    const val TransitionDamping = 0.6f    // 平滑进入，近乎线性后半段
    const val TransitionStiffness = 450f

    val springTransition: SpringSpec<Float> = spring(
        dampingRatio = TransitionDamping,
        stiffness = TransitionStiffness
    )

    // ══════ 淡入淡出 (OriginUI: 50ms 快速) ══════
    val fadeInFast = tween<Float>(150)
    val fadeOutFast = tween<Float>(100)
    val fadeInSlow = tween<Float>(300)
    val fadeOutSlow = tween<Float>(200)

    // ══════ OriginUI 原始贝塞尔点 (供参考) ══════
    // 触感:      pathInterpolator(0.25, 0.45, 0.3, 1.0)
    // 弹窗进入1: pathInterpolator(0.14, 0.89, 0.56, 1.0) — 前期急速
    // 弹窗进入2: pathInterpolator(0.13, 0.0, 0.33, 1.0) — 回弹阶段
    // 弹窗退出:  pathInterpolator(0.56, 0.24, 0.62, 1.0)
    // 菜单进入:  pathInterpolator(0.2, 0.7, 0.6, 1.0)
    // 菜单回弹:  pathInterpolator(0.19, 0.0, 0.33, 1.0)
    // 菜单退出1: pathInterpolator(0.54, 0.18, 0.7, 1.0)
    // 菜单退出2: pathInterpolator(0.4, 0.0, 0.88, 0.41)
    // 快速缓入:  pathInterpolator(0.4, 0.0, 0.2, 1.0)
    // Fragment:  path(M0,0 C0.05,0 0.133,0.06 0.167,0.4 C0.208,0.82 0.25,1 1,1)
}
