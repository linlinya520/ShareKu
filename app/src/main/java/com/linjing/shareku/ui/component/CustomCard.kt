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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.linjing.shareku.ui.theme.ShareKuAnimationSpecs
import kotlin.math.roundToInt

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
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

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

    val animatedShape = remember {
        DynamicCornerShape(
            topStart = { animatedTopStart },
            topEnd = { animatedTopEnd },
            bottomStart = { animatedBottomStart },
            bottomEnd = { animatedBottomEnd }
        )
    }

    val animatedScale by animateFloatAsState(
        targetValue = if (doCardInteractions) pressedScale else 1f,
        animationSpec = ShareKuAnimationSpecs.springFloat,
        label = "scale_anim"
    )

    Card(
        modifier = modifier
            .hoverable(interactionSource)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            },
        colors = colors,
        elevation = elevation,
        border = border,
        shape = animatedShape
    ) {
        Column(
            modifier = Modifier
                .clip(animatedShape)
                .combinedClickable(
                    enabled = clickable,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (enableHaptic) haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onClick()
                    },
                    onLongClick = {
                        if (enableHaptic) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                )
                .indication(
                    interactionSource = interactionSource,
                    indication = ripple()
                )
        ) {
            content()
        }
    }
}

private class DynamicCornerShape(
    private val topStart: () -> Dp,
    private val topEnd: () -> Dp,
    private val bottomStart: () -> Dp,
    private val bottomEnd: () -> Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val ts = with(density) { topStart().coerceIn(0.dp, 100.dp).toPx() }
        val te = with(density) { topEnd().coerceIn(0.dp, 100.dp).toPx() }
        val bs = with(density) { bottomStart().coerceIn(0.dp, 100.dp).toPx() }
        val be = with(density) { bottomEnd().coerceIn(0.dp, 100.dp).toPx() }
        return Outline.Rounded(
            RoundRect(
                rect = Rect(Offset.Zero, size),
                topLeft = CornerRadius(ts, ts),
                topRight = CornerRadius(te, te),
                bottomRight = CornerRadius(be, be),
                bottomLeft = CornerRadius(bs, bs)
            )
        )
    }
}