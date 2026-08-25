package com.sualtikasifi.cizimhafiza.presentation.game

import android.app.Activity
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.ads.AdManager
import com.sualtikasifi.cizimhafiza.data.local.WordSeeder
import com.sualtikasifi.cizimhafiza.domain.model.Difficulty
import com.sualtikasifi.cizimhafiza.domain.model.DrawingResult
import com.sualtikasifi.cizimhafiza.domain.model.DrawingStroke
import com.sualtikasifi.cizimhafiza.domain.model.GameMode
import com.sualtikasifi.cizimhafiza.domain.model.LevelCatalog
import com.sualtikasifi.cizimhafiza.domain.model.ResultItem
import com.sualtikasifi.cizimhafiza.domain.model.Word
import com.sualtikasifi.cizimhafiza.domain.model.World
import com.sualtikasifi.cizimhafiza.domain.repository.LevelProgressRepository
import com.sualtikasifi.cizimhafiza.domain.usecase.GetWordsForGameUseCase
import com.sualtikasifi.cizimhafiza.domain.usecase.SaveGameSessionUseCase
import com.sualtikasifi.cizimhafiza.domain.usecase.SubmitGuessUseCase
import com.sualtikasifi.cizimhafiza.presentation.navigation.Screen
import com.sualtikasifi.cizimhafiza.util.AnswerMatcher
import com.sualtikasifi.cizimhafiza.util.GameConstants
import com.sualtikasifi.cizimhafiza.util.SoundManager
import com.sualtikasifi.cizimhafiza.util.VibratorHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getWordsForGameUseCase: GetWordsForGameUseCase,
    private val submitGuessUseCase: SubmitGuessUseCase,
    private val saveGameSessionUseCase: SaveGameSessionUseCase,
    private val levelProgressRepository: LevelProgressRepository,
    private val vibratorHelper: VibratorHelper,
    private val soundManager: SoundManager,
    private val adManager: AdManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val wordCount: Int = savedStateHandle.get<String>(Screen.ArgWordCount)?.toIntOrNull() ?: 10
    private val category: String? = savedStateHandle.get<String>(Screen.ArgCategory)
        ?.takeUnless { it == Screen.AllCategoriesArg }
    private val difficulty: Difficulty? = savedStateHandle.get<String>(Screen.ArgDifficulty)
        ?.takeUnless { it == Screen.AllDifficultiesArg }
        ?.let { runCatching { Difficulty.valueOf(it) }.getOrNull() }
    private val mode: GameMode = savedStateHandle.get<String>(Screen.ArgMode)
        ?.let { runCatching { GameMode.valueOf(it) }.getOrNull() }
        ?: GameMode.NORMAL
    private val worldId: Int? = savedStateHandle.get<String>(Screen.ArgWorldId)?.toIntOrNull()
    private val levelIndex: Int? = savedStateHandle.get<String>(Screen.ArgLevelIndex)?.toIntOrNull()

    private val _phase = MutableStateFlow<GamePhase>(GamePhase.Loading)
    val phase: StateFlow<GamePhase> = _phase.asStateFlow()

    private var words: List<Word> = emptyList()
    private var drawingIndex = 0
    private val results = mutableListOf<DrawingResult>()
    private var currentStrokes = mutableListOf<DrawingStroke>()

    // Latest in-progress (not-yet-lifted-finger) stroke, reported live by
    // DrawableCanvas. Folded into the word's saved strokes if the timer
    // expires (or "next word" is tapped) mid-drag, so nothing drawn is lost.
    private var pendingStroke: DrawingStroke = emptyList()

    private var guessOrder: List<Int> = emptyList()
    private var guessPos = 0
    private var guessShownAtMillis = 0L

    // One rewarded-ad hint per whole match (not per word) — see useHint().
    private var hintUsedThisMatch = false
    private var revealedHintLetter: String? = null
    // Grows by HINT_BONUS_SECONDS the moment a hint is earned, so the ring's
    // secondsLeft/totalSeconds stay proportionate instead of overshooting 100%.
    private var currentGuessTotal = GameConstants.GUESS_DURATION_SECONDS

    // Separate one-per-match rewarded-ad budget for the drawing phase — see
    // useDrawingHint(). Independent of hintUsedThisMatch above.
    private var drawingHintUsedThisMatch = false
    private var currentDrawingTotal = 0

    private var timerJob: Job? = null

    init {
        startSession()
    }

    /**
     * Loads this session's word list. Level-map sessions and free-play
     * sessions ask for completely different things, so this is the single
     * place that decides which — [restart] goes through it too, or replaying
     * a level would silently fall back to the free-play query and hand the
     * player a different set of words than the level actually specifies.
     */
    private suspend fun loadWords(): List<Word> =
        if (worldId != null && levelIndex != null) {
            // The route's path-encoded category/difficulty/wordCount are
            // placeholders (a level can be a two-difficulty mix, which can't be
            // represented as a single Difficulty path segment) — the real,
            // authoritative config is always recomputed from worldId+levelIndex.
            // config.category is the route's Turkish placeholder value (see
            // Screen.levelGameRoute) — the `words` table's category column is
            // re-seeded per-language (see WordPoolSynchronizer), so the actual
            // query must use World.categoryFor(currentLanguage), not that
            // placeholder, or an English-language session would either match
            // zero rows or (worse, if a re-seed hadn't run yet) silently pull
            // Turkish-language words into an English game.
            val config = LevelCatalog.levelConfig(worldId, levelIndex)
            val language = WordSeeder.currentLanguage(context)
            val levelCategory = World.forId(worldId)?.categoryFor(language) ?: config.category
            getWordsForGameUseCase(levelCategory, config.difficultyMix)
        } else {
            getWordsForGameUseCase(wordCount, category, difficulty)
        }

    private fun startSession() {
        viewModelScope.launch {
            words = runCatching { loadWords() }.getOrDefault(emptyList())
            if (words.isEmpty()) {
                // No words matched (an over-narrow filter, or a word pool
                // still mid-reseed): fall straight through to an empty result
                // rather than sitting on the loading spinner forever.
                _phase.value = GamePhase.Result(0, 0, 0, null, emptyList())
            } else {
                runDrawingTurn()
            }
        }
    }

    // --- Drawing phase ---

    // wordId is the id the originating DrawableCanvas instance was created
    // for (see DrawingScreen's key(state.word.id) block) — a stale callback
    // from a canvas Compose hasn't torn down yet (the user's finger was
    // still down when the turn advanced) would otherwise silently attach a
    // leftover drag to whichever word is now current. Ignoring anything
    // that doesn't match the actual current word closes that race.
    fun onStrokeFinished(wordId: Int, stroke: DrawingStroke) {
        if (words.getOrNull(drawingIndex)?.id != wordId) return
        currentStrokes.add(stroke)
        pendingStroke = emptyList()
        (_phase.value as? GamePhase.Drawing)?.let { current ->
            _phase.value = current.copy(strokes = currentStrokes.toList())
        }
    }

    fun onStrokeProgress(wordId: Int, points: DrawingStroke) {
        if (words.getOrNull(drawingIndex)?.id != wordId) return
        pendingStroke = points
    }

    fun onClearCanvas() {
        currentStrokes.clear()
        pendingStroke = emptyList()
        (_phase.value as? GamePhase.Drawing)?.let { current ->
            _phase.value = current.copy(strokes = emptyList())
        }
    }

    /** Eraser tool: removes one whole stroke the player dragged over (see DrawableCanvas). */
    fun onEraseStroke(stroke: DrawingStroke) {
        if (!currentStrokes.remove(stroke)) return
        (_phase.value as? GamePhase.Drawing)?.let { current ->
            _phase.value = current.copy(strokes = currentStrokes.toList())
        }
    }

    fun onUndoLastStroke() {
        if (currentStrokes.isEmpty()) return
        currentStrokes.removeAt(currentStrokes.lastIndex)
        (_phase.value as? GamePhase.Drawing)?.let { current ->
            _phase.value = current.copy(strokes = currentStrokes.toList())
        }
    }

    private fun runDrawingTurn() {
        timerJob?.cancel()
        currentStrokes = mutableListOf()
        pendingStroke = emptyList()
        val word = words[drawingIndex]

        if (mode == GameMode.RELAXED) {
            // No countdown at all — just show the word and wait for
            // advanceRelaxedDrawing() (triggered by a "next word" button).
            _phase.value = GamePhase.Drawing(
                word = word,
                wordNumber = drawingIndex + 1,
                totalWords = words.size,
                secondsLeft = 0,
                totalSeconds = 0,
                isWarning = false,
                strokes = currentStrokes.toList(),
                isUntimed = true
            )
            return
        }

        currentDrawingTotal = GameConstants.drawingDurationSeconds(word.difficulty)
        runDrawingCountdown(word, startSecondsLeft = currentDrawingTotal)
    }

    /**
     * Runs (or resumes) the drawing countdown from [startSecondsLeft] down
     * to 1. Split out from [runDrawingTurn] so [useDrawingHint] can pause
     * this loop for the whole rewarded-ad flow and resume it afterward —
     * same reasoning as [runGuessCountdown] for the guessing phase.
     */
    private fun runDrawingCountdown(word: Word, startSecondsLeft: Int) {
        timerJob?.cancel()
        // Sum of every remaining word's OWN drawing time (not this word's
        // break/guess phases, and not later words' break/guess either) —
        // the header clock counts down to when the last drawing finishes,
        // not to when the whole match (guessing included) finishes. Fixed
        // regardless of this word's own bonus: matchSecondsRemaining is
        // derived from secondsLeft below, so a drawing-hint bonus flows
        // through automatically without adjusting this sum separately.
        val laterWordsSeconds = words.drop(drawingIndex + 1).sumOf { GameConstants.drawingDurationSeconds(it.difficulty) }

        timerJob = viewModelScope.launch {
            for (secondsLeft in startSecondsLeft downTo 1) {
                val isWarning = secondsLeft <= GameConstants.WARNING_THRESHOLD_SECONDS
                if (isWarning && secondsLeft == GameConstants.WARNING_THRESHOLD_SECONDS) {
                    vibratorHelper.vibrateCountdownWarning()
                    soundManager.playCountdownTick()
                }
                _phase.value = GamePhase.Drawing(
                    word = word,
                    wordNumber = drawingIndex + 1,
                    totalWords = words.size,
                    secondsLeft = secondsLeft,
                    totalSeconds = currentDrawingTotal,
                    isWarning = isWarning,
                    strokes = currentStrokes.toList(),
                    matchSecondsRemaining = secondsLeft + laterWordsSeconds,
                    hintUsed = drawingHintUsedThisMatch
                )
                delay(1_000)
            }
            finishDrawingTurn(word)
        }
    }

    /**
     * Watches a rewarded ad for this match's one-time "+time" drawing hint —
     * a separate budget from [useHint]'s guessing-phase letter reveal.
     * Pauses the drawing countdown for the whole ad flow, then resumes with
     * a +[GameConstants.DRAWING_TIME_BONUS_SECONDS] bonus if it was watched.
     */
    fun useDrawingHint(activity: Activity) {
        if (drawingHintUsedThisMatch) return
        val current = _phase.value as? GamePhase.Drawing ?: return
        if (current.isUntimed) return // RELAXED mode has no timer to extend
        timerJob?.cancel()
        val pausedSecondsLeft = current.secondsLeft
        val word = words[drawingIndex]
        adManager.maybeShowRewarded(activity) { earned ->
            if (earned) {
                drawingHintUsedThisMatch = true
                currentDrawingTotal += GameConstants.DRAWING_TIME_BONUS_SECONDS
                runDrawingCountdown(word, startSecondsLeft = pausedSecondsLeft + GameConstants.DRAWING_TIME_BONUS_SECONDS)
            } else {
                runDrawingCountdown(word, startSecondsLeft = pausedSecondsLeft)
            }
        }
    }

    /** RELAXED mode only: called when the user taps "next word" instead of a timer expiring. */
    fun advanceRelaxedDrawing() {
        val current = _phase.value as? GamePhase.Drawing ?: return
        if (!current.isUntimed) return
        finishDrawingTurn(current.word)
    }

    private fun finishDrawingTurn(word: Word) {
        // Fold in whatever was mid-stroke (finger still down) at the exact
        // moment the turn ended, so a timeout mid-drag doesn't lose that
        // partial line or leave it to bleed into the next word's canvas.
        val finalStrokes = currentStrokes.toList() +
            listOfNotNull(pendingStroke.takeIf { it.size >= 2 })
        pendingStroke = emptyList()

        results.add(
            DrawingResult(
                sessionId = 0L,
                wordId = word.id,
                word = word,
                strokes = finalStrokes
            )
        )
        drawingIndex++
        if (drawingIndex < words.size) {
            runDrawingTurn()
        } else {
            runBreak()
        }
    }

    // --- Break phase ---

    private fun runBreak() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val total = GameConstants.BREAK_DURATION_SECONDS
            for (secondsLeft in total downTo 1) {
                _phase.value = GamePhase.Break(secondsLeft = secondsLeft, totalSeconds = total)
                delay(1_000)
            }
            startGuessPhase()
        }
    }

    // --- Guessing phase ---

    private fun startGuessPhase() {
        guessOrder = results.indices.shuffled()
        guessPos = 0
        showCurrentGuess()
    }

    private fun showCurrentGuess() {
        guessShownAtMillis = System.currentTimeMillis()
        revealedHintLetter = null
        currentGuessTotal = GameConstants.GUESS_DURATION_SECONDS
        runGuessCountdown(startSecondsLeft = currentGuessTotal)
    }

    /**
     * Runs (or resumes) the guess countdown from [startSecondsLeft] down to 1.
     * Split out from [showCurrentGuess] so [useHint] can pause this loop for
     * the whole rewarded-ad flow and resume it afterward — without this, the
     * countdown kept ticking underneath the ad and the word had already
     * moved on by the time the player got back, making the hint pointless.
     */
    private fun runGuessCountdown(startSecondsLeft: Int) {
        timerJob?.cancel()
        val result = results[guessOrder[guessPos]]
        timerJob = viewModelScope.launch {
            for (secondsLeft in startSecondsLeft downTo 1) {
                val isWarning = secondsLeft <= GameConstants.WARNING_THRESHOLD_SECONDS
                if (isWarning && secondsLeft == GameConstants.WARNING_THRESHOLD_SECONDS) {
                    vibratorHelper.vibrateCountdownWarning()
                    soundManager.playCountdownTick()
                }
                _phase.value = GamePhase.Guessing(
                    guessNumber = guessPos + 1,
                    totalGuesses = results.size,
                    strokes = result.strokes,
                    feedback = null,
                    secondsLeft = secondsLeft,
                    totalSeconds = currentGuessTotal,
                    isWarning = isWarning,
                    hintUsed = hintUsedThisMatch,
                    hintLetter = revealedHintLetter
                )
                delay(1_000)
            }
            submitGuess("") // time's up — counts the same as tapping "Atla"
        }
    }

    /**
     * Watches a rewarded ad for this match's one-time hint: the current
     * word's first letter. The countdown is paused (not just visually — the
     * timer coroutine itself is cancelled) the instant this is called, for
     * the whole ad load+watch, and only resumes once the ad flow is fully
     * done — plus a +[GameConstants.HINT_BONUS_SECONDS] bonus if the ad was
     * actually watched, so getting the hint at the last second is still
     * useful instead of an instant auto-skip.
     */
    fun useHint(activity: Activity) {
        if (hintUsedThisMatch) return
        val current = _phase.value as? GamePhase.Guessing ?: return
        if (current.feedback != null) return // already answered
        timerJob?.cancel()
        val pausedSecondsLeft = current.secondsLeft
        adManager.maybeShowRewarded(activity) { earned ->
            if (earned) {
                hintUsedThisMatch = true
                revealedHintLetter = results[guessOrder[guessPos]].word.text.take(1)
                currentGuessTotal += GameConstants.HINT_BONUS_SECONDS
                runGuessCountdown(startSecondsLeft = pausedSecondsLeft + GameConstants.HINT_BONUS_SECONDS)
            } else {
                runGuessCountdown(startSecondsLeft = pausedSecondsLeft)
            }
        }
    }

    /** Called on every keystroke; auto-submits the moment the typed text exactly matches the word. */
    fun onAnswerChanged(text: String) {
        val current = _phase.value as? GamePhase.Guessing ?: return
        if (current.feedback != null) return
        if (text.isBlank()) return
        val result = results[guessOrder[guessPos]]
        if (AnswerMatcher.normalize(text) == AnswerMatcher.normalize(result.word.text)) {
            submitGuess(text)
        }
    }

    fun submitGuess(answer: String) {
        val current = _phase.value as? GamePhase.Guessing ?: return
        if (current.feedback != null) return // already answered (guards a timeout/manual-submit race)
        timerJob?.cancel()
        val responseTimeMs = System.currentTimeMillis() - guessShownAtMillis
        val result = results[guessOrder[guessPos]]
        val outcome = submitGuessUseCase(answer, result.word.text, responseTimeMs)

        result.userAnswer = answer
        result.isCorrect = outcome.isCorrect
        result.responseTimeMs = responseTimeMs
        result.pointsAwarded = outcome.pointsAwarded

        _phase.value = current.copy(
            feedback = GuessFeedback(isCorrect = outcome.isCorrect, correctAnswer = result.word.text)
        )

        if (outcome.isCorrect) {
            soundManager.playCorrectGuess()
            vibratorHelper.vibrateSuccess()
        } else {
            soundManager.playWrongGuess()
            vibratorHelper.vibrateError()
        }

        viewModelScope.launch {
            delay(1_200) // let the correct/wrong feedback animation show briefly
            guessPos++
            if (guessPos < guessOrder.size) {
                showCurrentGuess()
            } else {
                finishGame()
            }
        }
    }

    // --- Result phase ---

    private suspend fun finishGame() {
        val totalScore = results.sumOf { it.pointsAwarded }
        // Return value (newly unlocked achievements) isn't needed here — the
        // MainMenu badge + StatisticsScreen shimmer read persisted
        // unseen/seen state straight from AchievementDao instead of a
        // per-match snapshot (see StatisticsViewModel).
        saveGameSessionUseCase(results)
        soundManager.playGameOver()

        val correctCount = results.count { it.isCorrect }
        val stars = if (worldId != null && levelIndex != null) {
            levelProgressRepository.recordLevelResult(
                worldId = worldId,
                levelIndex = levelIndex,
                correctCount = correctCount,
                totalWords = results.size,
                score = totalScore
            )
        } else null

        val fastest = results.filter { it.isCorrect }.minOfOrNull { it.responseTimeMs }
        _phase.value = GamePhase.Result(
            totalScore = totalScore,
            correctCount = correctCount,
            wrongCount = results.count { !it.isCorrect },
            fastestCorrectSeconds = fastest?.let { it / 1000.0 },
            items = results.map { ResultItem(it.word.text, it.isCorrect, it.strokes) },
            levelStars = stars
        )
    }

    /** Called once when the Result screen appears — see AdManager's placement doc. */
    fun showResultInterstitial(activity: Activity, onDismissed: () -> Unit = {}) {
        adManager.maybeShowInterstitial(activity, onDismissed)
    }

    fun restart() {
        timerJob?.cancel()
        drawingIndex = 0
        results.clear()
        currentStrokes = mutableListOf()
        pendingStroke = emptyList()
        guessOrder = emptyList()
        guessPos = 0
        hintUsedThisMatch = false
        revealedHintLetter = null
        currentGuessTotal = GameConstants.GUESS_DURATION_SECONDS
        drawingHintUsedThisMatch = false
        currentDrawingTotal = 0
        _phase.value = GamePhase.Loading
        startSession()
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}
