package com.linjing.shareku.ui.screen

import com.linjing.shareku.ui.component.AppSwitch
import com.linjing.shareku.ui.component.AppTopBar
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.location.LocationManager
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.linjing.shareku.AppSingletons
import com.linjing.shareku.CacheUtils
import com.linjing.shareku.SettingsActivity
import com.linjing.shareku.ui.screen.DirectShareActivity
import com.linjing.shareku.server.NetworkUtils
import com.linjing.shareku.service.ServerForegroundService
import com.linjing.shareku.ui.component.CustomCard
import com.linjing.shareku.ui.component.FileBrowserDialog
import com.linjing.shareku.ui.component.QrCodeCard
import com.linjing.shareku.ui.theme.LocalUiStyle
import com.linjing.shareku.ui.theme.ShareKuAnimationSpecs
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
    val uiStyle by prefs.uiStyle.collectAsState(initial = "material")
    val networkUtils = remember { NetworkUtils() }

    val isServerRunning by AppSingletons.isServerRunning.collectAsState()
    val pendingIp by AppSingletons.pendingConfirmIp.collectAsState()
    val pendingConfirmCode by AppSingletons.pendingConfirmCode.collectAsState()
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
    val enableLocationKeepAlive by prefs.enableLocationKeepAlive.collectAsState(initial = true)
    val sharedDir by prefs.sharedDir.collectAsState(initial = "/sdcard")
    var showDirDialog by remember { mutableStateOf(false) }
    var dirInput by remember { mutableStateOf(sharedDir) }
    var showFileBrowser by remember { mutableStateOf(false) }
    var showPortDialog by remember { mutableStateOf(false) }
    var portInput by remember { mutableStateOf(port.toString()) }
    var showLocExplainDialog by remember { mutableStateOf(false) }
    var showLocServiceOffDialog by remember { mutableStateOf(false) }

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

    // Location permission launcher (background keepalive)
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* ignore */ }
    // Location service (system toggle) launcher
    val locationServiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* re-check after return */ }

    // ── 定位辅助函数 ──
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    fun isLocationServiceOn(): Boolean {
        val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) || lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

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
            AppTopBar(
                title = "ShareKu",
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
                    if (LocalUiStyle.current == "miuix") {
                        top.yukonga.miuix.kmp.basic.Button(onClick = {
                            dirInput = sharedDir
                            showDirDialog = true
                        }) { Text("更改") }
                    } else {
                        FilledTonalButton(onClick = {
                            dirInput = sharedDir
                            showDirDialog = true
                        }) { Text("更改") }
                    }
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
                if (LocalUiStyle.current == "miuix") {
                    Dialog(
                        onDismissRequest = { showPortDialog = false },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                            Column(Modifier.padding(20.dp)) {
                                Text("修改端口", style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title3)
                                Spacer(Modifier.height(12.dp))
                                top.yukonga.miuix.kmp.basic.TextField(
                                    value = portInput,
                                    onValueChange = { portInput = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = "端口号",
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                Spacer(Modifier.height(20.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    top.yukonga.miuix.kmp.basic.TextButton(text = "取消", onClick = { showPortDialog = false })
                                    Spacer(Modifier.padding(start = 8.dp))
                                    top.yukonga.miuix.kmp.basic.Button(
                                        onClick = {
                                            portInput.toIntOrNull()?.let { p ->
                                                scope.launch { prefs.setPort(p) }
                                            }
                                            showPortDialog = false
                                        },
                                        colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColorsPrimary()
                                    ) { Text("确认") }
                                }
                            }
                        }
                    }
                } else {
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
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Change directory dialog
            if (showDirDialog) {
                if (LocalUiStyle.current == "miuix") {
                    Dialog(
                        onDismissRequest = { showDirDialog = false },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                            Column(Modifier.padding(20.dp)) {
                                Text("更改挂载目录", style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title3)
                                Spacer(Modifier.height(8.dp))
                                Text("输入要共享的文件夹路径，例如 /sdcard/Download",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(12.dp))
                                top.yukonga.miuix.kmp.basic.TextField(
                                    value = dirInput,
                                    onValueChange = { dirInput = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = "目录路径",
                                    singleLine = true
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("/storage/emulated/0", "/sdcard/Download", "/sdcard/DCIM").forEach { path ->
                                        top.yukonga.miuix.kmp.basic.TextButton(
                                            text = path.split("/").last().ifEmpty { path },
                                            onClick = { dirInput = path }
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                top.yukonga.miuix.kmp.basic.Button(
                                    onClick = { safDirLauncher.launch(null) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("使用系统文件管理器选择")
                                }
                                Spacer(Modifier.height(8.dp))
                                top.yukonga.miuix.kmp.basic.Button(
                                    onClick = { showFileBrowser = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Folder, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("使用自带文件管理器选择")
                                }
                                Spacer(Modifier.height(20.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    top.yukonga.miuix.kmp.basic.TextButton(text = "取消", onClick = { showDirDialog = false })
                                    Spacer(Modifier.padding(start = 8.dp))
                                    top.yukonga.miuix.kmp.basic.Button(
                                        onClick = {
                                            scope.launch { prefs.setSharedDir(dirInput) }
                                            showDirDialog = false
                                        }
                                    ) { Text("确认") }
                                }
                            }
                        }
                    }
                } else {
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
                                    animationSpec = ShareKuAnimationSpecs.springExpandOffset
                                ) + fadeIn(ShareKuAnimationSpecs.fadeInSlow)) togetherWith
                                        (slideOutVertically(
                                            targetOffsetY = { fullHeight -> -fullHeight },
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = 500f
                                            )
                                        ) + fadeOut(ShareKuAnimationSpecs.fadeOutFast))
                            } else {
                                (slideInVertically(
                                    initialOffsetY = { fullHeight -> -fullHeight },
                                    animationSpec = ShareKuAnimationSpecs.springExpandOffset
                                ) + fadeIn(ShareKuAnimationSpecs.fadeInSlow)) togetherWith
                                        (slideOutVertically(
                                            targetOffsetY = { fullHeight -> fullHeight },
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = 500f
                                            )
                                        ) + fadeOut(ShareKuAnimationSpecs.fadeOutFast))
                            }
                        },
                        label = "serverState"
                    ) { running ->
                        if (running) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🔆 服务运行中", style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(12.dp))
                                QrCodeCard(url = url, modifier = Modifier.size(200.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(url, style = MaterialTheme.typography.bodyLarge,
                                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center, maxLines = 1)
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (LocalUiStyle.current == "miuix") {
                                        top.yukonga.miuix.kmp.basic.Button(onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                            clipboardManager.setText(AnnotatedString(url))
                                            showCopied = true
                                            scope.launch { delay(2000); showCopied = false }
                                        }) { Text(if (showCopied) "已复制！" else "复制链接") }
                                        top.yukonga.miuix.kmp.basic.Button(onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                            generateWindowsMapScript(
                                                url = url,
                                                authEnabled = enableAuth,
                                                authUser = authUsername,
                                                authPass = authPassword,
                                                clipboardManager = clipboardManager
                                            )
                                        }) { Text("映射 Z: 盘") }
                                    } else {
                                        FilledTonalButton(onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                            clipboardManager.setText(AnnotatedString(url))
                                            showCopied = true
                                            scope.launch { delay(2000); showCopied = false }
                                        }) { Text(if (showCopied) "已复制！" else "复制链接") }
                                        Button(onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                            generateWindowsMapScript(
                                                url = url,
                                                authEnabled = enableAuth,
                                                authUser = authUsername,
                                                authPass = authPassword,
                                                clipboardManager = clipboardManager
                                            )
                                        }) { Text("映射 Z: 盘") }
                                    }
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
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("若启动后闪退，可能是端口 $port 被占用，\n请在设置中更换端口",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }

            // ── 后台定位保活快捷开关 ──
            Spacer(Modifier.height(14.dp))
            if (!isServerRunning) {
                CustomCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    border = null,
                    clickable = false,
                    enableHaptic = false,
                    colors = CardDefaults.cardColors(
                        containerColor = if (enableLocationKeepAlive)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocationOn, null,
                                tint = if (enableLocationKeepAlive) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("后台定位保活", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text("防止熄屏/切后台后断网", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            AppSwitch(checked = enableLocationKeepAlive, onCheckedChange = { v ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch { prefs.setEnableLocationKeepAlive(v) }
                            })
                        }
                        if (enableLocationKeepAlive) {
                            Spacer(Modifier.height(8.dp))
                            val locPermOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || hasLocationPermission()
                            val locSvcOk = isLocationServiceOn()
                            when {
                                !locPermOk -> Text("⚠️ 未授权定位权限，点击启动时将弹窗引导授权",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                !locSvcOk -> Text("⚠️ 系统定位服务已关闭，需开启才能保活",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
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
                    if (uiStyle == "miuix") {
                        top.yukonga.miuix.kmp.basic.Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                val intent = Intent(context, ServerForegroundService::class.java).apply {
                                    action = ServerForegroundService.ACTION_STOP
                                }
                                context.startService(intent)
                                AppSingletons.setServerRunning(false)
                            },
                            modifier = Modifier.fillMaxWidth()
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
                    }
                } else {
                    // Reusable start helper
                    fun doStartService() {
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
                    }

                    if (uiStyle == "miuix") {
                        top.yukonga.miuix.kmp.basic.Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                if (enableLocationKeepAlive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    when {
                                        !hasLocationPermission() -> showLocExplainDialog = true
                                        !isLocationServiceOn() -> showLocServiceOffDialog = true
                                        else -> doStartService()
                                    }
                                } else {
                                    doStartService()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("启动服务器", fontWeight = FontWeight.Bold)
                        }
                    } else {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            // ═══ 定位保活预检 ═══
                            if (enableLocationKeepAlive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                when {
                                    !hasLocationPermission() -> showLocExplainDialog = true
                                    !isLocationServiceOn() -> showLocServiceOffDialog = true
                                    else -> doStartService()
                                }
                            } else {
                                doStartService()
                            }
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

            // ── 设备直连 ──
            Spacer(modifier = Modifier.height(12.dp))
            CustomCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    context.startActivity(Intent(context, DirectShareActivity::class.java))
                },
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.DevicesOther, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("设备直连", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f))
                        Text("两台手机直接传输文件，无需浏览器",
                            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }

            // ── 缓存清理横条 ──
            Spacer(modifier = Modifier.height(12.dp))
            var cacheSizeBytes by remember { mutableLongStateOf(0L) }
            var showCleanedToast by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                cacheSizeBytes = CacheUtils.getCacheSize(context)
            }
            CustomCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    scope.launch {
                        CacheUtils.cleanCacheDir(context)
                        cacheSizeBytes = CacheUtils.getCacheSize(context)
                        showCleanedToast = true
                        delay(2000)
                        showCleanedToast = false
                    }
                },
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("应用缓存", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (showCleanedToast) "清理成功"
                            else "当前缓存: ${CacheUtils.formatSize(cacheSizeBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (showCleanedToast) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    TextButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        scope.launch {
                            CacheUtils.cleanCacheDir(context)
                            cacheSizeBytes = CacheUtils.getCacheSize(context)
                            showCleanedToast = true
                            delay(2000)
                            showCleanedToast = false
                        }
                    }) {
                        Text(if (showCleanedToast) "✓" else "清理", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Approval dialog —— 展示一次性验证码（码即授权凭证），拒绝 + 右上角关闭
    if (pendingIp != null) {
        val pendingCode = pendingConfirmCode
        Dialog(onDismissRequest = { AppSingletons.dequeuePendingIp() }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "新设备请求访问",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { AppSingletons.dequeuePendingIp() }) {
                            Icon(Icons.Default.Close, "关闭")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("IP: $pendingIp", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "一次性验证码",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        pendingCode ?: "",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "把此码告知对方，对方输入后即可访问。验证码仅可使用一次，5分钟内有效。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(20.dp))
                    if (LocalUiStyle.current == "miuix") {
                        top.yukonga.miuix.kmp.basic.Button(
                            onClick = {
                                val intent = Intent(context, ServerForegroundService::class.java).apply {
                                    action = ServerForegroundService.ACTION_DENY
                                    putExtra(ServerForegroundService.EXTRA_CONFIRM_IP, pendingIp)
                                }
                                context.startService(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("拒绝该设备") }
                    } else {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(context, ServerForegroundService::class.java).apply {
                                    action = ServerForegroundService.ACTION_DENY
                                    putExtra(ServerForegroundService.EXTRA_CONFIRM_IP, pendingIp)
                                }
                                context.startService(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("拒绝该设备") }
                    }
                }
            }
        }
    }

    // Location explanation dialog
    if (showLocExplainDialog) {
        if (LocalUiStyle.current == "miuix") {
            Dialog(
                onDismissRequest = { showLocExplainDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Text("后台定位保活说明", style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title3)
                        Spacer(Modifier.height(8.dp))
                        Text("为防止鸿蒙/国产手机在熄屏或切后台后切断网络连接，ShareKu 需要在后台使用定位服务保持网络活跃。\n\n• 仅使用网络定位（低功耗），不会使用 GPS\n• 不会记录或上传任何位置信息\n• 可在「设置 → 安全 → 后台定位保活」中随时关闭",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            top.yukonga.miuix.kmp.basic.TextButton(text = "暂不", onClick = { showLocExplainDialog = false })
                            Spacer(Modifier.padding(start = 8.dp))
                            top.yukonga.miuix.kmp.basic.Button(
                                onClick = {
                                    showLocExplainDialog = false
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                    }
                                }
                            ) { Text("知道了，去授权") }
                        }
                    }
                }
            }
        } else {
            AlertDialog(
                onDismissRequest = { showLocExplainDialog = false },
                title = { Text("后台定位保活说明") },
                text = {
                    Text("为防止鸿蒙/国产手机在熄屏或切后台后切断网络连接，ShareKu 需要在后台使用定位服务保持网络活跃。\n\n• 仅使用网络定位（低功耗），不会使用 GPS\n• 不会记录或上传任何位置信息\n• 可在「设置 → 安全 → 后台定位保活」中随时关闭")
                },
                confirmButton = {
                    Button(onClick = {
                        showLocExplainDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    }) { Text("知道了，去授权") }
                },
                dismissButton = { TextButton(onClick = { showLocExplainDialog = false }) { Text("暂不") } }
            )
        }
    }
    // Location service off dialog
    if (showLocServiceOffDialog) {
        if (LocalUiStyle.current == "miuix") {
            Dialog(
                onDismissRequest = { showLocServiceOffDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Text("需开启系统定位服务", style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title3)
                        Spacer(Modifier.height(8.dp))
                        Text("后台定位保活需要系统的「定位服务」处于开启状态。\n\n当前系统定位服务已关闭，定位保活将无法工作。\n\n请前往系统设置开启定位后重新启动。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            top.yukonga.miuix.kmp.basic.TextButton(text = "取消", onClick = { showLocServiceOffDialog = false })
                            Spacer(Modifier.padding(start = 8.dp))
                            top.yukonga.miuix.kmp.basic.Button(
                                onClick = {
                                    showLocServiceOffDialog = false
                                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                    locationServiceLauncher.launch(intent)
                                }
                            ) { Text("前往开启") }
                        }
                    }
                }
            }
        } else {
            AlertDialog(
                onDismissRequest = { showLocServiceOffDialog = false },
                title = { Text("需开启系统定位服务") },
                text = {
                    Text("后台定位保活需要系统的「定位服务」处于开启状态。\n\n当前系统定位服务已关闭，定位保活将无法工作。\n\n请前往系统设置开启定位后重新启动。")
                },
                confirmButton = {
                    Button(onClick = {
                        showLocServiceOffDialog = false
                        // Open system location settings
                        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        locationServiceLauncher.launch(intent)
                    }) { Text("前往开启") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showLocServiceOffDialog = false
                    // Start anyway without location keepalive
                    // Note: doStartService is not accessible here, just dismiss
                }) { Text("暂不开启") }
            }
        )
        }
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

private fun generateWindowsMapScript(
    url: String,
    authEnabled: Boolean,
    authUser: String,
    authPass: String,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager
) {
    val uri = java.net.URI(url)
    val ip = uri.host ?: "127.0.0.1"
    val port = uri.port
    val uncPath = "\\\\$ip@$port\\webdav"
    val authLine = if (authEnabled) "/user:$authUser $authPass" else ""
        val script = """
@echo off
title ShareKu WebDAV Mapping
echo ============================================
echo   ShareKu WebDAV Mapping
echo ============================================
echo.
echo Mapping Z: drive...
net use Z: $uncPath /persistent:no $authLine
if %errorlevel%==0 (
 echo.
 echo [OK] Z: drive mapped successfully.
 echo Opening in Explorer...
 explorer Z:
) else (
 echo.
 echo [FAILED] Error code: %errorlevel%
 echo Browser: http://$ip:$port/webdav
 start http://$ip:$port/webdav
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