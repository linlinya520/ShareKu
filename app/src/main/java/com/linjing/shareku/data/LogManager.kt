package com.linjing.shareku.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val ip: String,
    val method: String,
    val path: String,
    val status: Int,
    val userAgent: String = "",
    val bytesTransferred: Long = 0
) {
    // 构造时格式化一次（避免列表滚动时每帧重建 SimpleDateFormat）
    val formattedTime: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

class LogManager {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()
    private val _filterIp = MutableStateFlow<String?>(null)

    fun addEntry(entry: LogEntry) {
        _logs.value = (_logs.value + entry).takeLast(500)
    }

    fun setFilter(ip: String?) {
        _filterIp.value = ip
    }

    fun getFilteredLogs(): List<LogEntry> {
        val ip = _filterIp.value
        return if (ip == null) _logs.value.asReversed()
        else _logs.value.filter { it.ip.contains(ip, ignoreCase = true) }.reversed()
    }

    fun clear() {
        _logs.value = emptyList()
    }
}