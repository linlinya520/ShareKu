package com.linjing.shareku

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.linjing.shareku.ui.component.LiquidPreRenderOverlay
import com.linjing.shareku.ui.navigation.LocalShareNavHost
import com.linjing.shareku.ui.screen.DockLayout
import com.linjing.shareku.ui.theme.LocalShareTheme
import com.linjing.shareku.ui.theme.ThemeMode
import com.linjing.shareku.widget.ShareKuWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        cleanCacheIfNeeded()
        handleAutoStart(intent)
        ShareKuWidgetProvider.refresh(this)
        setContent {
            val prefs = AppSingletons.preferencesManager
            // 首帧必须用用户持久化的设置：同步读取一次并缓存（remember），
            // 否则 collectAsState 默认值会先渲染出一帧「系统主题/默认样式」→ 主题/布局闪变。
            val initial = remember {
                runBlocking {
                    InitialUiState(
                        themeModeName = prefs.themeMode.first(),
                        dynamicColor = prefs.dynamicColor.first(),
                        paletteOrdinal = prefs.paletteStyleOrdinal.first(),
                        uiStyle = prefs.uiStyle.first(),
                        layoutMode = prefs.layoutMode.first()
                    )
                }
            }
            val themeModeName by prefs.themeMode.collectAsState(initial = initial.themeModeName)
            val dynamicColor by prefs.dynamicColor.collectAsState(initial = initial.dynamicColor)
            val paletteOrdinal by prefs.paletteStyleOrdinal.collectAsState(initial = initial.paletteOrdinal)
            val paletteStyle = com.linjing.shareku.ui.theme.color.PaletteStyle.entries
                .getOrElse(paletteOrdinal) { com.linjing.shareku.ui.theme.color.PaletteStyle.TONAL_SPOT }
            val uiStyle by prefs.uiStyle.collectAsState(initial = initial.uiStyle)
            val layoutMode by prefs.layoutMode.collectAsState(initial = initial.layoutMode)
            LocalShareTheme(
                themeMode = ThemeMode.fromName(themeModeName),
                dynamicColor = dynamicColor,
                paletteStyle = paletteStyle,
                uiStyle = uiStyle
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.ui.graphics.Color.Transparent
                ) {
                    Box(Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        val isClassic = layoutMode != "dock"
                        // 首次启用液态玻璃（经典布局）：一次性预渲染引导，底部正式界面照常渲染完成首绘
                        var classicPreRendering by remember { mutableStateOf(false) }
                        LaunchedEffect(uiStyle, layoutMode) {
                            if (uiStyle == "liquid" && isClassic && !prefs.liquidPreRenderDone.first()) {
                                classicPreRendering = true
                                delay(2600)
                                prefs.setLiquidPreRenderDone(true)
                                classicPreRendering = false
                            }
                        }
                        if (isClassic) {
                            LocalShareNavHost(navController = navController, modifier = Modifier.fillMaxSize())
                        } else {
                            // 第二套布局：底部悬浮 Dock（主页 / 工具 / 外观 / 关于）
                            com.linjing.shareku.ui.screen.DockLayout()
                        }
                        if (classicPreRendering) {
                            LiquidPreRenderOverlay()
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAutoStart(intent)
        ShareKuWidgetProvider.refresh(this)
    }

    /** QS 磁贴 / 桌面小组件点击时带 extra_auto_start=true，自动启动服务器 */
    private fun handleAutoStart(intent: Intent?) {
        if (intent?.getBooleanExtra(ShareKuWidgetProvider.EXTRA_AUTO_START, false) == true) {
            CoroutineScope(Dispatchers.IO).launch {
                ServerStarter.start(applicationContext)
                ShareKuWidgetProvider.refresh(applicationContext)
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

/** MainActivity 首帧需要的用户设置（同步读取一次，避免主题/布局闪变） */
private data class InitialUiState(
    val themeModeName: String,
    val dynamicColor: Boolean,
    val paletteOrdinal: Int,
    val uiStyle: String,
    val layoutMode: String
)