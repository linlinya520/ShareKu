package com.linjing.shareku.ui.component

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.linjing.shareku.AppSingletons
import com.linjing.shareku.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 全局壁纸背景层：贯穿所有界面、所有 UI 风格。
 *
 * - `default`：内置默认壁纸（assets/optional/wallpaper_default.jpg，缺失降级纯色）
 * - `image`  ：本地图片（ContentScale.Crop 裁剪填充，绝不拉伸变形）
 * - `video`  ：本地视频（Media3 播放，循环/音频/音量可调；texture_view 模式保证画面可被玻璃层采样）
 * - `system` ：系统壁纸（静态壁纸直读；Live Wallpaper / 读取失败 → 降级纯色防闪退）
 * - 暗黑模式下按 `overlay` 浓度叠加黑色遮罩
 *
 * ⚠️ 图片解码一律放 IO 线程：大图 jpeg 在主线程解码会卡死首帧（实测数百 ms 级）。
 */
@Composable
fun WallpaperBackground(
    fallbackColor: Color,
    darkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val prefs = AppSingletons.preferencesManager
    val source by prefs.wallpaperSource.collectAsState(initial = "default")
    val path by prefs.wallpaperPath.collectAsState(initial = "")
    val overlay by prefs.wallpaperOverlay.collectAsState(initial = 0.55f)
    val wScale by prefs.wallpaperScale.collectAsState(initial = 1f)
    val wOffX by prefs.wallpaperOffsetX.collectAsState(initial = 0f)
    val wOffY by prefs.wallpaperOffsetY.collectAsState(initial = 0f)
    val wRot by prefs.wallpaperRotation.collectAsState(initial = 0f)
    val context = LocalContext.current

    Box(modifier.fillMaxSize()) {
        // ① 壁纸内容（解码一律放 IO 线程：大图 jpeg 在主线程解码会卡死首帧）
        when (source) {
            "image" -> {
                val bmp by produceState<ImageBitmap?>(initialValue = null, path) {
                    value = withContext(Dispatchers.IO) {
                        if (path.isNotEmpty() && File(path).exists()) {
                            try { BitmapFactory.decodeFile(path)?.asImageBitmap() } catch (_: Exception) { null }
                        } else null
                    }
                }
                val img = bmp
                if (img != null) {
                    Image(
                        painter = BitmapPainter(img),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            // 应用裁剪界面的缩放/偏移（scale 下限 1，保证永远铺满不变形）
                            .graphicsLayer(
                                scaleX = wScale.coerceAtLeast(1f),
                                scaleY = wScale.coerceAtLeast(1f),
                                translationX = wOffX,
                                translationY = wOffY,
                                rotationZ = wRot
                            ),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(fallbackColor))
                }
            }
            "video" -> {
                val pathOk = path.isNotEmpty() && File(path).exists()
                if (!pathOk) {
                    Box(Modifier.fillMaxSize().background(fallbackColor))
                } else {
                    var playError by remember { mutableStateOf(false) }
                    if (playError) {
                        // 播放失败 → 降级纯色，防闪退
                        Box(Modifier.fillMaxSize().background(fallbackColor))
                    } else {
                        val loop by prefs.videoLoop.collectAsState(initial = true)
                        val audioOn by prefs.videoAudio.collectAsState(initial = false)
                        val videoVol by prefs.videoVolume.collectAsState(initial = 1f)
                        val exoPlayer = remember(path) {
                            ExoPlayer.Builder(context).build().apply {
                                setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
                                repeatMode = Player.REPEAT_MODE_ALL
                                prepare()
                                playWhenReady = true
                            }
                        }
                        LaunchedEffect(loop) {
                            exoPlayer.repeatMode =
                                if (loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
                        }
                        LaunchedEffect(audioOn, videoVol) {
                            exoPlayer.volume = if (audioOn) videoVol.coerceIn(0f, 1f) else 0f
                        }
                        DisposableEffect(exoPlayer) {
                            val listener = object : Player.Listener {
                                override fun onPlayerError(error: PlaybackException) {
                                    playError = true
                                }
                            }
                            exoPlayer.addListener(listener)
                            val owner = context.findLifecycleOwner()
                            val observer = LifecycleEventObserver { _, event ->
                                when (event) {
                                    Lifecycle.Event.ON_START -> exoPlayer.play()
                                    Lifecycle.Event.ON_STOP -> exoPlayer.pause()
                                    else -> {}
                                }
                            }
                            owner?.lifecycle?.addObserver(observer)
                            onDispose {
                                owner?.lifecycle?.removeObserver(observer)
                                exoPlayer.removeListener(listener)
                                exoPlayer.release()
                            }
                        }
                        AndroidView(
                            factory = { ctx ->
                                val v = android.view.LayoutInflater.from(ctx)
                                    .inflate(R.layout.wallpaper_player, null) as PlayerView
                                v.player = exoPlayer
                                v.useController = false
                                v.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                v.setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                v
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = wScale.coerceAtLeast(1f),
                                    scaleY = wScale.coerceAtLeast(1f),
                                    translationX = wOffX,
                                    translationY = wOffY,
                                    rotationZ = wRot
                                )
                        )
                    }
                }
            }
            "system" -> {
                // 动态壁纸（如 Wallpaper Engine 视频壁纸）：画面无法通过公开 API 读取，
                // 由系统在窗口后绘制（FLAG_SHOW_WALLPAPER，LocalShareTheme 按同一条件开启），这里留空让其透出。
                // 静态系统壁纸仍走下面的读图逻辑（可完整支持玻璃折射 / 遮罩）。
                if (remember(context) { isLiveSystemWallpaper(context) }) {
                    Box(Modifier.fillMaxSize())
                } else {
                    val sysBmp by produceState<ImageBitmap?>(initialValue = null) {
                        value = withContext(Dispatchers.IO) {
                            try {
                                val wm = android.app.WallpaperManager.getInstance(context)
                                val d = wm.drawable
                                if (d != null) drawableToBitmap(d).asImageBitmap() else null
                            } catch (_: Throwable) { null }
                        }
                    }
                    val sysImg = sysBmp
                    if (sysImg != null) {
                        Image(
                            painter = BitmapPainter(sysImg),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // 读取失败 → 降级纯色，防闪退
                        Box(Modifier.fillMaxSize().background(fallbackColor))
                    }
                }
            }
            else -> {
                // default（及未知来源占位）
                val defaultBmp by produceState<ImageBitmap?>(initialValue = null) {
                    value = withContext(Dispatchers.IO) {
                        try {
                            context.assets.open("optional/wallpaper_default.jpg").use {
                                BitmapFactory.decodeStream(it)?.asImageBitmap()
                            }
                        } catch (_: Exception) { null }
                    }
                }
                val img = defaultBmp
                if (source == "default" && img != null) {
                    Image(
                        painter = BitmapPainter(img),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(fallbackColor))
                }
            }
        }

        // ② 暗黑遮罩（实时浓度）
        if (darkTheme && overlay > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = overlay.coerceIn(0f, 1f)))
            )
        }
    }
}
/** Drawable → Bitmap（系统壁纸可能是非 BitmapDrawable） */
private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): android.graphics.Bitmap {
    if (drawable is android.graphics.drawable.BitmapDrawable) {
        drawable.bitmap?.let { return it }
    }
    val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1080
    val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 2400
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    drawable.setBounds(0, 0, w, h)
    drawable.draw(canvas)
    return bmp
}

/** 从任意包装的 Context 向上找到 LifecycleOwner（用于视频随前后台启停） */
private fun android.content.Context.findLifecycleOwner(): LifecycleOwner? {
    var c: android.content.Context? = this
    while (c != null) {
        if (c is LifecycleOwner) return c
        c = (c as? android.content.ContextWrapper)?.baseContext
    }
    return null
}

/**
 * 当前系统壁纸是否为动态壁纸（WallpaperService，如 Wallpaper Engine）。
 *
 * 动态壁纸的画面无法通过公开 API 读取（getDrawable 只能拿到静态底层图），
 * 只能由系统在窗口后绘制（FLAG_SHOW_WALLPAPER，见 LocalShareTheme）。
 */
fun isLiveSystemWallpaper(context: android.content.Context): Boolean = try {
    android.app.WallpaperManager.getInstance(context).wallpaperInfo != null
} catch (_: Throwable) {
    false
}