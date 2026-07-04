package com.linjing.shareku.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp

object ShareKuAnimationSpecs {
    const val DampingRatioMediumHighBouncy = 0.35f

    val springFloat: SpringSpec<Float> = spring(
        dampingRatio = DampingRatioMediumHighBouncy,
        stiffness = Spring.StiffnessLow
    )

    val springDp: SpringSpec<Dp> = spring(
        dampingRatio = DampingRatioMediumHighBouncy,
        stiffness = Spring.StiffnessLow
    )
}
