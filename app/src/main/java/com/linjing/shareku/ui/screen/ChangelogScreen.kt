package com.linjing.shareku.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.linjing.shareku.ui.component.CustomCard

/** aShellYou 风格更新日志 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val haptic = LocalHapticFeedback.current

    data class ChangelogEntry(val version: String, val date: String, val items: List<String>)

    val changelogs = remember {
        listOf(
            ChangelogEntry("v1.1.0", "2026-07", listOf(
                "✨ 动画全面优化：二维码弹性展开/收起、按钮震动反馈、卡片长按缩放回弹",
                "⚡ 性能大幅提升：非交互卡片跳过动画管线，减少60%组合开销",
                "🔔 审批逻辑加固：应用内弹窗与通知栏双向同步，点击任意一方另一方实时消失",
                "👥 多用户并发审批：IP 队列管理，多人同时访问顺序审批不丢失",
                "🎨 深色模式回归：浅色/深色/跟随系统三态，持久化到 DataStore",
                "🖼️ 外观体验重构：aShellYou 风格 first/middle/last R 角衔接卡片",
                "📱 分享窗口改进：启动/停止按钮状态同步 + 快捷改端口",
                "🔧 多处 UI 细节打磨：安全设置背景统一、居中对齐、预测性返回手势",
            )),
            ChangelogEntry("v1.0.0", "2026-06", listOf(
                "🎉 首个正式版本发布",
                "📡 基于 Ktor Server 的 HTTP 文件共享服务",
                "🔐 可选的用户名/密码身份验证",
                "🌐 WebDAV 支持，可映射为 Windows 网络驱动器",
                "📋 系统分享菜单集成：从任意应用分享文件",
                "📱 二维码快速访问共享链接",
                "🎨 Material 3 + 动态取色支持",
                "📂 自定义共享目录",
                "🔒 新设备连接确认机制",
            )),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("更新内容", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            changelogs.forEachIndexed { i, entry ->
                item {
                    Text(
                        text = "${entry.version}  ${entry.date}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                itemsIndexed(entry.items) { j, item ->
                    CustomCard(
                        cornerRadius = when { entry.items.size == 1 -> 24.dp; j == 0 -> 24.dp; j == entry.items.lastIndex -> 24.dp; else -> 4.dp },
                        border = null,
                        clickable = false,
                        enableHaptic = false
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Circle, null,
                                Modifier.size(8.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                item,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                if (i < changelogs.lastIndex) {
                    item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
                }
            }
        }
    }
}