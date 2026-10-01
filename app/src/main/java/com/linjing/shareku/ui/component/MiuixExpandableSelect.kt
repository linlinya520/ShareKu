package com.linjing.shareku.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.window.WindowListPopup

/**
 * MIUI 风格展开式选择（miuix 库原生实现）。
 *
 * ⚠️ 锚点机制（miuix 源码行为）：[WindowListPopup] 会把「调用处分隔符所在父布局节点」的
 * bounds 作为锚点定位弹层。因此必须把「触发器行 + WindowListPopup」包在同一个 [Box] 里 ——
 * 否则锚点会变成整个设置分组容器，弹层位置会飘/方向错乱（此前实测的"镜像"问题）。
 *
 * 对外 API 保持不变（title / options / selectedIndex / onSelected），调用方零改动。
 */
@Composable
fun MiuixExpandableSelect(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // 锚点容器：Box 的 bounds 就是「这一行」的区域，miuix 按此定位弹层
    Box(Modifier.fillMaxWidth()) {

        // 触发器行（外观与其它设置行保持一致）
        Row(
            Modifier.fillMaxWidth()
                .clickable { expanded = !expanded }
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

        // miuix 原生窗口级弹层：右对齐展开（数值/箭头在行右侧，弹层贴行右缘）
        WindowListPopup(
            show = expanded,
            alignment = PopupPositionProvider.Align.End,
            popupPositionProvider = ListPopupDefaults.dropdownPositionProvider(
                verticalMargin = 8.dp,
                horizontalMargin = 12.dp
            ),
            onDismissRequest = { expanded = false }
        ) {
            ListPopupColumn {
                options.forEachIndexed { i, opt ->
                    DropdownImpl(
                        item = DropdownItem(text = opt),
                        optionSize = options.size,
                        isSelected = i == selectedIndex,
                        index = i,
                        isFirst = i == 0,
                        isLast = i == options.lastIndex,
                        onSelectedIndexChange = { idx ->
                            onSelected(idx)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}