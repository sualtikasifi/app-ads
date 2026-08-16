package com.sualtikasifi.cizimhafiza.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sualtikasifi.cizimhafiza.presentation.game.GameScreen
import com.sualtikasifi.cizimhafiza.presentation.mainmenu.MainMenuScreen
import com.sualtikasifi.cizimhafiza.presentation.online.CreateRoomScreen
import com.sualtikasifi.cizimhafiza.presentation.online.JoinRoomScreen
import com.sualtikasifi.cizimhafiza.presentation.online.OnlineGameScreen
import com.sualtikasifi.cizimhafiza.presentation.online.OnlineLobbyScreen
import com.sualtikasifi.cizimhafiza.presentation.online.WaitingRoomScreen
import com.sualtikasifi.cizimhafiza.presentation.settings.SettingsScreen
import com.sualtikasifi.cizimhafiza.presentation.stats.StatisticsScreen
import com.sualtikasifi.cizimhafiza.presentation.wordcount.WordCountScreen

private const val TRANSITION_MS = 320

@Composable
fun CizimHafizaNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.MainMenu,
        enterTransition = {
            slideInHorizontally(animationSpec = tween(TRANSITION_MS)) { it / 4 } + fadeIn(tween(TRANSITION_MS))
        },
        exitTransition = {
            slideOutHorizontally(animationSpec = tween(TRANSITION_MS)) { -it / 4 } + fadeOut(tween(TRANSITION_MS))
        },
        popEnterTransition = {
            slideInHorizontally(animationSpec = tween(TRANSITION_MS)) { -it / 4 } + fadeIn(tween(TRANSITION_MS))
        },
        popExitTransition = {
            slideOutHorizontally(animationSpec = tween(TRANSITION_MS)) { it / 4 } + fadeOut(tween(TRANSITION_MS))
        }
    ) {

        composable(Screen.MainMenu) {
            MainMenuScreen(
                onPlay = { navController.navigate(Screen.WordCountSelect) },
                onPlayOnline = { navController.navigate(Screen.OnlineLobby) },
                onStatistics = { navController.navigate(Screen.Statistics) },
                onSettings = { navController.navigate(Screen.Settings) }
            )
        }

        composable(Screen.WordCountSelect) {
            WordCountScreen(
                onStart = { count, category, difficulty, mode ->
                    navController.navigate(Screen.gameRoute(count, category, difficulty, mode))
                }
            )
        }

        composable(
            route = Screen.Game,
            arguments = listOf(
                navArgument(Screen.ArgWordCount) { type = NavType.StringType },
                navArgument(Screen.ArgCategory) { type = NavType.StringType },
                navArgument(Screen.ArgDifficulty) { type = NavType.StringType },
                navArgument(Screen.ArgMode) { type = NavType.StringType }
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

        composable(Screen.OnlineLobby) {
            OnlineLobbyScreen(
                onCreateRoom = { navController.navigate(Screen.OnlineCreateRoom) },
                onJoinRoom = { navController.navigate(Screen.OnlineJoinRoom) }
            )
        }

        composable(Screen.OnlineCreateRoom) {
            CreateRoomScreen(
                onRoomCreated = { roomCode ->
                    navController.navigate(Screen.onlineWaitingRoomRoute(roomCode)) {
                        popUpTo(Screen.OnlineLobby)
                    }
                }
            )
        }

        composable(Screen.OnlineJoinRoom) {
            JoinRoomScreen(
                onJoined = { roomCode ->
                    navController.navigate(Screen.onlineWaitingRoomRoute(roomCode)) {
                        popUpTo(Screen.OnlineLobby)
                    }
                }
            )
        }

        composable(
            route = Screen.OnlineWaitingRoom,
            arguments = listOf(navArgument(Screen.ArgRoomCode) { type = NavType.StringType })
        ) {
            WaitingRoomScreen(
                onGameStarted = { roomCode ->
                    navController.navigate(Screen.onlineGameRoute(roomCode)) {
                        popUpTo(Screen.MainMenu)
                    }
                },
                onLeave = { navController.popBackStack(Screen.MainMenu, inclusive = false) }
            )
        }

        composable(
            route = Screen.OnlineGame,
            arguments = listOf(navArgument(Screen.ArgRoomCode) { type = NavType.StringType })
        ) {
            OnlineGameScreen(
                onMainMenu = {
                    navController.navigate(Screen.MainMenu) {
                        popUpTo(Screen.MainMenu) { inclusive = true }
                    }
                }
            )
        }
    }
}
