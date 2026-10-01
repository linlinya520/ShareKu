package com.linjing.shareku.ui.navigation

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.linjing.shareku.AppSingletons
import com.linjing.shareku.ui.screen.HomeScreen
import com.linjing.shareku.ui.screen.LogScreen

object Routes {
    const val HOME = "home"
    const val LOG = "log"
}

/** miuix 经典转场缓动（快进慢出） */
private val NavEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val NAV_ANIM_MS = 300

/** 主页被覆盖时的模糊半径（px）：返回时从模糊中逐渐清晰（返回过渡模糊） */
private const val HOME_COVERED_BLUR_PX = 12f

/** 日志页退出时的模糊半径（px）：退出滑动期间渐糊（返回过渡模糊） */
private const val LOG_EXIT_BLUR_PX = 8f

@Composable
fun LocalShareNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    // ═══ 动画与交互开关 ═══
    val prefs = AppSingletons.preferencesManager
    val pageAnim by prefs.pageTransitionAnim.collectAsState(initial = true)
    val blurEnabled by prefs.backTransitionBlur.collectAsState(initial = true)

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
        // ── 转场（miuix 经典风）────────────────────────────────────────
        // push：新页从右滑入；旧页左移 1/5 + 轻微缩小 + 淡出
        // pop：顶页右滑退出；底层页从 0.92 缩放 + 淡入恢复
        // 预测性返回手势（navigation 2.8+ / Android 13+）会自动跟手播放这些动画
        enterTransition = {
            if (pageAnim) {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(NAV_ANIM_MS, easing = NavEasing)
                ) + fadeIn(tween(NAV_ANIM_MS, easing = NavEasing))
            } else EnterTransition.None
        },
        exitTransition = {
            if (pageAnim) {
                slideOutHorizontally(
                    targetOffsetX = { -it / 5 },
                    animationSpec = tween(NAV_ANIM_MS, easing = NavEasing)
                ) + scaleOut(
                    targetScale = 0.96f,
                    animationSpec = tween(NAV_ANIM_MS, easing = NavEasing)
                ) + fadeOut(tween(NAV_ANIM_MS, easing = NavEasing))
            } else ExitTransition.None
        },
        popEnterTransition = {
            if (pageAnim) {
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec = tween(NAV_ANIM_MS, easing = NavEasing)
                ) + fadeIn(tween(NAV_ANIM_MS, easing = NavEasing))
            } else EnterTransition.None
        },
        popExitTransition = {
            if (pageAnim) {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(NAV_ANIM_MS, easing = NavEasing)
                ) + fadeOut(tween(NAV_ANIM_MS, easing = NavEasing))
            } else ExitTransition.None
        }
    ) {
        composable(Routes.HOME) {
            // ── 返回过渡模糊 ──
            // 主页被日志页覆盖期间处于模糊；返回时随手势/动画逐渐清晰。
            // 模糊动画挂在导航 Transition 上 → 预测性返回手势拖动时会被"寻址"跟手播放
            // （静止时无动画开销；SDK<31 无 RenderEffect 时自动降级为无模糊）
            val blurState = transition.animateFloat(
                transitionSpec = {
                    if (targetState == EnterExitState.Visible) {
                        tween(320, easing = NavEasing)
                    } else {
                        tween(160, easing = NavEasing)
                    }
                },
                label = "homeCoveredBlur"
            ) { state ->
                when (state) {
                    EnterExitState.Visible -> 0f
                    EnterExitState.PreEnter -> 0f
                    EnterExitState.PostExit -> if (blurEnabled) HOME_COVERED_BLUR_PX else 0f
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val r = blurState.value
                        renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && r > 0.1f) {
                            RenderEffect
                                .createBlurEffect(r, r, Shader.TileMode.CLAMP)
                                .asComposeRenderEffect()
                        } else null
                    }
            ) {
                HomeScreen(
                    onNavigateToLog = { navController.navigate(Routes.LOG) }
                )
            }
        }
        composable(Routes.LOG) {
            // 返回过渡模糊：日志页退出时渐糊（与滑动同步），进入/恢复保持清晰
            val logBlur = transition.animateFloat(
                transitionSpec = {
                    if (targetState == EnterExitState.PostExit) tween(260, easing = NavEasing)
                    else tween(200, easing = NavEasing)
                },
                label = "logExitBlur"
            ) { state ->
                if (state == EnterExitState.PostExit && blurEnabled) LOG_EXIT_BLUR_PX else 0f
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val r = logBlur.value
                        renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && r > 0.1f) {
                            RenderEffect
                                .createBlurEffect(r, r, Shader.TileMode.CLAMP)
                                .asComposeRenderEffect()
                        } else null
                    }
            ) {
                LogScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}