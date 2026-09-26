package com.linjing.shareku.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.linjing.shareku.ui.theme.LocalUiStyle
import com.linjing.shareku.ui.theme.ShareKuAnimationSpecs

@Composable
fun CustomCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    topStartCorner: Dp = cornerRadius,
    topEndCorner: Dp = cornerRadius,
    bottomStartCorner: Dp = cornerRadius,
    bottomEndCorner: Dp = cornerRadius,
    pressedCornerRadius: Dp = 12.dp,
    pressedScale: Float = 0.97f,
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = CardDefaults.outlinedCardBorder(),
    clickable: Boolean = true,
    enableHaptic: Boolean = true,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    // ── MIUI 风格：直接用 miuix 卡片组件 ──
    if (LocalUiStyle.current == "miuix") {
        top.yukonga.miuix.kmp.basic.Card(
            modifier = modifier,
            onClick = if (clickable) onClick else null
        ) { content() }
        return
    }
    val haptic = LocalHapticFeedback.current
    val liquid = isLiquidGlassActive()

    // ── 非点击态：零动画开销 ──
    if (!clickable) {
        val staticShape = remember(liquid, topStartCorner, topEndCorner, bottomStartCorner, bottomEndCorner) {
            if (liquid) {
                RoundedCornerShape(topStartCorner, topEndCorner, bottomEndCorner, bottomStartCorner)
            } else {
                StaticCornerShape(topStartCorner, topEndCorner, bottomStartCorner, bottomEndCorner)
            }
        }
        Card(
            modifier = modifier.cardGlassSurface(liquid, staticShape),
            colors = if (liquid) liquidCardColors() else colors,
            elevation = if (liquid) CardDefaults.cardElevation(defaultElevation = 0.dp) else elevation,
            border = if (liquid) null else border,
            shape = staticShape
        ) {
            content()
        }
        return
    }

    // ── 点击态：完整动画管线 ──
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val doCardInteractions = isPressed || isHovered

    val animatedTopStart by animateDpAsState(
        targetValue = if (doCardInteractions) pressedCornerRadius else topStartCorner,
        animationSpec = ShareKuAnimationSpecs.springDp, label = "ts"
    )
    val animatedTopEnd by animateDpAsState(
        targetValue = if (doCardInteractions) pressedCornerRadius else topEndCorner,
        animationSpec = ShareKuAnimationSpecs.springDp, label = "te"
    )
    val animatedBottomStart by animateDpAsState(
        targetValue = if (doCardInteractions) pressedCornerRadius else bottomStartCorner,
        animationSpec = ShareKuAnimationSpecs.springDp, label = "bs"
    )
    val animatedBottomEnd by animateDpAsState(
        targetValue = if (doCardInteractions) pressedCornerRadius else bottomEndCorner,
        animationSpec = ShareKuAnimationSpecs.springDp, label = "be"
    )

    val animatedShape = remember(liquid, animatedTopStart, animatedTopEnd, animatedBottomStart, animatedBottomEnd) {
        if (liquid) {
            RoundedCornerShape(
                topStart = animatedTopStart, topEnd = animatedTopEnd,
                bottomEnd = animatedBottomEnd, bottomStart = animatedBottomStart
            )
        } else {
            DynamicCornerShape(
                topStart = animatedTopStart, topEnd = animatedTopEnd,
                bottomStart = animatedBottomStart, bottomEnd = animatedBottomEnd
            )
        }
    }

    val animatedScale by animateFloatAsState(
        targetValue = if (doCardInteractions) pressedScale else 1f,
        animationSpec = ShareKuAnimationSpecs.springFloat, label = "scale"
    )

    Card(
        modifier = modifier
            .hoverable(interactionSource)
            .graphicsLayer { scaleX = animatedScale; scaleY = animatedScale }
            .cardGlassSurface(liquid, animatedShape),
        colors = if (liquid) liquidCardColors() else colors,
        elevation = if (liquid) CardDefaults.cardElevation(defaultElevation = 0.dp) else elevation,
        border = if (liquid) null else border,
        shape = animatedShape
    ) {
        Column(
            modifier = Modifier
                .clip(animatedShape)
                .combinedClickable(
                    enabled = true, interactionSource = interactionSource, indication = null,
                    onClick = {
                        if (enableHaptic) haptic.performHapticFeedback(HapticFeedbackType.ContextClick); onClick()
                    },
                    onLongClick = {
                        if (enableHaptic) haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick()
                    }
                )
                .indication(interactionSource = interactionSource, indication = ripple())
        ) { content() }
    }
}

private class StaticCornerShape(
    private val topStart: Dp, private val topEnd: Dp,
    private val bottomStart: Dp, private val bottomEnd: Dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        with(density) {
            return Outline.Rounded(RoundRect(
                rect = Rect(Offset.Zero, size),
                topLeft = CornerRadius(topStart.toPx(), topStart.toPx()),
                topRight = CornerRadius(topEnd.toPx(), topEnd.toPx()),
                bottomRight = CornerRadius(bottomEnd.toPx(), bottomEnd.toPx()),
                bottomLeft = CornerRadius(bottomStart.toPx(), bottomStart.toPx())
            ))
        }
    }
}

private class DynamicCornerShape(
    topStart: Dp, topEnd: Dp, bottomStart: Dp, bottomEnd: Dp,
) : Shape {
    private val ts = topStart; private val te = topEnd
    private val bs = bottomStart; private val be = bottomEnd
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        with(density) {
            return Outline.Rounded(RoundRect(
                rect = Rect(Offset.Zero, size),
                topLeft = CornerRadius(ts.toPx(), ts.toPx()),
                topRight = CornerRadius(te.toPx(), te.toPx()),
                bottomRight = CornerRadius(be.toPx(), be.toPx()),
                bottomLeft = CornerRadius(bs.toPx(), bs.toPx())
            ))
        }
    }
}

/** 液态玻璃卡片配色：容器透明，让 backdrop 玻璃层透出来 */
@Composable
private fun liquidCardColors(): CardColors = CardDefaults.cardColors(
    containerColor = Color.Transparent,
    contentColor = MaterialTheme.colorScheme.onSurface
)

/**
 * 卡片的「毛玻璃 + 液态玻璃」融合表面（省成本版）。
 *
 * - 玻璃本体走 liquidGlass(plain = true)：vibrancy + blur + lens，
 *   不挂库默认 Highlight / Shadow（它们各自是独立离屏层，逐帧录制成本高）；
 * - 改用「HWUI 高程阴影 + 静态渐变高光边」代替，外观接近、每帧成本低得多。
 */
@Composable
private fun Modifier.cardGlassSurface(enabled: Boolean, shape: Shape): Modifier {
    if (!enabled) return this
    return this
        .shadow(elevation = 8.dp, shape = shape, clip = false)
        .liquidGlass(
            shape = shape,
            surfaceAlpha = 0.14f,
            blurRadius = 6.dp,
            dispersion = false,
            plain = true
        )
        .border(
            border = BorderStroke(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.05f),
                        Color.White.copy(alpha = 0.12f)
                    )
                )
            ),
            shape = shape
        )
}