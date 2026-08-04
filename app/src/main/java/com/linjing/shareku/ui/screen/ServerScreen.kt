package com.linjing.shareku.ui.screen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.linjing.shareku.AppSingletons
import com.linjing.shareku.ui.component.CustomCard
import com.linjing.shareku.ui.theme.ShareThemeWrapper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val prefs = AppSingletons.preferencesManager
    val haptic = LocalHapticFeedback.current
    val port by prefs.port.collectAsState(initial = 8080)
    val enableWebDav by prefs.enableWebDav.collectAsState(initial = true)

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("服务器", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.ContextClick); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } })
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {

            Text("端口", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            CustomCard(cornerRadius = 24.dp, border = null, clickable = false, enableHaptic = false) {
                ListItem(
                    headlineContent = { Text("端口号", style = MaterialTheme.typography.bodyLarge) },
                    supportingContent = { Text("当前端口: $port", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = {
                        OutlinedTextField(
                            value = port.toString(), singleLine = true,
                            onValueChange = { v -> v.toIntOrNull()?.let { scope.launch { prefs.setPort(it) } } },
                            modifier = Modifier.width(80.dp), shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                )
            }

            Text("协议", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            CustomCard(cornerRadius = 24.dp, border = null, clickable = false, enableHaptic = false) {
                ListItem(
                    headlineContent = { Text("启用 WebDAV", style = MaterialTheme.typography.bodyLarge) },
                    supportingContent = { Text("支持 Windows 映射网络驱动器", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = {
                        Switch(checked = enableWebDav, onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch { prefs.setEnableWebDav(it) }
                        })
                    }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

class ServerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShareThemeWrapper { ServerScreen(onBack = { finish() }) }
        }
    }
}