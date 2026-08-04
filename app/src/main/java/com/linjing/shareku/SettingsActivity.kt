package com.linjing.shareku

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.linjing.shareku.ui.screen.AboutActivity
import com.linjing.shareku.ui.screen.AppearanceActivity
import com.linjing.shareku.ui.screen.CacheCleanupActivity
import com.linjing.shareku.ui.screen.FileOpsActivity
import com.linjing.shareku.ui.screen.SecurityActivity
import com.linjing.shareku.ui.screen.ServerActivity
import com.linjing.shareku.ui.screen.SettingsScreen
import com.linjing.shareku.ui.theme.ShareThemeWrapper

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShareThemeWrapper {
                SettingsScreen(
                    onBack = { finish() },
                    onSecurity = { startActivity(Intent(this@SettingsActivity, SecurityActivity::class.java)) },
                    onServer = { startActivity(Intent(this@SettingsActivity, ServerActivity::class.java)) },
                    onFileOps = { startActivity(Intent(this@SettingsActivity, FileOpsActivity::class.java)) },
                    onCacheCleanup = { startActivity(Intent(this@SettingsActivity, CacheCleanupActivity::class.java)) },
                    onAppearance = { startActivity(Intent(this@SettingsActivity, AppearanceActivity::class.java)) },
                    onAbout = { startActivity(Intent(this@SettingsActivity, AboutActivity::class.java)) }
                )
            }
        }
    }
}