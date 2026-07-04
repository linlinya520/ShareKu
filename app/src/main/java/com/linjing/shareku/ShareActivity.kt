package com.linjing.shareku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.linjing.shareku.server.NetworkInterfaceInfo
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val files = extractFilesFromIntent(intent)
        receivedFiles.addAll(files)
        isSandbox = files.size == 1 && files.first().isFile

        val cacheFiles = files.map { copyToCache(it) }
        receivedFiles.clear()
        receivedFiles.addAll(cacheFiles)

        val networkUtils = NetworkUtils()
        val iface = networkUtils.getPreferredInterface("auto")
        val ip = iface?.ipAddress ?: "127.0.0.1"
        val port = if (AppSingletons.isServerRunning.value) 8081 else 8080
        val url = "http://$ip:$port"
        // 同步读取当前主题，避免DataStore异步加载导致的闪黑
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
                        finish()
                    },
                    onStartSharing = {
                        startShareService(
                            ip = ip,
                            port = port,
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
            val fileName = uri.lastPathSegment ?: "shared_file"
            val cacheFile = File(cacheDir, fileName)
            contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            cacheFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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
    }

    private fun stopServiceIfRunning() {
        val intent = Intent(this, ServerForegroundService::class.java).apply {
            action = ServerForegroundService.ACTION_STOP
        }
        startService(intent) // Will stop the service via its onStartCommand
    }
}

@Composable
fun ShareSheetDialog(
    files: List<File>,
    url: String,
    ip: String,
    port: Int,
    onClose: () -> Unit,
    onStartSharing: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var showCopied by remember { mutableStateOf(false) }
    var isSharing by remember { mutableStateOf(false) }

    LaunchedEffect(isSharing) {
        if (isSharing) {
            onStartSharing()
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
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // File list
                if (files.size == 1) {
                    CustomCard(
                        modifier = Modifier.fillMaxWidth(), cornerRadius = 12.dp,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        clickable = false, enableHaptic = false
                    ) {
                        Text(
                            text = files.first().name,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    CustomCard(
                        modifier = Modifier.fillMaxWidth(), cornerRadius = 12.dp,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        clickable = false, enableHaptic = false
                    ) {
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

                // QR Code
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + scaleIn()
                ) {
                    QrCodeCard(
                        url = url,
                        modifier = Modifier.size(180.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "扫码访问共享文件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // URL display & copy
                CustomCard(
                    modifier = Modifier.fillMaxWidth(), cornerRadius = 12.dp,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    clickable = false, enableHaptic = false
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = url,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(url))
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
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Start sharing button
                Button(
                    onClick = { isSharing = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Text("启动共享", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}