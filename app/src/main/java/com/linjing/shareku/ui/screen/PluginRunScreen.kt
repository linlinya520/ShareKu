package com.linjing.shareku.ui.screen

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.linjing.shareku.plugin.PluginBridge
import com.linjing.shareku.plugin.PluginCapabilities
import com.linjing.shareku.plugin.PluginInfo
import com.linjing.shareku.plugin.PluginManager
import com.linjing.shareku.plugin.createPluginWebView
import com.linjing.shareku.ui.component.AppTopBar
import com.linjing.shareku.ui.component.CustomCard
import com.linjing.shareku.ui.theme.ShareThemeWrapper
import java.io.File

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PluginRunScreen(
    plugin: PluginInfo,
    granted: Set<String>,
    onBack: () -> Unit,
    onGrant: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    val missing = plugin.manifest.capabilities.filter { it !in granted }

    if (missing.isNotEmpty()) {
        // ── 授权页：逐项列出请求的权限 ──
        Scaffold(topBar = { AppTopBar(title = plugin.manifest.name, onBack = onBack) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text("插件请求以下权限", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                missing.forEach { cap ->
                    CustomCard(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        cornerRadius = 16.dp,
                        border = null,
                        clickable = false,
                        enableHaptic = false
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(cap, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(
                                PluginCapabilities.label(cap),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onGrant((granted + missing).toSet()) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) { Text("允许并运行") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) { Text("取消") }
            }
        }
        return
    }

    // ── 运行页：WebView + JS 桥 ──
    var webView by remember { mutableStateOf<WebView?>(null) }
    Scaffold(
        topBar = {
            AppTopBar(
                title = plugin.manifest.name,
                onBack = {
                    webView?.stopLoading()
                    onBack()
                }
            )
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = { ctx ->
                createPluginWebView(
                    ctx,
                    plugin,
                    "file://${File(plugin.dir, plugin.manifest.entry).absolutePath}"
                ).also { webView = it }
            },
            onRelease = { v ->
                (v.parent as? android.view.ViewGroup)?.removeView(v)
                v.stopLoading()
                v.destroy()
            }
        )
    }
}

class PluginRunActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID) ?: run { finish(); return }
        setContent {
            ShareThemeWrapper {
                var plugin by remember { mutableStateOf<PluginInfo?>(null) }
                LaunchedEffect(Unit) {
                    val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        PluginManager.scan(this@PluginRunActivity).firstOrNull { it.manifest.id == pluginId }
                    }
                    if (loaded == null) {
                        finish()
                    } else {
                        plugin = loaded
                    }
                }
                val p = plugin
                if (p == null) {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) { CircularProgressIndicator() }
                } else {
                    var granted by remember(p) { mutableStateOf(p.granted) }
                    PluginRunScreen(
                        plugin = p,
                        granted = granted,
                        onBack = { finish() },
                        onGrant = { caps ->
                            PluginManager.grantCapabilities(this@PluginRunActivity, pluginId, caps)
                            granted = caps
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_PLUGIN_ID = "plugin_id"
    }
}