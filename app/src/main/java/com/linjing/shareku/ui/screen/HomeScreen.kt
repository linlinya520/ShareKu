package com.linjing.shareku.ui.screen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.linjing.shareku.AppSingletons
import com.linjing.shareku.SettingsActivity
import com.linjing.shareku.server.NetworkUtils
import com.linjing.shareku.service.ServerForegroundService
import com.linjing.shareku.ui.component.CustomCard
import com.linjing.shareku.ui.component.FileBrowserDialog
import com.linjing.shareku.ui.component.QrCodeCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLDecoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToLog: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val prefs = AppSingletons.preferencesManager
    val networkUtils = remember { NetworkUtils() }

    val isServerRunning by AppSingletons.isServerRunning.collectAsState()
    val pendingIp by AppSingletons.pendingConfirmIp.collectAsState()
    var showCopied by remember { mutableStateOf(false) }

    // Collect prefs
    val port by prefs.port.collectAsState(initial = 8080)
    val enableAuth by prefs.enableAuth.collectAsState(initial = false)
    val authUsername by prefs.authUsername.collectAsState(initial = "localshare")
    val authPassword by prefs.authPassword.collectAsState(initial = "share123")
    val enableWebDav by prefs.enableWebDav.collectAsState(initial = true)
    val allowUpload by prefs.allowUpload.collectAsState(initial = false)
    val allowDelete by prefs.allowDelete.collectAsState(initial = false)
    val allowOverwrite by prefs.allowOverwrite.collectAsState(initial = true)
    val requireConnectionConfirm by prefs.requireConnectionConfirm.collectAsState(initial = false)
    val sharedDir by prefs.sharedDir.collectAsState(initial = "/sdcard")
    var showDirDialog by remember { mutableStateOf(false) }
    var dirInput by remember { mutableStateOf(sharedDir) }
    var showFileBrowser by remember { mutableStateOf(false) }
    var showPortDialog by remember { mutableStateOf(false) }
    var portInput by remember { mutableStateOf(port.toString()) }

    // SAF directory picker launcher
    val safDirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            // Convert content URI to filesystem path
            val path = uriToPath(context, it)
            if (path != null) {
                dirInput = path
                scope.launch { prefs.setSharedDir(path) }
            }
        }
    }

    val hasStoragePermission = isStoragePermissionGranted(context)
    var storageOk by remember { mutableStateOf(hasStoragePermission) }
    var permissionDenied by remember { mutableStateOf(!hasStoragePermission) }

    // MANAGE_EXTERNAL_STORAGE permission launcher (Android 11+)
    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = isStoragePermissionGranted(context)
        storageOk = granted
        permissionDenied = !granted
    }

    // READ_EXTERNAL_STORAGE launcher (Android 10 and below)
    val readStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        storageOk = granted
        permissionDenied = !granted
    }

    // Notification permission launcher (Android 13+)
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ignore */ }

    // 请求通知权限 (Android 13+) —— 必须在 notificationLauncher 声明之后
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val interfaces = remember { networkUtils.getAllInterfaces() }
    val currentInterface = interfaces.firstOrNull { it.isWifi }
        ?: interfaces.firstOrNull()

    val ip = currentInterface?.ipAddress ?: "未连接"
    val url = "http://$ip:$port"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ShareKu", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    IconButton(onClick = onNavigateToLog) {
                        Icon(Icons.Default.Terminal, "日志")
                    }
                    IconButton(onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }) {
                        Icon(Icons.Default.Settings, "设置")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Permission check card
            if (!storageOk || permissionDenied) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "需要存储权限",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "ShareKu 需要\"管理所有文件\"权限才能读取和共享你的文件。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:" + context.packageName)
                                    }
                                    storageLauncher.launch(intent)
                                } else {
                                    readStorageLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("前往设置")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Network card
            CustomCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.ContextClick) },
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Wifi, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = currentInterface?.displayName ?: "选择网络接口",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = ip,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Shared directory card
            CustomCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.ContextClick) },
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Folder, null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("挂载目录",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f))
                        Text(sharedDir,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1)
                        Text("网页中可访问此目录下的文件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f))
                    }
                    FilledTonalButton(onClick = {
                        dirInput = sharedDir
                        showDirDialog = true
                    }) { Text("更改") }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── 端口 ──
            CustomCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                onClick = { portInput = port.toString(); showPortDialog = true }
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Dns, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("端口", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        Text("$port", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                scope.launch { prefs.setPort(p) }
                            }
                            showPortDialog = false
                        }) { Text("确认") }
                    },
                    dismissButton = { TextButton(onClick = { showPortDialog = false }) { Text("取消") } }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Change directory dialog
            if (showDirDialog) {
                AlertDialog(
                    onDismissRequest = { showDirDialog = false },
                    title = { Text("更改挂载目录") },
                    text = {
                        Column {
                            Text("输入要共享的文件夹路径，例如 /sdcard/Download",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = dirInput,
                                onValueChange = { dirInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("目录路径") },
                                placeholder = { Text("/sdcard") }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            // Quick directory buttons
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("/storage/emulated/0", "/sdcard/Download", "/sdcard/DCIM").forEach { path ->
                                    SuggestionChip(
                                        onClick = { dirInput = path },
                                        label = { Text(path.split("/").last().ifEmpty { path }, fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            // SAF native file picker button
                            OutlinedButton(
                                onClick = { safDirLauncher.launch(null) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("使用系统文件管理器选择")
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            // Built-in file browser button
                            OutlinedButton(
                                onClick = { showFileBrowser = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Folder, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("使用自带文件管理器选择")
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            scope.launch { prefs.setSharedDir(dirInput) }
                            showDirDialog = false
                        }) { Text("确认") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDirDialog = false }) { Text("取消") }
                    }
                )
            }

            // Server status — always composed, animated transition
            CustomCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.ContextClick) },
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedContent(
                        targetState = isServerRunning,
                        transitionSpec = {
                            if (targetState) {
                                (slideInVertically(
                                    initialOffsetY = { fullHeight -> fullHeight },
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                ) + fadeIn(tween(300))) togetherWith
                                        (slideOutVertically(
                                            targetOffsetY = { fullHeight -> -fullHeight },
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        ) + fadeOut(tween(200)))
                            } else {
                                (slideInVertically(
                                    initialOffsetY = { fullHeight -> -fullHeight },
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                ) + fadeIn(tween(300))) togetherWith
                                        (slideOutVertically(
                                            targetOffsetY = { fullHeight -> fullHeight },
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        ) + fadeOut(tween(200)))
                            }
                        },
                        label = "serverState"
                    ) { running ->
                        if (running) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🟢 服务运行中", style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(12.dp))
                                QrCodeCard(url = url, modifier = Modifier.size(200.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(url, style = MaterialTheme.typography.bodyLarge,
                                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center, maxLines = 1)
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        clipboardManager.setText(AnnotatedString(url))
                                        showCopied = true
                                        scope.launch { delay(2000); showCopied = false }
                                    }) { Text(if (showCopied) "已复制！" else "复制链接") }
                                    Button(onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        generateWindowsMapScript(url, clipboardManager)
                                    }) { Text("映射 Z: 盘") }
                                }
                            }
                        } else {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.FolderOpen, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(72.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("等待设备接入…", style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("从任意应用中分享文件\n或手动启动服务器",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Start/Stop button
            AnimatedContent(
                targetState = isServerRunning,
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                },
                label = "btn"
            ) { running ->
                if (running) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            val intent = Intent(context, ServerForegroundService::class.java).apply {
                                action = ServerForegroundService.ACTION_STOP
                            }
                            context.startService(intent)
                            AppSingletons.setServerRunning(false)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Icon(Icons.Default.Stop, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("停止服务器", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            val intent = Intent(context, ServerForegroundService::class.java).apply {
                                action = ServerForegroundService.ACTION_START
                                putExtra(ServerForegroundService.EXTRA_HOST, ip)
                                putExtra(ServerForegroundService.EXTRA_PORT, port)
                                putStringArrayListExtra(ServerForegroundService.EXTRA_FILES,
                                    ArrayList(listOf(sharedDir)))
                                putExtra(ServerForegroundService.EXTRA_AUTH, enableAuth)
                                putExtra(ServerForegroundService.EXTRA_AUTH_USER, authUsername)
                                putExtra(ServerForegroundService.EXTRA_AUTH_PASS, authPassword)
                                putExtra(ServerForegroundService.EXTRA_WEBDAV, enableWebDav)
                                putExtra(ServerForegroundService.EXTRA_UPLOAD, allowUpload)
                                putExtra(ServerForegroundService.EXTRA_DELETE, allowDelete)
                                putExtra(ServerForegroundService.EXTRA_OVERWRITE, allowOverwrite)
                                putExtra(ServerForegroundService.EXTRA_CONFIRM, requireConnectionConfirm)
                            }
                            context.startService(intent)
                            AppSingletons.setServerRunning(true)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("启动服务器", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("快速分享", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            CustomCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                clickable = false,
                enableHaptic = false
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "在任意文件管理器中选择文件，点击分享，选择 ShareKu 即可。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }

    // Approval dialog
    if (pendingIp != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("新设备请求连接") },
            text = { Text("IP: $pendingIp\n正在请求访问 ShareKu，是否批准？") },
            confirmButton = {
                Button(onClick = {
                    val intent = Intent(context, ServerForegroundService::class.java).apply {
                        action = ServerForegroundService.ACTION_APPROVE
                        putExtra(ServerForegroundService.EXTRA_CONFIRM_IP, pendingIp)
                    }
                    context.startService(intent)
                }) { Text("批准") }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    val intent = Intent(context, ServerForegroundService::class.java).apply {
                        action = ServerForegroundService.ACTION_DENY
                        putExtra(ServerForegroundService.EXTRA_CONFIRM_IP, pendingIp)
                    }
                    context.startService(intent)
                }) { Text("拒绝") }
            }
        )
    }

    // File browser dialog
    if (showFileBrowser) {
        FileBrowserDialog(
            initialPath = sharedDir,
            onConfirm = { path ->
                dirInput = path
                scope.launch { prefs.setSharedDir(path) }
                showFileBrowser = false
                showDirDialog = false
            },
            onDismiss = { showFileBrowser = false }
        )
    }
}

private fun generateWindowsMapScript(url: String, clipboardManager: androidx.compose.ui.platform.ClipboardManager) {
    val webdavUrl = "$url/webdav"
    val script = """
@echo off
echo 正在将 ShareKu 映射为 Z: 盘...
net use Z: $webdavUrl /persistent:no
if %errorlevel%==0 (
    echo 成功映射 Z: 盘
    explorer Z:
) else (
    echo 映射失败。请确认 ShareKu 正在运行且 WebDAV 已启用。
)
pause
    """.trimIndent()
    clipboardManager.setText(AnnotatedString(script))
}

private fun isStoragePermissionGranted(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return Environment.isExternalStorageManager()
    }
    return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
}

/**
 * Convert SAF content URI to filesystem path.
 * Works for primary external storage (content://com.android.externalstorage.documents/tree/primary%3A...).
 * May return null for non-standard providers.
 */
private fun uriToPath(context: android.content.Context, uri: Uri): String? {
    // Try to extract the path from the document tree URI
    // Format: content://com.android.externalstorage.documents/tree/primary%3ADCIM
    val docId = try {
        android.provider.DocumentsContract.getTreeDocumentId(uri)
    } catch (e: Exception) { null } ?: return null

    // Split by colon to get storage type and path
    val parts = docId.split(":")
    if (parts.size < 2) return null

    val type = parts[0] // "primary" for internal storage
    val path = parts[1] // "DCIM" or "Download/something"

    return if (type.equals("primary", ignoreCase = true)) {
        // Decode URL-encoded path and build full path
        val decoded = URLDecoder.decode(path, "UTF-8")
        "/storage/emulated/0/$decoded"
    } else {
        // External SD card
        "/storage/$type/$path"
    }
}