package com.linjing.shareku.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.linjing.shareku.AppSingletons
import com.linjing.shareku.ui.theme.color.PaletteStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private data class InitialPrefs(
    val themeModeName: String,
    val dynamicColor: Boolean,
    val paletteOrdinal: Int,
    val uiStyle: String
)

@Composable
fun ShareThemeWrapper(content: @Composable () -> Unit) {
    val prefs = AppSingletons.preferencesManager
    // 首帧同步读取一次用户设置并缓存：此前用「collectAsState + 默认值」会先渲染一帧
    // 系统主题/默认样式，导致「先暗黑 → 再变白」的闪烁。读取只发生一次，开销可忽略。
    val initial = remember {
        runBlocking {
            InitialPrefs(
                themeModeName = prefs.themeMode.first(),
                dynamicColor = prefs.dynamicColor.first(),
                paletteOrdinal = prefs.paletteStyleOrdinal.first(),
                uiStyle = prefs.uiStyle.first()
            )
        }
    }
    val themeModeName by prefs.themeMode.collectAsState(initial = initial.themeModeName)
    val dynamicColor by prefs.dynamicColor.collectAsState(initial = initial.dynamicColor)
    val paletteOrdinal by prefs.paletteStyleOrdinal.collectAsState(initial = initial.paletteOrdinal)
    val paletteStyle = PaletteStyle.entries.getOrElse(paletteOrdinal) { PaletteStyle.TONAL_SPOT }
    val uiStyle by prefs.uiStyle.collectAsState(initial = initial.uiStyle)

    LocalShareTheme(
        themeMode = ThemeMode.fromName(themeModeName),
        dynamicColor = dynamicColor,
        paletteStyle = paletteStyle,
        uiStyle = uiStyle
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = androidx.compose.ui.graphics.Color.Transparent
        ) {
            content()
        }
    }
}