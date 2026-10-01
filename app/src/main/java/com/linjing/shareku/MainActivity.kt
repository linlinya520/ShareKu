package com.linjing.shareku

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
                        // ═══ 作者的话（2.0.0 首次运行一次性提示，强制 5 秒后可关闭）═══
                        var showAuthorNote by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            val alreadyShown = prefs.authorNoteShown.first()
                            if (!alreadyShown) {
                                showAuthorNote = true
                                prefs.setAuthorNoteShown(true)
                            }
                        }
                        if (showAuthorNote) {
                            AuthorNoteDialog(onDismiss = { showAuthorNote = false })
                        }

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

@Composable
private fun AuthorNoteDialog(onDismiss: () -> Unit) {
    var canClose by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(5) }
    LaunchedEffect(Unit) {
        delay(5000)
        canClose = true
    }
    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown -= 1
        }
    }
    AlertDialog(
        onDismissRequest = { if (canClose) onDismiss() },
        title = {
            Text(
                "作者的话",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "关于这个软件，方向是越来越迷茫，不知道要做什么功能了，再添加下去就要臃肿了。\n\n" +
                    "你们有什么想法、想添加的功能，都可以加我 QQ（3470176230）告诉我，或者到 GitHub 提 Issue。\n\n" +
                    "没有什么想法的话，短时间内应该不会更新了。代码越来越臃肿，各种 bug 都有（有一些新鲜的功能，比如插件生态并没有完善，甚至很多问题），但其实大多数人也用不到这些功能吧？",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                enabled = canClose
            ) {
                Text(if (canClose) "我知道了" else "请稍候（${countdown}s）")
            }
        },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    )
}