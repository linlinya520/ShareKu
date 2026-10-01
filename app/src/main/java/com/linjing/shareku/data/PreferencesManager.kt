package com.linjing.shareku.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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
    // 屏幕刷新率档位（0 = 自动；其余为 Display.Mode.modeId）
    val screenDisplayModeId: Flow<Int> = context.dataStore.data.map { it[KEY_SCREEN_DISPLAY_MODE] ?: 0 }
    // 液态玻璃质感档位（0 = 清澈，0.5 = 均衡，1 = 磨砂）
    val glassDensity: Flow<Float> = context.dataStore.data.map { it[KEY_GLASS_DENSITY] ?: 0.5f }
    // 全局壁纸 / 布局
    val wallpaperSource: Flow<String> = context.dataStore.data.map { it[KEY_WALLPAPER_SOURCE] ?: "default" }
    val wallpaperPath: Flow<String> = context.dataStore.data.map { it[KEY_WALLPAPER_PATH] ?: "" }
    val wallpaperOverlay: Flow<Float> = context.dataStore.data.map { it[KEY_WALLPAPER_OVERLAY] ?: 0.55f }
    // 裁剪参数（缩放 + 偏移 + 旋转，由壁纸裁剪界面写入）
    val wallpaperScale: Flow<Float> = context.dataStore.data.map { it[KEY_WALLPAPER_SCALE] ?: 1f }
    val wallpaperOffsetX: Flow<Float> = context.dataStore.data.map { it[KEY_WALLPAPER_OFFSET_X] ?: 0f }
    val wallpaperOffsetY: Flow<Float> = context.dataStore.data.map { it[KEY_WALLPAPER_OFFSET_Y] ?: 0f }
    val wallpaperRotation: Flow<Float> = context.dataStore.data.map { it[KEY_WALLPAPER_ROTATION] ?: 0f }
    val layoutMode: Flow<String> = context.dataStore.data.map { it[KEY_LAYOUT_MODE] ?: "classic" }
    val videoLoop: Flow<Boolean> = context.dataStore.data.map { it[KEY_VIDEO_LOOP] ?: true }
    val videoAudio: Flow<Boolean> = context.dataStore.data.map { it[KEY_VIDEO_AUDIO] ?: false }
    val videoVolume: Flow<Float> = context.dataStore.data.map { it[KEY_VIDEO_VOLUME] ?: 1f }
    // 液态玻璃首次预渲染是否已完成（完成后不再弹出预渲染引导页）
    val liquidPreRenderDone: Flow<Boolean> = context.dataStore.data.map { it[KEY_LIQUID_PRERENDER_DONE] ?: false }
    // 设备直连接收开关（关闭后拒绝 /api/peer-upload）
    val allowPeerReceive: Flow<Boolean> = context.dataStore.data.map { it[KEY_ALLOW_PEER_RECEIVE] ?: true }

    // ═══ 动画与交互（用户可自由开关）═══
    /** 返回过渡模糊：返回/转场时页面渐糊 / 渐清晰 */
    val backTransitionBlur: Flow<Boolean> = context.dataStore.data.map { it[KEY_BACK_BLUR] ?: true }
    /** 页面转场动画：进入/退出的滑动与缩放（关 = 瞬时切换，更省电） */
    val pageTransitionAnim: Flow<Boolean> = context.dataStore.data.map { it[KEY_PAGE_TRANSITION] ?: true }
    /** 骨架呼吸动画：加载占位符轻微呼吸 */
    val skeletonBreathing: Flow<Boolean> = context.dataStore.data.map { it[KEY_SKELETON_BREATH] ?: true }
    /** Dock 滑块动效：拖拽时的液态跟随 / 拉伸 */
    val dockSliderMotion: Flow<Boolean> = context.dataStore.data.map { it[KEY_DOCK_MOTION] ?: true }
    /** 预见式返回预览：手势返回时跟手预览（关 = 直接完成返回，更省电） */
    val predictiveBackPreview: Flow<Boolean> = context.dataStore.data.map { it[KEY_PREDICTIVE_PREVIEW] ?: true }

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
    suspend fun setScreenDisplayModeId(value: Int) { context.dataStore.edit { it[KEY_SCREEN_DISPLAY_MODE] = value } }
    suspend fun setGlassDensity(value: Float) { context.dataStore.edit { it[KEY_GLASS_DENSITY] = value } }
    suspend fun setWallpaperSource(value: String) { context.dataStore.edit { it[KEY_WALLPAPER_SOURCE] = value } }
    suspend fun setWallpaperPath(value: String) { context.dataStore.edit { it[KEY_WALLPAPER_PATH] = value } }
    suspend fun setWallpaperOverlay(value: Float) { context.dataStore.edit { it[KEY_WALLPAPER_OVERLAY] = value } }
    suspend fun setWallpaperScale(value: Float) { context.dataStore.edit { it[KEY_WALLPAPER_SCALE] = value } }
    suspend fun setWallpaperOffsetX(value: Float) { context.dataStore.edit { it[KEY_WALLPAPER_OFFSET_X] = value } }
    suspend fun setWallpaperOffsetY(value: Float) { context.dataStore.edit { it[KEY_WALLPAPER_OFFSET_Y] = value } }
    suspend fun setWallpaperRotation(value: Float) { context.dataStore.edit { it[KEY_WALLPAPER_ROTATION] = value } }
    suspend fun setLayoutMode(value: String) { context.dataStore.edit { it[KEY_LAYOUT_MODE] = value } }
    suspend fun setVideoLoop(value: Boolean) { context.dataStore.edit { it[KEY_VIDEO_LOOP] = value } }
    suspend fun setVideoAudio(value: Boolean) { context.dataStore.edit { it[KEY_VIDEO_AUDIO] = value } }
    suspend fun setVideoVolume(value: Float) { context.dataStore.edit { it[KEY_VIDEO_VOLUME] = value } }
    suspend fun setLiquidPreRenderDone(value: Boolean) { context.dataStore.edit { it[KEY_LIQUID_PRERENDER_DONE] = value } }
    suspend fun setAllowPeerReceive(value: Boolean) { context.dataStore.edit { it[KEY_ALLOW_PEER_RECEIVE] = value } }
    // ═══ 动画与交互 ═══
    suspend fun setBackTransitionBlur(value: Boolean) { context.dataStore.edit { it[KEY_BACK_BLUR] = value } }
    suspend fun setPageTransitionAnim(value: Boolean) { context.dataStore.edit { it[KEY_PAGE_TRANSITION] = value } }
    suspend fun setSkeletonBreathing(value: Boolean) { context.dataStore.edit { it[KEY_SKELETON_BREATH] = value } }
    suspend fun setDockSliderMotion(value: Boolean) { context.dataStore.edit { it[KEY_DOCK_MOTION] = value } }
    suspend fun setPredictiveBackPreview(value: Boolean) { context.dataStore.edit { it[KEY_PREDICTIVE_PREVIEW] = value } }
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
    private val KEY_UI_STYLE = stringPreferencesKey("ui_style") // "material" | "miuix" | "liquid"
        private val KEY_SCREEN_DISPLAY_MODE = intPreferencesKey("screen_display_mode_id")
        private val KEY_GLASS_DENSITY = floatPreferencesKey("glass_density")
        private val KEY_SHARED_DIR = stringPreferencesKey("shared_dir")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEY_PALETTE_STYLE = intPreferencesKey("palette_style")
        private val KEY_AUTO_CLEAN = intPreferencesKey("auto_clean_interval")
        private val KEY_LAST_CLEAN_TIME = longPreferencesKey("last_cleanup_time")
        private val KEY_RECEIVE_DIR = stringPreferencesKey("receive_dir")
        private val KEY_LOCATION_KEEPALIVE = booleanPreferencesKey("location_keepalive")
        private val KEY_WALLPAPER_SOURCE = stringPreferencesKey("wallpaper_source")
        private val KEY_WALLPAPER_PATH = stringPreferencesKey("wallpaper_path")
        private val KEY_WALLPAPER_OVERLAY = floatPreferencesKey("wallpaper_overlay")
        private val KEY_WALLPAPER_SCALE = floatPreferencesKey("wallpaper_scale")
        private val KEY_WALLPAPER_OFFSET_X = floatPreferencesKey("wallpaper_offset_x")
        private val KEY_WALLPAPER_OFFSET_Y = floatPreferencesKey("wallpaper_offset_y")
        private val KEY_WALLPAPER_ROTATION = floatPreferencesKey("wallpaper_rotation")
        private val KEY_LAYOUT_MODE = stringPreferencesKey("layout_mode")
        private val KEY_VIDEO_LOOP = booleanPreferencesKey("video_loop")
        private val KEY_VIDEO_AUDIO = booleanPreferencesKey("video_audio")
        private val KEY_VIDEO_VOLUME = floatPreferencesKey("video_volume")
        private val KEY_LIQUID_PRERENDER_DONE = booleanPreferencesKey("liquid_prerender_done")
        private val KEY_ALLOW_PEER_RECEIVE = booleanPreferencesKey("allow_peer_receive")
        // 动画与交互
        private val KEY_BACK_BLUR = booleanPreferencesKey("back_transition_blur")
        private val KEY_PAGE_TRANSITION = booleanPreferencesKey("page_transition_anim")
        private val KEY_SKELETON_BREATH = booleanPreferencesKey("skeleton_breathing")
        private val KEY_DOCK_MOTION = booleanPreferencesKey("dock_slider_motion")
        private val KEY_PREDICTIVE_PREVIEW = booleanPreferencesKey("predictive_back_preview")
    }
}