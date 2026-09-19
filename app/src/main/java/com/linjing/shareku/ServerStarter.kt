package com.linjing.shareku

import android.content.Context
import android.content.Intent
import android.os.Build
import com.linjing.shareku.server.NetworkUtils
import com.linjing.shareku.service.ServerForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 统一启动入口 —— 主页 / QS 磁贴 / 桌面小组件共用，
 * 从 DataStore 读取配置后拉起 ServerForegroundService。
 */
object ServerStarter {

    /**
     * 启动服务器。
     * @return true = 已启动或之前已在运行；false = 启动失败（共享目录不存在等）
     */
    suspend fun start(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            if (AppSingletons.isServerRunning.value) return@withContext true
            val prefs = AppSingletons.preferencesManager

            val sharedDir = prefs.sharedDir.first()
            if (!File(sharedDir).exists()) return@withContext false

            val ip = NetworkUtils().getPreferredInterface(prefs.networkInterface.first())?.ipAddress
                ?: "0.0.0.0"

            val intent = Intent(context, ServerForegroundService::class.java).apply {
                action = ServerForegroundService.ACTION_START
                putExtra(ServerForegroundService.EXTRA_HOST, ip)
                putExtra(ServerForegroundService.EXTRA_PORT, prefs.port.first())
                putStringArrayListExtra(
                    ServerForegroundService.EXTRA_FILES,
                    ArrayList(listOf(sharedDir))
                )
                putExtra(ServerForegroundService.EXTRA_AUTH, prefs.enableAuth.first())
                putExtra(ServerForegroundService.EXTRA_AUTH_USER, prefs.authUsername.first())
                putExtra(ServerForegroundService.EXTRA_AUTH_PASS, prefs.authPassword.first())
                putExtra(ServerForegroundService.EXTRA_WEBDAV, prefs.enableWebDav.first())
                putExtra(ServerForegroundService.EXTRA_UPLOAD, prefs.allowUpload.first())
                putExtra(ServerForegroundService.EXTRA_DELETE, prefs.allowDelete.first())
                putExtra(ServerForegroundService.EXTRA_OVERWRITE, prefs.allowOverwrite.first())
                putExtra(ServerForegroundService.EXTRA_CONFIRM, prefs.requireConnectionConfirm.first())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            AppSingletons.setServerRunning(true)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 停止服务器 */
    fun stop(context: Context) {
        try {
            val intent = Intent(context, ServerForegroundService::class.java).apply {
                action = ServerForegroundService.ACTION_STOP
            }
            context.startService(intent)
            AppSingletons.setServerRunning(false)
        } catch (_: Exception) {}
    }
}
