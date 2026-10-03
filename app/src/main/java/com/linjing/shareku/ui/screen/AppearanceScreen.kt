package com.linjing.shareku.ui.screen

import com.linjing.shareku.ui.component.AppTopBar
import com.linjing.shareku.ui.component.AppSwitch
import com.linjing.shareku.ui.component.AppSwitchRow
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.linjing.shareku.AppSingletons
import com.linjing.shareku.CacheUtils
import com.linjing.shareku.ui.component.AdaptiveButton
import com.linjing.shareku.ui.component.ColorPickerPanel
import com.linjing.shareku.ui.component.AdaptiveSlider
import com.linjing.shareku.ui.component.AdaptiveTextField
import com.linjing.shareku.ui.component.CustomCard
import com.linjing.shareku.ui.component.WallpaperCropDialog
import com.linjing.shareku.ui.component.MiuixExpandableSelect
import com.linjing.shareku.ui.performance.SYSTEM_AUTO_DISPLAY_MODE_ID
import com.linjing.shareku.ui.performance.applyPreferredDisplayMode
import com.linjing.shareku.ui.performance.displayModePreferenceLabel
import com.linjing.shareku.ui.performance.findActivityCompat
import com.linjing.shareku.ui.performance.normalizePreferredDisplayModeId
import com.linjing.shareku.ui.performance.supportedAppDisplayModes
import com.linjing.shareku.ui.component.MiuixSettingsGroup
import com.linjing.shareku.ui.theme.LocalUiStyle
import com.linjing.shareku.ui.theme.resolveGlassTuning
import com.linjing.shareku.ui.theme.ShareThemeWrapper
import com.linjing.shareku.ui.theme.ThemeMode
import com.linjing.shareku.ui.theme.color.PaletteStyle
import com.linjing.shareku.ui.theme.color.DEFAULT_SEED_COLOR
import com.linjing.shareku.ui.theme.color.argbToHex
import com.linjing.shareku.ui.theme.color.argbToHsv
import com.linjing.shareku.ui.theme.color.hexToArgb
import com.linjing.shareku.ui.theme.color.hsvToArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(onBack: () -> Unit, embedded: Boolean = false) {
    val scope = rememberCoroutineScope()
    val prefs = AppSingletons.preferencesManager
    val haptic = LocalHapticFeedback.current
    val styles = remember { PaletteStyle.entries.toList() }
    // 壁纸裁剪/定位界面的待处理文件路径
    var cropPath by remember { mutableStateOf<String?>(null) }
    var cropIsVideo by remember { mutableStateOf(false) }

    val dynamicColor by prefs.dynamicColor.collectAsState(initial = true)
    // 动态取色（莫奈取色）依赖 Android 12+。设备不支持时必须「自动关停」而不是锁死，
    // 否则开关显示为「开」却无法点击，且下方配色方案也被连带禁用 → 用户完全改不了配色。
    val dynamicSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    val dynamicActive = dynamicColor && dynamicSupported
    val themeModeName by prefs.themeMode.collectAsState(initial = "SYSTEM")
    val paletteOrdinal by prefs.paletteStyleOrdinal.collectAsState(initial = 0)
    val currentStyle = PaletteStyle.entries.getOrElse(paletteOrdinal) { PaletteStyle.TONAL_SPOT }
    var selectedStyle by remember { mutableStateOf(currentStyle) }
    LaunchedEffect(currentStyle) { selectedStyle = currentStyle }

    val currentMode = remember(themeModeName) { ThemeMode.fromName(themeModeName) }

    Scaffold(
        topBar = {
            if (!embedded) {
                AppTopBar(
                    title = { Text("外观体验", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ═══ 布局模式 ═══
            Text("布局模式", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
            Text("经典：主页 + 右上角设置入口；Dock：底部悬浮导航栏（主页/工具/外观/关于）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp))
            val layoutMode by prefs.layoutMode.collectAsState(initial = "classic")
            if (LocalUiStyle.current == "miuix") {
                MiuixSettingsGroup(Modifier.fillMaxWidth()) {
                    MiuixExpandableSelect(
                        title = "布局模式",
                        options = listOf("经典布局", "底部悬浮 Dock"),
                        selectedIndex = if (layoutMode == "dock") 1 else 0,
                        onSelected = { idx ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch { prefs.setLayoutMode(if (idx == 1) "dock" else "classic") }
                        }
                    )
                }
            } else {
                CustomCard(cornerRadius = 24.dp, border = null, clickable = false, enableHaptic = false,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    listOf("classic" to "经典布局", "dock" to "底部悬浮 Dock").forEach { (value, label) ->
                        ListItem(
                            headlineContent = { Text(label, style = MaterialTheme.typography.bodyLarge) },
                            supportingContent = {
                                Column {
                                    Text(
                                        if (value == "dock") "底部悬浮导航栏，可长按拖动切换页面"
                                        else "主页 + 右上角设置入口",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            trailingContent = {
                                RadioButton(selected = layoutMode == value, onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    scope.launch { prefs.setLayoutMode(value) }
                                })
                            },
                            modifier = Modifier.clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch { prefs.setLayoutMode(value) }
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // ═══ 界面风格 ═══
            Text("界面风格", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
            val uiStyle by prefs.uiStyle.collectAsState(initial = "material")
            if (LocalUiStyle.current == "miuix") {
                MiuixSettingsGroup(Modifier.fillMaxWidth()) {
                    MiuixExpandableSelect(
                        title = "界面风格",
                        options = listOf("Material 3", "MIUI 风格", "液态玻璃"),
                        selectedIndex = when (uiStyle) {
                            "miuix" -> 1
                            "liquid" -> 2
                            else -> 0
                        },
                        onSelected = { idx ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch {
                                prefs.setUiStyle(
                                    when (idx) {
                                        1 -> "miuix"
                                        2 -> "liquid"
                                        else -> "material"
                                    }
                                )
                            }
                        }
                    )
                }
            } else {
                CustomCard(cornerRadius = 24.dp, border = null, clickable = false, enableHaptic = false,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    listOf("material" to "Material 3", "miuix" to "MIUI 风格", "liquid" to "液态玻璃").forEach { (value, label) ->
                        ListItem(
                            headlineContent = { Text(label, style = MaterialTheme.typography.bodyLarge) },
                            supportingContent = {
                                Text(
                                    when (value) {
                                        "miuix" -> "MIUI 组件风格（禁用莫奈取色）"
                                        "liquid" -> "苹果液态玻璃质感（Android 13+ 折射模糊）"
                                        else -> "Google Material 设计（支持莫奈取色）"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                RadioButton(selected = uiStyle == value, onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    scope.launch { prefs.setUiStyle(value) }
                                })
                            },
                            modifier = Modifier.clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch { prefs.setUiStyle(value) }
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // ═══ 深色模式 ═══
            Text("深色模式", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
            Text("选择浅色、深色或跟随系统", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))

            val modeEntries = remember {
                listOf(
                    ModeChip(ThemeMode.LIGHT, "浅色", Icons.Default.WbSunny),
                    ModeChip(ThemeMode.DARK, "深色", Icons.Default.NightsStay),
                    ModeChip(ThemeMode.SYSTEM, "跟随系统", Icons.Default.Settings),
                )
            }

            if (LocalUiStyle.current == "miuix") {
                // ── MIUI 风格：miuix 展开式下拉选择 ──
                MiuixSettingsGroup(Modifier.fillMaxWidth()) {
                    MiuixExpandableSelect(
                        title = "外观模式",
                        options = listOf("跟随系统", "浅色", "深色"),
                        selectedIndex = when (currentMode) {
                            ThemeMode.LIGHT -> 1
                            ThemeMode.DARK -> 2
                            else -> 0
                        },
                        onSelected = { idx ->
                            haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            val mode = when (idx) { 1 -> ThemeMode.LIGHT; 2 -> ThemeMode.DARK; else -> ThemeMode.SYSTEM }
                            scope.launch { prefs.setThemeMode(mode.name) }
                        }
                    )
                }
            } else {
                modeEntries.forEachIndexed { i, entry ->
                    val selected = currentMode == entry.mode
                    val isSingle = modeEntries.size == 1
                    CustomCard(
                        // 首项仅上方大圆角、末项仅下方大圆角，中间衔接处统一小圆角，
                        // 拼起来像一个完整的圆角容器（修复此前首尾四角都大的问题）
                        topStartCorner = if (isSingle || i == 0) 24.dp else 4.dp,
                        topEndCorner = if (isSingle || i == 0) 24.dp else 4.dp,
                        bottomStartCorner = if (isSingle || i == modeEntries.lastIndex) 24.dp else 4.dp,
                        bottomEndCorner = if (isSingle || i == modeEntries.lastIndex) 24.dp else 4.dp,
                        colors = if (selected)
                            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                        border = null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            scope.launch { prefs.setThemeMode(entry.mode.name) }
                        }
                    ) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(entry.icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Text(entry.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            RadioButton(selected = selected, onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                                scope.launch { prefs.setThemeMode(entry.mode.name) }
                            })
                        }
                    }
                }
            }

            // ═══ 屏幕帧率 ═══
            Spacer(Modifier.height(16.dp))
            Text("屏幕帧率", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            Text("选择应用运行时使用的屏幕刷新率档位（高刷更流畅、更耗电）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp))

            val hostActivity = LocalContext.current.findActivityCompat()
            val supportedDisplayModes = remember(hostActivity) {
                hostActivity?.supportedAppDisplayModes().orEmpty()
            }
            val storedDisplayModeId by prefs.screenDisplayModeId.collectAsState(initial = 0)
            val selectedDisplayModeId = remember(storedDisplayModeId, supportedDisplayModes) {
                normalizePreferredDisplayModeId(storedDisplayModeId, supportedDisplayModes)
            }
            val displayModeLabels = remember(supportedDisplayModes) {
                listOf("自动（系统调度）") + supportedDisplayModes.map { displayModePreferenceLabel(it) }
            }
            val displayModeIds = remember(supportedDisplayModes) {
                listOf(SYSTEM_AUTO_DISPLAY_MODE_ID) + supportedDisplayModes.map { it.modeId }
            }
            val selectedDisplayIndex = displayModeIds.indexOf(selectedDisplayModeId).coerceAtLeast(0)
            val applyDisplayMode: (Int) -> Unit = { idx ->
                val modeId = displayModeIds.getOrElse(idx) { SYSTEM_AUTO_DISPLAY_MODE_ID }
                haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                runCatching { hostActivity?.applyPreferredDisplayMode(modeId) }
                scope.launch { prefs.setScreenDisplayModeId(modeId) }
            }

            if (LocalUiStyle.current == "miuix") {
                MiuixSettingsGroup(Modifier.fillMaxWidth()) {
                    MiuixExpandableSelect(
                        title = "屏幕帧率",
                        options = displayModeLabels,
                        selectedIndex = selectedDisplayIndex,
                        onSelected = applyDisplayMode
                    )
                }
            } else if (supportedDisplayModes.isEmpty()) {
                Text("未获取到可用显示模式", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                displayModeLabels.forEachIndexed { i, label ->
                    val selected = i == selectedDisplayIndex
                    val isSingle = displayModeLabels.size == 1
                    CustomCard(
                        topStartCorner = if (isSingle || i == 0) 24.dp else 4.dp,
                        topEndCorner = if (isSingle || i == 0) 24.dp else 4.dp,
                        bottomStartCorner = if (isSingle || i == displayModeLabels.lastIndex) 24.dp else 4.dp,
                        bottomEndCorner = if (isSingle || i == displayModeLabels.lastIndex) 24.dp else 4.dp,
                        colors = if (selected)
                            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                        border = null,
                        onClick = { applyDisplayMode(i) }
                    ) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, null, Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            RadioButton(selected = selected, onClick = { applyDisplayMode(i) })
                        }
                    }
                }
            }

            // ═══ 动画与交互 ═══
            Spacer(Modifier.height(16.dp))
            Text("动画与交互", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            Text("页面切换与反馈动效的独立开关（关闭可提升流畅度 / 省电）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp))
            AnimationSwitches()

            // ═══ 液态玻璃质感 ═══
            Spacer(Modifier.height(16.dp))
            Text("液态玻璃质感", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            Text("调节液态玻璃的折射 / 模糊 / 色散强度（清澈 ↔ 磨砂），拖动实时生效",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp))

            val glassDensity by prefs.glassDensity.collectAsState(initial = 0.5f)
            val glassTuning = remember(glassDensity) { resolveGlassTuning(glassDensity) }
            CustomCard(cornerRadius = 24.dp, border = null, clickable = false, enableHaptic = false,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 20.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("质感档位", style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(glassTuning.presetLabel, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    AdaptiveSlider(
                        value = glassDensity.coerceIn(0f, 1f),
                        onValueChange = { scope.launch { prefs.setGlassDensity(it) } },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        if (LocalUiStyle.current == "liquid") "当前为液态玻璃风格，拖动即实时生效"
                        else "切换到「液态玻璃」风格后可见效果",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ═══ 莫奈取色 ═══
            Spacer(Modifier.height(16.dp))
            Text("莫奈取色", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            Text(
                text = if (dynamicSupported)
                    "从壁纸自动提取主题颜色" else "系统版本不支持（需 Android 12+），将使用下方「自定义颜色 / 配色方案」",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            CustomCard(
                cornerRadius = 24.dp,
                border = CardDefaults.outlinedCardBorder(),
                onClick = {},
                enableHaptic = false
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Palette, null, Modifier.size(24.dp).padding(start = 8.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("动态取色", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            when {
                                !dynamicSupported -> "设备不支持（Android 12+ 才可用），已自动改用手动配色"
                                dynamicColor -> "启用后下方的配色方案将被忽略"
                                else -> "已关闭 → 使用下方的自定义颜色 / 配色方案"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AppSwitch(
                        checked = dynamicActive,
                        enabled = dynamicSupported,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch { prefs.setDynamicColor(it) }
                        }
                    )
                }
            }

            // ═══ 配色方案 ═══
            Spacer(Modifier.height(16.dp))
            Text("配色方案", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            Text("选择手动配色风格（关闭动态取色后生效）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp))

            styles.forEachIndexed { index, style ->
                val selected = style == selectedStyle
                val enabled = !dynamicActive
                val isSingle = styles.size == 1

                CustomCard(
                    // 同上：首尾只在外侧用大圆角，衔接处统一小圆角
                    topStartCorner = if (isSingle || index == 0) 24.dp else 4.dp,
                    topEndCorner = if (isSingle || index == 0) 24.dp else 4.dp,
                    bottomStartCorner = if (isSingle || index == styles.lastIndex) 24.dp else 4.dp,
                    bottomEndCorner = if (isSingle || index == styles.lastIndex) 24.dp else 4.dp,
                    colors = if (selected && enabled)
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    border = null,
                    clickable = enabled,
                    enableHaptic = enabled,
                    onClick = {
                        if (enabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            selectedStyle = style
                            scope.launch { prefs.setPaletteStyleOrdinal(style.ordinal) }
                        }
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            Modifier.size(32.dp), CircleShape,
                            color = if (enabled) paletteStyleColor(style)
                                else paletteStyleColor(style).copy(alpha = 0.38f)
                        ) {}
                        Text(style.displayName, style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        RadioButton(selected = selected, enabled = enabled, onClick = {
                            if (enabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                                selectedStyle = style
                                scope.launch { prefs.setPaletteStyleOrdinal(style.ordinal) }
                            }
                        })
                    }
                }
            }

            // ═══ 自定义颜色（主题种子色）═══
            Spacer(Modifier.height(16.dp))
            Text("自定义颜色", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            Text("任意颜色都能作为主题种子，整套配色（含 MIUI 风格的强调色）会按它重新生成",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp))

            val seedColor by prefs.seedColor.collectAsState(initial = DEFAULT_SEED_COLOR)
            val seedEnabled = !dynamicActive
            val seedHsv = argbToHsv(seedColor)
            var hexInput by remember(seedColor) { mutableStateOf(argbToHex(seedColor)) }

            CustomCard(
                cornerRadius = 24.dp, border = null, clickable = false, enableHaptic = false,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 20.dp)) {
                    // 预览 + 重置
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            Modifier.size(40.dp), CircleShape,
                            color = if (seedEnabled) Color(seedColor)
                            else Color(seedColor).copy(alpha = 0.38f)
                        ) {}
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("主题种子色", style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold)
                            Text(argbToHex(seedColor), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AdaptiveButton(
                            modifier = Modifier.width(84.dp),
                            onClick = {
                                if (seedEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                                    scope.launch { prefs.setSeedColor(DEFAULT_SEED_COLOR) }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)
                        ) { Text("重置", style = MaterialTheme.typography.bodyMedium) }
                    }

                    Spacer(Modifier.height(12.dp))
                    // HSV 取色面板：色板拖动选色 + 色相条 + 明度条
                    ColorPickerPanel(
                        hue = seedHsv[0],
                        sat = seedHsv[1],
                        value = seedHsv[2],
                        onColorChange = { hh, ss, vv ->
                            if (seedEnabled) scope.launch {
                                prefs.setSeedColor(hsvToArgb(hh, ss.coerceAtLeast(0.05f), vv))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))
                    // 精确输入：HEX
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AdaptiveTextField(
                            value = hexInput,
                            onValueChange = { hexInput = it },
                            label = "HEX",
                            placeholder = "#FF5722",
                            modifier = Modifier.weight(1f).padding(end = 10.dp)
                        )
                        AdaptiveButton(
                            modifier = Modifier.width(84.dp),
                            onClick = {
                                val parsed = hexToArgb(hexInput)
                                if (parsed != null && seedEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                                    scope.launch { prefs.setSeedColor(parsed) }
                                } else {
                                    // 输入不合法：回显当前颜色，不写入脏值
                                    hexInput = argbToHex(seedColor)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 10.dp)
                        ) { Text("应用", style = MaterialTheme.typography.bodyMedium) }
                    }

                    if (!seedEnabled) {
                        Spacer(Modifier.height(8.dp))
                        Text("动态取色已开启，关闭后此处生效",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }

            // ═══ 全局壁纸 ═══
            Spacer(Modifier.height(16.dp))
            Text("全局壁纸", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            Text("应用内所有界面的统一背景",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp))

            val context = LocalContext.current
            val wallpaperSource by prefs.wallpaperSource.collectAsState(initial = "default")
            val wallpaperOverlay by prefs.wallpaperOverlay.collectAsState(initial = 0.55f)
            // 动态壁纸提示：系统壁纸为动态壁纸时将走「系统层透出」显示
            val isLiveWallpaper = remember { com.linjing.shareku.ui.component.isLiveSystemWallpaper(context) }
            if (isLiveWallpaper) {
                Text(
                    "检测到动态壁纸：选择「系统壁纸」时画面将由系统直接透出显示。" +
                        "动态壁纸持续渲染会略增耗电；该模式下液态玻璃的折射效果不可用（提示：想兼得动态 + 玻璃，可用「本地视频壁纸」选同一个视频文件）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // 选图后：先复制为 pending 暂存文件 → 弹出裁剪/定位界面（确认后才提升为正式壁纸，取消不污染旧壁纸）
            val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                if (uri != null) {
                    scope.launch {
                        val path = withContext(Dispatchers.IO) { persistWallpaper(context, uri, "custom_wallpaper_pending") }
                        if (path != null) {
                            cropIsVideo = false
                            cropPath = path
                        }
                    }
                }
            }

            // 选视频后：先复制为 pending 暂存文件 → 弹出裁剪/定位界面（首帧预览）
            val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                if (uri != null) {
                    scope.launch {
                        val path = withContext(Dispatchers.IO) { persistWallpaper(context, uri, "custom_video_pending") }
                        if (path != null) {
                            cropIsVideo = true
                            cropPath = path
                        }
                    }
                }
            }

            listOf(
                "default" to "默认壁纸",
                "image" to "本地图片",
                "system" to "系统壁纸",
                "video" to "本地视频"
            ).forEach { (value, label) ->
                val selected = wallpaperSource == value
                val pick: () -> Unit = {
                    haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    when (value) {
                        "image" -> { cropIsVideo = false; imageLauncher.launch("image/*") }
                        "video" -> videoLauncher.launch("video/*")
                        else -> scope.launch { prefs.setWallpaperSource(value) }
                    }
                }
                CustomCard(
                    cornerRadius = 24.dp,
                    colors = if (selected)
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    border = null,
                    clickable = true,
                    enableHaptic = true,
                    onClick = pick
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Image, null, Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        RadioButton(selected = selected, onClick = pick)
                    }
                }
            }

            // ═══ 视频壁纸设置（仅视频壁纸时显示）═══
            if (wallpaperSource == "video") {
                Spacer(Modifier.height(16.dp))
                Text("视频壁纸设置", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                val videoLoop by prefs.videoLoop.collectAsState(initial = true)
                val videoAudio by prefs.videoAudio.collectAsState(initial = false)
                val videoVolume by prefs.videoVolume.collectAsState(initial = 1f)
                CustomCard(cornerRadius = 24.dp, border = null, clickable = false, enableHaptic = false,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 20.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("循环播放", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text("播放到结尾后自动重播", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            AppSwitch(checked = videoLoop, onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch { prefs.setVideoLoop(it) }
                            })
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("播放声音", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text("默认静音播放", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            AppSwitch(checked = videoAudio, onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch { prefs.setVideoAudio(it) }
                            })
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("音量", style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("${(videoVolume * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        AdaptiveSlider(
                            value = videoVolume.coerceIn(0f, 1f),
                            onValueChange = { scope.launch { prefs.setVideoVolume(it) } },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 暗黑遮罩浓度（实时预览）
            Spacer(Modifier.height(16.dp))
            Text("暗黑遮罩浓度", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            Text("深色模式下叠加在壁纸上的黑色遮罩透明度",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp))
            CustomCard(cornerRadius = 24.dp, border = null, clickable = false, enableHaptic = false,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 20.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("遮罩浓度", style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${(wallpaperOverlay * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    AdaptiveSlider(
                        value = wallpaperOverlay.coerceIn(0f, 1f),
                        onValueChange = { scope.launch { prefs.setWallpaperOverlay(it) } },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ═══ 卡片不透明度 ═══
            Spacer(Modifier.height(16.dp))
            Text("卡片不透明度", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            Text("使用自定义背景时，卡片 / 面板的透明度——越低背景越透出来，界面更融为一体（液态玻璃风格由「质感档位」控制）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp))
            val cardOpacity by prefs.cardOpacity.collectAsState(initial = 1f)
            CustomCard(cornerRadius = 24.dp, border = null, clickable = false, enableHaptic = false,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 20.dp)) {
                    if (LocalUiStyle.current == "miuix") {
                        // MIUI 风格：卡片底色由 miuix 组件库按主题色推导（恒定不透明），
                        // 透明度设置在视觉上不生效 → 直接说明，避免「调了没反应」的困惑。
                        Text("MIUI 风格下不生效", style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("MIUI 组件库的卡片底色是固定的不透明色。想调透明度请切到「Material 3」或「液态玻璃」风格。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("卡片不透明度", style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("${(cardOpacity * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        AdaptiveSlider(
                            value = cardOpacity.coerceIn(0.15f, 1f),
                            onValueChange = { scope.launch { prefs.setCardOpacity(it) } },
                            valueRange = 0.15f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("100% = 完全不透明（默认）", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ═══ 清除本地壁纸占用 ═══
            Spacer(Modifier.height(16.dp))
            var wallpaperSizeBytes by remember { mutableStateOf(-1L) }
            var showClearWallpaperDialog by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                wallpaperSizeBytes = withContext(Dispatchers.IO) {
                    val dir = File(context.filesDir, "wallpaper")
                    if (!dir.exists()) 0L else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                }
            }
            CustomCard(
                cornerRadius = 24.dp,
                border = null,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    showClearWallpaperDialog = true
                }
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.DeleteSweep, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("清除本地壁纸占用", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text(
                            if (wallpaperSizeBytes > 0)
                                "删除已保存的本地壁纸文件（当前占用 ${CacheUtils.formatSize(wallpaperSizeBytes)}）"
                            else "删除已保存的本地壁纸文件（图片 / 视频）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // 清除确认弹窗（CustomCard 自动适配 Material / MIUI / 液态玻璃 三套 UI）
            if (showClearWallpaperDialog) {
                Dialog(
                    onDismissRequest = { showClearWallpaperDialog = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    CustomCard(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        cornerRadius = 28.dp,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        clickable = false,
                        enableHaptic = false
                    ) {
                        Column(Modifier.padding(24.dp)) {
                            Text("清除本地壁纸？", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "将删除已保存的本地壁纸文件（图片 / 视频），并将壁纸切回「默认壁纸」。\n\n清除后当前设置的壁纸将会消失，是否继续？",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(20.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val doClear: () -> Unit = {
                                    showClearWallpaperDialog = false
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            runCatching { File(context.filesDir, "wallpaper").deleteRecursively() }
                                        }
                                        wallpaperSizeBytes = 0L
                                        prefs.setWallpaperPath("")
                                        prefs.setWallpaperSource("default")
                                        prefs.setWallpaperScale(1f)
                                        prefs.setWallpaperOffsetX(0f)
                                        prefs.setWallpaperOffsetY(0f)
                                        prefs.setWallpaperRotation(0f)
                                    }
                                }
                                if (LocalUiStyle.current == "miuix") {
                                    top.yukonga.miuix.kmp.basic.TextButton(text = "取消", onClick = { showClearWallpaperDialog = false })
                                    Spacer(Modifier.padding(start = 8.dp))
                                    top.yukonga.miuix.kmp.basic.Button(
                                        onClick = doClear,
                                        colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColorsPrimary()
                                    ) { Text("清除") }
                                } else {
                                    TextButton(onClick = { showClearWallpaperDialog = false }) { Text("取消") }
                                    Spacer(Modifier.padding(start = 8.dp))
                                    Button(
                                        onClick = doClear,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        )
                                    ) { Text("清除") }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            // Dock 布局下给底部悬浮栏留出空间，避免内容被 Dock 挡住
            if (embedded) Spacer(Modifier.height(120.dp))
        }
    }

    // 壁纸裁剪 / 定位界面（图片/视频通用：视频用首帧预览）
    // 注：此块在 Column 作用域之外，需在此层重新取文件操作 Context
    val fileCtx = LocalContext.current
    cropPath?.let { path ->
        WallpaperCropDialog(
            imagePath = path,
            isVideo = cropIsVideo,
            onConfirm = { s, ox, oy, rot ->
                scope.launch {
                    // 确认后才把 pending 暂存文件提升为正式壁纸文件（覆盖旧壁纸）
                    val finalPath = withContext(Dispatchers.IO) {
                        try {
                            val dir = File(fileCtx.filesDir, "wallpaper").apply { mkdirs() }
                            val dest = File(dir, if (cropIsVideo) "custom_video" else "custom_wallpaper")
                            val src = File(path)
                            if (dest.exists()) dest.delete()
                            if (!src.renameTo(dest)) {
                                src.copyTo(dest, overwrite = true)
                                src.delete()
                            }
                            dest.absolutePath
                        } catch (_: Exception) { path }
                    }
                    prefs.setWallpaperPath(finalPath)
                    prefs.setWallpaperScale(s)
                    prefs.setWallpaperOffsetX(ox)
                    prefs.setWallpaperOffsetY(oy)
                    prefs.setWallpaperRotation(rot)
                    prefs.setWallpaperSource(if (cropIsVideo) "video" else "image")
                }
                cropPath = null
            },
            onDismiss = {
                // 取消：删除暂存文件，旧壁纸保持原样
                scope.launch(Dispatchers.IO) { runCatching { File(path).delete() } }
                cropPath = null
            }
        )
    }
}

/** 把选中的文件 URI 复制到应用私有目录，返回可持久访问的路径 */
private fun persistWallpaper(context: android.content.Context, uri: android.net.Uri, fileName: String): String? {
    return try {
        val dir = File(context.filesDir, "wallpaper").apply { mkdirs() }
        val f = File(dir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            f.outputStream().use { out -> input.copyTo(out) }
        }
        f.absolutePath
    } catch (_: Exception) { null }
}

private data class ModeChip(val mode: ThemeMode, val label: String, val icon: ImageVector)

private fun paletteStyleColor(style: PaletteStyle): Color = when (style) {
    PaletteStyle.TONAL_SPOT -> Color(0xFF6750A4)
    PaletteStyle.EXPRESSIVE -> Color(0xFFE85D04)
    PaletteStyle.VIBRANT -> Color(0xFF009688)
    PaletteStyle.RAINBOW -> Color(0xFF2196F3)
    PaletteStyle.FRUIT_SALAD -> Color(0xFF8BC34A)
    PaletteStyle.FIDELITY -> Color(0xFF9C27B0)
    PaletteStyle.CONTENT -> Color(0xFF607D8B)
    PaletteStyle.NEUTRAL -> Color(0xFF795548)
    PaletteStyle.MONOCHROME -> Color(0xFF424242)
}

/**
 * 「动画与交互」开关区（自适应三套 UI：miuix 整组 / 其余单卡片）。
 * 5 个开关均写入 PreferencesManager，全局生效。
 */
@Composable
private fun AnimationSwitches() {
    val prefs = AppSingletons.preferencesManager
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val backPreview by prefs.predictiveBackPreview.collectAsState(initial = true)
    val backBlur by prefs.backTransitionBlur.collectAsState(initial = true)
    val pageTransition by prefs.pageTransitionAnim.collectAsState(initial = true)
    val skeleton by prefs.skeletonBreathing.collectAsState(initial = true)
    val dockSlider by prefs.dockSliderMotion.collectAsState(initial = true)

    data class AnimSwitch(
        val title: String,
        val subtitle: String,
        val checked: Boolean,
        val apply: suspend (Boolean) -> Unit
    )

    val items = listOf(
        AnimSwitch("预见式返回预览", "手势返回时页面跟手预览（关闭 = 直接返回，更省电）", backPreview) { prefs.setPredictiveBackPreview(it) },
        AnimSwitch("返回过渡模糊", "返回 / 转场时页面渐糊、渐清晰，更有层次感", backBlur) { prefs.setBackTransitionBlur(it) },
        AnimSwitch("页面转场动画", "页面进入 / 退出的滑动与缩放（关闭 = 瞬时切换）", pageTransition) { prefs.setPageTransitionAnim(it) },
        AnimSwitch("骨架呼吸动画", "加载占位符的轻微呼吸动效", skeleton) { prefs.setSkeletonBreathing(it) },
        AnimSwitch("Dock 滑块动效", "底部指示滑块的液态跟随 / 拉伸", dockSlider) { prefs.setDockSliderMotion(it) }
    )

    val onToggle: (AnimSwitch) -> (Boolean) -> Unit = { item ->
        { value ->
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch { item.apply(value) }
        }
    }

    if (LocalUiStyle.current == "miuix") {
        MiuixSettingsGroup(Modifier.fillMaxWidth()) {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                AppSwitchRow(
                    title = item.title,
                    subtitle = item.subtitle,
                    checked = item.checked,
                    onChange = onToggle(item)
                )
            }
        }
    } else {
        CustomCard(cornerRadius = 24.dp, border = null, clickable = false, enableHaptic = false,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(4.dp)) {
                items.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                    AppSwitchRow(
                        title = item.title,
                        subtitle = item.subtitle,
                        checked = item.checked,
                        onChange = onToggle(item)
                    )
                }
            }
        }
    }
}

class AppearanceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShareThemeWrapper { AppearanceScreen(onBack = { finish() }) }
        }
    }
}