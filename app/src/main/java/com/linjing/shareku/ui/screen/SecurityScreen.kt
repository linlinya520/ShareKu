package com.linjing.shareku.ui.screen

import com.linjing.shareku.ui.component.AppTopBar
import com.linjing.shareku.ui.component.AppSwitch
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.linjing.shareku.AppSingletons
import com.linjing.shareku.ui.component.AppSwitchRow
import com.linjing.shareku.ui.component.CustomCard
import com.linjing.shareku.ui.component.MiuixSettingsGroup
import com.linjing.shareku.ui.theme.LocalUiStyle
import com.linjing.shareku.ui.theme.ShareThemeWrapper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(onBack: () -> Unit) {
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
            AppTopBar(
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

            if (LocalUiStyle.current == "miuix") {
                // ── MIUI 风格：三组合并为一个分组卡片 ──
                MiuixSettingsGroup(Modifier.fillMaxWidth()) {
                    top.yukonga.miuix.kmp.preference.SwitchPreference(
                        checked = enableAuth,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch { prefs.setEnableAuth(it) }
                        },
                        title = "启用身份验证",
                        summary = if (enableAuth) "用户名: $authUsername" else "需要用户名和密码才能访问"
                    )
                    top.yukonga.miuix.kmp.preference.SwitchPreference(
                        checked = requireConfirm,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch { prefs.setRequireConnectionConfirm(it) }
                        },
                        title = "连接确认",
                        summary = "新设备连接时需要手动批准"
                    )
                    val enableLocationM by prefs.enableLocationKeepAlive.collectAsState(initial = true)
                    top.yukonga.miuix.kmp.preference.SwitchPreference(
                        checked = enableLocationM,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch { prefs.setEnableLocationKeepAlive(it) }
                        },
                        title = "后台定位保活",
                        summary = "防止鸿蒙/国产手机熄屏或切后台后断网。仅用网络定位（低功耗），不记录坐标，不上传。"
                    )
                }
                if (enableAuth) {
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
                Spacer(Modifier.height(24.dp))
            } else {
                Text("身份验证", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                CustomCard(cornerRadius = 24.dp, border = null, clickable = false, enableHaptic = false,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(4.dp)) {
                        AppSwitchRow(
                            title = "启用身份验证",
                            subtitle = if (enableAuth) "用户名: $authUsername" else "需要用户名和密码才能访问",
                            checked = enableAuth,
                            onChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch { prefs.setEnableAuth(it) }
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
                    AppSwitchRow(
                        title = "连接确认",
                        subtitle = "新设备连接时需要手动批准",
                        checked = requireConfirm,
                        onChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch { prefs.setRequireConnectionConfirm(it) }
                        }
                    )
                }

                // ═══ 定位保活 ═══
                Text("后台保护", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                val enableLocation by prefs.enableLocationKeepAlive.collectAsState(initial = true)
                CustomCard(cornerRadius = 24.dp, border = null, clickable = false, enableHaptic = false,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    AppSwitchRow(
                        title = "后台定位保活",
                        subtitle = "防止鸿蒙/国产手机熄屏或切后台后断网。仅用网络定位（低功耗），不记录坐标，不上传。",
                        checked = enableLocation,
                        onChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch { prefs.setEnableLocationKeepAlive(it) }
                        }
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

class SecurityActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShareThemeWrapper { SecurityScreen(onBack = { finish() }) }
        }
    }
}