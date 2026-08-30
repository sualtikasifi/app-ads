package com.sualtikasifi.cizimhafiza.presentation.navigation

import com.sualtikasifi.cizimhafiza.domain.model.DailyChallenge
import com.sualtikasifi.cizimhafiza.domain.model.Difficulty
import com.sualtikasifi.cizimhafiza.domain.model.GameMode
import com.sualtikasifi.cizimhafiza.domain.model.LevelCatalog

/**
 * Drawing/Break/Guess/Result are one continuous play session, so they live
 * under a single "game" destination driven by [GameViewModel]'s phase state
 * rather than four separate NavHost routes — that keeps the sequential
 * timers/transitions in one place instead of racing with back-stack
 * navigation. Each still has its own Composable + focused UI state.
 */
object Screen {
    const val MainMenu = "main_menu"
    const val WordCountSelect = "word_count_select"
    const val Statistics = "statistics"
    const val Settings = "settings"
    const val WordReview = "word_review"
    const val DifficultyReview = "difficulty_review"
    const val BotTraining = "bot_training"
    const val ReportBug = "report_bug"
    const val Tutorial = "tutorial"

    // worldId/levelIndex are optional query args (same pattern as OnlineJoinRoom's
    // ?roomCode= below) — present only when this game was launched from the level
    // map, so GameViewModel can record level progress; absent for free play.
    private const val GameRoute =
        "game/{wordCount}/{category}/{difficulty}/{mode}?worldId={worldId}&levelIndex={levelIndex}&daily={daily}"
    const val Game = GameRoute
    const val ArgWordCount = "wordCount"
    const val ArgCategory = "category"
    const val ArgDifficulty = "difficulty"
    const val ArgMode = "mode"
    const val ArgWorldId = "worldId"
    const val ArgLevelIndex = "levelIndex"
    const val ArgDaily = "daily"
    const val AllCategoriesArg = "all"
    const val AllDifficultiesArg = "all"

    fun gameRoute(wordCount: Int, category: String?, difficulty: Difficulty?, mode: GameMode): String =
        "game/$wordCount/${category ?: AllCategoriesArg}/${difficulty?.name ?: AllDifficultiesArg}/${mode.name}"

    // --- Level map ("Bölümler") ---
    const val WorldMap = "world_map"
    private const val LevelMapRoute = "level_map/{worldId}"
    const val LevelMap = LevelMapRoute

    fun levelMapRoute(worldId: Int): String = "level_map/$worldId"

    fun levelGameRoute(worldId: Int, levelIndex: Int): String {
        val config = LevelCatalog.levelConfig(worldId, levelIndex)
        // Difficulty is always AllDifficultiesArg here — a level's real
        // config (including any two-difficulty mix) is recomputed by
        // GameViewModel straight from worldId+levelIndex, never parsed
        // back out of this path segment.
        return "game/${config.wordCount}/${config.category}/$AllDifficultiesArg/${GameMode.NORMAL.name}" +
            "?worldId=$worldId&levelIndex=$levelIndex"
    }

    // --- Online (friend-vs-friend) rooms ---
    const val OnlineLobby = "online_lobby"
    const val OnlineCreateRoom = "online_create_room"
    const val Friends = "friends"
    const val League = "league"

    const val ArgRoomCode = "roomCode"
    // roomCode is an optional query arg here (not a path segment) so an
    // invite deep link (karalak://join/482913, registered separately below)
    // can pre-fill the code while a plain in-app "Koda Katıl" tap still
    // matches the same route with no code at all. OnlineJoinRoom is the
    // *pattern* used to register the destination; navigate() calls must use
    // OnlineJoinRoomBase (no args) since a raw "{roomCode}" placeholder
    // isn't a valid literal navigation target.
    private const val OnlineJoinRoomBaseRoute = "online_join_room"
    const val OnlineJoinRoomBase = OnlineJoinRoomBaseRoute
    const val OnlineJoinRoom = "$OnlineJoinRoomBaseRoute?roomCode={roomCode}"
    const val InviteDeepLinkPattern = "karalak://join/{roomCode}"
    private const val OnlineWaitingRoomRoute = "online_waiting_room/{roomCode}"
    const val OnlineWaitingRoom = OnlineWaitingRoomRoute
    private const val OnlineGameRoute = "online_game/{roomCode}"
    const val OnlineGame = OnlineGameRoute
    private const val OnlineResultRoute = "online_result/{roomCode}"
    const val OnlineResult = OnlineResultRoute

    fun onlineWaitingRoomRoute(roomCode: String): String = "online_waiting_room/$roomCode"
    fun onlineGameRoute(roomCode: String): String = "online_game/$roomCode"
    fun onlineResultRoute(roomCode: String): String = "online_result/$roomCode"
    fun inviteDeepLink(roomCode: String): String = "karalak://join/$roomCode"

    // --- Daily challenge ---
    // Runs through the ordinary game destination rather than a screen of its
    // own: the drawing/guessing flow, its timers and its result screen are
    // identical, and only the word list and the end-of-round bookkeeping
    // differ. The path segments below are placeholders — GameViewModel
    // replaces the word query entirely when daily=true (see
    // domain.model.DailyChallenge).
    fun dailyChallengeRoute(): String =
        "game/${DailyChallenge.WORD_COUNT}/$AllCategoriesArg/$AllDifficultiesArg/${GameMode.NORMAL.name}?daily=true"
}
