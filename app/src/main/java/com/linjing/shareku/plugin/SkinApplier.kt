package com.linjing.shareku.plugin

import android.content.Context
import com.linjing.shareku.AppSingletons
import java.io.File

/** 将皮肤包设置应用到全局（组件风格 / 配色 / 主题 / 玻璃质感 / 布局 / 壁纸） */
object SkinApplier {
    /** 应用皮肤。返回结果描述 */
    suspend fun apply(context: Context, plugin: PluginInfo): String {
        val skin = plugin.manifest.skin ?: return "该插件不是皮肤包"
        val prefs = AppSingletons.preferencesManager
        skin.style?.let { prefs.setUiStyle(it) }
        skin.darkMode?.let { prefs.setThemeMode(it) }
        skin.palette?.let { prefs.setPaletteStyleOrdinal(it) }
        skin.dynamicColor?.let { prefs.setDynamicColor(it) }
        skin.glassDensity?.let { prefs.setGlassDensity(it.coerceIn(0f, 1f)) }
        skin.layoutMode?.let { prefs.setLayoutMode(it) }
        // 壁纸（皮肤目录内文件 → 应用目录 → 设为本地壁纸源）
        skin.wallpaper?.let { name ->
            val src = File(plugin.dir, name)
            if (src.isFile) {
                val ext = name.substringAfterLast('.', "jpg")
                val dst = File(context.filesDir, "skin_wallpaper_${plugin.manifest.id}.$ext")
                try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        src.copyTo(dst, overwrite = true)
                    }
                    prefs.setWallpaperPath(dst.absolutePath)
                    prefs.setWallpaperScale(1f)
                    prefs.setWallpaperOffsetX(0f)
                    prefs.setWallpaperOffsetY(0f)
                    prefs.setWallpaperRotation(0f)
                    prefs.setWallpaperSource("image")
                } catch (_: Exception) {
                }
            }
        }
        return "已应用皮肤「${plugin.manifest.name}」"
    }

    /** 恢复默认外观（保留用户当前布局模式，不强制切换） */
    suspend fun resetToDefault(context: Context): String {
        val prefs = AppSingletons.preferencesManager
        prefs.setUiStyle("material")
        prefs.setThemeMode("SYSTEM")
        prefs.setPaletteStyleOrdinal(0)
        prefs.setDynamicColor(true)
        prefs.setGlassDensity(0.5f)
        prefs.setWallpaperSource("default")
        prefs.setWallpaperPath("")
        return "已恢复默认外观"
    }
}