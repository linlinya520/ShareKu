package com.linjing.shareku.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Dock 布局的「工具」页：
 * 把原设置里的 安全 / 服务器 / 文件操作 / 缓存清理 四个模块
 * 用大标题分隔、直接铺列在同一个可滚动页面里。
 *
 * 各模块复用原 Screen 的 embedded 模式（隐藏各自的顶栏与返回键）。
 */
@Composable
fun ToolsScreen(onBack: () -> Unit = {}) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 8.dp)
    ) {
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