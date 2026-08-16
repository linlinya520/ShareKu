package com.linjing.shareku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.Bundle
import android.os.IBinder
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.linjing.shareku.peer.PeerDevice
import com.linjing.shareku.peer.PeerDiscovery
import com.linjing.shareku.peer.PeerTransferClient
import com.linjing.shareku.peer.TransferProgress
import com.linjing.shareku.server.NetworkUtils
import com.linjing.shareku.service.ServerForegroundService
import com.linjing.shareku.ui.component.CustomCard
import com.linjing.shareku.ui.component.QrCodeCard
import com.linjing.shareku.ui.theme.LocalShareTheme
import com.linjing.shareku.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress

class ShareActivity : ComponentActivity() {

    private var receivedFiles = mutableListOf<File>()
    private var isSandbox = false
    private var serviceStarted = false
    private var directEngine: io.ktor.server.engine.EmbeddedServer<*, *>? = null // for standalone share server

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val files = extractFilesFromIntent(intent)
        receivedFiles.addAll(files)
        isSandbox = files.size == 1 && files.first().isFile

        val cacheFiles = files.map { copyToCache(it) }
        receivedFiles.clear()
        receivedFiles.addAll(cacheFiles)
        // 注册为活跃文件（防止自动清理误删）
        cacheFiles.forEach { AppSingletons.activeSharedFiles.add(it.absolutePath) }

