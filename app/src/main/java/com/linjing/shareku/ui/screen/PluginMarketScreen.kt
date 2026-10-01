package com.linjing.shareku.ui.screen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.linjing.shareku.plugin.MarketPlugin
import com.linjing.shareku.plugin.PluginManager
import com.linjing.shareku.plugin.PluginMarket
import com.linjing.shareku.ui.component.AppTopBar
import com.linjing.shareku.ui.component.CustomCard
import com.linjing.shareku.ui.theme.ShareThemeWrapper
import kotlinx.coroutines.launch

/**
 * 插件市场：拉取 registry → 列表 → 一键安装（GitHub 仓库驱动，无需服务器）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginMarketScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loadKey by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var plugins by remember { mutableStateOf<List<MarketPlugin>>(emptyList()) }
    var installing by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var installed by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(refresh) {
        installed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            PluginManager.scan(context).map { it.manifest.id }.toSet()
        }
    }

    LaunchedEffect(loadKey) {
        loading = true
        error = null
        val (list, err) = PluginMarket.fetchRegistry()
        plugins = list
        error = err
        loading = false
    }

    Scaffold(
        topBar = { AppTopBar(title = "插件市场", onBack = onBack) }
    ) { padding ->
        when {
            loading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            error != null && plugins.isEmpty() -> {
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("无法连接插件市场", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { loadKey++ }, shape = RoundedCornerShape(20.dp)) { Text("重试") }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    message?.let {
                        item {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                    items(plugins, key = { it.id }) { p ->
                        CustomCard(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            cornerRadius = 20.dp,
                            border = null,
                            clickable = false,
                            enableHaptic = false
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(p.icon, style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(p.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.width(6.dp))
                                        Text("v${p.version}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        (if (p.author.isNotBlank()) "${p.author} · " else "") + p.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (p.capabilities.isNotEmpty()) {
                                        Text(
                                            "权限：${p.capabilities.size} 项（安装后确认）",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                if (p.id in installed) {
                                    Text("已安装", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                } else {
                                    Button(
                                        onClick = {
                                            installing = p.id
                                            message = null
                                            scope.launch {
                                                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                    PluginManager.installFromUrl(context, p.download)
                                                }
                                                message = result.second
                                                installing = null
                                                refresh++
                                            }
                                        },
                                        enabled = installing == null,
                                        shape = RoundedCornerShape(18.dp)
                                    ) {
                                        if (installing == p.id) {
                                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                        } else {
                                            Text("安装")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "市场由 ShareKu 仓库索引驱动（registry.json），无需自建服务器。\n安装后可在「插件」页管理或卸载。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

class PluginMarketActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShareThemeWrapper {
                PluginMarketScreen(onBack = { finish() })
            }
        }
    }
}