package com.linjing.shareku.ui.screen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.linjing.shareku.ui.theme.ShareThemeWrapper

/** aShellYou 风格更新日志 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val haptic = LocalHapticFeedback.current

    data class ChangelogEntry(val version: String, val date: String, val items: List<String>)

    val changelogs = remember {
        listOf(
            ChangelogEntry("v1.1.3", "2026-08", listOf(
                "🔍 设备直连传输：mDNS(NSD)自动发现附近设备，App间直接传文件，无需浏览器",
                "📲 直连传输审批：接收端弹出通知，接受/拒绝后通知自动消失",
                "📡 手动输入IP连接：NSD扫不到时可手动输入对方WiFi IP直连",
                "🛡️ 后台定位保活：解决鸿蒙/国产ROM熄屏或切后台后断网问题",
                "🎛️ 主页定位保活快捷开关：含状态实时检测（权限+系统定位服务）",
                "⚙️ 安全设置页新增后台保护区块：定位保活开关+详细说明文案",
                "🔄 端口自动递归：占用时递增端口不闪退，提示建议更换端口",
                "🐛 修复文件名含冒号导致传输EPERM错误",
                "🐛 修复分享服务器与主服务器端口冲突",
                "🐛 修复传输接收目录未生效、文件保存到应用私有目录",
                "⚠️ 未经充分测试，可能存在bug，欢迎提交 Issue",
            )),
            ChangelogEntry("v1.1.2", "2026-08", listOf(
                "设置子页面全部独立为 Activity，享受系统级预测性返回动画",
                "外观体验滚动流畅度大幅优化：LazyColumn 替换为 Column+verticalScroll",
                "缓存清理界面 UI 统一：卡片背景与页面融合，不再凸起",
                "缓存自动清理机制：启动时检查间隔，跳过活跃共享文件",
                "浏览器分享文件名修复：通过 ContentResolver 查询真实文件名",
                "主页新增缓存横条，支持一键手动清理",
                "设置新增缓存清理子页面，可配置自动清理间隔",
                "首页与设置页面切换动画统一为匀速整屏滑动",
            )),
            ChangelogEntry("v1.1.1", "2026-07", listOf(
                "剪贴板面板重构: 可收起为悬浮球, 拖拽移动, 5px死区防误触",
                "修复悬浮球点击穿透到后方文件卡片",
                "修复浏览器返回键退出网站 (history.pushState重复写入)",
                "映射 Z 盘脚本修复: UNC 格式, 去掉 DavWWWRoot, 重启 WebClient",
                "WebDAV PROPFIND 支持 Depth:1, Windows 资源管理器可浏览文件列表",
                "OPTIONS 响应添加 DAV 头, 正确标识 WebDAV 服务",
                "剪贴板 textarea 支持多行文本, 拉取批处理脚本不再揉成一行",
                "网页端新增一键下载映射脚本按钮 (动态IP端口)",
            )),
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

class ChangelogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShareThemeWrapper { ChangelogScreen(onBack = { finish() }) }
        }
    }
}