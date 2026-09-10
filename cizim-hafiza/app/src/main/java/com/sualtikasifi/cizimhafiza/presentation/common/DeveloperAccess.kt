package com.sualtikasifi.cizimhafiza.presentation.common

/**
 * The passcode in front of the screens meant for whoever maintains the game
 * rather than for whoever plays it.
 *
 * Deliberately not a security control, and never treated as one: this is a
 * constant in an APK anyone can unzip. It stops accidents and casual poking,
 * which is the entire threat — the screens behind it read shared data or
 * write to it, and none of them should be wandered into. The people meant to
 * get in are told the number directly.
 *
 * Shared so there is one number to remember and one place to change it.
 */
const val DEVELOPER_ACCESS_CODE = "8991"

/**
 * How many taps on the version line in Settings reveal the review screen.
 *
 * The version text is already on that screen and reads as decoration, which
 * is exactly what makes it the right door: nothing new appears in the menu,
 * nobody finds it by looking, and the passcode is still waiting behind it if
 * somebody does. Same idiom Android itself uses for developer options.
 */
const val DEVELOPER_REVEAL_TAPS = 7
