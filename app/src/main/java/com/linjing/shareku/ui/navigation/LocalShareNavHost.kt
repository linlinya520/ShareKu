package com.linjing.shareku.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier
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