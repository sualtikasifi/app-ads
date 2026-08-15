package com.sualtikasifi.cizimhafiza.presentation.navigation

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

    private const val GameRoute = "game/{wordCount}/{category}"
    const val Game = GameRoute
    const val ArgWordCount = "wordCount"
    const val ArgCategory = "category"
    const val AllCategoriesArg = "all"

    fun gameRoute(wordCount: Int, category: String?): String =
        "game/$wordCount/${category ?: AllCategoriesArg}"
}
