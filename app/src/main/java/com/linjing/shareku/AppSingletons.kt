package com.linjing.shareku

import android.content.Context
import com.linjing.shareku.data.LogManager
import com.linjing.shareku.data.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simple manual DI container - replaces Hilt.
 * Initialized in LocalShareApp.onCreate().
 */
object AppSingletons {
    lateinit var preferencesManager: PreferencesManager
        private set
    lateinit var logManager: LogManager
        private set

    // Global server running state - reactive StateFlow for UI binding
    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    fun setServerRunning(running: Boolean) {
        _isServerRunning.value = running
    }

    // IP 连接跟踪 —— 用于连接确认和黑名单
    val pendingConnections = mutableMapOf<String, Boolean>() // IP -> allowed
    val blockedIPs = mutableSetOf<String>()

    fun init(context: Context) {
        preferencesManager = PreferencesManager(context)
        logManager = LogManager()
    }
}