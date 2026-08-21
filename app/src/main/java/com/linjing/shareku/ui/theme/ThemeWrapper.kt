package com.linjing.shareku.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.linjing.shareku.AppSingletons
import com.linjing.shareku.ui.theme.color.PaletteStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@Composable
fun ShareThemeWrapper(content: @Composable () -> Unit) {
    val initialTheme = runBlocking { AppSingletons.preferencesManager.themeMode.first() }
    val themeModeName by AppSingletons.preferencesManager.themeMode.collectAsState(initial = initialTheme)
    val dynamicColor by AppSingletons.preferencesManager.dynamicColor.collectAsState(initial = true)
    val paletteOrdinal by AppSingletons.preferencesManager.paletteStyleOrdinal.collectAsState(initial = 0)
    val paletteStyle = PaletteStyle.entries.getOrElse(paletteOrdinal) { PaletteStyle.TONAL_SPOT }
    val initialUiStyle = runBlocking { AppSingletons.preferencesManager.uiStyle.first() }
    val uiStyle by AppSingletons.preferencesManager.uiStyle.collectAsState(initial = initialUiStyle)

    LocalShareTheme(
        themeMode = ThemeMode.fromName(themeModeName),
        dynamicColor = dynamicColor,
        paletteStyle = paletteStyle,
        uiStyle = uiStyle
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}