package com.linjing.shareku.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.linjing.shareku.ui.component.AppTopBar
import com.linjing.shareku.ui.component.MiuixSettingsGroup
import com.linjing.shareku.ui.component.CustomCard
import com.linjing.shareku.ui.theme.LocalUiStyle
import androidx.compose.foundation.clickable

/** aShellYou风格 — 顶部动画图片 + first/middle/last R角衔接卡片列表 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSecurity: () -> Unit,
    onServer: () -> Unit,
    onFileOps: () -> Unit,
    onCacheCleanup: () -> Unit,
    onAppearance: () -> Unit,
    onAbout: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    // 脉冲动画
    val infiniteTransition = rememberInfiniteTransition("pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    val isMiuix = LocalUiStyle.current == "miuix"

    Scaffold(
        topBar = {
            AppTopBar(
                title = "设置",
                onBack = { haptic.performHapticFeedback(HapticFeedbackType.ContextClick); onBack() }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp), // 无间距，靠 Divider
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // ── 顶部动画图标 ──
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size((72 * pulse).dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                }
            }

            // ── 卡片列表 (first/middle/last R角衔接) ──
            val items = listOf(
                SettingEntry("安全", "身份验证 · 连接确认", Icons.Default.Lock, onSecurity),
                SettingEntry("服务器", "端口 · WebDAV", Icons.Default.Dns, onServer),
                SettingEntry("文件操作", "上传 · 覆盖 · 删除", Icons.Default.Folder, onFileOps),
                SettingEntry("缓存清理", "手动清理 · 自动清理间隔", Icons.Default.Delete, onCacheCleanup),
                SettingEntry("外观", "深色模式 · 莫奈取色 · 配色方案", Icons.Default.Palette, onAppearance),
                SettingEntry("关于", "版本信息 · 开发者 · 致谢", Icons.Default.Info, onAbout),
            )

            if (isMiuix) {
                // ── MIUI 风格：整组 miuix Card + preference 组件（自带分隔线） ──
                item {
                    MiuixSettingsGroup(modifier = Modifier.fillMaxWidth()) {
                        items.forEach { entry ->
                            top.yukonga.miuix.kmp.preference.ArrowPreference(
                                title = entry.title,
                                summary = entry.subtitle,
                                onClick = entry.onClick
                            )
                        }
                    }
                }
            } else {
                // ── Material 风格：first/middle/last R角衔接卡片 ──
                items.forEachIndexed { index, entry ->
                    val isFirst = index == 0
                    val isLast = index == items.lastIndex
                    val isOnly = items.size == 1

                    item {
                        CustomCard(
                            modifier = Modifier.fillMaxWidth(),
                            topStartCorner = if (isFirst || isOnly) 24.dp else 4.dp,
                            topEndCorner = if (isFirst || isOnly) 24.dp else 4.dp,
                            bottomStartCorner = if (isLast || isOnly) 24.dp else 4.dp,
                            bottomEndCorner = if (isLast || isOnly) 24.dp else 4.dp,
                            border = null,
                            onClick = entry.onClick
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(entry.icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(entry.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    Text(entry.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Divider between cards (not after last)
                    if (!isLast) {
                        item {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 0.dp),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private data class SettingEntry(val title: String, val subtitle: String, val icon: ImageVector, val onClick: () -> Unit)