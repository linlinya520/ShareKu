package com.linjing.shareku.ui.component

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.linjing.shareku.data.ShizukuEntry
import com.linjing.shareku.data.ShizukuFileManager
import com.linjing.shareku.ui.theme.LocalUiStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File

/** 统一目录条目模型（普通模式 / Shizuku 模式共用） */
private data class DirEntry(
    val name: String,
    val isDirectory: Boolean,
    val path: String,
    val size: Long = 0L,
    val subCount: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserDialog(
    initialPath: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPath by remember { mutableStateOf(initialPath) }
    val history = remember { mutableStateListOf(initialPath) }
    val haptic = LocalHapticFeedback.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val isMiuix = LocalUiStyle.current == "miuix"

    // ═══ Shizuku 状态 ═══
    val shizukuAvailable = remember { ShizukuFileManager.isAvailable() }
    var shizukuMode by remember { mutableStateOf(false) }
    var shizukuGranted by remember { mutableStateOf(ShizukuFileManager.hasPermission()) }
    var shizukuLoading by remember { mutableStateOf(false) }
    var shizukuError by remember { mutableStateOf<String?>(null) }
    var shizukuEntries by remember { mutableStateOf<List<ShizukuEntry>>(emptyList()) }

    // Shizuku 授权结果回调
    DisposableEffect(Unit) {
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode == ShizukuFileManager.REQUEST_CODE) {
                    shizukuGranted = grantResult == PackageManager.PERMISSION_GRANTED
                }
            }
        }
        try {
            Shizuku.addRequestPermissionResultListener(listener)
        } catch (_: Throwable) {}
        onDispose {
            try {
                Shizuku.removeRequestPermissionResultListener(listener)
            } catch (_: Throwable) {}
        }
    }

    // Shizuku 模式下异步加载目录
    LaunchedEffect(currentPath, shizukuMode, shizukuGranted) {
        if (shizukuMode && shizukuGranted) {
            shizukuLoading = true
            shizukuError = null
            shizukuEntries = withContext(Dispatchers.IO) {
                ShizukuFileManager.listDirectory(context, currentPath) ?: emptyList()
            }
            if (shizukuEntries.isEmpty() && !ShizukuFileManager.isAvailable()) {
                shizukuError = "Shizuku 未运行或未授权"
            }
            shizukuLoading = false
        }
    }

    // 目录内容：普通模式用 File API，Shizuku 模式用 shell 结果（统一排序：目录优先 + 名称）
    val entries = remember(currentPath, shizukuMode, shizukuEntries) {
        if (shizukuMode) {
            shizukuEntries.map {
                DirEntry(
                    name = it.name,
                    isDirectory = it.isDirectory,
                    path = it.path,
                    size = it.size
                )
            }.sortedWith(
                compareByDescending<DirEntry> { it.isDirectory }
                    .thenBy { it.name.lowercase() }
            )
        } else {
            val dir = File(currentPath)
            if (dir.isDirectory) {
                dir.listFiles()
                    ?.filter { it.isDirectory }
                    ?.sortedBy { it.name }
                    ?.map {
                        DirEntry(
                            name = it.name,
                            isDirectory = true,
                            path = it.absolutePath
                        )
                    } ?: emptyList()
            } else emptyList()
        }
    }

    // 计算面包屑
    val pathParts = remember(currentPath) {
        currentPath.split("/").filter { it.isNotEmpty() }
    }

    BackHandler {
        if (history.size > 1) {
            history.removeLast()
            currentPath = history.last()
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ─── Header ───
                TopAppBar(
                    title = {
                        Text("选择目录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    },
                    navigationIcon = {
                        if (history.size > 1) {
                            IconButton(onClick = {
                                history.removeLast()
                                currentPath = history.last()
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回上级")
                            }
                        } else {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, "关闭")
                            }
                        }
                    },
                    actions = {
                        // Shizuku 切换按钮（仅 Shizuku 可用时显示）
                        if (shizukuAvailable) {
                            if (isMiuix) {
                                top.yukonga.miuix.kmp.basic.Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (!shizukuMode && !shizukuGranted) {
                                            ShizukuFileManager.requestPermission()
                                        } else {
                                            shizukuMode = !shizukuMode
                                        }
                                    },
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.AdminPanelSettings, null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (shizukuMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        if (shizukuMode) "Shizuku" else "普通",
                                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                        color = if (shizukuMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (!shizukuMode && !shizukuGranted) {
                                            ShizukuFileManager.requestPermission()
                                        } else {
                                            shizukuMode = !shizukuMode
                                        }
                                    },
                                    modifier = Modifier.padding(end = 8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.AdminPanelSettings, null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (shizukuMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        if (shizukuMode) "Shizuku" else "普通",
                                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                        color = if (shizukuMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )

                // ─── Breadcrumb ───
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                ScrollableRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "根",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentPath = "/"
                                history.clear()
                                history.add("/")
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    pathParts.forEachIndexed { i, part ->
                        Text(" › ", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val clickPath = "/" + pathParts.take(i + 1).joinToString("/")
                        Text(
                            text = part,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (i == pathParts.lastIndex)
                                MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentPath = clickPath
                                    history.add(clickPath)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

                // ─── 目录列表 ───
                when {
                    // Shizuku 授权提示
                    shizukuMode && !shizukuGranted -> {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AdminPanelSettings, null, Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(8.dp))
                                Text("需要授予 Shizuku 权限才能浏览受限目录",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("（如 /storage/emulated/0/Android/data/）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(12.dp))
                                if (isMiuix) {
                                    top.yukonga.miuix.kmp.basic.Button(onClick = { ShizukuFileManager.requestPermission() }) {
                                        Text("授权")
                                    }
                                } else {
                                    Button(onClick = { ShizukuFileManager.requestPermission() }) {
                                        Text("授权")
                                    }
                                }
                            }
                        }
                    }
                    // 加载中
                    shizukuMode && shizukuLoading -> {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(8.dp))
                                Text("加载中...", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    // 加载错误
                    shizukuMode && shizukuError != null -> {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ErrorOutline, null, Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(8.dp))
                                Text(shizukuError ?: "加载失败", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    // 空目录
                    entries.isEmpty() -> {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.FolderOff, null, Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (shizukuMode) "此目录下没有条目" else "此目录下没有子文件夹",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    else -> {
                        AnimatedContent(
                            targetState = currentPath,
                            transitionSpec = {
                                slideInHorizontally(spring(stiffness = 300f)) { it } + fadeIn(spring(stiffness = 300f)) togetherWith
                                        slideOutHorizontally(spring(stiffness = 300f)) { -it/3 } + fadeOut(spring(stiffness = 300f))
                            },
                            label = "dir_nav",
                            modifier = Modifier.weight(1f)
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(entries, key = { it.path }) { entry ->
                                    DirItem(
                                        entry = entry,
                                        shizukuMode = shizukuMode,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            if (entry.isDirectory) {
                                                currentPath = entry.path
                                                history.add(entry.path)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // ─── Bottom confirm button ───
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                if (isMiuix) {
                    top.yukonga.miuix.kmp.basic.Button(
                        onClick = { onConfirm(currentPath) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .height(52.dp)
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("选择此目录", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { onConfirm(currentPath) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("选择此目录", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DirItem(entry: DirEntry, shizukuMode: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "dirItem"
    )

    ListItem(
        modifier = Modifier
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        leadingContent = {
            // 文件夹图标（Shizuku 模式下右下角叠加对应包名的 App 图标，方便定位）
            Box {
                Icon(
                    if (entry.isDirectory) Icons.Default.Folder
                    else Icons.Default.InsertDriveFile, null,
                    tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
                if (shizukuMode && entry.isDirectory) {
                    AppIconBadge(
                        packageName = entry.name,
                        modifier = Modifier.align(Alignment.BottomEnd).size(14.dp)
                    )
                }
            }
        },
        headlineContent = {
            Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            // 不再扫描子文件夹数（列表滚动时大量同步/异步磁盘 IO 是掉帧元凶）
            val sub = if (entry.isDirectory) "文件夹" else formatEntrySize(entry.size)
            Text(sub, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    )
}

private fun formatEntrySize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
}

/**
 * 包名 → App 图标角标。
 * 在 Shizuku 模式下，Android/data 下的目录名通常是包名，
 * 用它解析出对应 App 的图标显示在文件夹右下角，方便定位。
 * 解析失败（不是包名/未安装）时不显示任何内容。
 */
@Composable
private fun AppIconBadge(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // 异步加载图标（IO 线程），避免列表组合时同步 PackageManager 查询卡顿
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName, Dispatchers.IO) {
        value = runCatching {
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            val drawable = ai.loadIcon(pm)
            val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 48
            val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 48
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            bmp.asImageBitmap()
        }.getOrNull()
    }
    val current = icon
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = null,
            modifier = modifier.clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun ScrollableRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}