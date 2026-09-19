package com.linjing.shareku.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.linjing.shareku.AppSingletons
import com.linjing.shareku.MainActivity
import com.linjing.shareku.R
import com.linjing.shareku.ServerStarter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 控制中心快捷磁贴：点击 → 自动启动服务器并打开 App。
 */
@RequiresApi(Build.VERSION_CODES.N)
class ShareKuTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val running = AppSingletons.isServerRunning.value
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "ShareKu"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (running) "运行中" else "已停止"
        }
        tile.icon = Icon.createWithResource(this, R.mipmap.ic_launcher_foreground)
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val running = AppSingletons.isServerRunning.value
        if (!running) {
            // 后台启动服务器
            scope.launch { ServerStarter.start(applicationContext) }
        }
        // 打开 App（主页会实时同步运行状态）
        val i = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                this, 0, i,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(i)
        }
    }
}