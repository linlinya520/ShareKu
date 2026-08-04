package com.linjing.shareku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.linjing.shareku.ui.component.CustomCard
import com.linjing.shareku.ui.screen.AboutScreen
import com.linjing.shareku.ui.screen.AppearanceScreen
import com.linjing.shareku.ui.screen.CacheCleanupScreen
import com.linjing.shareku.ui.screen.ChangelogScreen
import com.linjing.shareku.ui.screen.SettingsScreen
import com.linjing.shareku.ui.theme.LocalShareTheme
import com.linjing.shareku.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialTheme = runBlocking { AppSingletons.preferencesManager.themeMode.first() }
        setContent {
            val themeModeName by AppSingletons.preferencesManager.themeMode.collectAsState(initial = initialTheme)
            val dynamicColor by AppSingletons.preferencesManager.dynamicColor.collectAsState(initial = true)
            val paletteOrdinal by AppSingletons.preferencesManager.paletteStyleOrdinal.collectAsState(initial = 0)
            val paletteStyle = com.linjing.shareku.ui.theme.color.PaletteStyle.entries
                .getOrElse(paletteOrdinal) { com.linjing.shareku.ui.theme.color.PaletteStyle.TONAL_SPOT }

            var page by remember { mutableStateOf("settings") }

            LocalShareTheme(themeMode = ThemeMode.fromName(themeModeName), dynamicColor = dynamicColor, paletteStyle = paletteStyle) {
                Surface(modifier = Modifier.fillMaxSize()) {
AnimatedContent(
                        targetState = page,
transitionSpec = {
                             (fadeIn(tween(150)) + scaleIn(initialScale = 0.95f, animationSpec = tween(150))) togetherWith
                                     (fadeOut(tween(120)) + scaleOut(targetScale = 1.03f, animationSpec = tween(120)))
                         },
                        label = "settings_nav"
                    ) { current ->
                        when (current) {
                            "about" -> {
                                BackHandler { page = "settings" }
                                AboutScreen(
                                    onBack = { page = "settings" },
                                    onChangelog = { page = "changelog" }
                                )
                            }
                            "changelog" -> {
                                BackHandler { page = "about" }
                                ChangelogScreen(onBack = { page = "about" })
                            }
                            "appearance" -> {
                                BackHandler { page = "settings" }
                                AppearanceScreen(onBack = { page = "settings" })
                            }
                            "security" -> SecurityPage(onBack = { page = "settings" })
                            "server" -> ServerPage(onBack = { page = "settings" })
                            "fileOps" -> FileOpsPage(onBack = { page = "settings" })
                            "cacheCleanup" -> {
                                BackHandler { page = "settings" }
                                CacheCleanupScreen(onBack = { page = "settings" })
                            }
                            else -> SettingsScreen(
                                onBack = { finish() },
                                onSecurity = { page = "security" },
                                onServer = { page = "server" },
                                onFileOps = { page = "fileOps" },
                                onCacheCleanup = { page = "cacheCleanup" },
                                onAppearance = { page = "appearance" },
                                onAbout = { page = "about" }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════ 分类详情页 ═══════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecurityPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val prefs = AppSingletons.preferencesManager
    val haptic = LocalHapticFeedback.current
    val enableAuth by prefs.enableAuth.collectAsState(initial = false)
    val authUsername by prefs.authUsername.collectAsState(initial = "admin")
    val authPassword by prefs.authPassword.collectAsState(initial = "admin")
    val requireConfirm by prefs.requireConnectionConfirm.collectAsState(initial = false)
    var passwordVisible by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("安全", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.ContextClick); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {

            Text("身份验证", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            CustomCard(cornerRadius = 24.dp, border = null, clickable = false, enableHaptic = false,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(4.dp)) {
                    ListItem(
                        headlineContent = { Text("启用身份验证", style = MaterialTheme.typography.bodyLarge) },
                        supportingContent = { Text(if (enableAuth) "用户名: $authUsername" else "需要用户名和密码才能访问", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingContent = {
                            Switch(checked = enableAuth, onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch { prefs.setEnableAuth(it) }
                            })
                        }
                    )
                    AnimatedVisibility(visible = enableAuth, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            OutlinedTextField(authUsername, { scope.launch { prefs.setAuthUsername(it) } }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("用户名") }, shape = RoundedCornerShape(12.dp))
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(authPassword, { scope.launch { prefs.setAuthPassword(it) } }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("密码") }, shape = RoundedCornerShape(12.dp),
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, if (passwordVisible) "隐藏" else "显示") } }
                            )
                        }
                    }
                }
            }

            Text("连接", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            CustomCard(cornerRadius = 24.dp, border = null, clickable = false, enableHaptic = false,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                ListItem(
                    headlineContent = { Text("连接确认", style = MaterialTheme.typography.bodyLarge) },
                    supportingContent = { Text("新设备连接时需要手动批准", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = {
                        Switch(checked = requireConfirm, onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch { prefs.setRequireConnectionConfirm(it) }
                        })
                    }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerPage(onBack: () -> Unit) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileOpsPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val prefs = AppSingletons.preferencesManager
    val haptic = LocalHapticFeedback.current
    val allowUpload by prefs.allowUpload.collectAsState(initial = false)
    val allowOverwrite by prefs.allowOverwrite.collectAsState(initial = true)
    val allowDelete by prefs.allowDelete.collectAsState(initial = false)

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("文件操作", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.ContextClick); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } })
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {

            Text("权限", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            SwitchRow("允许上传", "允许已连接的设备上传文件", allowUpload) { scope.launch { prefs.setAllowUpload(it) } }
            SwitchRow("允许覆盖", "允许覆盖已有文件（WebDAV 必须）", allowOverwrite) { scope.launch { prefs.setAllowOverwrite(it) } }
            SwitchRow("允许删除", "允许已连接的设备删除文件", allowDelete) { scope.launch { prefs.setAllowDelete(it) } }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    CustomCard(
        cornerRadius = 24.dp, border = null, clickable = false, enableHaptic = false,
    ) {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
            supportingContent = { Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingContent = {
                Switch(checked = checked, onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onChange(it)
                })
            }
        )
    }
}