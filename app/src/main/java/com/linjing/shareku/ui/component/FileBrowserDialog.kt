package com.linjing.shareku.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File

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

    // Get directory content
    val entries = remember(currentPath) {
        val dir = File(currentPath)
        if (dir.isDirectory) {
            dir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
        } else emptyList()
    }

    // Calculate breadcrumb parts
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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )

                // ─── Breadcrumb ───
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
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
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                // ─── Directory list ───
                if (entries.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FolderOff, null, Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("此目录下没有子文件夹", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
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
                            items(entries, key = { it.absolutePath }) { dir ->
                                DirItem(
                                    dir = dir,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        currentPath = dir.absolutePath
                                        history.add(dir.absolutePath)
                                    }
                                )
                            }
                        }
                    }
                }

                // ─── Bottom confirm button ───
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
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

@Composable
private fun DirItem(dir: File, onClick: () -> Unit) {
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
            Icon(
                Icons.Default.Folder, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        headlineContent = {
            Text(dir.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            val count = dir.listFiles()?.count { it.isDirectory } ?: 0
            Text("$count 个子文件夹 · ${dir.lastModified().let {
                java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault()).format(it)
            }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    )
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