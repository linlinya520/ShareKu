package com.linjing.shareku.ui.component

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 壁纸裁剪 / 定位界面。
 *
 * 选完图片/视频后弹出：双指捏合缩放、单指拖动位置，右上角勾确认、左上角叉取消。
 * 视频用首帧做预览，确认后的缩放/偏移参数同样作用于视频播放层。
 * 确认后返回 (scale, offsetX, offsetY)，由调用方持久化。
 */
@Composable
fun WallpaperCropDialog(
    imagePath: String,
    isVideo: Boolean = false,
    initialScale: Float = 1f,
    initialOffsetX: Float = 0f,
    initialOffsetY: Float = 0f,
    initialRotation: Float = 0f,
    onConfirm: (scale: Float, offsetX: Float, offsetY: Float, rotation: Float) -> Unit,
    onDismiss: () -> Unit
) {
    // 解码/抽帧放 IO 线程：大图 / 视频首帧在主线程解码会卡进入动画（与 Wallpaper.kt 同一原则）
    var bitmap by remember(imagePath, isVideo) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var loadFailed by remember(imagePath, isVideo) { mutableStateOf(false) }
    LaunchedEffect(imagePath, isVideo) {
        val result = withContext(Dispatchers.IO) {
            try {
                if (isVideo) {
                    val r = android.media.MediaMetadataRetriever()
                    try {
                        r.setDataSource(imagePath)
                        r.getFrameAtTime(0)
                    } finally {
                        runCatching { r.release() }
                    }
                } else {
                    BitmapFactory.decodeFile(imagePath)
                }
            } catch (_: Exception) { null }
        }
        bitmap = result
        loadFailed = result == null
    }
    var scale by remember { mutableStateOf(initialScale.coerceAtLeast(1f)) }
    var offset by remember { mutableStateOf(Offset(initialOffsetX, initialOffsetY)) }
    var rotation by remember { mutableStateOf(initialRotation) }
    // 旋转死区状态：净旋转累计超过阈值才认定「确实在旋转」（防止缩放/平移时手形抖动被误判为旋转）
    var rotDebt by remember { mutableStateOf(0f) }
    var rotActive by remember { mutableStateOf(false) }
    var lastRotNs by remember { mutableStateOf(0L) }

    val transformState = rememberTransformableState { zoomChange, panChange, rotChange ->
        scale = (scale * zoomChange).coerceIn(1f, 6f)
        offset += panChange
        if (rotChange != 0f) {
            // rotChange 单位是「度」（与双指旋转角度 1:1）
            val now = System.nanoTime()
            // 超过 350ms 没有旋转事件 → 视为新手势，重置旋转激活状态
            if (lastRotNs != 0L && now - lastRotNs > 350_000_000L) {
                rotActive = false
                rotDebt = 0f
            }
            val dtSec = if (lastRotNs == 0L) 0.016f else ((now - lastRotNs) / 1_000_000_000f).coerceIn(0.001f, 0.2f)
            lastRotNs = now
            if (!rotActive) {
                // 死区阶段：先按净值累计，超过阈值才「点亮」旋转；未超过前完全不动（防误判）
                rotDebt += rotChange
                if (kotlin.math.abs(rotDebt) > ROT_DEADZONE_DEG) {
                    rotActive = true
                    rotation += rotDebt
                    rotDebt = 0f
                }
            } else {
                // 已激活：1:1 跟手；仅快速旋转时吸附对齐（缓慢微调不吸附）
                val raw = rotation + rotChange
                val rate = kotlin.math.abs(rotChange) / dtSec
                rotation = if (rate > SNAP_SPEED_THRESHOLD_DPS) snapAngle(raw) else raw
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                            rotationZ = rotation
                        )
                        .transformable(transformState)
                )
            } else {
                Text(
                    if (loadFailed) "预览加载失败" else "正在加载预览…",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // 右上角：确认
            IconButton(
                onClick = { onConfirm(scale, offset.x, offset.y, rotation) },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.22f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, contentDescription = "完成", tint = Color.White)
                    }
                }
            }

            // 左上角：取消
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.22f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, contentDescription = "取消", tint = Color.White)
                    }
                }
            }

            // 底部：当前角度 + 操作提示
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val normDeg = ((rotation % 360f) + 360f) % 360f
                Text(
                    "${Math.round(normDeg)}°",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    "双指缩放 · 拖动位置 · 双指旋转（快速旋转自动吸附水平 / 垂直）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
        }
    }
}

/** 旋转吸附速度阈值（度/秒）：高于该速度才吸附，缓慢微调时不吸附 */
private const val SNAP_SPEED_THRESHOLD_DPS = 15f

/** 旋转死区（度）：净旋转累计超过该值才认定「确实在旋转」；防止缩放/平移时被误判 */
private const val ROT_DEADZONE_DEG = 2.5f

/** 吸附作用范围（度）：距水平/垂直角度 5° 内才吸附 */
private const val SNAP_ANGLE_RANGE_DEG = 5f

/** 将角度吸附到最近的正交角度（0/90/180/270/360）；超出范围返回原值 */
private fun snapAngle(deg: Float): Float {
    val norm = ((deg % 360f) + 360f) % 360f
    val targets = floatArrayOf(0f, 90f, 180f, 270f, 360f)
    var best = targets[0]
    for (t in targets) {
        if (kotlin.math.abs(t - norm) < kotlin.math.abs(best - norm)) best = t
    }
    if (kotlin.math.abs(best - norm) > SNAP_ANGLE_RANGE_DEG) return deg
    return if (best == 360f) 0f else best
}