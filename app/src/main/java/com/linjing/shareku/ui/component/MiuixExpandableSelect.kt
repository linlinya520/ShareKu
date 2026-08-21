package com.linjing.shareku.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * MIUI 风格展开式选择：点击行 → 弹出覆盖式浮层（带缩放/淡入"流水"动画），
 * 选中项打勾，点击外部自动关闭。复刻 miuix OverlayDropdown 的视觉与交互。
 */
@Composable
fun MiuixExpandableSelect(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current
    val scale by animateFloatAsState(if (expanded) 1f else 0.92f, spring(dampingRatio = 0.75f), label = "pop")

    Row(
        Modifier.fillMaxWidth()
            .clickable { expanded = !expanded }
            .onGloballyPositioned {
                val pos = it.positionInWindow()
                anchor = Rect(pos.x, pos.y, it.size.width.toFloat(), it.size.height.toFloat())
            }
            .padding(vertical = 13.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            options.getOrElse(selectedIndex) { options.firstOrNull() ?: "" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (expanded) {
        Popup(
            alignment = androidx.compose.ui.Alignment.TopStart,
            offset = IntOffset(anchor.left.toInt(), (anchor.bottom + 4).toInt()),
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = true, dismissOnClickOutside = true)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .width(with(density) { anchor.width.toDp() })
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        alpha = if (expanded) 1f else 0f
                    }
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    options.forEachIndexed { i, opt ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { onSelected(i); expanded = false }
                                .padding(vertical = 11.dp, horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(opt, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (i == selectedIndex) {
                                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (i < options.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.width(0.dp))
}