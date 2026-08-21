package com.linjing.shareku.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

private val Context.dataStore by preferencesDataStore(name = "shareku_prefs")

class PreferencesManager(private val context: Context) {

    val port: Flow<Int> = context.dataStore.data.map { it[KEY_PORT] ?: 8080 }
    val sharePort: Flow<Int> = context.dataStore.data.map { it[KEY_SHARE_PORT] ?: 8085 }
    val enableWebDav: Flow<Boolean> = context.dataStore.data.map { it[KEY_WEBDAV] ?: true }
    val enableAuth: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTH] ?: false }
    val authUsername: Flow<String> = context.dataStore.data.map { it[KEY_AUTH_USER] ?: "shareku" }
    val authPassword: Flow<String> = context.dataStore.data.map { it[KEY_AUTH_PASS] ?: "share123" }
    val allowUpload: Flow<Boolean> = context.dataStore.data.map { it[KEY_UPLOAD] ?: false }
    val allowDelete: Flow<Boolean> = context.dataStore.data.map { it[KEY_DELETE] ?: false }
    val allowOverwrite: Flow<Boolean> = context.dataStore.data.map { it[KEY_OVERWRITE] ?: true }
    val networkInterface: Flow<String> = context.dataStore.data.map { it[KEY_INTERFACE] ?: "auto" }
    val uploadDir: Flow<String> = context.dataStore.data.map { it[KEY_UPLOAD_DIR] ?: "" }
    val uploadSortByType: Flow<Boolean> = context.dataStore.data.map { it[KEY_UPLOAD_SORT_TYPE] ?: false }
    val requireConnectionConfirm: Flow<Boolean> = context.dataStore.data.map { it[KEY_CONFIRM] ?: false }
    val sharedDir: Flow<String> = context.dataStore.data.map { it[KEY_SHARED_DIR] ?: "/sdcard" }
    val themeMode: Flow<String> = context.dataStore.data.map { it[KEY_THEME_MODE] ?: "SYSTEM" }
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[KEY_DYNAMIC_COLOR] ?: true }
    val paletteStyleOrdinal: Flow<Int> = context.dataStore.data.map { it[KEY_PALETTE_STYLE] ?: 0 }
    val autoCleanIntervalMinutes: Flow<Int> = context.dataStore.data.map { it[KEY_AUTO_CLEAN] ?: 0 }
    val lastCleanupTime: Flow<Long> = context.dataStore.data.map { it[KEY_LAST_CLEAN_TIME] ?: 0L }
    val receiveDir: Flow<String> = context.dataStore.data.map { it[KEY_RECEIVE_DIR] ?: "/sdcard/Download/ShareKu" }
    val enableLocationKeepAlive: Flow<Boolean> = context.dataStore.data.map { it[KEY_LOCATION_KEEPALIVE] ?: true }
    val uiStyle: Flow<String> = context.dataStore.data.map { it[KEY_UI_STYLE] ?: "material" }

    /** Write-safe receive directory: if configured dir is unwritable, fall back to app files dir. */
    fun getReceiveDirFile(context: Context, configuredPath: String): File {
        val dir = File(configuredPath)
        if (dir.exists() && dir.isDirectory && dir.canWrite()) return dir
        // Try to create it
        if (dir.mkdirs() && dir.canWrite()) return dir
        // Fallback to app's external files dir (always writable)
        val fallback = File(context.getExternalFilesDir(null), "ShareKu")
        if (!fallback.exists()) fallback.mkdirs()
        return fallback
    }

    suspend fun setPort(port: Int) { context.dataStore.edit { it[KEY_PORT] = port } }
    suspend fun setSharePort(port: Int) { context.dataStore.edit { it[KEY_SHARE_PORT] = port } }
    suspend fun setEnableWebDav(value: Boolean) { context.dataStore.edit { it[KEY_WEBDAV] = value } }
    suspend fun setEnableAuth(value: Boolean) { context.dataStore.edit { it[KEY_AUTH] = value } }
    suspend fun setAuthUsername(value: String) { context.dataStore.edit { it[KEY_AUTH_USER] = value } }
    suspend fun setAuthPassword(value: String) { context.dataStore.edit { it[KEY_AUTH_PASS] = value } }
    suspend fun setAllowUpload(value: Boolean) { context.dataStore.edit { it[KEY_UPLOAD] = value } }
    suspend fun setAllowDelete(value: Boolean) { context.dataStore.edit { it[KEY_DELETE] = value } }
    suspend fun setAllowOverwrite(value: Boolean) { context.dataStore.edit { it[KEY_OVERWRITE] = value } }
    suspend fun setNetworkInterface(value: String) { context.dataStore.edit { it[KEY_INTERFACE] = value } }
    suspend fun setUploadDir(value: String) { context.dataStore.edit { it[KEY_UPLOAD_DIR] = value } }
    suspend fun setRequireConnectionConfirm(value: Boolean) { context.dataStore.edit { it[KEY_CONFIRM] = value } }
    suspend fun setSharedDir(value: String) { context.dataStore.edit { it[KEY_SHARED_DIR] = value } }
    suspend fun setThemeMode(value: String) { context.dataStore.edit { it[KEY_THEME_MODE] = value } }
    suspend fun setDynamicColor(value: Boolean) { context.dataStore.edit { it[KEY_DYNAMIC_COLOR] = value } }
    suspend fun setPaletteStyleOrdinal(value: Int) { context.dataStore.edit { it[KEY_PALETTE_STYLE] = value } }
    suspend fun setAutoCleanInterval(minutes: Int) { context.dataStore.edit { it[KEY_AUTO_CLEAN] = minutes } }
    suspend fun setLastCleanupTime(time: Long) { context.dataStore.edit { it[KEY_LAST_CLEAN_TIME] = time } }
    suspend fun setReceiveDir(value: String) { context.dataStore.edit { it[KEY_RECEIVE_DIR] = value } }
    suspend fun setEnableLocationKeepAlive(value: Boolean) { context.dataStore.edit { it[KEY_LOCATION_KEEPALIVE] = value } }
    suspend fun setUiStyle(value: String) { context.dataStore.edit { it[KEY_UI_STYLE] = value } }
    companion object {
        private val KEY_PORT = intPreferencesKey("port")
    private val KEY_SHARE_PORT = intPreferencesKey("share_port")
    private val KEY_WEBDAV = booleanPreferencesKey("webdav")
        private val KEY_AUTH = booleanPreferencesKey("auth")
        private val KEY_AUTH_USER = stringPreferencesKey("auth_user")
        private val KEY_AUTH_PASS = stringPreferencesKey("auth_pass")
        private val KEY_UPLOAD = booleanPreferencesKey("upload")
        private val KEY_DELETE = booleanPreferencesKey("delete")
        private val KEY_OVERWRITE = booleanPreferencesKey("overwrite")
        private val KEY_INTERFACE = stringPreferencesKey("interface")
        private val KEY_UPLOAD_DIR = stringPreferencesKey("upload_dir")
        private val KEY_UPLOAD_SORT_TYPE = booleanPreferencesKey("upload_sort_type")
        private val KEY_CONFIRM = booleanPreferencesKey("confirm")
    private val KEY_UI_STYLE = stringPreferencesKey("ui_style") // "material" | "miuix"
        private val KEY_SHARED_DIR = stringPreferencesKey("shared_dir")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEY_PALETTE_STYLE = intPreferencesKey("palette_style")
        private val KEY_AUTO_CLEAN = intPreferencesKey("auto_clean_interval")
        private val KEY_LAST_CLEAN_TIME = longPreferencesKey("last_cleanup_time")
        private val KEY_RECEIVE_DIR = stringPreferencesKey("receive_dir")
        private val KEY_LOCATION_KEEPALIVE = booleanPreferencesKey("location_keepalive")
    }
}