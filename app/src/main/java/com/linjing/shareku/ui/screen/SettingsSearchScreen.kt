package com.linjing.shareku.ui.screen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.linjing.shareku.ui.component.AdaptiveTextField
import com.linjing.shareku.ui.component.AppTopBar
import com.linjing.shareku.ui.component.CustomCard
import com.linjing.shareku.ui.theme.ShareThemeWrapper

/**
 * 设置项搜索：本地注册表 + 关键词过滤 → 点击结果打开对应设置页面。
 */
data class SettingsSearchEntry(
    val title: String,
    val subtitle: String,
    val category: String,
    val keywords: String,
    val target: String
)

object SettingsSearchRegistry {
    val entries: List<SettingsSearchEntry> = listOf(
        // ═══ 安全 ═══
        SettingsSearchEntry("启用身份验证", "需要用户名和密码才能访问共享", "安全", "auth密码验证登录用户名鉴权", "security"),
        SettingsSearchEntry("连接确认", "新设备连接时需要手动批准", "安全", "confirm批准授权确认允许", "security"),
        SettingsSearchEntry("后台定位保活", "防止熄屏或切后台后断网（仅网络定位）", "安全", "定位保活熄屏断网 location", "security"),
        // ═══ 服务器 ═══
        SettingsSearchEntry("端口设置", "修改服务器监听端口（默认 8080）", "服务器", "port端口8080监听", "server"),
        SettingsSearchEntry("启用 WebDAV", "支持 Windows 映射网络驱动器", "服务器", "webdav windows映射网盘", "server"),
        SettingsSearchEntry("网络接口", "选择服务器绑定的网络接口", "服务器", "网络网卡接口 interface wifi", "server"),
        // ═══ 文件操作 ═══
        SettingsSearchEntry("允许上传", "允许已连接的设备上传文件", "文件操作", "upload上传", "fileops"),
        SettingsSearchEntry("允许覆盖", "允许覆盖已有文件（WebDAV 必须）", "文件操作", "overwrite覆盖", "fileops"),
        SettingsSearchEntry("允许删除", "允许已连接的设备删除文件", "文件操作", "delete删除", "fileops"),
        SettingsSearchEntry("允许接收直连文件", "设备直连的文件将保存到接收目录", "文件操作", "直连 peer接收传输", "fileops"),
        SettingsSearchEntry("共享目录", "设置对外共享的文件夹", "文件操作", "目录文件夹根目录 shared", "fileops"),
        SettingsSearchEntry("接收目录", "设备直连文件的保存位置", "文件操作", "接收保存 receive", "fileops"),
        // ═══ 缓存清理 ═══
        SettingsSearchEntry("启用自动清理", "定期自动清理缓存文件", "缓存清理", "清理自动缓存 cache", "cache"),
        SettingsSearchEntry("手动清理缓存", "立即释放缓存占用空间", "缓存清理", "清理缓存空间释放", "cache"),
        // ═══ 外观 ═══
        SettingsSearchEntry("布局模式", "经典布局 / 底部悬浮 Dock", "外观", "布局 dock经典", "appearance"),
        SettingsSearchEntry("界面风格", "Material 3 / MIUI / 液态玻璃", "外观", "风格主题 miui玻璃 liquid", "appearance"),
        SettingsSearchEntry("深色模式", "浅色 / 深色 / 跟随系统", "外观", "深色暗黑 dark主题", "appearance"),
        SettingsSearchEntry("屏幕帧率", "应用运行时使用的刷新率档位", "外观", "帧率刷新率120 144 hz流畅", "appearance"),
        SettingsSearchEntry("动画与交互", "预见式返回 / 返回模糊 / 转场动画等开关", "外观", "动画交互返回模糊转场骨架滑块效果", "appearance"),
        SettingsSearchEntry("预见式返回预览", "手势返回时页面跟手预览", "外观", "返回手势预览跟手省电", "appearance"),
        SettingsSearchEntry("返回过渡模糊", "返回 / 转场时页面渐糊渐清晰", "外观", "返回模糊过渡 blur", "appearance"),
        SettingsSearchEntry("页面转场动画", "页面进入 / 退出的滑动与缩放", "外观", "转场动画滑动缩放页面", "appearance"),
        SettingsSearchEntry("骨架呼吸动画", "加载占位符轻微呼吸", "外观", "骨架呼吸加载占位", "appearance"),
        SettingsSearchEntry("Dock 滑块动效", "底部滑块的液态跟随 / 拉伸", "外观", "dock滑块液滴动效", "appearance"),
        SettingsSearchEntry("液态玻璃质感", "调节折射 / 模糊 / 色散强度（清澈↔磨砂）", "外观", "玻璃质感折射模糊色散磨砂", "appearance"),
        SettingsSearchEntry("莫奈取色", "从壁纸自动提取主题颜色", "外观", "莫奈取色动态颜色 monet", "appearance"),
        SettingsSearchEntry("配色方案", "选择手动配色风格", "外观", "配色颜色主题色 palette", "appearance"),
        SettingsSearchEntry("全局壁纸", "应用内所有界面的统一背景", "外观", "壁纸背景图片视频 wallpaper", "appearance"),
        SettingsSearchEntry("视频壁纸设置", "循环播放 / 声音 / 音量", "外观", "视频壁纸播放声音音量", "appearance"),
        SettingsSearchEntry("暗黑遮罩浓度", "深色模式下壁纸上叠加的黑色遮罩", "外观", "遮罩浓度深色壁纸", "appearance"),
        // ═══ 插件 ═══
        SettingsSearchEntry("插件管理", "安装 / 启用 / 管理 ShareKu 插件", "插件", "插件 plugin扩展生态", "plugins"),
        SettingsSearchEntry("导入插件", "从 ZIP 安装插件", "插件", "导入 zip安装插件", "plugins"),
        // ═══ 关于 ═══
        SettingsSearchEntry("关于 ShareKu", "版本信息 · 开发者 · 致谢", "关于", "关于版本开发者致谢", "about"),
        SettingsSearchEntry("更新日志", "查看各版本更新内容", "关于", "更新日志 changelog版本", "changelog")
    )

