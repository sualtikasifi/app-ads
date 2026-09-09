package com.sualtikasifi.cizimhafiza.domain.model

import com.sualtikasifi.cizimhafiza.util.GameConstants
import java.util.Locale
import kotlin.random.Random

/**
 * What Sude scored in a round she was never actually in.
 *
 * The offer and the result screen are two separate journeys through the app
 * — the opponent is handed to the game screen as a route argument and the
 * drawings are fetched much later, from a different collection — so both
 * have to arrive at the identical answer with nothing passed between them.
 * That is why every field here is derived, not stored.
 */
data class BotGhostOutcome(
    /** Per word, in the run's own order: did she recall her own drawing? */
    val correctness: List<Boolean>,
    val totalScore: Int,
    val correctCount: Int,
    val fastestCorrectMs: Long?
)

/**
 * A Hızlı Eşleş opponent assembled from the hand-trained drawing set
 * (`botTrainedWords`, see BotTrainingRepository) rather than from a round
 * anybody played.
 *
 * The pool grows with games played, which is a slow way to start: for the
 * first player of the day there is nothing to be matched against, and being
 * told "havuz henüz boş" is exactly the moment somebody stops opening the
 * mode — so the pool never gets the rounds that would have filled it. There
 * are already hundreds of real, hand-drawn words sitting in Firestore for
 * the online bot room; this lets that same data answer the empty-pool case,
 * and it costs one small document read to do it.
 *
 * These are a FALLBACK, never a preference: [GhostRunRepository.findOpponent]
 * reaches for one only after the real pool has been walked band by band and
 * come back with nobody. As real rounds accumulate they are offered less and
 * less often, without anything having to be switched off.
 *
 * The player each round is presented as comes from [GhostPersonas] — a
 * generated name and a level near the challenger's, not one fixed character.
 * A single recurring opponent is transparently not a pool, and the point of
 * this mode is to feel like there is one.
 *
 * ### Why the run id carries the whole round
 *
 * A real opponent's drawings live in `ghostRunItems/{runId}`, so the id is
 * enough to find them later. These do not exist as a round at all — they
 * are individual trained words — so the id has to carry what a stored
 * document would have: which words, and which roll of the dice. Everything
 * else is re-derived from those two, identically, on both sides.
 */
object BotGhostRuns {

    private const val ID_PREFIX = "ghost:"

    /**
     * How often she rides a correct answer's speed bonus. Same idea as
     * BotRoomEngine's, and the same reason: a bot whose score is always an
     * exact multiple of five is a bot.
     */
    private const val SPEED_BONUS_PERCENT = 40

    fun isBotRun(runId: String): Boolean = runId.startsWith(ID_PREFIX)

    fun idFor(seed: Long, wordIds: List<Int>): String =
        ID_PREFIX + seed + ":" + wordIds.joinToString(",")

    /** The word ids and dice roll packed into [idFor], or null if this is not one of hers. */
    fun parse(runId: String): Pair<Long, List<Int>>? {
        if (!isBotRun(runId)) return null
        val body = runId.removePrefix(ID_PREFIX)
        val seed = body.substringBefore(':', "").toLongOrNull() ?: return null
        val wordIds = body.substringAfter(':', "")
            .split(',')
            .map { it.toIntOrNull() ?: return null }
            .takeIf { it.isNotEmpty() }
            ?: return null
        return seed to wordIds
    }

    /**
     * Sude always DRAWS her trained strokes — that half is genuinely hers —
     * but she does not always recall her own drawing afterwards, exactly as
     * a real player forgets one of theirs.
     *
     * The distribution is deliberately harsher on her than BotRoomEngine's,
     * which was written for a ten-word round: over [GhostRuns.RUN_WORD_COUNT]
     * words, that one's 40% chance of a clean sweep would hand a beginner an
     * unbeatable score in four matches out of ten. A quick match is somebody's
     * first taste of playing against another person, and losing every time to
     * a perfect stranger is the version of this feature nobody plays twice.
     */
    fun outcomeFor(seed: Long, wordIds: List<Int>): BotGhostOutcome {
        // Seeded, so the offer screen and the result screen — which never
        // speak to each other — cannot disagree about what she scored.
        val random = Random(seed)
        val wrongCount = sampleWrongCount(random, wordIds.size)
        val wrongIndices = wordIds.indices.shuffled(random).take(wrongCount).toSet()
        val correctness = wordIds.indices.map { it !in wrongIndices }

        val correctCount = correctness.count { it }
        val speedBonuses = (0 until correctCount).count { random.nextInt(100) < SPEED_BONUS_PERCENT }
        return BotGhostOutcome(
            correctness = correctness,
            totalScore = correctCount * GameConstants.POINTS_CORRECT +
                speedBonuses * GameConstants.SPEED_BONUS_POINTS,
            correctCount = correctCount,
            fastestCorrectMs = if (correctCount > 0) random.nextLong(1_200, 3_501) else null
        )
    }