        val networkUtils = NetworkUtils()
        val iface = networkUtils.getPreferredInterface("auto")
        val ip = iface?.ipAddress ?: "127.0.0.1"
        // 分享界面使用独立的端口（默认 8085，与主页端口完全独立，互不影响）
        val port = runBlocking { AppSingletons.preferencesManager.sharePort.first() }
        val url = "http://$ip:$port"
        // 同步读取当前主题
        val initialTheme = runBlocking { AppSingletons.preferencesManager.themeMode.first() }
        setContent {
            val themeModeName by AppSingletons.preferencesManager.themeMode.collectAsState(initial = initialTheme)
            val paletteOrdinal by AppSingletons.preferencesManager.paletteStyleOrdinal.collectAsState(initial = 0)
            val paletteStyle = com.linjing.shareku.ui.theme.color.PaletteStyle.entries
                .getOrElse(paletteOrdinal) { com.linjing.shareku.ui.theme.color.PaletteStyle.TONAL_SPOT }
            LocalShareTheme(themeMode = ThemeMode.fromName(themeModeName), paletteStyle = paletteStyle) {
                ShareSheetDialog(
                    files = cacheFiles,
                    url = url,
                    ip = ip,
                    port = port,
                    onClose = {
                        stopServiceIfRunning()
                        cleanupCache()
                        finish()
                    },
                    onStartSharing = { p ->
                        serviceStarted = true
                        startShareService(
                            ip = ip,
                            port = p,
                            files = cacheFiles,
                            singleFileSandbox = isSandbox
                        )
                    },
                    onStopSharing = {
                        stopServiceIfRunning()
                    }
                )
            }
        }
    }

    private fun extractFilesFromIntent(intent: Intent): List<File> {
        val files = mutableListOf<File>()

        // Try clipData first (multiple files, API 16+)
        val clipData = intent.clipData
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                clipData.getItemAt(i).uri?.let { uri ->
                    val file = uriToCacheFile(uri)
                    if (file != null) files.add(file)
                }
            }
        }

        // Fallback to single data URI
        intent.data?.let { uri ->
            val file = uriToCacheFile(uri)
            if (file != null) files.add(file)
        }

        // Try EXTRA_STREAM
        if (files.isEmpty()) {
            val streamUri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
            if (streamUri != null) {
                val file = uriToCacheFile(streamUri)
                if (file != null) files.add(file)
            }
        }

        return files
    }

    private fun uriToCacheFile(uri: android.net.Uri): File? {
        return try {
            // 从 ContentResolver 获取真实文件名（浏览器分享用数字ID，需查询 DISPLAY_NAME）
            val fileName = queryDisplayName(uri) ?: uri.lastPathSegment ?: "shared_file"
            val cacheFile = File(cacheDir, fileName)
            if (!cacheFile.exists()) {
                contentResolver.openInputStream(uri)?.use { input ->
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            cacheFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun queryDisplayName(uri: android.net.Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) { null }
    }

    private fun cleanupCache() {
        receivedFiles.forEach {
            AppSingletons.activeSharedFiles.remove(it.absolutePath)
            it.delete()
        }
        receivedFiles.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!serviceStarted) cleanupCache()
    }

    private fun copyToCache(file: File): File {
        val cacheFile = File(cacheDir, file.name)
        if (!cacheFile.exists()) {
            file.copyTo(cacheFile, overwrite = true)
        }
        return cacheFile
    }

    private fun startShareService(
        ip: String,
        port: Int,
        files: List<File>,
        singleFileSandbox: Boolean
    ) {
        // If main server is already running, start a separate standalone server
        if (AppSingletons.isServerRunning.value) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val srv = com.linjing.shareku.server.ShareKuServer(
                        context = this@ShareActivity,
                        logManager = AppSingletons.logManager,
                        sharedFiles = files,
                        isSingleFileSandbox = singleFileSandbox,
                        allowUpload = false,
                        allowDelete = false,
                        enableWebDav = true
                    )
                    val engine = srv.start(ip, port)
                    engine.start(wait = false)
                    directEngine = engine
                    serviceStarted = true
                } catch (e: Exception) {
                    android.util.Log.e("ShareKu", "Share server failed", e)
                    runOnUiThread {
                        android.widget.Toast.makeText(
                            this@ShareActivity,
                            "端口 $port 启动失败：${e.message}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        } else {
            // No server running → use the foreground service (normal flow)
            val intent = Intent(this, ServerForegroundService::class.java).apply {
                action = ServerForegroundService.ACTION_START
                putExtra(ServerForegroundService.EXTRA_HOST, ip)
                putExtra(ServerForegroundService.EXTRA_PORT, port)
                putStringArrayListExtra(ServerForegroundService.EXTRA_FILES, ArrayList(files.map { it.absolutePath }))
                putExtra(ServerForegroundService.EXTRA_SINGLE_FILE, singleFileSandbox)
                putExtra(ServerForegroundService.EXTRA_UPLOAD, false)
                putExtra(ServerForegroundService.EXTRA_DELETE, false)
                putExtra(ServerForegroundService.EXTRA_WEBDAV, true)
            }
            startService(intent)
            serviceStarted = true
        }
    }

    private fun stopServiceIfRunning() {
        // 独立分享服务器（主页服务在运行时走这里）→ 只停自己，不影响主页
        directEngine?.let { engine ->
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try { engine.stop(1000, 2000) } catch (_: Exception) {}
            }
            directEngine = null
            serviceStarted = false
            return
        }
        // 走前台服务的情况：仅当分享自己启动过服务才停止（避免误关主页服务）
        if (serviceStarted) {
            val intent = Intent(this, ServerForegroundService::class.java).apply {
                action = ServerForegroundService.ACTION_STOP
            }
            startService(intent)
            serviceStarted = false
        }
    }
}

@Composable
fun ShareSheetDialog(
    files: List<File>,
    url: String,
    ip: String,
    port: Int,
    onClose: () -> Unit,
    onStartSharing: (Int) -> Unit,
    onStopSharing: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // 分享界面独立运行状态（与主页服务器互不影响）
    var shareRunning by remember { mutableStateOf(false) }
    var showCopied by remember { mutableStateOf(false) }

    // Port state — editable（写独立 sharePort，不影响主页端口）
    var currentPort by remember { mutableIntStateOf(port) }
    var showPortDialog by remember { mutableStateOf(false) }
    var portInput by remember { mutableStateOf(port.toString()) }
    val displayUrl = "http://$ip:$currentPort"

    // 修改分享端口只写独立配置，与主页端口互不影响
    LaunchedEffect(currentPort) {
        AppSingletons.preferencesManager.setSharePort(currentPort)
    }

    // ═══ 设备直连发送状态 ═══
    var showPeerPanel by remember { mutableStateOf(false) }
    var peers by remember { mutableStateOf<List<PeerDevice>>(emptyList()) }
    var peerScanning by remember { mutableStateOf(false) }
    var peerSendProgress by remember { mutableStateOf<TransferProgress?>(null) }
    var peerSendDone by remember { mutableStateOf(false) }
    var peerSendError by remember { mutableStateOf<String?>(null) }
    val peerDiscovery = remember { PeerDiscovery(context) }

    // 展开面板时自动扫描，收起时停止
    LaunchedEffect(showPeerPanel) {
        if (showPeerPanel) {
            peerScanning = true
            peerDiscovery.startScan()
            // 轮询扫描结果（PeerDiscovery 内部 StateFlow 更新）
            while (true) {
                kotlinx.coroutines.delay(500)
                peers = peerDiscovery.peers.value
                peerScanning = peerDiscovery.isScanning.value
                if (!peerDiscovery.isScanning.value && peers.isEmpty()) break
                if (peers.isNotEmpty()) break
            }
        } else {
            peerDiscovery.stopScan()
        }
    }
    DisposableEffect(Unit) {
        onDispose { peerDiscovery.stopScan() }
    }

    // 发送到指定设备
    fun sendToPeer(peer: PeerDevice) {
        peerSendProgress = null
        peerSendDone = false
        peerSendError = null
        scope.launch {
            val client = PeerTransferClient()
            try {
                client.sendFiles(files, peer.host, peer.port).collect { p ->
                    peerSendProgress = p
                    if (p.done) {
                        peerSendDone = true
                        peerSendProgress = null
                    }
                }
            } catch (e: Exception) {
                peerSendError = e.message ?: "发送失败"
            } finally {
                client.close()
            }
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CustomCard(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            cornerRadius = 28.dp,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            clickable = false, enableHaptic = false
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "共享文件",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "关闭")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // File list
                CustomCard(
                    modifier = Modifier.fillMaxWidth(), cornerRadius = 12.dp,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    clickable = false, enableHaptic = false
                ) {
                    if (files.size == 1) {
                        Text(
                            text = files.first().name,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "已选择 ${files.size} 个文件",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            files.take(3).forEach { file ->
                                Text(
                                    text = "• ${file.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (files.size > 3) {
                                Text(
                                    text = "...and ${files.size - 3} more",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // QR Code — only visible when server is running
                AnimatedVisibility(
                    visible = shareRunning,
                    enter = scaleIn(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        initialScale = 0.6f
                    ) + fadeIn(tween(300)),
                    exit = scaleOut(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        targetScale = 0.6f
                    ) + fadeOut(tween(200))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        QrCodeCard(url = displayUrl, modifier = Modifier.size(180.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "扫码访问共享文件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // URL display & copy + port
                CustomCard(
                    modifier = Modifier.fillMaxWidth(), cornerRadius = 12.dp,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    clickable = false, enableHaptic = false
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = displayUrl,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(displayUrl))
                                showCopied = true
                                scope.launch {
                                    kotlinx.coroutines.delay(2000)
                                    showCopied = false
                                }
                            }) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "复制",
                                    tint = if (showCopied) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        // Quick port change
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "端口: $currentPort",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                portInput = currentPort.toString()
                                showPortDialog = true
                            }) {
                                Text("修改", fontSize = MaterialTheme.typography.labelMedium.fontSize)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 端口提示（与主页端口不同的建议）
                if (!shareRunning) {
                    Text(
                        "注意：此界面端口建议与主页服务器端口不同，避免冲突",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // ═══ 设备直连发送 ═══
                CustomCard(
                    modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    clickable = false, enableHaptic = false
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DevicesOther, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("设备直连发送", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                showPeerPanel = !showPeerPanel
                                if (!showPeerPanel) peerDiscovery.stopScan()
                            }) {
                                Text(if (showPeerPanel) "收起" else "选择设备")
                            }
                        }

                        AnimatedVisibility(visible = showPeerPanel, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                            Column {
                                Spacer(Modifier.height(4.dp))
                                if (peerScanning && peers.isEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("正在扫描附近设备...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (peers.isEmpty() && !peerScanning) {
                                    Text(
                                        "未发现设备。请确认两台设备在同一 WiFi 下，且对方已启动服务器",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(onClick = {
                                        peerScanning = true
                                        peerDiscovery.rescan()
                                    }) { Text("重新扫描") }
                                }
                                peers.forEach { peer ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                                sendToPeer(peer)
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(peer.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                            Text("${peer.host}:${peer.port}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                // 发送进度
                                peerSendProgress?.let { prog ->
                                    if (!prog.done) {
                                        Spacer(Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { prog.percent / 100f },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                            "正在发送 ${prog.fileName} (${prog.fileIndex + 1}/${prog.totalFiles})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (peerSendDone) {
                                    Spacer(Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("已发送到对方设备（对方确认后保存）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                peerSendError?.let {
                                    Spacer(Modifier.height(6.dp))
                                    Text("发送失败: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Start/Stop sharing button
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        if (shareRunning) {
                            // Stop —— 只停本分享服务器
                            onStopSharing()
                            shareRunning = false
                        } else {
                            // Start
                            onStartSharing(currentPort)
                            shareRunning = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    colors = if (shareRunning) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ) else ButtonDefaults.buttonColors()
                ) {
                    if (shareRunning) {
                        Icon(Icons.Default.Stop, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (shareRunning) "停止共享" else "启动共享",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Port dialog
    if (showPortDialog) {
        AlertDialog(
            onDismissRequest = { showPortDialog = false },
            title = { Text("修改端口") },
            text = {
                OutlinedTextField(
                    value = portInput, singleLine = true,
                    onValueChange = { portInput = it },
                    label = { Text("端口号") },
                    placeholder = { Text("8080") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                Button(onClick = {
                    portInput.toIntOrNull()?.let { p ->
                        currentPort = p
                    }
                    showPortDialog = false
                }) { Text("确认") }
            },
            dismissButton = { TextButton(onClick = { showPortDialog = false }) { Text("取消") } }
        )
    }
}