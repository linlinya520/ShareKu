package com.linjing.shareku.peer

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

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

data class TransferResult(
    val success: Boolean,
    val message: String
)

/**
 * Sends files to a peer ShareKu device via HTTP multipart upload.
 * Uses Ktor HttpClient (CIO engine, pure Kotlin).
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
    ): Flow<TransferProgress> = flow {
        val totalBytes = files.sumOf { it.length() }
        var overallSent = 0L

        files.forEachIndexed { idx, file ->
            emit(
                TransferProgress(
                    fileName = file.name,
                    fileIndex = idx,
                    totalFiles = files.size,
                    bytesSent = overallSent,
                    totalBytes = totalBytes,
                    done = false
                )
            )

            try {
                val fileBytes = withContext(Dispatchers.IO) { file.readBytes() }

                val response = withContext(Dispatchers.IO) {
                    client.post("http://$host:$port/api/peer-upload?name=${java.net.URLEncoder.encode(file.name, "UTF-8")}") {
                        setBody(fileBytes)
                        contentType(ContentType.Application.OctetStream)
                    }
                }
                if (response.status.isSuccess()) {
                    val body = response.bodyAsText()
                    if (!body.contains("\"status\":\"pending\"")) {
                        // Unexpected response
                    }
                    overallSent += file.length()
                    emit(
                        TransferProgress(
                            fileName = file.name,
                            fileIndex = idx,
                            totalFiles = files.size,
                            bytesSent = overallSent,
                            totalBytes = totalBytes,
                            done = idx == files.lastIndex
                        )
                    )
                } else {
                    val err = response.bodyAsText()
                    throw Exception("Server error: $err")
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }

    fun close() {
        client.close()
    }
}