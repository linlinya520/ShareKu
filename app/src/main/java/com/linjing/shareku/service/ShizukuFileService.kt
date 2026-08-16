package com.linjing.shareku.service

import android.os.Bundle
import java.io.File

/**
 * Shizuku UserService：由 Shizuku 以 shell 身份（root/shell uid）反射创建。
 *
 * ⚠️ 关键：**不是** android.app.Service！参照官方 demo，UserService 是
 * 普通类实现自定义 Binder Stub，Shizuku starter 通过无参构造函数
 * 直接反射实例化，拿到的实例本身就是 IBinder。
 *
 * 在此类中 File API 不受应用沙箱限制，可访问 /storage/emulated/0/Android/data/ 等受限目录。
 */
class ShizukuFileService : IRemoteFileService.Stub() {

    override fun listDirectory(path: String): List<Bundle>? {
        android.util.Log.d("Shizuku", "listDirectory called: $path")
        return try {
            val dir = File(path)
            if (!dir.isDirectory) {
                android.util.Log.e("Shizuku", "not a directory: $path")
                return null
            }
            val result = dir.listFiles()?.mapNotNull { f ->
                val name = f.name
                if (name.isEmpty()) return@mapNotNull null
                Bundle().apply {
                    putString("name", name)
                    putBoolean("isDirectory", f.isDirectory)
                    putLong("size", f.length())
                    putString("path", f.absolutePath)
                }
            } ?: emptyList()
            android.util.Log.d("Shizuku", "listDirectory: ${result.size} entries for $path")
            result
        } catch (e: Exception) {
            android.util.Log.e("Shizuku", "listDirectory exception", e)
            null
        }
    }

    override fun readFile(path: String, offset: Long, size: Int): ByteArray? {
        return try {
            val file = File(path)
            if (!file.isFile || offset < 0 || size <= 0) return null
            val len = file.length()
            val start = offset
            val toRead = minOf(size.toLong(), len - start).toInt()
            if (toRead <= 0) return ByteArray(0)
            java.io.RandomAccessFile(file, "r").use { raf ->
                raf.seek(start)
                val buf = ByteArray(toRead)
                var read = 0
                while (read < toRead) {
                    val r = raf.read(buf, read, toRead - read)
                    if (r <= 0) break
                    read += r
                }
                if (read == toRead) buf else buf.copyOf(read)
            }
        } catch (e: Exception) {
            android.util.Log.e("Shizuku", "readFile exception: $path", e)
            null
        }
    }

    override fun stat(path: String): Bundle? {
        return try {
            val file = File(path)
            if (!file.exists()) return null
            Bundle().apply {
                putLong("size", file.length())
                putBoolean("isDirectory", file.isDirectory)
                putBoolean("exists", true)
                putLong("lastModified", file.lastModified())
            }
        } catch (e: Exception) {
            null
        }
    }
}