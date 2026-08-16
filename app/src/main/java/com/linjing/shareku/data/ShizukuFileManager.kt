package com.linjing.shareku.data

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import com.linjing.shareku.service.IRemoteFileService
import com.linjing.shareku.service.ShizukuFileService
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Shizuku 文件条目（UserService 返回结果） */
data class ShizukuEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val path: String
)

/**
 * Shizuku 高权限文件访问（UserService 方案）。
 *
 * 原理：Shizuku 授权后，通过 [Shizuku.bindUserService] 以 shell 身份启动
 * [ShizukuFileService]，在该 Service 进程内直接使用 File API 访问
 * 应用沙箱访问不到的目录（如 /storage/emulated/0/Android/data/）。
 *
 * 优雅降级：未安装 Shizuku / 服务未运行 / 未授权 / 绑定失败时
 * [listDirectory] 返回 null，调用方保持原有 File API 行为，不影响正常使用。
 */
object ShizukuFileManager {

    const val REQUEST_CODE = 10001
    private const val BIND_TIMEOUT_MS = 5000L

    private var binder: IRemoteFileService? = null
    private var boundArgs: Shizuku.UserServiceArgs? = null
    private var boundConn: ServiceConnection? = null

    /** Shizuku 服务是否在运行（需先安装 Shizuku App 并启动服务） */
    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Throwable) {
        false
    }

    /** 本应用是否已被授予 Shizuku 权限 */
    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    /** 发起 Shizuku 授权请求（结果通过 Shizuku.OnRequestPermissionResultListener 回调） */
    fun requestPermission() {
        try {
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (_: Throwable) {}
    }

    /**
     * 列出目录内容（通过 UserService，shell 身份）。
     * @return 目录条目列表；无 Shizuku / 未授权 / 绑定失败 / 目录不存在时返回 null
     */
    fun listDirectory(context: Context, path: String): List<ShizukuEntry>? {
        if (!isAvailable() || !hasPermission()) return null
        val svc = getBinder(context)
        if (svc == null) {
            android.util.Log.e("Shizuku", "listDirectory: binder null (UserService bind failed)")
            return null
        }
        return try {
            val bundles = svc.listDirectory(path)
            if (bundles == null) {
                android.util.Log.e("Shizuku", "listDirectory: service returned null for $path")
                return null
            }
            android.util.Log.d("Shizuku", "listDirectory: ${bundles.size} entries for $path")
            bundles.mapNotNull { b ->
                val name = b.getString("name") ?: return@mapNotNull null
                ShizukuEntry(
                    name = name,
                    isDirectory = b.getBoolean("isDirectory"),
                    size = b.getLong("size"),
                    path = b.getString("path") ?: (if (path.endsWith("/")) "$path$name" else "$path/$name")
                )
            }
        } catch (e: Throwable) {
            android.util.Log.e("Shizuku", "listDirectory: exception", e)
            null
        }
    }

    /** 读取受限目录下文件的指定范围（用于服务器下载）；失败返回 null */
    fun readFile(context: Context, path: String, offset: Long, size: Int): ByteArray? {
        if (!isAvailable() || !hasPermission()) return null
        val svc = getBinder(context) ?: return null
        return try {
            svc.readFile(path, offset, size)
        } catch (e: Throwable) {
            android.util.Log.e("Shizuku", "readFile: exception $path", e)
            null
        }
    }

    /** 受限目录下文件信息；失败返回 null */
    fun stat(context: Context, path: String): Bundle? {
        if (!isAvailable() || !hasPermission()) return null
        val svc = getBinder(context) ?: return null
        return try {
            svc.stat(path)
        } catch (e: Throwable) {
            null
        }
    }

    /** 获取（或首次绑定）UserService binder；成功后保持连接复用 */
    @Synchronized
    private fun getBinder(context: Context): IRemoteFileService? {
        binder?.let { return it }
        val latch = CountDownLatch(1)
        var result: IRemoteFileService? = null
        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        } catch (e: Throwable) {
            1
        }
        val args = Shizuku.UserServiceArgs(ComponentName(context, ShizukuFileService::class.java))
            .processNameSuffix(":shizuku")
            .version(appVersion)
            .daemon(false)
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                android.util.Log.d("Shizuku", "UserService connected: $name")
                result = IRemoteFileService.Stub.asInterface(service)
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                android.util.Log.w("Shizuku", "UserService disconnected: $name")
                binder = null
            }
        }
        try {
            android.util.Log.d("Shizuku", "bindUserService: $args")
            Shizuku.bindUserService(args, conn)
            if (!latch.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                android.util.Log.e("Shizuku", "bindUserService TIMEOUT after ${BIND_TIMEOUT_MS}ms")
                try { Shizuku.unbindUserService(args, conn, true) } catch (_: Throwable) {}
                return null
            }
        } catch (e: Throwable) {
            android.util.Log.e("Shizuku", "bindUserService exception", e)
            return null
        }
        binder = result
        boundArgs = args
        boundConn = conn
        return result
    }
}