    fun search(query: String): List<SettingsSearchEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return entries.filter {
            it.title.lowercase().contains(q) ||
                it.subtitle.lowercase().contains(q) ||
                it.category.lowercase().contains(q) ||
                it.keywords.lowercase().contains(q)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSearchScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { SettingsSearchRegistry.search(query) }

    Scaffold(
        topBar = { AppTopBar(title = "搜索设置", onBack = onBack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AdaptiveTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索设置项（如：深色、端口、插件…）",
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            when {
                query.isBlank() -> {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
                        Text(
                            "输入关键词，搜索全部设置项\n支持标题、说明与分类（安全 / 外观 / 插件…）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                results.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
                        Text(
                            "未找到「$query」相关的设置",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        items(results, key = { it.title + it.category }) { entry ->
                            CustomCard(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                cornerRadius = 16.dp,
                                border = null,
                                onClick = { onOpen(entry.target) }
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(entry.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                entry.category,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            entry.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class SettingsSearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShareThemeWrapper {
                SettingsSearchScreen(
                    onBack = { finish() },
                    onOpen = { target -> openSettingsTarget(this, target) }
                )
            }
        }
    }
}

/** 根据搜索目标打开对应设置页（供搜索页与外部复用） */
fun openSettingsTarget(context: Context, target: String) {
    val cls: Class<out ComponentActivity>? = when (target) {
        "security" -> SecurityActivity::class.java
        "server" -> ServerActivity::class.java
        "fileops" -> FileOpsActivity::class.java
        "cache" -> CacheCleanupActivity::class.java
        "appearance" -> AppearanceActivity::class.java
        "about" -> AboutActivity::class.java
        "changelog" -> ChangelogActivity::class.java
        "plugins" -> PluginsActivity::class.java
        else -> null
    }
    if (cls != null) context.startActivity(Intent(context, cls))
}