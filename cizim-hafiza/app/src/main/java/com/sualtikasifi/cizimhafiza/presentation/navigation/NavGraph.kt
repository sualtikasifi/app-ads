package com.sualtikasifi.cizimhafiza.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.sualtikasifi.cizimhafiza.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.sualtikasifi.cizimhafiza.domain.model.LevelCatalog
import com.sualtikasifi.cizimhafiza.presentation.bottraining.BotTrainingScreen
import com.sualtikasifi.cizimhafiza.presentation.common.IncomingInviteBanner
import com.sualtikasifi.cizimhafiza.presentation.common.IncomingInviteViewModel
import com.sualtikasifi.cizimhafiza.presentation.difficultyreview.DifficultyReviewScreen
import com.sualtikasifi.cizimhafiza.presentation.friends.FriendsScreen
import com.sualtikasifi.cizimhafiza.presentation.league.LeagueScreen
import com.sualtikasifi.cizimhafiza.presentation.game.GameScreen
import com.sualtikasifi.cizimhafiza.presentation.levelmap.LevelMapScreen
import com.sualtikasifi.cizimhafiza.presentation.mainmenu.MainMenuScreen
import com.sualtikasifi.cizimhafiza.presentation.online.CreateRoomScreen
import com.sualtikasifi.cizimhafiza.presentation.online.JoinRoomScreen
import com.sualtikasifi.cizimhafiza.presentation.online.OnlineGameScreen
import com.sualtikasifi.cizimhafiza.presentation.online.OnlineLobbyScreen
import com.sualtikasifi.cizimhafiza.presentation.online.OnlineResultScreen
import com.sualtikasifi.cizimhafiza.presentation.online.WaitingRoomScreen
import com.sualtikasifi.cizimhafiza.presentation.reportbug.ReportBugScreen
import com.sualtikasifi.cizimhafiza.presentation.settings.SettingsScreen
import com.sualtikasifi.cizimhafiza.presentation.stats.StatisticsScreen
import com.sualtikasifi.cizimhafiza.presentation.tutorial.TutorialScreen
import com.sualtikasifi.cizimhafiza.presentation.wordcount.WordCountScreen
import com.sualtikasifi.cizimhafiza.presentation.wordreview.WordReviewScreen
import com.sualtikasifi.cizimhafiza.presentation.worldmap.WorldMapScreen

private const val TRANSITION_MS = 260

