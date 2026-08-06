package com.linjing.shareku.ui.screen

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.linjing.shareku.AppSingletons
import com.linjing.shareku.ui.component.CustomCard
import com.linjing.shareku.ui.theme.ShareThemeWrapper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileOpsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val prefs = AppSingletons.preferencesManager
    val haptic = LocalHapticFeedback.current
    val allowUpload by prefs.allowUpload.collectAsState(initial = false)
    val allowOverwrite by prefs.allowOverwrite.collectAsState(initial = true)
    val allowDelete by prefs.allowDelete.collectAsState(initial = false)
    val receiveDir by prefs.receiveDir.collectAsState(initial = "/sdcard/Download/ShareKu")
    var showDirDialog by remember { mutableStateOf(false) }
    var dirInput by remember { mutableStateOf(receiveDir) }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("文件操作", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.ContextClick); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } })
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {

            Text("权限", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            SwitchRow("允许上传", "允许已连接的设备上传文件", allowUpload) { scope.launch { prefs.setAllowUpload(it) } }
            SwitchRow("允许覆盖", "允许覆盖已有文件（WebDAV 必须）", allowOverwrite) { scope.launch { prefs.setAllowOverwrite(it) } }
            SwitchRow("允许删除", "允许已连接的设备删除文件", allowDelete) { scope.launch { prefs.setAllowDelete(it) } }

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
                        FilledTonalButton(onClick = {
                            dirInput = receiveDir
                            showDirDialog = true
                        }) { Text("更改") }
                    }
                )
            }

            if (showDirDialog) {
                AlertDialog(
                    onDismissRequest = { showDirDialog = false },
                    title = { Text("接收文件保存位置") },
                    text = {
                        Column {
                            Text("设备直连接收的文件将保存到此目录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = dirInput, singleLine = true,
                                onValueChange = { dirInput = it },
                                label = { Text("目录路径") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            scope.launch { prefs.setReceiveDir(dirInput) }
                            showDirDialog = false
                        }) { Text("确认") }
                    },
                    dismissButton = { TextButton(onClick = { showDirDialog = false }) { Text("取消") } }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    CustomCard(cornerRadius = 24.dp, border = null, clickable = false, enableHaptic = false) {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
            supportingContent = { Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingContent = {
                Switch(checked = checked, onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onChange(it)
                })
            }
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