package com.linjing.shareku.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.linjing.shareku.plugin.PluginCapabilities
import com.linjing.shareku.plugin.PluginInfo
import com.linjing.shareku.plugin.PluginManager
import com.linjing.shareku.ui.component.AppSwitch
import com.linjing.shareku.ui.component.AppTopBar
import com.linjing.shareku.ui.component.CustomCard
import com.linjing.shareku.ui.theme.ShareThemeWrapper
import kotlinx.coroutines.launch

/**
 * 插件管理：列表（启用开关 / 详情 / 删除）+ 导入 ZIP + 安装示例。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var plugins by remember { mutableStateOf<List<PluginInfo>>(emptyList()) }
    LaunchedEffect(refresh) {
        plugins = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            PluginManager.scan(context)
        }
    }
    var message by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<PluginInfo?>(null) }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                message = "安装中…"
                val (_, msg) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    PluginManager.installFromZip(context, uri)
                }
                message = msg
                refresh++
            }
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "插件", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 操作区
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { zipPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp)
                    ) { Text("导入 ZIP") }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                message = "安装中…"
                                val (_, msg) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    PluginManager.installSamplePlugin(context)
                                }
                                message = msg
                                refresh++
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp)
                    ) { Text("安装示例") }
                }
            }
            // 插件市场入口
            item {
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(context, PluginMarketActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(20.dp)
                ) { Text("浏览插件市场") }
            }
            // 消息
            item {
                message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            if (plugins.isEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "尚未安装插件。\n\n• 点击「导入 ZIP」安装插件包\n• 点击「安装示例」体验内置示例\n• 插件目录：${PluginManager.pluginRoot(context).absolutePath}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(plugins, key = { it.manifest.id }) { p ->
                    PluginRow(
                        plugin = p,
                        onOpen = {
                            if (!p.enabled) {
                                message = "插件已禁用"
                            } else if (p.manifest.type == "skin") {
                                detail = p
                            } else {
                                context.startActivity(
                                    Intent(context, PluginRunActivity::class.java)
                                        .putExtra(PluginRunActivity.EXTRA_PLUGIN_ID, p.manifest.id)
                                )
                            }
                        },
                        onToggle = { enabled ->
                            PluginManager.setEnabled(context, p.manifest.id, enabled)
                            refresh++
                        },
                        onDetail = { detail = p },
                        onDelete = {
                            PluginManager.deletePlugin(context, p.manifest.id)
                            message = "已删除「${p.manifest.name}」"
                            refresh++
                        }
                    )
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    "插件是网页应用（HTML/JS），通过 ShareKuBridge 调用原生能力。\n权限在首次运行时逐项确认。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // 详情对话框
    detail?.let { p ->
        val sk = p.manifest.skin
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text("${p.manifest.icon} ${p.manifest.name}") },
            text = {
                Column {
                    Text(
                        "版本：${p.manifest.version}" + if (p.manifest.author.isNotBlank()) " · ${p.manifest.author}" else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (p.manifest.description.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(p.manifest.description, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (sk != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("皮肤设置：", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val styleLabel = when (sk.style) {
                            "material" -> "Material 3"
                            "miuix" -> "MIUI 风格"
                            "liquid" -> "液态玻璃"
                            else -> null
                        }
                        styleLabel?.let { Text("• 组件风格：$it", style = MaterialTheme.typography.bodySmall) }
                        sk.darkMode?.let { Text("• 主题模式：$it", style = MaterialTheme.typography.bodySmall) }
                        sk.glassDensity?.let { Text("• 玻璃质感：$it", style = MaterialTheme.typography.bodySmall) }
                        sk.layoutMode?.let { Text("• 布局模式：$it", style = MaterialTheme.typography.bodySmall) }
                        sk.wallpaper?.let { Text("• 附带壁纸：$it", style = MaterialTheme.typography.bodySmall) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("声明的能力：", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (p.manifest.capabilities.isEmpty()) {
                        Text("（无）", style = MaterialTheme.typography.bodySmall)
                    } else {
                        p.manifest.capabilities.forEach { cap ->
                            Text("• ${PluginCapabilities.label(cap)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    if (p.manifest.type == "skin") {
                        TextButton(onClick = {
                            scope.launch {
                                message = com.linjing.shareku.plugin.SkinApplier.apply(context, p)
                                detail = null
                            }
                        }) { Text("应用皮肤") }
                        TextButton(onClick = {
                            scope.launch {
                                message = com.linjing.shareku.plugin.SkinApplier.resetToDefault(context)
                                detail = null
                            }
                        }) { Text("恢复默认") }
                    }
                    TextButton(onClick = { detail = null }) { Text("关闭") }
                }
            }
        )
    }
}

@Composable
private fun PluginRow(
    plugin: PluginInfo,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDetail: () -> Unit,
    onDelete: () -> Unit
) {
    CustomCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        cornerRadius = 20.dp,
        border = null,
        onClick = onOpen
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(plugin.manifest.icon, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(plugin.manifest.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        if (plugin.manifest.type == "skin") {
                            Spacer(Modifier.width(6.dp))
                            Text("皮肤", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(
                        "v${plugin.manifest.version}" +
                            if (plugin.manifest.description.isNotBlank()) " · ${plugin.manifest.description}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AppSwitch(checked = plugin.enabled, onCheckedChange = onToggle)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                TextButton(onClick = onDetail) { Text("详情") }
                TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

class PluginsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShareThemeWrapper {
                PluginsScreen(onBack = { finish() })
            }
        }
    }
}