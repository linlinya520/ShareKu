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
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.linjing.shareku.ui.navigation.LocalShareNavHost
import com.linjing.shareku.ui.theme.LocalShareTheme
import com.linjing.shareku.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 自动缓存清理：每次启动检测时间间隔
        cleanCacheIfNeeded()
        setContent {
            val prefs = AppSingletons.preferencesManager
            val themeModeName by prefs.themeMode.collectAsState(initial = "SYSTEM")
            val dynamicColor by prefs.dynamicColor.collectAsState(initial = true)
            val paletteOrdinal by prefs.paletteStyleOrdinal.collectAsState(initial = 0)
            val paletteStyle = com.linjing.shareku.ui.theme.color.PaletteStyle.entries
                .getOrElse(paletteOrdinal) { com.linjing.shareku.ui.theme.color.PaletteStyle.TONAL_SPOT }
            LocalShareTheme(
                themeMode = ThemeMode.fromName(themeModeName),
                dynamicColor = dynamicColor,
                paletteStyle = paletteStyle
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    LocalShareNavHost(
                        navController = navController,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    private fun cleanCacheIfNeeded() {
        val ctx = applicationContext
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
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
            } catch (_: Exception) { /* 清理失败不崩溃 */ }
        }
    }
}

/** 缓存工具：计算大小、清理 */
object CacheUtils {
    fun getCacheSize(context: Context): Long {
        return context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun cleanCacheDir(context: Context): Int {
        val active = AppSingletons.activeSharedFiles.toSet()
        var count = 0
        context.cacheDir.listFiles()?.forEach { f ->
            if (f.isFile && f.absolutePath !in active && f.delete()) count++
        }
        return count
    }

    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes} B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
        }
    }
}