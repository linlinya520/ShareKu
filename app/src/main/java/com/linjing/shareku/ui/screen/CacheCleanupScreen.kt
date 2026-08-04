package com.linjing.shareku.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.linjing.shareku.AppSingletons
import com.linjing.shareku.CacheUtils
import com.linjing.shareku.ui.component.CustomCard
import com.linjing.shareku.ui.theme.ShareThemeWrapper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheCleanupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val prefs = AppSingletons.preferencesManager

    val initialInterval = runBlocking { prefs.autoCleanIntervalMinutes.first() }
    val autoCleanInterval by prefs.autoCleanIntervalMinutes.collectAsState(initial = initialInterval)
    var intervalInput by remember { mutableStateOf(autoCleanInterval.toString()) }
    var showCustomDialog by remember { mutableStateOf(false) }

    // 实时计算缓存大小
    var cacheSizeBytes by remember { mutableLongStateOf(0L) }
    var showCleaned by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        cacheSizeBytes = CacheUtils.getCacheSize(context)
    }

    androidx.activity.compose.BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("缓存清理", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── 当前缓存大小 ──
            Text("当前缓存", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            CustomCard(cornerRadius = 24.dp, border = null, clickable = false, enableHaptic = false,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("缓存大小", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            CacheUtils.formatSize(cacheSizeBytes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    FilledTonalButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        scope.launch {
                            CacheUtils.cleanCacheDir(context)
                            cacheSizeBytes = CacheUtils.getCacheSize(context)
                            showCleaned = true
                            kotlinx.coroutines.delay(2000)
                            showCleaned = false
                        }
                    }) {
                        if (showCleaned) Text("已清理")
                        else {
                            Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("清理")
                        }
                    }
                }
            }

            // ── 自动清理 ──
            Text("自动清理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            CustomCard(cornerRadius = 24.dp, border = null, clickable = false, enableHaptic = false,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(4.dp)) {
                    ListItem(
                        headlineContent = { Text("启用自动清理", style = MaterialTheme.typography.bodyLarge) },
                        supportingContent = {
                            Text(
                                if (autoCleanInterval > 0) "每 ${formatInterval(autoCleanInterval)} 自动清理一次"
                                else "未启用",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Switch(checked = autoCleanInterval > 0, onCheckedChange = { enabled ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch {
                                    if (enabled) prefs.setAutoCleanInterval(60)
                                    else prefs.setAutoCleanInterval(0)
                                }
                            })
                        }
                    )

                    AnimatedVisibility(visible = autoCleanInterval > 0, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Text("自动清理间隔", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(30, 60, 180, 360, 720).forEach { mins ->
                                    FilterChip(
                                        selected = autoCleanInterval == mins,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            scope.launch { prefs.setAutoCleanInterval(mins) }
                                        },
                                        label = { Text(formatInterval(mins), fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = {
                                intervalInput = autoCleanInterval.toString()
                                showCustomDialog = true
                            }) { Text("自定义间隔", fontSize = MaterialTheme.typography.labelMedium.fontSize) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // 自定义间隔弹窗
    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("自定义清理间隔") },
            text = {
                Column {
                    Text("输入间隔时间（分钟），最小 5 分钟。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = intervalInput, singleLine = true,
                        onValueChange = { intervalInput = it },
                        label = { Text("分钟") },
                        placeholder = { Text("60") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val mins = intervalInput.toIntOrNull()?.coerceAtLeast(5) ?: 60
                    scope.launch { prefs.setAutoCleanInterval(mins) }
                    showCustomDialog = false
                }) { Text("确认") }
            },
            dismissButton = { TextButton(onClick = { showCustomDialog = false }) { Text("取消") } }
        )
    }
}

private fun formatInterval(minutes: Int): String {
    return when {
        minutes < 60 -> "${minutes} 分钟"
        minutes < 1440 -> "${minutes / 60} 小时"
        else -> "${minutes / 1440} 天"
    }
}

class CacheCleanupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShareThemeWrapper { CacheCleanupScreen(onBack = { finish() }) }
        }
    }
}