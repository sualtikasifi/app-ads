package com.sualtikasifi.cizimhafiza.util

import com.sualtikasifi.cizimhafiza.BuildConfig
import com.sualtikasifi.cizimhafiza.domain.model.Difficulty

/**
 * Single place to tune game balance / feature toggles without hunting
 * through screens and ViewModels.
 */
object GameConstants {

    // --- Word count choices offered on the selection screen ---
    val WORD_COUNT_OPTIONS = listOf(10, 20, 30, 40, 50)

    // --- Drawing phase duration per difficulty. RELAXED (see GameMode) skips
    // the timer entirely, so this only applies to the NORMAL mode. ---
    fun drawingDurationSeconds(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 5
        Difficulty.MEDIUM -> 7
        Difficulty.HARD -> 10
    }

    // Last N seconds of the drawing countdown trigger the warning color + vibration.
    const val WARNING_THRESHOLD_SECONDS = 2

    // Break shown between the drawing phase and the guessing phase.
    const val BREAK_DURATION_SECONDS = 3

    // "3, 2, 1…" countdown shown before an online match's first Drawing
    // phase — both on the initial match and on every rematch.
    const val ONLINE_START_COUNTDOWN_SECONDS = 3

    // Max players in one "Arkadaşla Yarış" room. Also hardcoded in
    // firestore.rules' join-cap check (Firestore rules can't reference a
    // Kotlin constant) — keep both in sync if this ever changes.
    const val MAX_ROOM_SIZE = 8

    // A 2v2 team room (see C5 / OnlineRoom.teamMode) is exactly two teams of
    // this many — not a subset of MAX_ROOM_SIZE, a separate cap entirely.
    // Also hardcoded in firestore.rules' join-cap check, same reasoning as
    // MAX_ROOM_SIZE above.
    const val TEAM_SIZE = 2
    const val TEAM_ROOM_SIZE = TEAM_SIZE * 2

    // How long a declined match invite blocks the sender from re-inviting
    // that same recipient. Also hardcoded as 300000 in firestore.rules'
    // invites create rule (the real enforcement — this constant only drives
    // the app's own pre-send check/error message) — keep both in sync.
    const val FRIEND_INVITE_COOLDOWN_MILLIS = 5 * 60 * 1000L

    // "Son Oyunlar" (Statistics screen) keeps only this many most-recent
    // game sessions — solo and online combined — pruning older ones.
    const val RECENT_GAMES_LIMIT = 20

    // A free-play round narrowed to one specific category (see
    // GameRepositoryImpl.getRandomWordsMix) draws independently of every
    // other round with no shared memory, so without this the same word can
    // easily resurface a session or two later — worst for a thin category
    // (e.g. very few HARD words), where consecutive rounds could end up
    // asking almost the exact same small set repeatedly. Excludes that
    // category's most recently drawn word ids, up to this many, before
    // falling back to allowing repeats when a difficulty's pool is too thin
    // to fill a round while avoiding them. The level map ("Bölümler") no
    // longer uses this at all — its levels mix every category at once (see
    // LevelCatalog/World), and that combined pool is large enough that
    // repeats aren't a practical concern the way a single thin category was.
    const val RECENT_WORD_EXCLUSION_WINDOW = 60

    // Time limit to answer each guess. Timing out counts as skipped (wrong/0 points).
    const val GUESS_DURATION_SECONDS = 10

    // Rewarded-ad hint bonus: the guess countdown is paused for the whole ad
    // (load + watch), then resumed with this many extra seconds added on
    // top of wherever it was paused — the point of watching is to actually
    // have time left to type the answer, not just see a letter with the
    // clock about to hit zero. Only granted once per match — see
    // GameViewModel/OnlineGameViewModel.useHint().
    const val HINT_BONUS_SECONDS = 5

    // Same idea, separate budget: watching a rewarded ad during the DRAWING
    // phase (see GameViewModel/OnlineGameViewModel.useDrawingHint) extends
    // that word's timer instead of revealing a letter — there's nothing to
    // "hint" while drawing, so this is deliberately a bigger bonus than
    // HINT_BONUS_SECONDS, independently tunable from it.
    const val DRAWING_TIME_BONUS_SECONDS = 10

    // --- Scoring ---
    const val POINTS_CORRECT = 5
    const val POINTS_WRONG = 0

    // Feature flag: toggle the speed bonus system on/off in one place.
    const val SPEED_BONUS_ENABLED = true
    const val SPEED_BONUS_THRESHOLD_MS = 3_000L
    const val SPEED_BONUS_POINTS = 2

    // "Yakın doğru" toleransı artık burada sabit değil: hedef kelimenin
    // uzunluğuna göre hesaplanıyor (bkz. AnswerMatcher.toleranceFor). Sabit
    // 2 tolerans kısa kelimelerde `gül`/`gol`, `top`/`gol`, `kale`/`file`
    // gibi havuzdaki ayrı kelimeleri birbirine eşitliyordu.

    /**
     * Master switch for every ad in the app. **Off for launch, on purpose.**
     *
     * The whole AdMob path is built and tested (see AdManager) but the store
     * release ships without ads and they get turned on in a later update.
     * Three things follow from that, and all three are load-bearing:
     *
     *  - The ad unit IDs still fall back to Google's public TEST IDs, which
     *    pay nothing. Shipping with those live would have shown real players
     *    real ads and earned exactly zero.
     *  - AndroidManifest.xml removes the AD_ID permission, and the Play
     *    Console Data Safety form declares no advertising ID. Both are only
     *    truthful while this is false. **Flipping this to true means editing
     *    the manifest and updating that declaration in the same change** —
     *    serving ads while declaring you collect no advertising ID is a
     *    policy problem, not just a lost-revenue one.
     *  - Every control that exists to open a rewarded ad (the two hints, the
     *    XP doubler, the streak rescue) is hidden while this is false. A
     *    button that can only ever answer "reklam yüklenemedi" is worse than
     *    no button, so they come back with the ads rather than sitting there
     *    failing. Search for ADMOB_ENABLED to find all four.
     */
    val ADMOB_ENABLED: Boolean = false
}
