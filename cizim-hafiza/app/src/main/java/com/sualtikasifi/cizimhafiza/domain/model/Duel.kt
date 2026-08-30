package com.sualtikasifi.cizimhafiza.domain.model

/** Lifecycle of an asynchronous duel — see DuelRepository/firestore.rules' duels/{duelId}. */
enum class DuelStatus { AWAITING_OPPONENT, COMPLETE }

/**
 * One asynchronous duel: the challenger plays a normal solo round (see
 * GameViewModel's duel-challenge args), and [items] — that round's own
 * drawings — becomes a challenge the opponent can open and guess whenever
 * they next launch the app, with no clock forcing either side to be online
 * at the same time. Whoever scored higher wins (see [challengerWon]).
 *
 * The challenger's own strokes only ever get INTERPRETED by the opponent —
 * the opponent never draws anything of their own in a duel (see
 * DuelPlayViewModel), which is what makes this "one player draws now, the
 * other guesses later" rather than a second, delayed online room.
 */
data class Duel(
    val id: String,
    val challengerUid: String,
    val challengerName: String,
    val opponentUid: String,
    val opponentName: String,
    /** The challenger's own finished round — word, whether THEY guessed it right, and its strokes for the opponent to see. */
    val items: List<ResultItem>,
    val challengerScore: Int,
    val challengerCorrectCount: Int,
    /** Null until the opponent has played (see DuelStatus.COMPLETE). */
    val opponentScore: Int? = null,
    val opponentCorrectCount: Int? = null,
    val status: DuelStatus = DuelStatus.AWAITING_OPPONENT,
    val createdAt: Long = 0L,
    val completedAt: Long? = null,
    /** False right after the opponent completes it — clears once the challenger has opened the result once. */
    val seenByChallenger: Boolean = true
) {
    val totalWords: Int get() = items.size

    /** Null until COMPLETE. True if the challenger's own round outscored the opponent's guesses; null again on an exact tie. */
    val challengerWon: Boolean?
        get() = opponentScore?.let { score ->
            when {
                challengerScore > score -> true
                challengerScore < score -> false
                else -> null
            }
        }
}
