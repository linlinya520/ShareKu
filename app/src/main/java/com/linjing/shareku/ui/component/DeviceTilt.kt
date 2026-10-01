package com.linjing.shareku.ui.component

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 设备重力方向（单位向量：x 右、y 下），**3° 量化**输出。
 *
 * 用途：玻璃高光跟随设备倾斜（对齐 miuix-blur 的 `sensor.rememberDeviceTilt` 做法）。
 * 量化到 3° 步进可避免传感器高频回调造成的每帧重组/重绘。
 */
@Composable
fun rememberQuantizedDeviceTilt(): State<Pair<Float, Float>> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(0f to -1f) }

    DisposableEffect(context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sm == null || sensor == null) {
            onDispose {}
        } else {
            val step = (3f * PI / 180.0).toFloat()
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val x = event.values.getOrElse(0) { 0f }
                    val y = event.values.getOrElse(1) { 0f }
                    val mag = sqrt(x * x + y * y)
                    if (mag < 0.6f) return // 接近水平/抖动时忽略
                    val nx = x / mag
                    val ny = y / mag
                    val q = (atan2(ny, nx) / step).roundToInt() * step
                    val qx = cos(q)
                    val qy = sin(q)
                    val cur = state.value
                    if (abs(qx - cur.first) > 0.05f || abs(qy - cur.second) > 0.05f) {
                        state.value = qx to qy
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            onDispose { sm.unregisterListener(listener) }
        }
    }
    return state
}