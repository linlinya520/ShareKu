package com.linjing.shareku.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.ClipboardManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.linjing.shareku.AppSingletons
import com.linjing.shareku.ShareKuApp
import com.linjing.shareku.MainActivity
import com.linjing.shareku.R
import com.linjing.shareku.server.ShareKuServer
import io.ktor.server.engine.ApplicationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

class ServerForegroundService : Service() {

    private val logManager get() = AppSingletons.logManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverEngine: io.ktor.server.engine.EmbeddedServer<*, *>? = null
    private var server: ShareKuServer? = null

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
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
                val files = filePaths.map { File(it) }
                startServer(host, port, files, singleFile, upload, delete, overwrite, auth, authUser, authPass, webdav, confirm, uploadDir?.let { File(it) })
            }
            ACTION_STOP -> {
                stopServer()
                AppSingletons.setServerRunning(false)
                stopSelf()
            }
            ACTION_APPROVE -> {
                val ip = intent.getStringExtra(EXTRA_CONFIRM_IP) ?: return START_NOT_STICKY
                server?.let { s ->
                    s.pendingIps.remove(ip)
                    s.approvedIps.add(ip)
                }
                AppSingletons.dequeuePendingIp()
                cancelConfirmNotification()
            }
            // 拒绝某IP的连接请求
            ACTION_DENY -> {
                val ip = intent.getStringExtra(EXTRA_CONFIRM_IP) ?: return START_NOT_STICKY
                server?.let { s ->
                    s.pendingIps.remove(ip)
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
        webdav: Boolean, confirm: Boolean, uploadDir: File?
    ) {
        val notification = createNotification(host, port)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

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
                    clipboardManager = clipboardManager,
                    onNewConnection = { ip ->
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            showConfirmNotification(ip)
                        }
                    }
                )
                // Port fallback: try up to 10 ports if occupied
                var actualPort = port
                var engine: io.ktor.server.engine.EmbeddedServer<*, *>? = null
                for (attempt in 0 until 10) {
                    try {
                        engine = server!!.start(host, actualPort)
                        engine.start(wait = false)
                        serverEngine = engine
                        break
                    } catch (e: java.net.BindException) {
                        actualPort++
                        if (attempt == 9) throw e
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                stopSelf()
            }
        }
    }

    private fun stopServer() {
        serviceScope.launch {
            serverEngine?.stop(1000, 2000)
            serverEngine = null
            server = null
        }
    }

    // 连接确认通知 —— 带批准/拒绝两个按钮
    private fun showConfirmNotification(ip: String) {
        val approveIntent = PendingIntent.getService(
            this, ip.hashCode() and 0xFFFF,
            Intent(this, ServerForegroundService::class.java).apply {
                action = ACTION_APPROVE
                putExtra(EXTRA_CONFIRM_IP, ip)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val denyIntent = PendingIntent.getService(
            this, (ip.hashCode() and 0xFFFF) + 1,
            Intent(this, ServerForegroundService::class.java).apply {
                action = ACTION_DENY
                putExtra(EXTRA_CONFIRM_IP, ip)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notify = NotificationCompat.Builder(this, ShareKuApp.CHANNEL_CONFIRM)
            .setContentTitle("新设备请求连接")
            .setContentText("IP: $ip 正在请求访问 ShareKu")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_input_add, "批准", approveIntent)
            .addAction(android.R.drawable.ic_delete, "拒绝", denyIntent)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFY_CONFIRM_ID, notify)
        AppSingletons.enqueuePendingIp(ip)
    }

    private fun cancelConfirmNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFY_CONFIRM_ID)
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
        stopServer()
        serviceScope.cancel()
        super.onDestroy()
    }
}