package com.linjing.shareku

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class ShareKuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // :shizuku 进程以 shell 身份运行，无法访问应用私有数据（DataStore），跳过 App 初始化
        val procName = try {
            android.os.Process.myProcessName()
        } catch (e: Throwable) {
            null
        }
        if (procName?.contains(":shizuku") == true) return
        AppSingletons.init(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)

        val serverChannel = NotificationChannel(
            CHANNEL_SERVER,
            getString(R.string.notification_channel_server),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when the server is running"
            setShowBadge(false)
        }
        nm.createNotificationChannel(serverChannel)

        // 连接确认通知渠道 —— 高优先级，弹出提醒
        val confirmChannel = NotificationChannel(
            CHANNEL_CONFIRM,
            "连接请求确认",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "新设备请求连接时的审批通知"
            setShowBadge(true)
        }
        nm.createNotificationChannel(confirmChannel)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bubbleChannel = NotificationChannel(
                CHANNEL_BUBBLE,
                "Share Bubble",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Floating bubble for quick share access"
                setShowBadge(false)
                @Suppress("DEPRECATION")
                setAllowBubbles(true)
            }
            nm.createNotificationChannel(bubbleChannel)
        }
    }

    companion object {
        const val CHANNEL_SERVER = "localshare_server"
        const val CHANNEL_CONFIRM = "localshare_confirm"
        const val CHANNEL_BUBBLE = "localshare_bubble"
    }
}
