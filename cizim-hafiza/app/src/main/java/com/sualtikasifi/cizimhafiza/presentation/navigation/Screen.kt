package com.sualtikasifi.cizimhafiza.presentation.navigation

import com.sualtikasifi.cizimhafiza.domain.model.Difficulty
import com.sualtikasifi.cizimhafiza.domain.model.GameMode

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

    private const val GameRoute = "game/{wordCount}/{category}/{difficulty}/{mode}"
    const val Game = GameRoute
    const val ArgWordCount = "wordCount"
    const val ArgCategory = "category"
    const val ArgDifficulty = "difficulty"
    const val ArgMode = "mode"
    const val AllCategoriesArg = "all"
    const val AllDifficultiesArg = "all"

    fun gameRoute(wordCount: Int, category: String?, difficulty: Difficulty?, mode: GameMode): String =
        "game/$wordCount/${category ?: AllCategoriesArg}/${difficulty?.name ?: AllDifficultiesArg}/${mode.name}"
}
