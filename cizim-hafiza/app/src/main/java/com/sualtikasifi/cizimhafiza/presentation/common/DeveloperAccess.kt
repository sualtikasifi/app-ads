package com.sualtikasifi.cizimhafiza.presentation.common

/**
 * The passcode in front of Bot Eğitim (see BotTrainingGate).
 *
 * Deliberately not a security control, and never treated as one: this is a
 * constant in an APK anyone can unzip. It stops accidents and casual poking,
 * which is the entire threat — the screen behind it writes shared data every
 * player's opponent is built from, and it should not be wandered into. The
 * people meant to get in (a handful of friends helping train it) are told
 * the number directly.
 */
const val DEVELOPER_ACCESS_CODE = "8991"

/**
 * The passcode in front of the report inbox (see DrawingReportsGate) — a
 * separate code from [DEVELOPER_ACCESS_CODE] on purpose. That one is handed
 * to several friends for a temporary training task; this one guards other
 * players' drawings and reported uids and is known to nobody but whoever
 * maintains the game, so it stays its own number even after Bot Eğitim's
 * code has been shared around and forgotten.
 */
const val REPORTS_ACCESS_CODE = "417296"

/**
 * How many taps on the version line in Settings reveal the report inbox's
 * passcode gate.
 *
 * Deliberately NOT 7: that count is the exact, widely-known convention for
 * Android's own hidden "Developer options" screen, which means a fair
 * number of curious or technical players will try tapping any version line
 * seven times purely out of habit. Landing on a passcode prompt on the
 * first try would all but confirm something is hidden here; requiring more
 * taps than the famous shortcut means that reflex fails silently instead.
 */
const val DEVELOPER_REVEAL_TAPS = 15
