package com.linjing.shareku.peer

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong

data class TransferProgress(
    val fileName: String,
    val fileIndex: Int,
    val totalFiles: Int,
    val bytesSent: Long,
    val totalBytes: Long,
    val done: Boolean = false
) {
    val percent: Int get() = if (totalBytes > 0) ((bytesSent * 100) / totalBytes).toInt() else 0
}

/** 发送端字节计数器：包装 InputStream，把实际读出的字节数写入 counter */
private class CountingInputStream(
    private val delegate: InputStream,
    private val counter: AtomicLong
) : FilterInputStream(delegate) {
    override fun read(): Int {
        val r = super.read()
        if (r >= 0) counter.incrementAndGet()
        return r
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val r = super.read(b, off, len)
        if (r > 0) counter.addAndGet(r.toLong())
        return r
    }
}

/**
 * Sends files to a peer ShareKu device via HTTP request body upload.
 * Uses Ktor HttpClient (CIO engine, pure Kotlin).
 *
 * 进度模型：传输期间逐字节上报（100ms 节流），发送端界面可显示真实百分比。
 */
class PeerTransferClient {

    private val client = HttpClient(CIO) {
        engine {
            requestTimeout = 0 // no timeout for large files
        }
    }

    /**
     * Upload files to a peer device. Returns a [Flow] for progress tracking.
     * @param files List of files to send
     * @param host Peer IP address
     * @param port Peer HTTP port
     */
    fun sendFiles(
        files: List<File>,
        host: String,
        port: Int
    ): Flow<TransferProgress> = channelFlow {
        val totalBytes = files.sumOf { it.length() }
        var baseSent = 0L

        files.forEachIndexed { idx, file ->
            val fileLen = file.length()
            val sent = AtomicLong(0)

            // 发送前先推一条当前进度（0%）
            send(
                TransferProgress(
                    fileName = file.name,
                    fileIndex = idx,
                    totalFiles = files.size,
                    bytesSent = baseSent,
                    totalBytes = totalBytes,
                    done = false
                )
            )

            // 传输期间逐字节上报（100ms 节流，真实进度）
            val poller = launch {
                while (isActive) {
                    delay(100)
                    send(
                        TransferProgress(
                            fileName = file.name,
                            fileIndex = idx,
                            totalFiles = files.size,
                            bytesSent = baseSent + sent.get(),
                            totalBytes = totalBytes,
                            done = false
                        )
                    )
                }
            }

            try {
                // 流式发送：边读边传，内存占用恒定，避免大文件 OOM
                val response = withContext(Dispatchers.IO) {
                    client.post("http://$host:$port/api/peer-upload?name=${java.net.URLEncoder.encode(file.name, "UTF-8")}") {
                        // ShareKu 发送端标记头（接收端校验，过滤盲目扫描/网页注入）
                        header("X-ShareKu-Peer", "1")
                        setBody(object : OutgoingContent.ReadChannelContent() {
                            override val contentLength: Long = fileLen
                            override fun readFrom(): ByteReadChannel =
                                CountingInputStream(file.inputStream(), sent).toByteReadChannel()
                        })
                        contentType(ContentType.Application.OctetStream)
                    }
                }
                if (!response.status.isSuccess()) {
                    val err = response.bodyAsText()
                    throw Exception("Server error: $err")
                }
                baseSent += fileLen
            } finally {
                poller.cancel()
            }

            send(
                TransferProgress(
                    fileName = file.name,
                    fileIndex = idx,
                    totalFiles = files.size,
                    bytesSent = baseSent,
                    totalBytes = totalBytes,
                    done = idx == files.lastIndex
                )
            )
        }
    }

    fun close() {
        client.close()
    }
}