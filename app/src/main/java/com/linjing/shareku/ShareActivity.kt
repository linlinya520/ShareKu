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
        val port = runBlocking { AppSingletons.preferencesManager.port.first() }
        // If server already running (e.g. from main page), auto-offset to avoid conflict
        val actualPort = if (AppSingletons.isServerRunning.value) {
            var p = port + 1
            while (!java.net.ServerSocket(p).use { true }) { p++ }
            p
        } else port
        val url = "http://$ip:$actualPort"
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
                    port = actualPort,
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
        directEngine?.stop(1000, 2000)
        directEngine = null
        if (!AppSingletons.isServerRunning.value || directEngine != null) {
            // If we started a standalone server, stop it; otherwise stop the main service
        }
        val intent = Intent(this, ServerForegroundService::class.java).apply {
            action = ServerForegroundService.ACTION_STOP
        }
        startService(intent)
    }
}

@Composable
fun ShareSheetDialog(
    files: List<File>,
    url: String,
    ip: String,
    port: Int,
    onClose: () -> Unit,
    onStartSharing: (Int) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val isServerRunning by AppSingletons.isServerRunning.collectAsState()
    var showCopied by remember { mutableStateOf(false) }

    // Port state — editable
    var currentPort by remember { mutableIntStateOf(port) }
    var showPortDialog by remember { mutableStateOf(false) }
    var portInput by remember { mutableStateOf(port.toString()) }
    val displayUrl = "http://$ip:$currentPort"

    // Sync port when changed via dialog
    LaunchedEffect(currentPort) {
        AppSingletons.preferencesManager.setPort(currentPort)
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
                    visible = isServerRunning,
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

                // Start/Stop sharing button
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        if (isServerRunning) {
                            // Stop
                            val intent = Intent(context, ServerForegroundService::class.java).apply {
                                action = ServerForegroundService.ACTION_STOP
                            }
                            context.startService(intent)
                            AppSingletons.setServerRunning(false)
                        } else {
                            // Start
                            onStartSharing(currentPort)
                            AppSingletons.setServerRunning(true)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    colors = if (isServerRunning) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ) else ButtonDefaults.buttonColors()
                ) {
                    if (isServerRunning) {
                        Icon(Icons.Default.Stop, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (isServerRunning) "停止共享" else "启动共享",
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