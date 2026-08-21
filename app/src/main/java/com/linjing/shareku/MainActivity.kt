package com.linjing.shareku

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.linjing.shareku.ui.navigation.LocalShareNavHost
import com.linjing.shareku.ui.theme.LocalShareTheme
import com.linjing.shareku.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        cleanCacheIfNeeded()
        setContent {
            val prefs = AppSingletons.preferencesManager
            val themeModeName by prefs.themeMode.collectAsState(initial = "SYSTEM")
            val dynamicColor by prefs.dynamicColor.collectAsState(initial = true)
            val paletteOrdinal by prefs.paletteStyleOrdinal.collectAsState(initial = 0)
            val paletteStyle = com.linjing.shareku.ui.theme.color.PaletteStyle.entries
        .getOrElse(paletteOrdinal) { com.linjing.shareku.ui.theme.color.PaletteStyle.TONAL_SPOT }
    val initialUiStyle = runBlocking { prefs.uiStyle.first() }
    val uiStyle by prefs.uiStyle.collectAsState(initial = initialUiStyle)
    LocalShareTheme(
        themeMode = ThemeMode.fromName(themeModeName),
        dynamicColor = dynamicColor,
        paletteStyle = paletteStyle,
        uiStyle = uiStyle
    ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    LocalShareNavHost(navController = navController, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    private fun cleanCacheIfNeeded() {
        val ctx = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = AppSingletons.preferencesManager
                val interval = prefs.autoCleanIntervalMinutes.first()
                if (interval <= 0) return@launch
                val lastClean = prefs.lastCleanupTime.first()
                val now = System.currentTimeMillis()
                val elapsed = now - lastClean
                if (elapsed < 0 || elapsed >= interval * 60_000L) {
                    CacheUtils.cleanCacheDir(ctx)
                    prefs.setLastCleanupTime(now)
                }
            } catch (_: Exception) {}
        }
    }
}

object CacheUtils {
    fun getCacheSize(context: Context): Long {
        return context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * 递归清理缓存目录（含子目录），返回实际释放的字节数。
     * - 跳过正在共享的文件（activeSharedFiles）
     * - 保留 cacheDir 根目录本身（系统约定）
     * - 删除失败（文件被占用）静默跳过，不中断
     */
    fun cleanCacheDir(context: Context): Long {
        val active = AppSingletons.activeSharedFiles.toSet()
        val root = context.cacheDir
        var freed = 0L
        // 自底向上遍历：先删深层文件/目录，最后处理浅层
        root.walkTopDown().toList().asReversed().forEach { f ->
            if (f == root) return@forEach // 保留根目录
            if (f.absolutePath in active) return@forEach // 正在共享的文件不删
            if (f.isFile) {
                val len = f.length()
                if (f.delete()) freed += len
            } else {
                // 空目录直接删；非空（有文件删不掉）会失败，忽略
                f.delete()
            }
        }
        return freed
    }

    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "${bytes} B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
    }
}