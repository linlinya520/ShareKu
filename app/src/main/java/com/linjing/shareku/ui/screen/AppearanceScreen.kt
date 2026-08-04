package com.linjing.shareku.ui.screen

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.linjing.shareku.AppSingletons
import com.linjing.shareku.ui.component.CustomCard
import com.linjing.shareku.ui.theme.ShareThemeWrapper
import com.linjing.shareku.ui.theme.ThemeMode
import com.linjing.shareku.ui.theme.color.PaletteStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val scope = rememberCoroutineScope()
    val prefs = AppSingletons.preferencesManager
    val haptic = LocalHapticFeedback.current
    val styles = remember { PaletteStyle.entries.toList() }

    val dynamicColor by prefs.dynamicColor.collectAsState(initial = true)
    val themeModeName by prefs.themeMode.collectAsState(initial = "SYSTEM")
    val paletteOrdinal by prefs.paletteStyleOrdinal.collectAsState(initial = 0)
    val currentStyle = PaletteStyle.entries.getOrElse(paletteOrdinal) { PaletteStyle.TONAL_SPOT }
    var selectedStyle by remember { mutableStateOf(currentStyle) }
    LaunchedEffect(currentStyle) { selectedStyle = currentStyle }

    val currentMode = remember(themeModeName) { ThemeMode.fromName(themeModeName) }

    Scaffold(
        topBar = {
            TopAppBar(
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
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Spacer(Modifier.height(8.dp))

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

            modeEntries.forEachIndexed { i, entry ->
                val selected = currentMode == entry.mode
                val isSingle = modeEntries.size == 1
                CustomCard(
                    cornerRadius = when { isSingle -> 24.dp; i == 0 -> 24.dp; i == modeEntries.lastIndex -> 24.dp; else -> 4.dp },
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

            // ═══ 莫奈取色 ═══
            Spacer(Modifier.height(16.dp))
            Text("莫奈取色", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            Text(
                text = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                    "从壁纸自动提取主题颜色" else "系统版本不支持 (需 Android 12+)",
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
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                                "启用后下方的配色方案将被忽略" else "系统版本不支持",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = dynamicColor,
                        enabled = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S,
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
                val enabled = !dynamicColor
                val isSingle = styles.size == 1

                CustomCard(
                    cornerRadius = when { isSingle -> 24.dp; index == 0 -> 24.dp; index == styles.lastIndex -> 24.dp; else -> 4.dp },
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

            Spacer(Modifier.height(32.dp))
        }
    }
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

class AppearanceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShareThemeWrapper { AppearanceScreen(onBack = { finish() }) }
        }
    }
}