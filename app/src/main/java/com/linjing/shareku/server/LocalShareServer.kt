package com.linjing.shareku.server

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import com.linjing.shareku.data.LogEntry
import com.linjing.shareku.data.LogManager
import com.linjing.shareku.data.ShizukuEntry
import com.linjing.shareku.data.ShizukuFileManager
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ShutDownUrl
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.deflate
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.contentType
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.request.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.delete
import io.ktor.server.routing.head
import io.ktor.server.routing.options
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.WebSocketSession
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.readAvailable
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet
import java.io.FileInputStream
import java.net.URLDecoder
import java.nio.file.Files as JavaFiles
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ShareKuServer(
    private val context: Context,
    private val logManager: LogManager,
    val sharedFiles: List<File> = emptyList(),
    val isSingleFileSandbox: Boolean = false,
    val allowUpload: Boolean = false,
    val allowDelete: Boolean = false,
    val allowOverwrite: Boolean = true,
    val enableAuth: Boolean = false,
    val authUsername: String = "shareku",
    val authPassword: String = "share123",
    val enableWebDav: Boolean = true,
    val requireConfirm: Boolean = false,
    val uploadDir: File? = null,
    val receiveDir: File? = null,
    val clipboardManager: ClipboardManager? = null,
    // 连接确认回调：当新IP需要审批时调用，传入IP地址 + 一次性验证码
    val onNewConnection: ((String, String) -> Unit)? = null,
    // 直连传输请求回调：发送端IP + 文件名 + 大小 + 临时文件路径
    val onPeerTransfer: ((senderIp: String, fileName: String, fileSize: Long, tempFile: File) -> Unit)? = null,
    // 直连传输接收进度回调（写盘循环中调用，用于接收端进度条/通知）
    val onPeerReceiveProgress: ((fileName: String, received: Long, total: Long) -> Unit)? = null
) {
    private val _logChannel = Channel<LogEntry>(Channel.UNLIMITED)
    private val _clipboardState = MutableStateFlow("")
    // WebSocket 客户端集合，用于广播上传/写盘进度
    private val wsClients = CopyOnWriteArraySet<WebSocketSession>()
    // 独立协程用于异步广播，绝不阻塞上传写盘循环
    private val broadcastScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 异步向所有已连接的网页端广播消息（不阻塞调用方） */
    private fun broadcastAsync(message: String) {
        broadcastScope.launch {
            wsClients.forEach { session ->
                try { session.send(message) } catch (_: Exception) { wsClients.remove(session) }
            }
        }
    }

    // ═══ 连接确认（防 IP 冒用） ═══
    // 说明：不信任 IP 白名单（局域网内 IP 可被伪造/占用），
    // 改为「一次性验证码 + 会话令牌」：新 IP 需在手机上看到一次性 4 位码，
    // 由持有手机的人把码告知访问者；访问者输入码验证通过后获得绑定 IP 的会话令牌。
    // 即使别人改 IP 成已授权 IP，没有令牌也无法访问。
    val blockedIps = mutableSetOf<String>()
    // 一次性验证码：ip -> (code, 过期时间)
    private val pendingCodes = mutableMapOf<String, Pair<String, Long>>()
    // 会话令牌：token -> (绑定的ip, 过期时间)
    private val sessionTokens = mutableMapOf<String, Pair<String, Long>>()
    private val codeChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val random = java.security.SecureRandom()
    private val TOKEN_TTL_MS = 24L * 3600 * 1000   // 令牌有效期 24 小时
    private val CODE_TTL_MS = 5L * 60 * 1000       // 验证码有效期 5 分钟

    // ═══ Shizuku 受限目录访问 ═══
    // 共享根目录（用于判断是否受限）
    private val accessRootPath: String = sharedFiles.firstOrNull()?.let {
        if (it.isDirectory) it.absolutePath else it.parentFile?.absolutePath
    } ?: ""

    /** 共享根目录是否受限（普通 File API 无法访问，如 Android/data/obb） */
    private val isRestrictedRoot: Boolean by lazy {
        val root = File(accessRootPath)
        !(root.exists() && root.canRead() && root.listFiles() != null)
    }

    /** 受限目录访问是否可用（受限 + Shizuku 已授权） */
    private val shizukuAccessReady: Boolean by lazy {
        isRestrictedRoot && ShizukuFileManager.isAvailable() && ShizukuFileManager.hasPermission()
    }

    /** 共享根受限但 Shizuku 未就绪时的提示（网页端将无法访问该目录） */
    val restrictedAccessWarning: String?
        get() = if (isRestrictedRoot && !shizukuAccessReady) {
            "共享目录为受限目录（如 Android/data），未授权 Shizuku 时网页端将无法访问"
        } else {
            null
        }

    fun start(
        host: String,
        port: Int
    ) = embeddedServer(
        factory = CIO,
        port = port,
        host = host
    ) {
        install(Compression) {
            gzip { priority = 1.0 }
            deflate { priority = 10.0 }
        }
        install(PartialContent)
        install(ConditionalHeaders)
        install(CORS) {
            allowHost("localhost")
            allowHost("127.0.0.1")
            allowHost("0.0.0.0")
            allowHost("*.local")
            allowMethod(io.ktor.http.HttpMethod.Options)
            allowMethod(io.ktor.http.HttpMethod.Get)
            allowMethod(io.ktor.http.HttpMethod.Post)
            allowMethod(io.ktor.http.HttpMethod.Put)
            allowMethod(io.ktor.http.HttpMethod.Delete)
            allowMethod(io.ktor.http.HttpMethod.Head)
            allowMethod(io.ktor.http.HttpMethod.Options)
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.ContentType)
            allowHeader("Depth")
            allowHeader("Destination")
            allowHeader("Overwrite")
        }
        install(WebSockets)

        routing {
            // Static assets (no auth required)
            get("/assets/style.css") {
                call.respondText(
                    context.assets.open("web/style.css").bufferedReader().use { it.readText() },
                    ContentType.Text.CSS
                )
            }
            get("/assets/script.js") {
                call.respondText(
                    context.assets.open("web/script.js").bufferedReader().use { it.readText() },
                    ContentType.Text.JavaScript
                )
            }

            // WebSocket for real-time logs & clipboard sync
    webSocket("/ws") {
        wsClients.add(this)
        try {
            for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            when {
                                text.startsWith("clipboard:") -> {
                                    val data = text.removePrefix("clipboard:")
                                    clipboardManager?.let { cm ->
                                        val clip = android.content.ClipData.newPlainText("from_web", data)
                                        cm.setPrimaryClip(clip)
                                    }
                                    send("clipboard:ok")
                                }
                                text == "get_clipboard" -> {
                                    val clip = clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                    send("clipboard_data:$clip")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Client disconnected
                } finally {
                    wsClients.remove(this)
                }
            }

            // Unauthenticated endpoints
            get("/api/status") {
                call.respondText("""{"allowUpload":$allowUpload,"allowDelete":$allowDelete,"authRequired":$enableAuth,"webdav":$enableWebDav}""", ContentType.Application.Json)
            }
            // 一次性验证码验证端点：验证通过后签发会话令牌（Set-Cookie，绑定IP，24小时有效）
            post("/api/verify") {
                val code = call.request.queryParameters["code"]?.trim().orEmpty()
                val ip = call.request.local.remoteHost
                val now = System.currentTimeMillis()
                val entry = pendingCodes[ip]
                if (entry == null || entry.second < now) {
                    call.respondText(
                        """{"ok":false,"error":"验证码已过期，请在手机上重新获取"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.Unauthorized
                    )
                    return@post
                }
                if (!entry.first.equals(code, ignoreCase = true)) {
                    call.respondText(
                        """{"ok":false,"error":"验证码错误"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.Unauthorized
                    )
                    return@post
                }
                // 一次性：验证通过立即作废该码
                pendingCodes.remove(ip)
                val token = generateToken()
                sessionTokens[token] = ip to (now + TOKEN_TTL_MS)
                call.response.headers.append(
                    HttpHeaders.SetCookie,
                    "shareku_token=$token; Path=/; HttpOnly; Max-Age=${TOKEN_TTL_MS / 1000}"
                )
                call.respondText("""{"ok":true,"token":"$token"}""", ContentType.Application.Json)
            }
            // Peer-to-peer file receive endpoint (raw binary) — requires approval
            post("/api/peer-upload") {
                val destDir = receiveDir ?: File(context.getExternalFilesDir(null), "ShareKu").also { it.mkdirs() }
                if (!destDir.exists()) destDir.mkdirs()
                try {
                    val rawName = call.request.queryParameters["name"] ?: "received_${System.currentTimeMillis()}"
                    val origName = rawName.replace(":", "_").replace("/", "_").replace("\\", "_")
                        // 只过滤文件系统非法字符，保留中文等 Unicode 字符
                        .replace(Regex("[/\\\\:*?\"<>|\\u0000-\\u001F]"), "_")
                    // Save to temp file first, then request approval
                    val tempDir = File(context.cacheDir, "peer_pending")
                    if (!tempDir.exists()) tempDir.mkdirs()
                    val tempFile = File(tempDir, "${System.currentTimeMillis()}_$origName")
val channel = call.request.receiveChannel()
val totalHint = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull() ?: 0L
tempFile.outputStream().use { fos ->
val buf = ByteArray(32768)
var written = 0L
var lastReport = 0L
while (!channel.isClosedForRead) {
val r = channel.readAvailable(buf, 0, buf.size)
if (r <= 0) break
fos.write(buf, 0, r)
written += r
// 每 ~1MB 上报一次接收进度（接收端通知栏进度条）
if (written - lastReport >= 1024 * 1024 || r <= 0) {
lastReport = written
onPeerReceiveProgress?.invoke(origName, written, totalHint)
}
}
}
                    val finalDest = File(destDir, origName)
                    // Notify callback on main thread → shows approval notification
                    val senderIp = call.request.local.remoteHost
                    onPeerTransfer?.let { cb ->
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            cb(senderIp, origName, tempFile.length(), tempFile)
                        }
                    }
                    call.respondText("""{"status":"pending","name":"$origName","size":${tempFile.length()}}""", ContentType.Application.Json)
                } catch (e: Exception) {
                    call.respondText("""{"error":"${e.message}"}""", status = HttpStatusCode.InternalServerError)
                }
            }
            get("/api/clipboard") {
                if (!checkIp(call)) return@get
                if (!checkAuth(call)) { call.respondText("""{"error":"Unauthorized"}""", status = HttpStatusCode.Unauthorized); return@get }
                val clip = clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                call.respondText("""{"text":"${clip.replace("\"","\\\"")}"}""", ContentType.Application.Json)
            }
            get("/api/files") {
                if (!checkIp(call)) return@get
                if (!checkAuth(call)) { call.respondText("""{"error":"Unauthorized"}""", status = HttpStatusCode.Unauthorized); return@get }
                if (shizukuAccessReady) {
                    // 受限目录：共享根信息通过 Shizuku 获取
                    val rootFile = sharedFiles.firstOrNull()
                    val rootStat = rootFile?.let { ShizukuFileManager.stat(context, it.absolutePath) }
                    val entries = if (rootFile != null && rootStat != null && rootStat.getBoolean("isDirectory")) {
                        ShizukuFileManager.listDirectory(context, rootFile.absolutePath)?.map { shizukuToJson(it) } ?: emptyList()
                    } else if (rootFile != null && rootStat != null) {
                        listOf(shizukuStatToJson(rootFile, rootStat))
                    } else {
                        emptyList()
                    }
                    call.respondText(buildFileListJson(entries), ContentType.Application.Json)
                    return@get
                }
                call.respondText(
                    buildFileListJson(sharedFiles.map { fileToJson(it) }),
                    ContentType.Application.Json
                )
            }

            // Authenticated routes (use checkAuth inline)
            get("/") {
                if (!checkIp(call)) return@get
                if (!checkAuth(call)) { call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized); return@get }
                logRequest(call, 200)
                call.respondText(buildWebUI(), ContentType.Text.Html)
            }
            get("/download") {
                if (!checkIp(call)) return@get
                if (!checkAuth(call)) { call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized); return@get }
                val reqPath = call.request.queryParameters["path"] ?: ""
                val file = resolvePath(reqPath)
                if (shizukuAccessReady) {
                    // 受限目录（Android/data 等）：通过 Shizuku 读取
                    val st = file?.let { ShizukuFileManager.stat(context, it.absolutePath) }
                    if (file != null && st != null && !st.getBoolean("isDirectory") && isAllowed(file)) {
                        val size = st.getLong("size")
                        logRequest(call, 200, size)
                        call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=\"${file.name}\"")
                        call.response.headers.append(HttpHeaders.ContentType, guessContentType(file).toString())
                        call.respondOutputStream(ContentType.Application.OctetStream, HttpStatusCode.OK) {
                            var offset = 0L
                            while (offset < size) {
                                val chunk = ShizukuFileManager.readFile(context, file.absolutePath, offset, 512 * 1024) ?: break
                                if (chunk.isEmpty()) break
                                write(chunk)
                                offset += chunk.size
                            }
                        }
                    } else {
                        logRequest(call, 404)
                        call.respondText("404 Not Found", status = HttpStatusCode.NotFound)
                    }
                    return@get
                }
                if (file != null && file.exists() && file.isFile && isAllowed(file)) {
                    logRequest(call, 200, file.length())
                    call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=\"${file.name}\"")
                    call.response.headers.append(HttpHeaders.ContentType, guessContentType(file).toString())
                    call.respondFile(file)
                } else {
                    logRequest(call, 404)
                    call.respondText("404 Not Found", status = HttpStatusCode.NotFound)
                }
            }
            get("/api/list") {
                if (!checkIp(call)) return@get
                if (!checkAuth(call)) { call.respondText("""{"error":"Unauthorized"}""", status = HttpStatusCode.Unauthorized); return@get }
                val reqPath = call.request.queryParameters["path"] ?: ""
                val dir = resolvePath(reqPath)
                if (shizukuAccessReady) {
                    // 受限目录：通过 Shizuku 列目录
                    val st = dir?.let { ShizukuFileManager.stat(context, it.absolutePath) }
                    if (dir != null && st != null && isAllowed(dir)) {
                        if (st.getBoolean("isDirectory")) {
                            val list = ShizukuFileManager.listDirectory(context, dir.absolutePath)
                            if (list != null) {
                                logRequest(call, 200)
                                call.respondText(buildFileListJson(list.map { shizukuToJson(it) }), ContentType.Application.Json)
                                return@get
                            }
                        } else {
                            logRequest(call, 200)
                            call.respondText(buildFileListJson(listOf(shizukuStatToJson(dir, st))), ContentType.Application.Json)
                            return@get
                        }
                    }
                    logRequest(call, 404)
                    val dbg = if (dir == null) "null" else "stat=${st != null}"
                    call.respondText("""{"error":"404","debug":"$dbg","path":"$reqPath"}""", status = HttpStatusCode.NotFound)
                    return@get
                }
                if (dir != null && dir.isDirectory && isAllowed(dir)) {
                    val files = dir.listFiles()?.map { fileToJson(it) }
                    logRequest(call, 200)
                    call.respondText(buildFileListJson(files), ContentType.Application.Json)
                } else if (dir != null && dir.isFile && isAllowed(dir)) {
                    logRequest(call, 200)
                    call.respondText(buildFileListJson(listOf(fileToJson(dir))), ContentType.Application.Json)
                } else {
                    logRequest(call, 404)
                    val dbg = if (dir == null) "null" else "exist=${dir.exists()} isDir=${dir.isDirectory}"
                    call.respondText("""{"error":"404","debug":"$dbg","path":"$reqPath"}""", status = HttpStatusCode.NotFound)
                }
            }
            get("/api/zip") {
                if (!checkIp(call)) return@get
                if (!checkAuth(call)) { call.respondText("""{"error":"Unauthorized"}""", status = HttpStatusCode.Unauthorized); return@get }
                val paths = call.request.queryParameters.getAll("paths") ?: emptyList()
                val counter = AtomicLong(0)
                val byteCounter = AtomicLong(0)
                try {
                    logRequest(call, 200)
                    call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=\"shareku_archive.zip\"")
                    call.respondOutputStream(ContentType.Application.Zip) {
                        ZipOutputStream(this).use { zos ->
                            for (path in paths) {
                                val file = resolvePath(path)
                                if (file != null && isAllowed(file)) {
                                    addToZip(file, "", zos, counter, byteCounter)
                                }
                            }
                        }
                    }
                } catch (e: ZipLimitExceededException) {
                    throw e
                }
            }
            if (allowUpload) {
                post("/api/upload") {
                    if (!checkIp(call)) return@post
                    if (!checkAuth(call)) { call.respondText("""{"error":"Unauthorized"}""", status = HttpStatusCode.Unauthorized); return@post }
                    val root = getRootDir() ?: run {
                        call.respondText("""{"error":"No shared directory"}""", status = HttpStatusCode.BadRequest)
                        return@post
                    }
                    val queryName = call.request.queryParameters["name"]
                    try {
                        val isMultipart = call.request.contentType().toString().startsWith("multipart/")
                        if (isMultipart) {
                            // 网页 FormData 上传：正确解析 multipart（流式，支持大文件）
                            val multipart = call.receiveMultipart()
                            var saved = 0
                            var savedName = ""
                            var savedSize = 0L
                            val totalHint = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull() ?: 0L
                            multipart.forEachPart { part ->
                                if (part is PartData.FileItem) {
                                    val rawName = part.originalFileName ?: queryName ?: "upload_${System.currentTimeMillis()}"
                                    val origName = URLDecoder.decode(rawName, "UTF-8")
                                        .replace("/", "_").replace("\\", "_")
                                    var dest = File(root, origName)
                                    var counter = 1
                                    while (dest.exists()) {
                                        val dot = origName.lastIndexOf('.')
                                        val base = if (dot > 0) origName.substring(0, dot) else origName
                                        val ext = if (dot > 0) origName.substring(dot) else ""
                                        dest = File(root, "${base}_${counter}${ext}")
                                        counter++
                                    }
                                    // 流式写入，内存占用恒定；每 1MB 异步广播一次真实写盘进度（不阻塞写盘）
                                    var written = 0L
                                    var lastBroadcast = 0L
                                    dest.outputStream().use { fos ->
                                        val buf = ByteArray(65536)
                                        val ch = part.provider()
                                        while (true) {
                                            val r = ch.readAvailable(buf, 0, buf.size)
                                            if (r <= 0) break
                                            fos.write(buf, 0, r)
                                            written += r
                                            if (written - lastBroadcast >= 1024 * 1024) {
                                                lastBroadcast = written
                                                broadcastAsync("""{"type":"up","name":"${dest.name.replace("\"", "\\\"")}","written":$written,"total":$totalHint}""")
                                            }
                                        }
                                    }
                                    // 写盘完成，广播真实保存大小
                                    broadcastAsync("""{"type":"up_done","name":"${dest.name.replace("\"", "\\\"")}","size":${dest.length()}}""")
                                    saved = 1
                                    savedName = dest.name
                                    savedSize = dest.length()
                                }
                                part.dispose()
                            }
                            logRequest(call, 200, savedSize)
                            call.respondText("""{"uploaded":$saved,"name":"$savedName","size":$savedSize}""", ContentType.Application.Json)
                        } else {
                            // 兼容 raw 字节流上传（curl/脚本等）
                            val channel = call.request.receiveChannel()
                            val origName = (queryName ?: "upload_${System.currentTimeMillis()}")
                                .let { URLDecoder.decode(it, "UTF-8") }
                                .replace("/", "_").replace("\\", "_")
                            var dest = File(root, origName)
                            var counter = 1
                            while (dest.exists()) {
                                val dot = origName.lastIndexOf('.')
                                val base = if (dot > 0) origName.substring(0, dot) else origName
                                val ext = if (dot > 0) origName.substring(dot) else ""
                                dest = File(root, "${base}_${counter}${ext}")
                                counter++
                            }
                            dest.outputStream().use { fos ->
                                val buf = ByteArray(32768)
                                while (!channel.isClosedForRead) {
                                    val r = channel.readAvailable(buf, 0, buf.size)
                                    if (r <= 0) break
                                    fos.write(buf, 0, r)
                                }
                            }
                            logRequest(call, 200, dest.length())
                            call.respondText("""{"uploaded":1,"name":"${dest.name}","size":${dest.length()}}""", ContentType.Application.Json)
                        }
                    } catch (e: Exception) {
                        logRequest(call, 500)
                        call.respondText("""{"error":"${e.message}"}""", status = HttpStatusCode.InternalServerError)
                    }
                }
            }
            if (allowDelete) {
                delete("/api/delete") {
                    if (!checkIp(call)) return@delete
                    val path = call.request.queryParameters["path"] ?: ""
                    val file = resolvePath(path)
                    if (file != null && isAllowed(file) && deleteRecursively(file)) {
                        logRequest(call, 200)
                        call.respondText("""{"deleted":true}""", ContentType.Application.Json)
                    } else {
                        call.respondText("""{"deleted":false}""", status = HttpStatusCode.BadRequest)
                    }
                }
            }
            if (enableWebDav) {
                route("/webdav") {
                    route("/{...}") {
                        handle { handleWebDav(call) }
                    }
                }
            }
        }
    }

    // Recursive delete — handles non-empty directories
    private fun deleteRecursively(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        return file.delete()
    }

    class ZipLimitExceededException(msg: String) : Exception(msg)

    companion object {
        const val MAX_ZIP_FILES = 10000L
        const val MAX_ZIP_BYTES = 4L * 1024 * 1024 * 1024  // 4 GB
    }

    private suspend fun logRequest(call: ApplicationCall, status: Int, bytes: Long = 0) {
        val entry = LogEntry(
            ip = call.request.local.remoteHost,
            method = call.request.httpMethod.value,
            path = call.request.uri,
            status = status,
            userAgent = call.request.header(HttpHeaders.UserAgent) ?: "",
            bytesTransferred = bytes
        )
        logManager.addEntry(entry)
    }

    // 【安全】路径解析 —— 防路径穿越
    private fun resolvePath(path: String): File? {
        if (isSingleFileSandbox && sharedFiles.size == 1) return sharedFiles.firstOrNull()
        val decoded = URLDecoder.decode(path.trimStart('/'), "UTF-8").trim()
        val root = getRootDir() ?: return null
        if (decoded.isEmpty()) return root
        val target = File(root, decoded)
        // 路径穿越检查：标准化路径后必须仍在根目录内
        // 受限目录（Android/data 等）下 canonicalFile 可能抛异常 → 降级为绝对路径比较
        val rootCanonical = try { root.canonicalPath } catch (e: Exception) { root.absolutePath }
        val targetCanonical = try { target.canonicalFile.canonicalPath } catch (e: Exception) { target.absolutePath }
        if (!targetCanonical.startsWith(rootCanonical + "/") && targetCanonical != rootCanonical) {
            return null // 拒绝路径穿越
        }
        return target
    }

    private fun resolveWebDavPath(path: String): File? {
        return resolvePath(path)
    }

    private fun isAllowed(file: File): Boolean {
        if (!isSingleFileSandbox) return true
        return sharedFiles.any { sf ->
            file.absolutePath == sf.absolutePath ||
            file.absolutePath.startsWith(sf.absolutePath)
        }
    }

    // 获取共享根目录，用于计算相对路径
    private fun getRootDir(): File? {
        val first = sharedFiles.firstOrNull() ?: return null
        // 受限目录（Android/data 等）在沙箱下 File.isDirectory() 可能误判为 false，
        // 此时必须以共享项本身作为根，否则路径基准错乱
        return if (first.isDirectory || shizukuAccessReady) first else first.parentFile
    }

    // 手动 HTTP Basic Auth 验证 —— 参考网页文件挂载器的实现
    private fun checkAuth(call: ApplicationCall): Boolean {
        if (!enableAuth) return true
        val authHeader = call.request.header("authorization") ?: run {
            call.response.headers.append("WWW-Authenticate", "Basic realm=\"ShareKu\"")
            return false
        }
        if (!authHeader.startsWith("Basic ", ignoreCase = true)) {
            call.response.headers.append("WWW-Authenticate", "Basic realm=\"ShareKu\"")
            return false
        }
        try {
            val encoded = authHeader.removePrefix("Basic ").trim()
            val decoded = String(Base64.getDecoder().decode(encoded))
            val colonIdx = decoded.indexOf(':')
            if (colonIdx < 0) return false
            val user = decoded.substring(0, colonIdx)
            val pass = decoded.substring(colonIdx + 1)
            return user == authUsername && pass == authPassword
        } catch (e: Exception) {
            return false
        }
    }

    // 【连接确认】一次性验证码 + 会话令牌检查 —— 不信任 IP 白名单
    // 返回 true=放行, false=已拦截(已返回等待页或403)
    private suspend fun checkIp(call: ApplicationCall): Boolean {
        if (!requireConfirm) return true
        val ip = call.request.local.remoteHost
        val now = System.currentTimeMillis()
        // 本机回环地址永远放行
        if (ip == "127.0.0.1" || ip == "0:0:0:0:0:0:0:1" || ip == "localhost") return true
        // 已拉黑 → 403
        if (ip in blockedIps) {
            call.respondText(buildForbiddenHtml(), ContentType.Text.Html, HttpStatusCode.Forbidden)
            return false
        }
        // 有效会话令牌 → 放行（令牌绑定 IP，改 IP 无效）
        if (hasValidToken(call, ip, now)) return true
        // 新 IP → 生成一次性验证码并发通知（若已有未过期码则复用，避免重复弹通知）
        val entry = pendingCodes[ip]
        if (entry == null || entry.second < now) {
            val code = generateCode()
            pendingCodes[ip] = code to (now + CODE_TTL_MS)
            // 异步回调，不阻塞当前请求（通知栏展示 IP + 一次性码）
            onNewConnection?.invoke(ip, code)
        }
        // 返回等待授权页面（输入一次性验证码）
        call.respondText(buildWaitingHtml(ip), ContentType.Text.Html)
        return false
    }

    /** 校验会话令牌：cookie 中的 shareku_token，或 Basic Auth 密码位（供 WebDAV/资源管理器使用） */
    private fun hasValidToken(call: ApplicationCall, ip: String, now: Long): Boolean {
        val token = requestCookie(call, "shareku_token") ?: basicAuthPassword(call) ?: return false
        val entry = sessionTokens[token] ?: return false
        return entry.first == ip && entry.second > now
    }

    /** 手动解析请求 Cookie 头中的指定字段 */
    private fun requestCookie(call: ApplicationCall, name: String): String? {
        val h = call.request.header("cookie") ?: return null
        return h.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("$name=", ignoreCase = true) }
            ?.substringAfter('=')
    }

    /** 从 Authorization: Basic 头中提取密码位（用户名:密码 → 密码） */
    private fun basicAuthPassword(call: ApplicationCall): String? {
        val h = call.request.header("authorization") ?: return null
        if (!h.startsWith("Basic ", ignoreCase = true)) return null
        return try {
            val decoded = String(java.util.Base64.getDecoder().decode(h.substring(6).trim()), Charsets.UTF_8)
            decoded.substringAfter(':', "")
        } catch (_: Exception) {
            null
        }
    }

    /** 生成 4 位一次性验证码（62 字符集：26大写 + 26小写 + 10数字） */
    private fun generateCode(): String {
        val sb = StringBuilder(4)
        repeat(4) { sb.append(codeChars[random.nextInt(codeChars.length)]) }
        return sb.toString()
    }

    /** 生成 32 位十六进制会话令牌 */
    private fun generateToken(): String {
        val b = ByteArray(16)
        random.nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    // 等待授权的HTML页面 —— 输入一次性验证码（码由手机端通知栏显示，仅可使用一次）
    private fun buildWaitingHtml(ip: String): String {
        return """<!DOCTYPE html>
<html lang="zh">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0">
<title>等待授权 - ShareKu</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;
 background:#f5f8f6;color:#2d3436;min-height:100vh;display:flex;
 align-items:center;justify-content:center;-webkit-font-smoothing:antialiased}
.card{background:#fff;border-radius:20px;padding:48px 40px;text-align:center;
 box-shadow:0 1px 3px rgba(0,0,0,.06),0 4px 12px rgba(0,0,0,.08);max-width:400px;margin:20px}
h1{font-size:20px;font-weight:600;margin-bottom:8px;color:#2d3436}
p{font-size:14px;color:#636e72;line-height:1.5}
.ip{display:inline-block;background:#e8f3f0;color:#3d7a6e;padding:2px 10px;
 border-radius:6px;font-family:monospace;font-size:13px;margin:4px 0}
.code{display:block;width:160px;margin:20px auto;padding:12px 16px;font-size:24px;
 text-align:center;letter-spacing:8px;font-family:monospace;border:2px solid #dde4e1;
 border-radius:12px;outline:none;text-transform:uppercase}
.code:focus{border-color:#50998b}
.btn{display:block;width:100%;padding:12px;font-size:15px;font-weight:600;
 background:#50998b;color:#fff;border:none;border-radius:12px;cursor:pointer}
.btn:disabled{opacity:.5}
#msg{margin-top:14px;font-size:13px;min-height:18px}
#msg.err{color:#d38c8c}
#msg.ok{color:#3d7a6e}
.hint{margin-top:20px;font-size:12px;color:#b2bec3}
.tok{margin-top:14px;font-size:12px;color:#3d7a6e;word-break:break-all;display:none}
</style></head>
<body>
<div class="card">
 <h1>连接需要授权</h1>
 <p>你的设备 <span class="ip">$ip</span><br>正在请求访问 ShareKu</p>
 <p style="margin-top:12px">请输入手机上显示的一次性验证码</p>
 <input class="code" id="code" maxlength="4" autocomplete="off" autocapitalize="characters">
 <button class="btn" id="btn" onclick="submitCode()">验证并访问</button>
 <p id="msg"></p>
 <p class="tok" id="tok"></p>
 <p class="hint">验证码仅可使用一次 · 5分钟内有效</p>
</div>
<script>
async function submitCode(){
 var code=document.getElementById('code').value.trim();
 var msg=document.getElementById('msg'), btn=document.getElementById('btn');
 if(code.length!=4){ msg.className='err'; msg.textContent='请输入4位验证码'; return; }
 btn.disabled=true; msg.className=''; msg.textContent='验证中...';
 try{
  var r=await fetch('/api/verify?code='+encodeURIComponent(code),{method:'POST'});
  var j=await r.json().catch(function(){return{};});
  if(r.ok && j.ok){
   msg.className='ok'; msg.textContent='验证成功，正在进入...';
   document.getElementById('tok').style.display='block';
   document.getElementById('tok').textContent='WebDAV/映射Z盘密码(24小时有效): '+j.token;
   setTimeout(function(){location.href='/';},600);
  }else{
   msg.className='err'; msg.textContent=(j.error||'验证码错误或已过期');
   btn.disabled=false;
  }
 }catch(e){ msg.className='err'; msg.textContent='网络错误，请重试'; btn.disabled=false; }
}
document.getElementById('code').addEventListener('keydown',function(e){ if(e.key==='Enter') submitCode(); });
</script>
</body></html>""".trimIndent()
    }

    // 已拉黑的提示页
    private fun buildForbiddenHtml(): String {
        return """<!DOCTYPE html>
<html lang="zh">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0">
<title>访问被拒 - ShareKu</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;
 background:#f5f8f6;color:#2d3436;min-height:100vh;display:flex;
 align-items:center;justify-content:center;-webkit-font-smoothing:antialiased}
.card{background:#fff;border-radius:20px;padding:48px 40px;text-align:center;
 box-shadow:0 1px 3px rgba(0,0,0,.06),0 4px 12px rgba(0,0,0,.08);max-width:400px;margin:20px}
.icon{width:56px;height:56px;border-radius:50%;background:#fdf0f0;color:#d38c8c;
 display:flex;align-items:center;justify-content:center;font-size:28px;margin:0 auto 20px}
h1{font-size:20px;font-weight:600;margin-bottom:8px}
p{font-size:14px;color:#636e72;line-height:1.5}
</style></head>
<body>
<div class="card">
 <div class="icon">&#x2715;</div>
 <h1>访问已被拒绝</h1>
 <p>此设备的连接请求已被管理员拒绝</p>
</div>
</body></html>""".trimIndent()
    }

    private fun fileToJson(file: File): Map<String, Any?> {
        val root = getRootDir()
        // 使用相对于共享根目录的路径，而非绝对路径
        val relPath = if (root != null && file.absolutePath.startsWith(root.absolutePath)) {
            file.absolutePath.removePrefix(root.absolutePath).trimStart('/')
        } else {
            file.absolutePath
        }
        return mapOf(
            "name" to file.name,
            "path" to relPath,
            "size" to file.length(),
            "isDirectory" to file.isDirectory,
            "lastModified" to file.lastModified(),
            "mimeType" to guessMimeType(file.name)
        )
    }

    // Shizuku 受限目录条目 → JSON（与 fileToJson 结构一致）
    private fun shizukuToJson(e: ShizukuEntry): Map<String, Any?> {
        val rel = if (accessRootPath.isNotEmpty() && e.path.startsWith(accessRootPath)) {
            e.path.removePrefix(accessRootPath).trimStart('/')
        } else {
            e.path
        }
        return mapOf(
            "name" to e.name,
            "path" to rel,
            "size" to e.size,
            "isDirectory" to e.isDirectory,
            "lastModified" to 0L,
            "mimeType" to guessMimeType(e.name)
        )
    }

    // Shizuku stat → 单文件 JSON
    private fun shizukuStatToJson(file: File, st: Bundle): Map<String, Any?> {
        val rel = if (accessRootPath.isNotEmpty() && file.absolutePath.startsWith(accessRootPath)) {
            file.absolutePath.removePrefix(accessRootPath).trimStart('/')
        } else {
            file.absolutePath
        }
        return mapOf(
            "name" to file.name,
            "path" to rel,
            "size" to st.getLong("size"),
            "isDirectory" to st.getBoolean("isDirectory"),
            "lastModified" to st.getLong("lastModified"),
            "mimeType" to guessMimeType(file.name)
        )
    }

    private fun jsonEscape(s: String): String {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
    }

    private fun buildFileListJson(files: List<Map<String, Any?>>?): String {
        val sb = StringBuilder("[")
        files?.forEachIndexed { index, file ->
            sb.append("{")
            file.forEach { (key, value) ->
                sb.append("\"$key\":")
                when (value) {
                    is String -> sb.append("\"${jsonEscape(value)}\"")
                    is Number -> sb.append(value)
                    is Boolean -> sb.append(value)
                    null -> sb.append("null")
                    else -> sb.append("\"${jsonEscape(value.toString())}\"")
                }
                sb.append(",")
            }
            sb.setLength(sb.length - 1) // remove trailing comma
            sb.append("}")
            if (index < files.size - 1) sb.append(",")
        }
        sb.append("]")
        return sb.toString()
    }

    private suspend fun handleWebDav(call: ApplicationCall) {
        if (!checkIp(call)) return
        if (!checkAuth(call)) { call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized); return }
        val method = call.request.httpMethod.value
        when {
            method == "OPTIONS" -> {
                call.response.headers.append("Allow", "GET, PUT, DELETE, OPTIONS, PROPFIND, MKCOL, COPY, MOVE, HEAD")
                call.response.headers.append("DAV", "1,2")
                call.response.headers.append("MS-Author-Via", "DAV")
                call.respondText("")
            }
            method == "HEAD" -> {
                val path = call.request.uri.removePrefix("/webdav")
                val file = resolveWebDavPath(path)
                if (file != null && file.isFile && isAllowed(file)) {
                    logRequest(call, 200, file.length())
                    call.response.headers.append(HttpHeaders.ContentType, guessContentType(file).toString())
                    call.response.headers.append(HttpHeaders.ContentLength, file.length().toString())
                    call.respondText("")
                } else { logRequest(call, 404); call.respondText("404", status = HttpStatusCode.NotFound) }
            }
            method == "GET" -> {
                val path = call.request.uri.removePrefix("/webdav")
                val file = resolveWebDavPath(path)
                if (file != null && file.isFile && isAllowed(file)) {
                    logRequest(call, 200, file.length())
                    call.response.headers.append(HttpHeaders.ContentType, guessContentType(file).toString())
                    call.respondFile(file)
                } else {
                    logRequest(call, 404)
                    call.respondText("404", status = HttpStatusCode.NotFound)
                }
            }
            method == "PROPFIND" -> {
                val sb = StringBuilder()
                sb.append("""<?xml version="1.0" encoding="utf-8"?>""")
                sb.append("""<D:multistatus xmlns:D="DAV:">""")
                val path = call.request.uri.removePrefix("/webdav")
                val file = resolveWebDavPath(path)
                val depth = call.request.header("Depth") ?: "infinity"
                val baseUrl = "${call.request.local.scheme}://${call.request.local.localHost}:${call.request.local.localPort}"
                if (file != null && file.exists()) {
                    appendPropfindResponse(sb, file, path, baseUrl)
                    // Depth 1: also list immediate children of directories
                    if (file.isDirectory && (depth == "1" || depth == "infinity")) {
                        file.listFiles()?.forEach { child ->
                            val childPath = if (path.endsWith("/")) "$path${child.name}" else "$path/${child.name}"
                            appendPropfindResponse(sb, child, childPath, baseUrl)
                        }
                    }
                }
                sb.append("</D:multistatus>")
                logRequest(call, 207)
                call.respondText(sb.toString(), ContentType.Text.Xml)
            }
            method == "PUT" -> {
                if (!allowUpload) {
                    call.respondText("Upload disabled", status = HttpStatusCode.Forbidden)
                    return
                }
                val outPath = call.request.uri.removePrefix("/webdav")
                val file = resolveWebDavPath(outPath)
                if (file != null && !allowOverwrite && file.exists()) {
                    call.respondText("File exists", status = HttpStatusCode.Conflict)
                } else {
                    file?.parentFile?.mkdirs()
                    // 流式接收二进制数据
                    val channel = call.request.receiveChannel()
                    file?.outputStream()?.use { fos ->
                        val buf = ByteArray(32768)
                        while (!channel.isClosedForRead) {
                            val r = channel.readAvailable(buf, 0, buf.size)
                            if (r <= 0) break
                            fos.write(buf, 0, r)
                        }
                    }
                    logRequest(call, 201, file?.length() ?: 0)
                    call.respondText("Created", status = HttpStatusCode.Created)
                }
            }
            method == "DELETE" -> {
                if (!allowDelete) {
                    call.respondText("Delete disabled", status = HttpStatusCode.Forbidden)
                    return
                }
                val path = call.request.uri.removePrefix("/webdav")
                val file = resolveWebDavPath(path)
                if (file != null && deleteRecursively(file)) {
                    logRequest(call, 204)
                    call.respondText("Deleted", status = HttpStatusCode.NoContent)
                } else {
                    call.respondText("Not found", status = HttpStatusCode.NotFound)
                }
            }
            method == "MKCOL" -> {
                val mPath = call.request.uri.removePrefix("/webdav")
                val mFile = resolveWebDavPath(mPath)
                if (mFile != null && mFile.mkdirs()) {
                    logRequest(call, 201)
                    call.respondText("Created", status = HttpStatusCode.Created)
                } else {
                    call.respondText("Failed", status = HttpStatusCode.Conflict)
                }
            }
        }
    }

    private fun addToZip(file: File, parentPath: String, zos: ZipOutputStream,
                        fileCounter: AtomicLong, byteCounter: AtomicLong) {
        // Check limits
        if (fileCounter.incrementAndGet() > MAX_ZIP_FILES) {
            throw ZipLimitExceededException("Too many files (max $MAX_ZIP_FILES)")
        }
        val entryPath = if (parentPath.isEmpty()) file.name else "$parentPath/${file.name}"
        if (file.isDirectory) {
            zos.putNextEntry(ZipEntry("$entryPath/"))
            zos.closeEntry()
            file.listFiles()?.forEach { addToZip(it, entryPath, zos, fileCounter, byteCounter) }
        } else {
            val fileSize = file.length()
            if (byteCounter.addAndGet(fileSize) > MAX_ZIP_BYTES) {
                throw ZipLimitExceededException("Archive too large (max ${MAX_ZIP_BYTES / (1024*1024*1024)} GB)")
            }
            zos.putNextEntry(ZipEntry(entryPath))
            FileInputStream(file).use { it.copyTo(zos, 65536) }
            zos.closeEntry()
        }
    }

    private fun appendPropfindResponse(sb: StringBuilder, file: File, path: String, baseUrl: String) {
        val href = "$baseUrl/webdav${encodeDavPath(path)}"
        sb.append("<D:response><D:href>$href</D:href>")
        sb.append("<D:propstat><D:prop>")
        if (file.isDirectory) {
            sb.append("<D:resourcetype><D:collection/></D:resourcetype>")
            sb.append("<D:getcontentlength>0</D:getcontentlength>")
        } else {
            sb.append("<D:resourcetype/>")
            sb.append("<D:getcontenttype>${guessContentType(file)}</D:getcontenttype>")
            sb.append("<D:getcontentlength>${file.length()}</D:getcontentlength>")
        }
        sb.append("<D:getlastmodified>${java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US).format(java.util.Date(file.lastModified()))}</D:getlastmodified>")
        sb.append("</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>")
        sb.append("</D:response>")
    }

    // URL 编码 WebDAV href 路径（逐段编码，保留 /；空格用 %20，Windows 要求）
    private fun encodeDavPath(path: String): String {
        if (path.isEmpty() || path == "/") return "/"
        return path.split("/").joinToString("/") { seg ->
            if (seg.isEmpty()) "" else java.net.URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
        }
    }

    private fun guessContentType(file: File): ContentType {
        val name = file.name.lowercase()
        return when {
            name.endsWith(".html") -> ContentType.Text.Html
            name.endsWith(".css") -> ContentType.Text.CSS
            name.endsWith(".js") -> ContentType.Text.JavaScript
            name.endsWith(".json") -> ContentType.Application.Json
            name.endsWith(".xml") -> ContentType.Text.Xml
            name.endsWith(".txt") || name.endsWith(".log") -> ContentType.Text.Plain
            name.endsWith(".png") -> ContentType.Image.PNG
            name.endsWith(".jpg") || name.endsWith(".jpeg") -> ContentType.Image.JPEG
            name.endsWith(".gif") -> ContentType.Image.GIF
            name.endsWith(".svg") -> ContentType.Image.SVG
            name.endsWith(".webp") -> ContentType("image", "webp")
            name.endsWith(".mp4") -> ContentType.Video.MP4
            name.endsWith(".webm") -> ContentType("video", "webm")
            name.endsWith(".mp3") -> ContentType("audio", "mpeg")
            name.endsWith(".ogg") -> ContentType("audio", "ogg")
            name.endsWith(".wav") -> ContentType("audio", "wav")
            name.endsWith(".pdf") -> ContentType.Application.Pdf
            name.endsWith(".zip") -> ContentType.Application.Zip
            name.endsWith(".apk") -> ContentType("application", "vnd.android.package-archive")
            name.endsWith(".md") -> ContentType("text", "markdown")
            else -> ContentType.Application.OctetStream
        }
    }

    private fun guessMimeType(name: String): String {
        return when {
            name.endsWith(".html") -> "text/html"
            name.endsWith(".css") -> "text/css"
            name.endsWith(".js") -> "text/javascript"
            name.endsWith(".json") -> "application/json"
            name.endsWith(".xml") -> "text/xml"
            name.endsWith(".txt") || name.endsWith(".log") -> "text/plain"
            name.endsWith(".png") -> "image/png"
            name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
            name.endsWith(".gif") -> "image/gif"
            name.endsWith(".svg") -> "image/svg+xml"
            name.endsWith(".mp4") -> "video/mp4"
            name.endsWith(".webm") -> "video/webm"
            name.endsWith(".mp3") -> "audio/mpeg"
            name.endsWith(".ogg") -> "audio/ogg"
            name.endsWith(".wav") -> "audio/wav"
            name.endsWith(".pdf") -> "application/pdf"
            name.endsWith(".zip") -> "application/zip"
            name.endsWith(".apk") -> "application/vnd.android.package-archive"
            name.endsWith(".md") -> "text/markdown"
            name.endsWith(".py") -> "text/x-python"
            name.endsWith(".kt") -> "text/x-kotlin"
            name.endsWith(".java") -> "text/x-java"
            name.endsWith(".cpp") || name.endsWith(".c") || name.endsWith(".h") -> "text/x-c"
            else -> "application/octet-stream"
        }
    }
    private fun buildWebUI(): String {
        return try {
            context.assets.open("web/index.html").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "<html><body><h1>ShareKu</h1><p>Error loading UI: ${e.message}</p></body></html>"
        }
    }
}
