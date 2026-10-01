package com.linjing.shareku.ui.component

import android.os.Build
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CancellationException

/**
 * 预见式返回预览开关（跨 Activity 场景）：
 * - 启用（默认）：不注册任何回调 → 系统自带的跟手预览动画生效
 * - 关闭：注册一个"空"动画回调吃掉预测事件 → 手势返回不再有跟手预览，
 *   松手后仍以窗口动画完成返回（更省电）
 *
 * 仅 Android 13+（API33）有意义；低版本无预测性返回。
 */
@Composable
fun BackPreviewGate(
    previewEnabled: Boolean,
    onBack: () -> Unit
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !previewEnabled) {
        PredictiveBackHandler { events ->
            try {
                // 吃掉进度事件：不驱动任何预览
                events.collect { /* no-op */ }
                // 手势提交：直接完成返回
                onBack()
            } catch (_: CancellationException) {
                // 手势取消：无操作
            }
        }
    }
}