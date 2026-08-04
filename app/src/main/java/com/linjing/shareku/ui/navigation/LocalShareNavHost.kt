package com.linjing.shareku.ui.navigation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.linjing.shareku.ui.screen.HomeScreen
import com.linjing.shareku.ui.screen.LogScreen

object Routes {
    const val HOME = "home"
    const val LOG = "log"
}

@Composable
fun LocalShareNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val anim = tween<IntOffset>(280, easing = LinearEasing)
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(initialOffsetX = { it }, animationSpec = anim)
        },
        exitTransition = {
            slideOutHorizontally(targetOffsetX = { -it }, animationSpec = anim)
        },
        popEnterTransition = {
            slideInHorizontally(initialOffsetX = { -it }, animationSpec = anim)
        },
        popExitTransition = {
            slideOutHorizontally(targetOffsetX = { it }, animationSpec = anim)
        }
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToLog = { navController.navigate(Routes.LOG) }
            )
        }
        composable(Routes.LOG) {
            LogScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}