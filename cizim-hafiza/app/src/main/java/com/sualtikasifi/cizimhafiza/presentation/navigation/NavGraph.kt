package com.sualtikasifi.cizimhafiza.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sualtikasifi.cizimhafiza.presentation.game.GameScreen
import com.sualtikasifi.cizimhafiza.presentation.mainmenu.MainMenuScreen
import com.sualtikasifi.cizimhafiza.presentation.settings.SettingsScreen
import com.sualtikasifi.cizimhafiza.presentation.stats.StatisticsScreen
import com.sualtikasifi.cizimhafiza.presentation.wordcount.WordCountScreen

@Composable
fun CizimHafizaNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.MainMenu) {

        composable(Screen.MainMenu) {
            MainMenuScreen(
                onPlay = { navController.navigate(Screen.WordCountSelect) },
                onStatistics = { navController.navigate(Screen.Statistics) },
                onSettings = { navController.navigate(Screen.Settings) }
            )
        }

        composable(Screen.WordCountSelect) {
            WordCountScreen(
                onStart = { count, category ->
                    navController.navigate(Screen.gameRoute(count, category))
                }
            )
        }

        composable(
            route = Screen.Game,
            arguments = listOf(
                navArgument(Screen.ArgWordCount) { type = NavType.StringType },
                navArgument(Screen.ArgCategory) { type = NavType.StringType }
            )
        ) {
            GameScreen(
                onMainMenu = {
                    navController.navigate(Screen.MainMenu) {
                        popUpTo(Screen.MainMenu) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Statistics) {
            StatisticsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Settings) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
