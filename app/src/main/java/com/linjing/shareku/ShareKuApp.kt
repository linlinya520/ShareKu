package com.linjing.shareku

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import com.linjing.shareku.ui.performance.applyPreferredDisplayMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShareKuApp : Application() {
    // 屏幕刷新率档位：设置值缓存 + 最近一个前台 Activity（供冷启动竞态补偿与实时生效）
    @Volatile
    private var cachedScreenDisplayModeId: Int = 0
    // App 内主题模式缓存（供 Activity 启动瞬间校正窗口底色，避免深色下白闪）
    @Volatile
    private var cachedThemeMode: String = "SYSTEM"
    // 壁纸来源缓存（系统壁纸模式下窗口需保持透明，透出系统壁纸层）
    @Volatile
    private var cachedWallpaperSource: String = "default"
    private var lastActivityRef: java.lang.ref.WeakReference<Activity>? = null
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
        installDisplayModeHooks()
    }

    /**
     * 屏幕帧率档位：
     * ① 每个 Activity 创建时立即应用当前保存的档位；
     * ② 监听设置变化，实时应用到当前 Activity（补偿冷启动时 DataStore 尚未读取的竞态）。
     */
    private fun installDisplayModeHooks() {
        appScope.launch {
            AppSingletons.preferencesManager.screenDisplayModeId.collect { modeId ->
                cachedScreenDisplayModeId = modeId
                lastActivityRef?.get()?.let { activity ->
                    activity.runOnUiThread {
                        runCatching { activity.applyPreferredDisplayMode(modeId) }
                    }
                }
            }
        }
        // 缓存 App 内主题模式（供窗口底色校正）
        appScope.launch {
            AppSingletons.preferencesManager.themeMode.collect { cachedThemeMode = it }
        }
        // 缓存壁纸来源（系统壁纸模式需保持窗口透明）
        appScope.launch {
            AppSingletons.preferencesManager.wallpaperSource.collect { cachedWallpaperSource = it }
        }
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                lastActivityRef = java.lang.ref.WeakReference(activity)
                runCatching { activity.applyPreferredDisplayMode(cachedScreenDisplayModeId) }
                applyLaunchWindowBackground(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                lastActivityRef = java.lang.ref.WeakReference(activity)
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (lastActivityRef?.get() === activity) lastActivityRef = null
            }
        })
    }

        /**
     * Activity 启动瞬间（Compose 首帧绘制之前）校正窗口底色。
     *
     * 为什么需要：主题资源的 windowBackground 只能跟随「系统」深色开关，
     * 而 App 允许「系统浅色 + App 内强制深色」这种组合 —— 此时二级页面启动瞬间
     * 会先闪一帧白色（用户所说「闪光弹」）。这里按 App 内主题再覆盖一次。
     *
     * 透明窗口（分享面板 / 气泡）与「系统壁纸」模式保持原样，不干预。
     */
    private fun applyLaunchWindowBackground(activity: Activity) {
        runCatching {
            val window = activity.window ?: return
            // 透明窗口（分享面板 / 气泡）不做任何处理：它们本就需要透出下层
            val ta = activity.theme.obtainStyledAttributes(
                intArrayOf(android.R.attr.windowIsTranslucent)
            )
            val translucent = try {
                ta.getBoolean(0, false)
            } finally {
                ta.recycle()
            }
            if (translucent) return
            val color = when {
                // 系统壁纸模式：窗口需透出系统绘制的壁纸层
                cachedWallpaperSource == "system" -> android.graphics.Color.TRANSPARENT
                isAppInDarkTheme() -> WINDOW_BG_DARK
                else -> WINDOW_BG_LIGHT
            }
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(color))
        }
    }

    /** App 内当前是否深色（App 强制优先，其次跟随系统） */
    private fun isAppInDarkTheme(): Boolean {
        val systemDark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        return when (cachedThemeMode) {
            "LIGHT" -> false
            "DARK" -> true
            else -> systemDark
        }
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

        /** 启动窗口底色（与主题 surface 对齐；深色 =色 #141218） */
        private const val WINDOW_BG_LIGHT = 0xFFFDFBFF.toInt()
        private const val WINDOW_BG_DARK = 0xFF141218.toInt()
    }
}
