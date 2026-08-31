package com.sualtikasifi.cizimhafiza.data.repository

import com.sualtikasifi.cizimhafiza.data.local.dao.AchievementDao
import com.sualtikasifi.cizimhafiza.data.local.dao.DrawingResultDao
import com.sualtikasifi.cizimhafiza.data.local.dao.GameSessionDao
import com.sualtikasifi.cizimhafiza.data.local.dao.WordDao
import com.sualtikasifi.cizimhafiza.data.local.entity.DrawingResultEntity
import com.sualtikasifi.cizimhafiza.data.local.entity.GameSessionEntity
import com.sualtikasifi.cizimhafiza.data.local.entity.UnlockedAchievementEntity
import com.sualtikasifi.cizimhafiza.data.local.entity.toDomain
import com.sualtikasifi.cizimhafiza.data.local.entity.WordEntity
import com.sualtikasifi.cizimhafiza.domain.model.Achievement
import com.sualtikasifi.cizimhafiza.domain.model.AchievementStats
import com.sualtikasifi.cizimhafiza.domain.model.XpAwards
import com.sualtikasifi.cizimhafiza.domain.model.Difficulty
import com.sualtikasifi.cizimhafiza.domain.model.DifficultyMix
import com.sualtikasifi.cizimhafiza.domain.model.DrawingResult
import com.sualtikasifi.cizimhafiza.domain.model.Word
import com.sualtikasifi.cizimhafiza.domain.repository.GameRepository
import com.sualtikasifi.cizimhafiza.util.GameConstants
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class GameRepositoryImpl @Inject constructor(
    private val wordDao: WordDao,
    private val gameSessionDao: GameSessionDao,
    private val drawingResultDao: DrawingResultDao,
    private val achievementDao: AchievementDao,
    private val settingsRepository: SettingsRepository
) : GameRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getCategories(): List<String> = wordDao.getCategories()

    override suspend fun getAllApprovedWords(): List<Word> =
        wordDao.getAllWordsOrderedByDifficulty().map(WordEntity::toDomain)

    override suspend fun getRandomWords(count: Int, category: String?, difficulty: Difficulty?): List<Word> {
        if (difficulty != null) {
            return wordDao.getRandomWords(count, category, difficulty.name).map(WordEntity::toDomain)
        }
        // "Tümü" (no specific difficulty): draw with a fixed EASY/MEDIUM/HARD
        // curve instead of pure uniform random, so a round can't luck into
        // being all-EASY or all-HARD — see DifficultyMix.
        return getRandomWordsMix(category, DifficultyMix.allDifficulties(count))
    }

    // A free-play round narrowed to one specific category draws independently
    // of every other round with no shared memory, so without this, the same
    // word can easily resurface a session or two later — worst for a thin
    // category (e.g. very few HARD words). Recently drawn words in that
    // category (from any past game) are excluded first; see
    // pickAvoidingRecent for the thin-pool fallback. A null [category] (every
    // level-map level, and free play's "Tümü") skips this entirely — the
    // combined pool across every category is large enough that repeats
    // aren't a practical concern the way one thin category was.
    override suspend fun getRandomWordsMix(category: String?, mix: Map<Difficulty, Int>): List<Word> {
        val recentIds = category
            ?.let { drawingResultDao.getRecentWordIds(it, GameConstants.RECENT_WORD_EXCLUSION_WINDOW) }
            .orEmpty()
        return mix.flatMap { (difficulty, n) -> pickAvoidingRecent(n, category, difficulty, recentIds) }
            .shuffled()
            .map(WordEntity::toDomain)
    }

    private suspend fun pickAvoidingRecent(
        limit: Int,
        category: String?,
        difficulty: Difficulty,
        recentIds: List<Int>
    ): List<WordEntity> {
        val avoiding = wordDao.getRandomWordsExcluding(limit, category, difficulty.name, recentIds)
        if (avoiding.size >= limit) return avoiding
        // This difficulty's pool is too thin to fill the level's quota
        // while avoiding recent words — top up from them instead of
        // shorting the level, but still never repeat a word within this
        // same level (excludes what avoiding already picked, not recentIds).
        val topUp = wordDao.getRandomWordsExcluding(
            limit - avoiding.size, category, difficulty.name, avoiding.map { it.id }
        )
        return avoiding + topUp
    }

    override suspend fun getWordsByIds(ids: List<Int>): List<Word> {
        val byId = wordDao.getWordsByIds(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it]?.toDomain() }
    }

    override suspend fun saveGame(totalScore: Int, results: List<DrawingResult>): List<Achievement> {
        val fastest = results.filter { it.isCorrect }.minOfOrNull { it.responseTimeMs }
        val correctCount = results.count { it.isCorrect }
        val sessionId = gameSessionDao.insert(
            GameSessionEntity(
                date = System.currentTimeMillis(),
                totalScore = totalScore,
                wordCount = results.size,
                correctCount = correctCount,
                fastestCorrectMs = fastest
            )
        )

        val entities = results.map { result ->
            DrawingResultEntity(
                sessionId = sessionId,
                wordId = result.wordId,
                pathDataJson = json.encodeToString(result.strokes),
                isCorrect = result.isCorrect,
                userAnswer = result.userAnswer,
                responseTimeMs = result.responseTimeMs,
                pointsAwarded = result.pointsAwarded
            )
        }
        drawingResultDao.insertAll(entities)
        gameSessionDao.pruneOlderThan(GameConstants.RECENT_GAMES_LIMIT)
        return finishSaving(
            totalScore = totalScore,
            wordCount = results.size,
            hadPerfectRound = results.isNotEmpty() && correctCount == results.size,
            isOnline = false,
            wasOnlineWin = false
        )
    }

    override suspend fun saveOnlineGameSession(
        totalScore: Int,
        wordCount: Int,
        correctCount: Int,
        fastestCorrectMs: Long?,
        placement: Int,
        playerCount: Int
    ): List<Achievement> {
        gameSessionDao.insert(
            GameSessionEntity(
                date = System.currentTimeMillis(),
                totalScore = totalScore,
                wordCount = wordCount,
                correctCount = correctCount,
                fastestCorrectMs = fastestCorrectMs,
                placement = placement,
                playerCount = playerCount
            )
        )
        gameSessionDao.pruneOlderThan(GameConstants.RECENT_GAMES_LIMIT)
        return finishSaving(
            totalScore = totalScore,
            wordCount = wordCount,
            hadPerfectRound = wordCount > 0 && correctCount == wordCount,
            isOnline = true,
            wasOnlineWin = placement == 1
        )
    }

    // Shared tail of both save paths: durable lifetime-counter bookkeeping,
    // then checking those counters against the achievement catalog for
    // anything newly earned. Kept here (not a separate use case) since it's
    // the exact same "settings bookkeeping on save" pattern addScore/
    // updateStreakOnPlay already established in this repository.
    private suspend fun finishSaving(
        totalScore: Int,
        wordCount: Int,
        hadPerfectRound: Boolean,
        isOnline: Boolean,
        wasOnlineWin: Boolean
    ): List<Achievement> {
        settingsRepository.addScore(totalScore)
        settingsRepository.addWordsDrawn(wordCount)
        settingsRepository.updateStreakOnPlay()
        settingsRepository.recordFinishedGame(wasPerfectRound = hadPerfectRound, wasOnlineWin = wasOnlineWin)

        // Only the online match/win bonus lives here — the per-word XP for
        // every mode (solo, level-map, online) is granted live the instant
        // each word is scored (see GameViewModel/OnlineGameViewModel.
        // submitGuess), so the level bar visibly moves during play instead
        // of jumping once the round is already over. Paying it again here
        // would double it. The daily challenge is the one mode that skips
        // the live per-word grant entirely, paying its own completion+streak
        // total instead (see GameViewModel.finishGame) — same reasoning,
        // just a different single place to avoid double-paying.
        if (isOnline) {
            settingsRepository.addXp(XpAwards.ONLINE_MATCH + if (wasOnlineWin) XpAwards.ONLINE_WIN else 0)
        }

        val stats = AchievementStats(
            gamesPlayed = settingsRepository.lifetimeGamesPlayed,
            lifetimeWordsDrawn = settingsRepository.lifetimeWordsDrawn.value,
            lifetimeScore = settingsRepository.lifetimeScore.value,
            currentStreak = settingsRepository.currentStreak,
            bestStreak = settingsRepository.bestStreak,
            perfectRounds = settingsRepository.lifetimePerfectRounds,
            onlineWins = settingsRepository.lifetimeOnlineWins
        )
        val alreadyUnlocked = achievementDao.getUnlockedIds().toSet()
        val newlyUnlocked = Achievement.entries.filter { it.name !in alreadyUnlocked && it.isUnlocked(stats) }
        newlyUnlocked.forEach { achievement ->
            achievementDao.insert(UnlockedAchievementEntity(id = achievement.name, unlockedAtMillis = System.currentTimeMillis()))
        }
        settingsRepository.addXp(newlyUnlocked.sumOf { it.xpReward })
        return newlyUnlocked
    }
}
