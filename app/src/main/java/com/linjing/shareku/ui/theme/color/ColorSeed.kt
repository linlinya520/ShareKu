package com.linjing.shareku.ui.theme.color

import android.graphics.Color as AndroidColor

/**
 * 主题种子色（自定义颜色）工具。
 *
 * 用户可在外观页用「色相 / 饱和度 / 明度」滑条或 HEX 输入框指定任意颜色，
 * 它作为 Material 动态配色的 seed 生成整套配色（任意 PaletteStyle 都适用）。
 */

/** 默认种子色（Material 基准紫） */
val DEFAULT_SEED_COLOR: Int = 0xFF6750A4.toInt()

/** ARGB → "#RRGGBB"（大写，便于显示与粘贴） */
fun argbToHex(argb: Int): String = String.format("#%06X", argb and 0xFFFFFF)

/**
 * HEX 字符串 → ARGB。支持 `#RGB`、`#RRGGBB`、`#AARRGGBB`（有无 `#` 均可）。
 * 不合法返回 null（调用方据此保持原值，不写入脏数据）。
 */
fun hexToArgb(hex: String): Int? {
    val raw = hex.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
    val v = raw.filter { it.isDigit() || it in "abcdefABCDEF" }
    return try {
        when (v.length) {
            3 -> {
                val r = v[0].toString().repeat(2).toInt(16)
                val g = v[1].toString().repeat(2).toInt(16)
                val b = v[2].toString().repeat(2).toInt(16)
                0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
            }
            6 -> 0xFF000000.toInt() or v.toInt(16)
            8 -> v.toLong(16).toInt()
            else -> null
        }
    } catch (_: Throwable) {
        null
    }
}

/** ARGB → HSV（h ∈ 0..360, s/v ∈ 0..1） */
fun argbToHsv(argb: Int): FloatArray = FloatArray(3).also {
    AndroidColor.colorToHSV(argb, it)
}

/** HSV → 不透明 ARGB（h ∈ 0..360, s/v ∈ 0..1） */
fun hsvToArgb(hue: Float, sat: Float, value: Float): Int {
    val c = AndroidColor.HSVToColor(
        floatArrayOf(
            hue.coerceIn(0f, 360f),
            sat.coerceIn(0f, 1f),
            value.coerceIn(0.05f, 1f)
        )
    )
    return c or 0xFF000000.toInt()
}

/** 该背景色上应使用深色还是浅色文字（按亮度阈值粗略判断，用于预览块） */
fun isLightColor(argb: Int): Boolean {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return (0.299 * r + 0.587 * g + 0.114 * b) > 160
}
