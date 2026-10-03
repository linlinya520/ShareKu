package com.linjing.shareku.ui.screen

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.linjing.shareku.ui.component.CustomCard

/**
 * Dock 布局的「工具」页：
 * 把原设置里的 安全 / 服务器 / 文件操作 / 缓存清理 四个模块
 * 用大标题分隔、直接铺列在同一个可滚动页面里。
 *
 * 各模块复用原 Screen 的 embedded 模式（隐藏各自的顶栏与返回键）。
 */
@Composable
fun ToolsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 8.dp)
    ) {
        // ═══ 快捷 ═══
        SectionHeader("快捷")
        ToolEntry(
            title = "搜索设置",
            subtitle = "快速查找全部设置项（安全 / 外观 / 插件…）",
            icon = Icons.Default.Search,
            onClick = { context.startActivity(Intent(context, SettingsSearchActivity::class.java)) }
        )
        Spacer(Modifier.height(8.dp))
        ToolEntry(
            title = "插件",
            subtitle = "安装 · 管理 · 插件生态",
            icon = Icons.Default.Extension,
            onClick = { context.startActivity(Intent(context, PluginsActivity::class.java)) }
        )

        SectionHeader("诊断")
        LogCaptureCard()

        SectionHeader("安全")
        SecurityScreen(onBack = onBack, embedded = true)

        SectionHeader("服务器")
        ServerScreen(onBack = onBack, embedded = true)

        SectionHeader("文件操作")
        FileOpsScreen(onBack = onBack, embedded = true)

        SectionHeader("缓存清理")
        CacheCleanupScreen(onBack = onBack, embedded = true)

        // 给底部悬浮 dock 留出空间
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun ToolEntry(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    CustomCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        cornerRadius = 20.dp,
        border = null,
        onClick = onClick
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 16.dp, top = 24.dp, bottom = 4.dp)
    )
}