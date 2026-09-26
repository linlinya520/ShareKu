package com.linjing.shareku

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShareKuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 未捕获崩溃自动落盘（含后台线程），方便无 adb 的设备取证
        installCrashLogger()
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
    }

    /**
     * 未捕获异常（含后台线程）写出到文件：优先 /sdcard/Download，其次外部私有目录 crash/。
     * 仍调用系统默认处理，保证照常弹崩溃提示。
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val text = buildString {
                    append("time=").append(stamp).append('\n')
                    append("thread=").append(thread.name).append('\n')
                    append("device=").append(Build.MODEL)
                        .append(" | Android ").append(Build.VERSION.RELEASE)
                        .append(" | ").append(Build.SUPPORTED_ABIS.joinToString()).append('\n')
                    append("----\n").append(sw.toString())
                }
                val name = "ShareKu-crash-" + System.currentTimeMillis() + ".txt"
                val dirs = listOf(
                    File("/sdcard/Download"),
                    File(getExternalFilesDir(null) ?: filesDir, "crash")
                )
                dirs.forEach { d ->
                    try {
                        d.mkdirs()
                        File(d, name).writeText(text)
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        const val CHANNEL_SERVER = "localshare_server"
        const val CHANNEL_CONFIRM = "localshare_confirm"
    }
}
