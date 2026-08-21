package com.linjing.shareku.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.ClipboardManager
import android.location.LocationManager
import android.location.LocationListener
import android.os.Looper
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.linjing.shareku.AppSingletons
import com.linjing.shareku.ShareKuApp
import com.linjing.shareku.MainActivity
import com.linjing.shareku.R
import com.linjing.shareku.server.ShareKuServer
import com.linjing.shareku.peer.PeerDiscovery
import io.ktor.server.engine.ApplicationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

class ServerForegroundService : Service() {

    private val logManager get() = AppSingletons.logManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
        android.util.Log.e("ShareKu", "Server crash", e)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(this@ServerForegroundService, "服务器异常: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
        AppSingletons.setServerRunning(false)
        stopSelf()
    })
    private var serverEngine: io.ktor.server.engine.EmbeddedServer<*, *>? = null
    private var server: ShareKuServer? = null
    private val peerDiscovery by lazy { PeerDiscovery(this) }
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    // 锁屏后台传输：服务运行期间持有 CPU 唤醒锁 + WiFi 锁，防止熄屏后传输中断
    private fun acquireKeepAliveLocks() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ShareKu:Server").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            android.util.Log.w("ShareKu", "WakeLock acquire failed", e)
        }
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ShareKu:Server").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            android.util.Log.w("ShareKu", "WifiLock acquire failed", e)
        }
    }

    private fun releaseKeepAliveLocks() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {}
        try {
            wifiLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {}
        wakeLock = null
        wifiLock = null
    }

    companion object {
        const val ACTION_START = "com.material.localshare.action.START_SERVER"
        const val ACTION_STOP = "com.material.localshare.action.STOP_SERVER"
        const val ACTION_APPROVE = "com.material.localshare.action.APPROVE_IP"
        const val ACTION_DENY = "com.material.localshare.action.DENY_IP"
        const val EXTRA_HOST = "extra_host"
        const val EXTRA_PORT = "extra_port"
        const val EXTRA_FILES = "extra_files"
        const val EXTRA_SINGLE_FILE = "extra_single_file"
        const val EXTRA_UPLOAD = "extra_upload"
        const val EXTRA_DELETE = "extra_delete"
        const val EXTRA_OVERWRITE = "extra_overwrite"
        const val EXTRA_AUTH = "extra_auth"
        const val EXTRA_AUTH_USER = "extra_auth_user"
        const val EXTRA_AUTH_PASS = "extra_auth_pass"
        const val EXTRA_WEBDAV = "extra_webdav"
        const val EXTRA_CONFIRM = "extra_confirm"
        const val EXTRA_CONFIRM_IP = "extra_confirm_ip"
        const val EXTRA_UPLOAD_DIR = "extra_upload_dir"
        const val NOTIFICATION_ID = 1001
        const val NOTIFY_CONFIRM_ID = 1002
        const val NOTIFY_TRANSFER_ID = 1003
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // 服务运行期间持有唤醒锁，锁屏后传输不中断
        acquireKeepAliveLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (serverEngine != null) return START_NOT_STICKY
                val host = intent.getStringExtra(EXTRA_HOST) ?: "0.0.0.0"
                val port = intent.getIntExtra(EXTRA_PORT, 8080)
                val filePaths = intent.getStringArrayListExtra(EXTRA_FILES) ?: arrayListOf()
                val singleFile = intent.getBooleanExtra(EXTRA_SINGLE_FILE, false)
                val upload = intent.getBooleanExtra(EXTRA_UPLOAD, false)
                val delete = intent.getBooleanExtra(EXTRA_DELETE, false)
                val overwrite = intent.getBooleanExtra(EXTRA_OVERWRITE, true)
                val auth = intent.getBooleanExtra(EXTRA_AUTH, false)
                val authUser = intent.getStringExtra(EXTRA_AUTH_USER) ?: "admin"
                val authPass = intent.getStringExtra(EXTRA_AUTH_PASS) ?: "admin"
                val webdav = intent.getBooleanExtra(EXTRA_WEBDAV, true)
                val confirm = intent.getBooleanExtra(EXTRA_CONFIRM, false)
                val uploadDir = intent.getStringExtra(EXTRA_UPLOAD_DIR)
                val receiveDir = runBlocking { AppSingletons.preferencesManager.receiveDir.first() }
                val files = filePaths.map { File(it) }
                startServer(host, port, files, singleFile, upload, delete, overwrite, auth, authUser, authPass, webdav, confirm, uploadDir?.let { File(it) }, receiveDir)
            }
            ACTION_STOP -> {
                stopServer()
                AppSingletons.setServerRunning(false)
                stopSelf()
            }
            // 拒绝某IP的连接请求（拉黑）
            ACTION_DENY -> {
                val ip = intent.getStringExtra(EXTRA_CONFIRM_IP) ?: return START_NOT_STICKY
                server?.let { s ->
                    s.blockedIps.add(ip)
                }
                AppSingletons.dequeuePendingIp()
                cancelConfirmNotification()
            }
        }
        return START_STICKY
    }

    private fun startServer(
        host: String, port: Int, files: List<File>,
        singleFileSandbox: Boolean, upload: Boolean, delete: Boolean, overwrite: Boolean,
        auth: Boolean, authUser: String, authPass: String,
        webdav: Boolean, confirm: Boolean, uploadDir: File?, receiveDir: String
    ) {
        val notification = createNotification(host, port)
        // 仅当用户开启定位保活且已授予定位权限时才附加 location 类型，
        // 否则 Android 14+ (targetSdk 35) 会因缺少运行时权限抛 SecurityException 闪退
        val keepAliveOn = runBlocking { AppSingletons.preferencesManager.enableLocationKeepAlive.first() }
        val hasLocationPerm = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = if (keepAliveOn && hasLocationPerm)
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            else
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            try {
                startForeground(NOTIFICATION_ID, notification, fgsType)
            } catch (_: SecurityException) {
                // 后台启动 location 类型被拒 → 降级为纯 dataSync（保活用 NETWORK_PROVIDER 不依赖该类型）
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        startLocationKeepAlive()

        serviceScope.launch {
            try {
                val clipboardManager = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                server = ShareKuServer(
                    context = this@ServerForegroundService,
                    logManager = logManager,
                    sharedFiles = files,
                    isSingleFileSandbox = singleFileSandbox,
                    allowUpload = upload,
                    allowDelete = delete,
                    allowOverwrite = overwrite,
                    enableAuth = auth,
                    authUsername = authUser,
                    authPassword = authPass,
                    enableWebDav = webdav,
                    requireConfirm = confirm,
                    uploadDir = uploadDir,
                    receiveDir = File(receiveDir),
                    clipboardManager = clipboardManager,
                    onNewConnection = { ip, code ->
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        showConfirmNotification(ip, code)
                    }
                },
                    onPeerTransfer = { senderIp, fileName, fileSize, tempFile ->
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            // 局域网直连：不再询问，直接保存到接收目录
                            val destDir = (server?.receiveDir ?: File(getExternalFilesDir(null), "ShareKu"))
                                .also { if (!it.exists()) it.mkdirs() }.absolutePath
                            serviceScope.launch {
                                approveTransfer(tempFile.absolutePath, destDir, fileName)
                            }
                        }
                    },
                    onPeerReceiveProgress = { name, received, total ->
                        // 写盘进度 → 通知栏进度条（接收端可见传输进度）
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            showTransferProgress(name, received, total)
                        }
                    }
                )
                // Port fallback: try up to 10 ports if occupied
                var actualPort = port
                var engine: io.ktor.server.engine.EmbeddedServer<*, *>? = null
                val srv = server ?: return@launch
                // 受限目录但 Shizuku 未授权 → 提示（网页端将无法访问）
                srv.restrictedAccessWarning?.let { warn ->
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(this@ServerForegroundService, warn, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
                for (attempt in 0 until 10) {
                    try {
                        engine = srv.start(host, actualPort)
                        engine.start(wait = false)
                        serverEngine = engine
                        // Register NSD so other devices can discover us
                        peerDiscovery.registerService(actualPort)
                        break
                    } catch (e: Exception) {
                        if (attempt == 9) throw e
                        actualPort++
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(this@ServerForegroundService, "服务启动失败: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
                AppSingletons.setServerRunning(false)
                stopSelf()
            }
        }
    }

    private fun stopServer() {
        stopLocationKeepAlive()
        peerDiscovery.unregisterService()
        serviceScope.launch {
            serverEngine?.stop(1000, 2000)
            serverEngine = null
            server = null
        }
    }

    // 连接确认通知 —— 展示一次性验证码 + 拒绝按钮（不再有"批准"按钮，码即授权凭证）
    private fun showConfirmNotification(ip: String, code: String) {
        val denyIntent = PendingIntent.getService(
            this, (ip.hashCode() and 0xFFFF) + 1,
            Intent(this, ServerForegroundService::class.java).apply {
                action = ACTION_DENY
                putExtra(EXTRA_CONFIRM_IP, ip)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notify = NotificationCompat.Builder(this, ShareKuApp.CHANNEL_CONFIRM)
            .setContentTitle("新设备请求访问")
            .setContentText("IP: $ip · 一次性验证码: $code（仅5分钟有效）")
            .setStyle(NotificationCompat.BigTextStyle().bigText("IP: $ip 请求访问 ShareKu\n\n一次性验证码: $code\n\n把此码告知对方，对方输入后即可访问。验证码仅可使用一次。"))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_delete, "拒绝", denyIntent)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFY_CONFIRM_ID, notify)
        AppSingletons.enqueuePendingIp(ip, code)
    }

    private fun cancelConfirmNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFY_CONFIRM_ID)
    }

    // ═══ 直连接收进度通知（接收端可见传输进度条） ═══
    private fun showTransferProgress(fileName: String, received: Long, total: Long) {
        val pct = if (total > 0) ((received * 100) / total).toInt().coerceIn(0, 100) else 0
        val sizeStr = when {
            received < 1024 -> "${received}B"
            received < 1024 * 1024 -> "${received / 1024}KB"
            else -> "${"%.1f".format(received.toDouble() / (1024 * 1024))}MB"
        }
        val totalStr = when {
            total < 1024 -> "${total}B"
            total < 1024 * 1024 -> "${total / 1024}KB"
            else -> "${"%.1f".format(total.toDouble() / (1024 * 1024))}MB"
        }
        val notify = NotificationCompat.Builder(this, ShareKuApp.CHANNEL_SERVER)
            .setContentTitle("📥 正在接收 $fileName")
            .setContentText("$sizeStr / $totalStr ($pct%)")
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setProgress(100, pct, total <= 0)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFY_TRANSFER_ID, notify)
    }

    // ═══ 传输保存核心逻辑（直接保存，不再询问） ═══
    private suspend fun approveTransfer(tempPath: String, destDir: String, fileName: String) {
        val tempFile = File(tempPath)
        if (!tempFile.exists()) return
        try {
            val destFile = File(destDir, tempFile.name.replaceFirst(Regex("^\\d+_"), ""))
            var counter = 1
            var finalDest = destFile
            while (finalDest.exists()) {
                val dot = destFile.name.lastIndexOf('.')
                val base = if (dot > 0) destFile.name.substring(0, dot) else destFile.name
                val ext = if (dot > 0) destFile.name.substring(dot) else ""
                finalDest = File(destDir, "${base}_${counter}${ext}")
                counter++
            }
            tempFile.copyTo(finalDest, overwrite = true)
            tempFile.delete()
            // 保存完成 → 进度通知变完成通知（100%）
            showTransferDone(fileName, finalDest.absolutePath)
        } catch (e: Exception) {
            // 目标目录不可写（缺少存储权限等）→ 回退到应用外部目录，不崩溃
            try {
                val fallbackDir = File(getExternalFilesDir(null), "ShareKu")
                if (!fallbackDir.exists()) fallbackDir.mkdirs()
                val fallback = File(fallbackDir, tempFile.name.replaceFirst(Regex("^\\d+_"), ""))
                tempFile.copyTo(fallback, overwrite = true)
                tempFile.delete()
                showTransferDone(fileName, fallback.absolutePath)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(
                        this@ServerForegroundService,
                        "存储权限不足，文件已保存到应用目录：$fallback",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e2: Exception) {
                android.util.Log.e("ShareKu", "Save transfer failed", e2)
            }
        }
    }

    private fun showTransferDone(fileName: String, savedPath: String) {
        val notify = NotificationCompat.Builder(this, ShareKuApp.CHANNEL_CONFIRM)
            .setContentTitle("✅ 已接收 $fileName")
            .setContentText("已保存到 $savedPath")
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFY_TRANSFER_ID, notify)
    }

    // ═══ 后台定位保活 (鸿蒙/国产ROM防断网) ═══
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null

    private fun startLocationKeepAlive() {
        // Check if user has enabled location keep-alive in settings
        val enabled = runBlocking { AppSingletons.preferencesManager.enableLocationKeepAlive.first() }
        if (!enabled) return
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        try {
            locationListener = LocationListener { /* 不需要坐标，只需"呼吸" */ }
            // NETWORK_PROVIDER: 低功耗，不显示GPS图标
            locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60_000L, 0f, locationListener!!, Looper.getMainLooper())
        } catch (_: Exception) {}
    }

    private fun stopLocationKeepAlive() {
        locationListener?.let { locationManager?.removeUpdates(it) }
        locationListener = null
        locationManager = null
    }

    private fun createNotification(host: String, port: Int): Notification {
        val protocol = "http"
        val address = "$protocol://$host:$port"

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                putExtra("server_address", address)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ServerForegroundService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ShareKuApp.CHANNEL_SERVER)
            .setContentTitle(getString(R.string.notification_server_running))
            .setContentText(address)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.stop_service), stopIntent)
            .build()
    }

    override fun onDestroy() {
        releaseKeepAliveLocks()
        stopServer()
        serviceScope.cancel()
        super.onDestroy()
    }
}