    private fun sampleWrongCount(random: Random, wordCount: Int): Int {
        val roll = random.nextInt(100)
        val target = when {
            roll < 15 -> 0
            roll < 50 -> 1
            roll < 80 -> 2
            else -> 3
        }
        return target.coerceAtMost(wordCount)
    }
}

/**
 * Who a synthesized round is presented as playing against.
 *
 * Every fallback round used to be the same character at the same level,
 * which reads as exactly what it was — one bot, over and over — and told
 * the player there was nobody else here. A pool has to look like a pool
 * before anyone believes it is worth adding to.
 *
 * Names are built rather than listed: two word tables and a few shapes give
 * thousands of combinations from a few dozen lines, and none of them can
 * collide with a real person the way a list of real first names could. They
 * are deliberately gamer handles, not names — a stranger who turns out to
 * be "MaviTilki42" is a player, while one called "Ayşe" invites the
 * question of who that is.
 *
 * Derived from the run's own seed, like the score, so a round is entirely
 * reproducible from its id.
 */
object GhostPersonas {

    /**
     * Offsets the seed away from the score's, so a name and a result drawn
     * from the same run are not two readings of one dice roll.
     */
    private const val NAME_SALT = 0x9E37_79B9L
    private const val LEVEL_SALT = 0x7F4A_7C15L

    /** How far from the challenger's own level an opponent may be drawn. */
    private const val LEVEL_SPREAD = 6

    private val PREFIXES = listOf(
        "Gece", "Kara", "Mavi", "Kızıl", "Altın", "Gümüş", "Yıldız", "Gölge",
        "Ateş", "Buz", "Fırtına", "Şimşek", "Sessiz", "Hızlı", "Deli", "Uçan",
        "Pixel", "Neon", "Turbo", "Mega", "Shadow", "Ghost", "Cyber", "Nova",
        "Retro", "Hyper", "Vega", "Zen"
    )

    private val ROOTS = listOf(
        "Kalem", "Çizer", "Avcı", "Kurt", "Kartal", "Usta", "Tilki", "Baykuş",
        "Ejder", "Balina", "Şahin", "Karga", "Fırça", "Silgi", "Panda", "Ninja",
        "Kaptan", "Ozan", "Pilot", "Rider", "Sniper", "Master", "Wolf", "Fox",
        "Hawk", "Kedi", "Ayı", "Kaplan"
    )

    fun nicknameFor(seed: Long): String {
        val random = Random(seed + NAME_SALT)
        val prefix = PREFIXES[random.nextInt(PREFIXES.size)]
        val root = ROOTS[random.nextInt(ROOTS.size)]
        return when (random.nextInt(3)) {
            // Locale.ROOT, not the device's: Turkish lowercasing maps I to ı,
            // so the default would give the same seed two different names
            // depending on which language the app happens to be in.
            0 -> "${prefix.lowercase(Locale.ROOT)}_${root.lowercase(Locale.ROOT)}"
            1 -> "$prefix$root${random.nextInt(2, 100)}"
            else -> "$prefix$root"
        }
    }

    /**
     * A level near the challenger's own.
     *
     * Unlike the lobby bot — who is one recognisable person across the whole
     * game and therefore fixed at BotRoomEngine.BOT_LEVEL — these are
     * strangers with no identity to keep consistent, so they can sit where a
     * real match would put them. Facing level 37 at level 3 was the single
     * clearest tell that the opponent was not drawn from any pool.
     */
    fun levelFor(seed: Long, challengerLevel: Int): Int {
        val random = Random(seed + LEVEL_SALT)
        val spread = random.nextInt(-LEVEL_SPREAD, LEVEL_SPREAD + 1)
        return (challengerLevel + spread).coerceIn(1, PlayerLevel.MAX_LEVEL)
    }
}
