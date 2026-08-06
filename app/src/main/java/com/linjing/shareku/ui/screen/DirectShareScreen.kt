package com.linjing.shareku.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.linjing.shareku.AppSingletons
import com.linjing.shareku.peer.PeerDevice
import com.linjing.shareku.peer.PeerDiscovery
import com.linjing.shareku.peer.PeerTransferClient
import com.linjing.shareku.peer.TransferProgress
import com.linjing.shareku.ui.component.CustomCard
import com.linjing.shareku.ui.theme.ShareThemeWrapper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectShareScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val peerDiscovery = remember { PeerDiscovery(context) }
    val peers by peerDiscovery.peers.collectAsState()
    val isScanning by peerDiscovery.isScanning.collectAsState()

    val isServerRunning by AppSingletons.isServerRunning.collectAsState()
    var showServerDialog by remember { mutableStateOf(false) }

    // Check server status on entry
    LaunchedEffect(Unit) {
        if (!AppSingletons.isServerRunning.value) {
            showServerDialog = true
        }
    }

    // Server not running dialog
    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { onBack() },
            title = { Text("服务器未启动") },
            text = { Text("需要先在主页启动服务器才能扫描到附近设备并进行文件传输。") },
            confirmButton = {
                Button(onClick = {
                    showServerDialog = false
                    onBack()
                }) { Text("好的，返回主页") }
            },
            dismissButton = {
                TextButton(onClick = { showServerDialog = false }) { Text("仍然进入") }
            }
        )
    }

    // File selection
    var selectedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var sendingTarget by remember { mutableStateOf<PeerDevice?>(null) }
    var sendProgress by remember { mutableStateOf<TransferProgress?>(null) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var sendSuccess by remember { mutableStateOf(false) }

    // SAF file picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val files = uris.mapNotNull { uri -> uriToFile(context, uri) }
        selectedFiles = files
    }

    // Scan lifecycle
    LaunchedEffect(Unit) {
        peerDiscovery.startScan()
    }
    DisposableEffect(Unit) {
        onDispose { peerDiscovery.stopScan() }
    }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设备直连", fontWeight = FontWeight.Bold) },
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

            // ═══ 扫描区域 ═══
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("附近设备", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (isScanning) "正在扫描..." else if (peers.isEmpty()) "未发现设备" else "发现 ${peers.size} 台设备",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    peerDiscovery.rescan()
                }) {
                    if (isScanning) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(if (isScanning) "扫描中" else "扫描")
                }
            }

            // ═══ 设备列表 ═══
            if (peers.isEmpty() && !isScanning) {
                CustomCard(cornerRadius = 20.dp, border = null, clickable = false, enableHaptic = false,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(
                        Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.DevicesOther, null, Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Spacer(Modifier.height(12.dp))
                        Text("未发现其他 ShareKu 设备",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("请确保两台设备在同一WiFi网络下，且均开启了服务",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center)
                    }
                }
            } else {
                peers.forEach { peer ->
                    CustomCard(
                        cornerRadius = 20.dp,
                        border = null,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            sendingTarget = peer
                            selectedFiles = emptyList()
                            sendProgress = null
                            sendError = null
                            sendSuccess = false
                        }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PhoneAndroid, null, Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(peer.displayName, style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium)
                                Text("${peer.host}:${peer.port}", style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ═══ 手动连接 ──
            var manualHost by remember { mutableStateOf("") }
            var manualPort by remember { mutableStateOf("8080") }
            var showManualInput by remember { mutableStateOf(false) }

            TextButton(onClick = { showManualInput = !showManualInput }) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (showManualInput) "收起手动连接" else "手动输入 IP 连接")
            }

            AnimatedVisibility(visible = showManualInput, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                CustomCard(cornerRadius = 20.dp, border = null, clickable = false, enableHaptic = false,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = manualHost, singleLine = true,
                            onValueChange = { manualHost = it },
                            label = { Text("IP 地址") },
                            placeholder = { Text("192.168.1.x") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = manualPort, singleLine = true,
                            onValueChange = { manualPort = it },
                            label = { Text("端口") },
                            placeholder = { Text("8080") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Button(onClick = {
                            val host = manualHost.trim()
                            val port = manualPort.trim().toIntOrNull() ?: 8080
                            if (host.isNotEmpty()) {
                                sendingTarget = PeerDevice(name = host, host = host, port = port, serviceName = "manual:$host")
                                selectedFiles = emptyList()
                                sendProgress = null
                                sendError = null
                                sendSuccess = false
                            }
                        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) { Text("连接") }
                    }
                }
            }

            // ═══ 发送目标 & 文件选择 ═══
            AnimatedVisibility(visible = sendingTarget != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.height(8.dp))
                    Text("发送到: ${sendingTarget?.displayName}", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))

                    // Selected files
                    if (selectedFiles.isNotEmpty()) {
                        Text("已选择 ${selectedFiles.size} 个文件:", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        selectedFiles.forEach { file ->
                            CustomCard(cornerRadius = 12.dp, border = null, clickable = false, enableHaptic = false,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.InsertDriveFile, null, Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                        Text(formatFileSize(file.length()), style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }

                    // Action buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            try { filePickerLauncher.launch(arrayOf("*/*")) } catch (_: Exception) {}
                        }) {
                            Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("选择文件")
                        }
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = {
                                val target = sendingTarget ?: return@Button
                                val files = selectedFiles
                                if (files.isEmpty()) return@Button
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                val client = PeerTransferClient()
                                scope.launch {
                                    try {
                                        client.sendFiles(files, target.host, target.port).collect { progress ->
                                            sendProgress = progress
                                            if (progress.done) {
                                                sendSuccess = true
                                                sendingTarget = null
                                                selectedFiles = emptyList()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        sendError = e.message ?: "发送失败"
                                    } finally {
                                        client.close()
                                    }
                                }
                            },
                            enabled = selectedFiles.isNotEmpty() && sendProgress?.done != true,
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(Icons.Default.Send, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("发送")
                        }
                    }

                    // Progress
                    sendProgress?.let { prog ->
                        if (!prog.done) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { prog.percent / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "正在发送 ${prog.fileName}  (${prog.fileIndex + 1}/${prog.totalFiles})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    sendError?.let {
                        Spacer(Modifier.height(4.dp))
                        Text("错误: $it", color = MaterialTheme.colorScheme.error)
                    }

                    if (sendSuccess) {
                        Spacer(Modifier.height(8.dp))
                        CustomCard(cornerRadius = 12.dp, border = null, clickable = false, enableHaptic = false,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("发送成功", color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        LaunchedEffect(sendSuccess) {
                            kotlinx.coroutines.delay(3000)
                            sendSuccess = false
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Convert SAF content URI to a local File via copy to cache, with proper display name */
private fun uriToFile(context: android.content.Context, uri: Uri): File? {
    return try {
        // Get real display name from content resolver
        var name = "file_${System.currentTimeMillis()}"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx)
            }
        }
        // Fallback: try last path segment (sanitize)
        if (name == "file_${System.currentTimeMillis()}") {
            name = uri.lastPathSegment?.replace(":", "_")?.replace("/", "_") ?: name
        }
        // Sanitize filename
        name = name.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
        val cacheFile = java.io.File(context.cacheDir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use { input.copyTo(it) }
        }
        cacheFile
    } catch (_: Exception) { null }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes} B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
}

class DirectShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShareThemeWrapper { DirectShareScreen(onBack = { finish() }) }
        }
    }
}