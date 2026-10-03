package com.linjing.shareku.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.RandomAccessFile
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全量诊断日志采集（用于排查闪退等疑难问题）。
 *
 * 设计要点 —— 面向「记录过程中应用直接闪退」的场景：
 *  1. 后台起 `logcat` 子进程抓本应用（同 uid，含 :shizuku 子进程）的全部日志；
 *  2. **逐行 flush**：每行立刻交给内核页缓存，进程被系统杀死也不会丢已写入的内容；
 *  3. 崩溃兜底：未捕获异常处理器会把完整堆栈补写进同一文件（无论是否在记录）；
 *  4. 文件固定为 `diagnostic.log`，便于「查看 / 导出 / 清除」。
 */
object DiagnosticLog {

    private const val DIR_NAME = "ShareKu"
    private const val FILE_NAME = "diagnostic.log"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stampFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording

    private val _lineCount = MutableStateFlow(0)
    val lineCount: StateFlow<Int> = _lineCount

    private var job: Job? = null
    @Volatile
    private var proc: Process? = null

    /** 日志文件：优先外置 `/sdcard/ShareKu/logs/`（用户可直接取走），否则退到应用外部私有目录 */
    fun logFile(context: Context): File {
        val ext = File("/sdcard/$DIR_NAME/logs")
        val ok = runCatching { ext.mkdirs(); ext.isDirectory && ext.canWrite() }.getOrDefault(false)
        if (ok) return File(ext, FILE_NAME)
        val fb = File(context.getExternalFilesDir(null) ?: context.filesDir, "logs")
        runCatching { fb.mkdirs() }
        return File(fb, FILE_NAME)
    }

    /** 开始记录（重复调用无副作用） */
    fun start(context: Context): String {
        if (_recording.value) return "已在记录中"
        val file = logFile(context)
        _lineCount.value = 0
        job = scope.launch {
            var writer: BufferedWriter? = null
            try {
                file.parentFile?.mkdirs()
                writer = BufferedWriter(OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8))
                writer.write("\n\n===== 开始记录 ${stampFmt.format(Date())} =====")
                writer.newLine()
                writer.write(
                    "device=${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE}" +
                        " (API ${Build.VERSION.SDK_INT}) | pid=${android.os.Process.myPid()}" +
                        " uid=${android.os.Process.myUid()} | ver=${appVersion(context)}"
                )
                writer.newLine()
                writer.flush()

                val p = ProcessBuilder(logcatCommand()).redirectErrorStream(true).start()
                proc = p
                p.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        writer.write(line)
                        writer.newLine()
                        // ★ 逐行落盘：闪退也不丢已采集内容
                        writer.flush()
                        _lineCount.value = _lineCount.value + 1
                    }
                }
            } catch (t: Throwable) {
                runCatching {
                    writer?.write("[collector] 采集线程结束：${t.message}")
                    writer?.newLine()
                    writer?.flush()
                }
            } finally {
                runCatching { writer?.flush() }
                runCatching { writer?.close() }
                proc = null
                _recording.value = false
            }
        }
        _recording.value = true
        return "已开始记录 → ${file.absolutePath}"
    }

    fun stop(): String {
        val was = _recording.value
        runCatching { proc?.destroy() }
        proc = null
        job?.cancel()
        job = null
        _recording.value = false
        return if (was) "已停止记录" else "当前未在记录"
    }

    fun clear(context: Context): String {
        stop()
        val f = logFile(context)
        if (!f.exists() || f.length() == 0L) return "暂无日志"
        val ok = runCatching { f.delete() }.getOrDefault(false)
        _lineCount.value = 0
        return if (ok) "已清除日志" else "清除失败（文件被占用）"
    }

    /** 读取日志尾部（默认最多 600KB，避免超大文件卡住界面） */
    fun readTail(context: Context, maxBytes: Int = 600_000): String {
        val f = logFile(context)
        if (!f.isFile) return ""
        return runCatching {
            val len = f.length()
            val start = (len - maxBytes).coerceAtLeast(0L)
            RandomAccessFile(f, "r").use { raf ->
                raf.seek(start)
                val bytes = ByteArray((len - start).toInt())
                raf.readFully(bytes)
                buildString {
                    if (start > 0L) append("…（仅显示最后 ${maxBytes / 1024} KB）\n\n")
                    append(String(bytes, Charsets.UTF_8))
                }
            }
        }.getOrDefault("")
    }

    /** 导出到「下载」目录，返回落盘路径（失败返回 null） */
    fun exportToDownload(context: Context): String? {
        val f = logFile(context)
        if (!f.isFile || f.length() == 0L) return null
        val name = "ShareKu-log-" +
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
        val dest = File("/sdcard/Download", name)
        return runCatching {
            f.copyTo(dest, overwrite = true)
            dest.absolutePath
        }.getOrNull()
    }

    /** 崩溃兜底写入（由 Application 的未捕获异常处理器调用） */
    fun appendCrash(context: Context, thread: Thread, throwable: Throwable) {
        runCatching {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val f = logFile(context)
            f.parentFile?.mkdirs()
            FileOutputStream(f, true).use { fos ->
                val w = OutputStreamWriter(fos, Charsets.UTF_8)
                w.write("\n\n===== 崩溃 ${stampFmt.format(Date())} =====")
                w.write("\nthread=${thread.name}")
                w.write("\ndevice=${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE}")
                w.write("\n$sw")
                w.flush()
                // 崩溃路径：额外 sync 一次，确保真正落盘
                runCatching { fos.fd.sync() }
            }
        }
    }

    /** Android 7+ 用 --uid 可同时抓到主进程与 :shizuku 等子进程 */
    private fun logcatCommand(): List<String> {
        val uid = android.os.Process.myUid()
        val pid = android.os.Process.myPid()
        val filter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) "--uid=$uid" else "--pid=$pid"
        return listOf("/system/bin/logcat", "-v", "threadtime", filter)
    }

    private fun appVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")
}