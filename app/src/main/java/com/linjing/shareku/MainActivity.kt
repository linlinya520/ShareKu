package com.linjing.shareku

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.linjing.shareku.ui.navigation.LocalShareNavHost
import com.linjing.shareku.ui.theme.LocalShareTheme
import com.linjing.shareku.ui.theme.ThemeMode
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 清理上次分享残留的孤儿缓存文件（防止崩溃/kill导致的堆积）
        cleanOrphanCache()
        setContent {
            val prefs = AppSingletons.preferencesManager
            val themeModeName by prefs.themeMode.collectAsState(initial = "SYSTEM")
            val dynamicColor by prefs.dynamicColor.collectAsState(initial = true)
            val paletteOrdinal by prefs.paletteStyleOrdinal.collectAsState(initial = 0)
            val paletteStyle = com.linjing.shareku.ui.theme.color.PaletteStyle.entries
                .getOrElse(paletteOrdinal) { com.linjing.shareku.ui.theme.color.PaletteStyle.TONAL_SPOT }
            LocalShareTheme(
                themeMode = ThemeMode.fromName(themeModeName),
                dynamicColor = dynamicColor,
                paletteStyle = paletteStyle
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    LocalShareNavHost(
                        navController = navController,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    /** 启动时清理上次分享残留的孤儿缓存文件（只清理超过10分钟的旧文件） */
    private fun cleanOrphanCache() {
        val files = cacheDir.listFiles() ?: return
        val now = System.currentTimeMillis()
        for (f in files) {
            if (f.isFile && (now - f.lastModified()) > 10 * 60 * 1000) {
                f.delete()
            }
        }
    }
}