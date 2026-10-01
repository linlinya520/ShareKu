package com.linjing.shareku.ui.component

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.linjing.shareku.ui.screen.PluginRunActivity
import java.io.File

/**
 * 主页「插件卡片」区：
 * 展示已启用、声明了 home.card 能力的插件卡片（内嵌 WebView，最多 3 个）。
 */
@Composable
fun PluginHomeCards() {
    val context = LocalContext.current
    var cards by remember { mutableStateOf<List<PluginInfo>>(emptyList()) }
    LaunchedEffect(Unit) {
        cards = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            PluginManager.scan(context)
                .filter { it.enabled && PluginCapabilities.HOME_CARD in it.manifest.capabilities }
                .take(3)
        }
    }
    if (cards.isEmpty()) return
    for (p in cards) {
        PluginCard(p)
        Spacer(Modifier.height(12.dp))
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PluginCard(plugin: PluginInfo) {
    val context = LocalContext.current
    CustomCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        border = null,
        clickable = false,
        enableHaptic = false
    ) {
        Column {
            // 头部行
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(plugin.manifest.icon, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Text(
                    plugin.manifest.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    context.startActivity(
                        Intent(context, PluginRunActivity::class.java)
                            .putExtra(PluginRunActivity.EXTRA_PLUGIN_ID, plugin.manifest.id)
                    )
                }) { Text("打开") }
            }
            // 卡片内容（WebView；granted 变化时重建以刷新权限）
            val cardFile = File(plugin.dir, plugin.manifest.card ?: plugin.manifest.entry)
            androidx.compose.runtime.key(plugin.granted) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(170.dp),
                    factory = { ctx ->
                        createPluginWebView(
                            ctx,
                            plugin,
                            "file://${cardFile.absolutePath}",
                            transparent = true
                        )
                    },
                    onRelease = { v ->
                        (v.parent as? android.view.ViewGroup)?.removeView(v)
                        v.destroy()
                    }
                )
            }
        }
    }
}