@Composable
fun CizimHafizaNavGraph(
    onNavControllerReady: (NavHostController) -> Unit = {},
    // False only on a device's very first launch — see
    // SettingsRepository.tutorialCompleted / presentation/tutorial/.
    tutorialCompleted: Boolean = true,
    inviteViewModel: IncomingInviteViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    LaunchedEffect(navController) { onNavControllerReady(navController) }

    // Scoped to this composable (not any single screen), so the invite
    // listener and its "someone invited you" banner stay alive across
    // navigation — a match invite shouldn't only be visible while the
    // Friends screen happens to be open.
    val inviteState by inviteViewModel.uiState.collectAsState()
    LaunchedEffect(inviteState.navigateToWaitingRoomCode) {
        inviteState.navigateToWaitingRoomCode?.let { roomCode ->
            navController.navigate(Screen.onlineWaitingRoomRoute(roomCode)) {
                popUpTo(Screen.MainMenu)
            }
            inviteViewModel.onNavigatedToWaitingRoom()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    NavHost(
        navController = navController,
        startDestination = if (tutorialCompleted) Screen.MainMenu else Screen.Tutorial,
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
                onLevels = { navController.navigate(Screen.WorldMap) },
                onStatistics = { navController.navigate(Screen.Statistics) },
                onSettings = { navController.navigate(Screen.Settings) },
                onBotTraining = { navController.navigate(Screen.BotTraining) },
                onDailyChallenge = { navController.navigate(Screen.dailyChallengeRoute()) }
            )
        }

        composable(Screen.BotTraining) {
            BotTrainingScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.WordReview) {
            WordReviewScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.DifficultyReview) {
            DifficultyReviewScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.WordCountSelect) {
            WordCountScreen(
                onStart = { count, category, difficulty, mode ->
                    navController.navigate(Screen.gameRoute(count, category, difficulty, mode))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Game,
            arguments = listOf(
                navArgument(Screen.ArgWordCount) { type = NavType.StringType },
                navArgument(Screen.ArgCategory) { type = NavType.StringType },
                navArgument(Screen.ArgDifficulty) { type = NavType.StringType },
                navArgument(Screen.ArgMode) { type = NavType.StringType },
                navArgument(Screen.ArgWorldId) { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument(Screen.ArgLevelIndex) { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument(Screen.ArgDaily) { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val worldIdArg = backStackEntry.arguments?.getString(Screen.ArgWorldId)?.toIntOrNull()
            val levelIndexArg = backStackEntry.arguments?.getString(Screen.ArgLevelIndex)?.toIntOrNull()

            GameScreen(
                onMainMenu = {
                    if (worldIdArg != null) {
                        navController.navigate(Screen.levelMapRoute(worldIdArg)) {
                            popUpTo(Screen.WorldMap)
                        }
                    } else {
                        navController.navigate(Screen.MainMenu) {
                            popUpTo(Screen.MainMenu) { inclusive = true }
                        }
                    }
                },
                onLevelNextAction = if (worldIdArg != null && levelIndexArg != null &&
                    levelIndexArg < LevelCatalog.LEVELS_PER_WORLD
                ) {
                    {
                        navController.navigate(Screen.levelGameRoute(worldIdArg, levelIndexArg + 1)) {
                            popUpTo(Screen.WorldMap)
                        }
                    }
                } else null,
                nextActionLabel = stringResource(R.string.next_level)
            )
        }

        composable(
            route = Screen.LevelMap,
            arguments = listOf(navArgument(Screen.ArgWorldId) { type = NavType.IntType })
        ) { backStackEntry ->
            val worldId = backStackEntry.arguments?.getInt(Screen.ArgWorldId) ?: 1
            LevelMapScreen(
                worldId = worldId,
                onLevelClick = { levelIndex -> navController.navigate(Screen.levelGameRoute(worldId, levelIndex)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Statistics) {
            StatisticsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Settings) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onReportBugClick = { navController.navigate(Screen.ReportBug) },
                onReplayTutorialClick = { navController.navigate(Screen.Tutorial) }
            )
        }

        composable(Screen.Tutorial) {
            TutorialScreen(
                // Replaces the tutorial in the back stack so finishing it
                // (or skipping) can't be undone with the back button —
                // whether it was the launch destination or replayed from
                // Settings, the player lands on a clean Main Menu.
                onFinished = {
                    navController.navigate(Screen.MainMenu) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ReportBug) {
            ReportBugScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.WorldMap) {
            WorldMapScreen(
                onWorldClick = { worldId -> navController.navigate(Screen.levelMapRoute(worldId)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.OnlineLobby) {
            OnlineLobbyScreen(
                onBack = { navController.popBackStack() },
                onCreateRoom = { navController.navigate(Screen.OnlineCreateRoom) },
                onJoinRoom = { navController.navigate(Screen.OnlineJoinRoomBase) },
                onFriends = { navController.navigate(Screen.Friends) },
                onLeague = { navController.navigate(Screen.League) }
            )
        }

        composable(Screen.Friends) {
            FriendsScreen(
                onNavigateToWaitingRoom = { roomCode ->
                    navController.navigate(Screen.onlineWaitingRoomRoute(roomCode)) {
                        popUpTo(Screen.OnlineLobby)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.League) {
            LeagueScreen(onBack = { navController.popBackStack() })
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

        composable(
            route = Screen.OnlineJoinRoom,
            arguments = listOf(
                navArgument(Screen.ArgRoomCode) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
            deepLinks = listOf(navDeepLink { uriPattern = Screen.InviteDeepLinkPattern })
        ) {
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
                onFinished = { roomCode ->
                    navController.navigate(Screen.onlineResultRoute(roomCode)) {
                        popUpTo(Screen.MainMenu)
                    }
                },
                onExit = {
                    navController.navigate(Screen.MainMenu) {
                        popUpTo(Screen.MainMenu) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.OnlineResult,
            arguments = listOf(navArgument(Screen.ArgRoomCode) { type = NavType.StringType })
        ) {
            OnlineResultScreen(
                onRematchStarted = { roomCode ->
                    navController.navigate(Screen.onlineGameRoute(roomCode)) {
                        popUpTo(Screen.MainMenu)
                    }
                },
                onReturnToWaitingRoom = { roomCode ->
                    navController.navigate(Screen.onlineWaitingRoomRoute(roomCode)) {
                        popUpTo(Screen.MainMenu)
                    }
                },
                onMainMenu = {
                    navController.navigate(Screen.MainMenu) {
                        popUpTo(Screen.MainMenu) { inclusive = true }
                    }
                }
            )
        }
    }

        IncomingInviteBanner(
            invite = inviteState.invite,
            isResponding = inviteState.isResponding,
            onAccept = inviteViewModel::accept,
            onDecline = inviteViewModel::decline,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
