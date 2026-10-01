package com.linjing.shareku.ui.component

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.linjing.shareku.AppSingletons

/**
 * 系统「减弱动效」是否开启（动画时长比例为 0）。
 * 尊重无障碍设置：开启时骨架屏等循环动画改为静态。
 */
@Composable
fun rememberSystemReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        } catch (_: Throwable) {
            1f
        }
        scale == 0f
    }
}

/**
 * 轻量骨架呼吸（对齐 BiliPai 的 SkeletonBreathing）：
 * 2.8s 一个完整周期、只动亮度不动布局；减弱动效时返回静态值。
 */
@Composable
fun rememberGentleSkeletonPulse(): State<Float> {
    // 用户开关：关闭骨架呼吸时静态显示（更省电）；同时尊重系统「减弱动效」
    val breathing by AppSingletons.preferencesManager.skeletonBreathing.collectAsState(initial = true)
    val reduceMotion = rememberSystemReduceMotion()
    if (!breathing || reduceMotion) {
        return rememberUpdatedState(0.5f)
    }
    return rememberInfiniteTransition(label = "gentleSkeleton").animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gentleSkeletonPulse"
    )
}

/** 骨架块：会呼吸的占位（只改 alpha，不改尺寸 → 不触发重新布局） */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    baseAlpha: Float = 0.14f
) {
    // 不在组合阶段解包脉冲值：在绘制阶段读取（drawBehind）→ 逐帧只重绘、不重组
    // （对齐 BiliPai 的性能做法：值只在 draw 阶段被读取）
    val pulse = rememberGentleSkeletonPulse()
    val blockColor = MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .clip(shape)
            .drawBehind {
                val a = baseAlpha * (0.35f + 0.65f * pulse.value)
                drawRect(blockColor.copy(alpha = a))
            }
    )
}