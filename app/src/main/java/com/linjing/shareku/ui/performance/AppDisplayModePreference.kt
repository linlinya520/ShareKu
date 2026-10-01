package com.linjing.shareku.ui.performance

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.Display
import java.util.Locale
import kotlin.math.abs

/** 自动档：释放应用对显示模式（刷新率）的占用，交还系统动态调度 */
const val SYSTEM_AUTO_DISPLAY_MODE_ID = 0

data class AppDisplayMode(
    val modeId: Int,
    val refreshRate: Float,
    val width: Int,
    val height: Int
)

/** 当前屏幕支持的所有显示模式（modeId + 刷新率 + 分辨率），按刷新率降序 */
fun Activity.supportedAppDisplayModes(): List<AppDisplayMode> {
    val targetDisplay = currentActivityDisplay() ?: return emptyList()
    return targetDisplay.supportedModes
        .map { mode ->
            AppDisplayMode(
                modeId = mode.modeId,
                refreshRate = mode.refreshRate,
                width = mode.physicalWidth,
                height = mode.physicalHeight
            )
        }
        .sortedWith(
            compareByDescending<AppDisplayMode> { it.refreshRate }
                .thenByDescending { it.width * it.height }
                .thenBy { it.modeId }
        )
}

/** 校验用户保存的 modeId 是否仍被支持；不支持则回退到"自动" */
fun normalizePreferredDisplayModeId(
    preferredModeId: Int,
    supportedModes: List<AppDisplayMode>
): Int = preferredModeId.takeIf { requested ->
    requested != SYSTEM_AUTO_DISPLAY_MODE_ID && supportedModes.any { it.modeId == requested }
} ?: SYSTEM_AUTO_DISPLAY_MODE_ID

/** 选项展示文案，例如 "144 Hz · 1080 × 2400" */
fun displayModePreferenceLabel(mode: AppDisplayMode): String {
    val rounded = mode.refreshRate.toInt()
    val rateText = if (abs(mode.refreshRate - rounded) < 0.05f) {
        rounded.toString()
    } else {
        String.format(Locale.ROOT, "%.2f", mode.refreshRate).trimEnd('0').trimEnd('.')
    }
    return "$rateText Hz · ${mode.width} × ${mode.height}"
}

/** 应用一次显式的刷新率档位选择（不做 touch boost / 定时器 / 动态投票，避免与系统调度打架） */
fun Activity.applyPreferredDisplayMode(preferredModeId: Int) {
    val resolvedModeId = normalizePreferredDisplayModeId(
        preferredModeId = preferredModeId,
        supportedModes = supportedAppDisplayModes()
    )
    window.attributes = window.attributes.apply {
        preferredDisplayModeId = resolvedModeId
        preferredRefreshRate = 0f
    }
}

/** 从任意 Context 向上寻找宿主 Activity */
fun Context.findActivityCompat(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun Activity.currentActivityDisplay(): Display? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display
    } else {
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay
    }