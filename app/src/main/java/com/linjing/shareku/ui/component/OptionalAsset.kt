package com.linjing.shareku.ui.component

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource

/**
 * 可选资源加载器。
 *
 * 优先从 `assets/<assetPath>` 加载真实图片；
 * 若该文件不存在（例如未上传照片的构建环境），自动回退到 fallbackRes 矢量占位图。
 *
 * 这样既能保留真实照片，又能保证任何环境 clone 后都能正常编译运行。
 */
@Composable
fun optionalAssetPainter(assetPath: String, @DrawableRes fallbackRes: Int): Painter {
    val context = LocalContext.current
    val bitmap = remember(assetPath) {
        try {
            context.assets.open(assetPath).use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            null
        }
    }
    return if (bitmap != null) {
        BitmapPainter(bitmap.asImageBitmap())
    } else {
        painterResource(fallbackRes)
    }
}