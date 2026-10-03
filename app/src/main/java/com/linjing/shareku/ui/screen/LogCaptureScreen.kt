package com.linjing.shareku.ui.screen

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.linjing.shareku.data.DiagnosticLog
import com.linjing.shareku.ui.component.AdaptiveButton
import com.linjing.shareku.ui.component.AppTopBar
import com.linjing.shareku.ui.component.CustomCard
import com.linjing.shareku.ui.theme.ShareThemeWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「日志采集」卡片（工具页内嵌，三套 UI 自适应）：
 * 开始/停止记录 · 查看日志 · 清除日志。
 */
@Composable
fun LogCaptureCard() {
    val context = LocalContext.current
    val recording by DiagnosticLog.recording.collectAsState()
    val lineCount by DiagnosticLog.lineCount.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }

    CustomCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        border = null,
        clickable = false,
        enableHaptic = false,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.BugReport, null, Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("日志采集", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(
                        if (recording) "记录中 · 已 $lineCount 行（复现问题后回来查看）"
                        else "记录全量运行日志，用于排查闪退 / 异常",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdaptiveButton(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    onClick = {
                        message = if (recording) DiagnosticLog.stop() else DiagnosticLog.start(context)
                    }
                ) { Text(if (recording) "停止" else "开始记录", style = MaterialTheme.typography.bodyMedium) }

                AdaptiveButton(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    onClick = { context.startActivity(Intent(context, LogCaptureActivity::class.java)) }
                ) { Text("查看日志", style = MaterialTheme.typography.bodyMedium) }

                AdaptiveButton(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    onClick = { message = DiagnosticLog.clear(context) }
                ) { Text("清除日志", style = MaterialTheme.typography.bodyMedium) }
            }
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

/** 日志详情页：内容可选中、可复制、可导出、可清除 */
@Composable
fun LogCaptureScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val recording by DiagnosticLog.recording.collectAsState()
    val lineCount by DiagnosticLog.lineCount.collectAsState()
    var content by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        content = withContext(Dispatchers.IO) { DiagnosticLog.readTail(context) }
    }
    // 记录中：定期刷新视图（日志本身已实时落盘）
    LaunchedEffect(recording) {
        if (recording) {
            while (true) {
                delay(1500)
                reloadKey += 1
            }
        }
    }

    Scaffold(topBar = { AppTopBar(title = "诊断日志", onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            CustomCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                cornerRadius = 20.dp,
                border = null,
                clickable = false,
                enableHaptic = false,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (recording) "记录中" else "未记录",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (recording) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        Text("$lineCount 行", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        DiagnosticLog.logFile(context).absolutePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    message?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AdaptiveButton(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    onClick = {
                        message = if (recording) DiagnosticLog.stop() else DiagnosticLog.start(context)
                        reloadKey += 1
                    }
                ) { Text(if (recording) "停止" else "开始记录", style = MaterialTheme.typography.bodyMedium) }

                AdaptiveButton(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    onClick = {
                        scope.launch {
                            val text = withContext(Dispatchers.IO) { DiagnosticLog.readTail(context) }
                            if (text.isNotEmpty()) {
                                clipboard.setText(AnnotatedString(text))
                                message = "已复制日志内容到剪贴板"
                            } else {
                                message = "暂无日志内容"
                            }
                        }
                    }
                ) { Text("复制内容", style = MaterialTheme.typography.bodyMedium) }

                AdaptiveButton(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    onClick = {
                        scope.launch {
                            val p = withContext(Dispatchers.IO) { DiagnosticLog.exportToDownload(context) }
                            message = if (p != null) "已导出 → $p" else "暂无可导出的日志"
                        }
                    }
                ) { Text("导出文件", style = MaterialTheme.typography.bodyMedium) }

                AdaptiveButton(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    onClick = {
                        message = DiagnosticLog.clear(context)
                        reloadKey += 1
                    }
                ) { Text("清除", style = MaterialTheme.typography.bodyMedium) }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            ) {
                SelectionContainer {
                    Text(
                        text = content.ifEmpty {
                            "暂无日志内容。\n\n点击「开始记录」后复现问题（例如闪退），再回到这里查看；\n崩溃堆栈也会自动写入本文件。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}

class LogCaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShareThemeWrapper {
                LogCaptureScreen(onBack = { finish() })
            }
        }
    }
}