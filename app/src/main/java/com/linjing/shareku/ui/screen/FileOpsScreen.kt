package com.linjing.shareku.ui.screen

import com.linjing.shareku.ui.component.AdaptiveTextField
import com.linjing.shareku.ui.component.AppTopBar
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.linjing.shareku.AppSingletons
import com.linjing.shareku.ui.component.AppSwitchRow
import com.linjing.shareku.ui.component.CustomCard
import com.linjing.shareku.ui.component.FileBrowserDialog
import com.linjing.shareku.ui.component.MiuixSettingsGroup
import com.linjing.shareku.ui.theme.LocalUiStyle
import com.linjing.shareku.ui.theme.ShareThemeWrapper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileOpsScreen(onBack: () -> Unit, embedded: Boolean = false) {
    val scope = rememberCoroutineScope()
    val prefs = AppSingletons.preferencesManager
    val haptic = LocalHapticFeedback.current
    val allowUpload by prefs.allowUpload.collectAsState(initial = false)
    val allowOverwrite by prefs.allowOverwrite.collectAsState(initial = true)
    val allowDelete by prefs.allowDelete.collectAsState(initial = false)
    val receiveDir by prefs.receiveDir.collectAsState(initial = "/sdcard/Download/ShareKu")
    val allowPeerReceive by prefs.allowPeerReceive.collectAsState(initial = true)
    var showDirBrowser by remember { mutableStateOf(false) }

    if (!embedded) BackHandler { onBack() }

    val body: @Composable (Modifier) -> Unit = { m ->
        Column(m.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {

            Text("权限", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            if (LocalUiStyle.current == "miuix") {
                // MIUI：整组一个背景卡片 + 分割线
                MiuixSettingsGroup(Modifier.fillMaxWidth()) {
                    AppSwitchRow("允许上传", "允许已连接的设备上传文件", allowUpload) { scope.launch { prefs.setAllowUpload(it) } }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    AppSwitchRow("允许覆盖", "允许覆盖已有文件（WebDAV 必须）", allowOverwrite) { scope.launch { prefs.setAllowOverwrite(it) } }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    AppSwitchRow("允许删除", "允许已连接的设备删除文件", allowDelete) { scope.launch { prefs.setAllowDelete(it) } }
                }
            } else {
                SwitchRow("允许上传", "允许已连接的设备上传文件", allowUpload) { scope.launch { prefs.setAllowUpload(it) } }
                SwitchRow("允许覆盖", "允许覆盖已有文件（WebDAV 必须）", allowOverwrite) { scope.launch { prefs.setAllowOverwrite(it) } }
                SwitchRow("允许删除", "允许已连接的设备删除文件", allowDelete) { scope.launch { prefs.setAllowDelete(it) } }
            }

            // ═══ 接收目录 ═══
            Text("接收文件", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            CustomCard(cornerRadius = 24.dp, border = null, clickable = false, enableHaptic = false,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                ListItem(
                    headlineContent = { Text("默认保存位置", style = MaterialTheme.typography.bodyLarge) },
                    supportingContent = {
                        Text(receiveDir, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingContent = {
                        if (LocalUiStyle.current == "miuix") {
                            top.yukonga.miuix.kmp.basic.Button(onClick = {
                                showDirBrowser = true
                            }) { Text("更改") }
                        } else {
                            FilledTonalButton(onClick = {
                                showDirBrowser = true
                            }) { Text("更改") }
                        }
                    }
                )
            }

            // 接收目录选择（自带文件浏览器：支持导航 + Shizuku 受限目录）
            if (showDirBrowser) {
                FileBrowserDialog(
                    initialPath = receiveDir,
                    onConfirm = { path ->
                        scope.launch { prefs.setReceiveDir(path) }
                        showDirBrowser = false
                    },
                    onDismiss = { showDirBrowser = false }
                )
            }

            // ═══ 设备直连 ═══
            Text("设备直连", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            if (LocalUiStyle.current == "miuix") {
                MiuixSettingsGroup(Modifier.fillMaxWidth()) {
                    AppSwitchRow("允许接收直连文件", "设备直连接收的文件将保存到上方目录；关闭后拒绝接收", allowPeerReceive) { scope.launch { prefs.setAllowPeerReceive(it) } }
                }
            } else {
                CustomCard(cornerRadius = 24.dp, border = null, clickable = false, enableHaptic = false,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    AppSwitchRow("允许接收直连文件", "设备直连接收的文件将保存到上方目录；关闭后拒绝接收", allowPeerReceive) { scope.launch { prefs.setAllowPeerReceive(it) } }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (embedded) {
        body(Modifier.fillMaxWidth())
    } else {
        Scaffold(
            topBar = {
                AppTopBar(title = { Text("文件操作", fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.ContextClick); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } })
            }
        ) { pad -> body(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    CustomCard(cornerRadius = 24.dp, border = null, clickable = false, enableHaptic = false) {
        AppSwitchRow(
            title = title,
            subtitle = sub,
            checked = checked,
            onChange = onChange
        )
    }
}

class FileOpsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShareThemeWrapper { FileOpsScreen(onBack = { finish() }) }
        }
    }
}