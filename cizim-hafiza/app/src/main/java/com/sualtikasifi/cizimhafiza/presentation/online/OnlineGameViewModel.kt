package com.sualtikasifi.cizimhafiza.presentation.online

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.ads.AdManager
import com.sualtikasifi.cizimhafiza.data.bot.BotRoomEngine
import com.sualtikasifi.cizimhafiza.domain.model.AvatarFrame
import com.sualtikasifi.cizimhafiza.domain.model.DrawingResult
import com.sualtikasifi.cizimhafiza.domain.model.DrawingStroke
import com.sualtikasifi.cizimhafiza.domain.model.ResultItem
import com.sualtikasifi.cizimhafiza.domain.model.Word
import com.sualtikasifi.cizimhafiza.domain.repository.OnlineGameRepository
import com.sualtikasifi.cizimhafiza.domain.usecase.GetWordsByIdsUseCase
import com.sualtikasifi.cizimhafiza.domain.model.LevelProgressState
import com.sualtikasifi.cizimhafiza.domain.usecase.SubmitGuessUseCase
import com.sualtikasifi.cizimhafiza.presentation.game.GamePhase
import com.sualtikasifi.cizimhafiza.presentation.game.GuessFeedback
import com.sualtikasifi.cizimhafiza.presentation.navigation.Screen
import com.sualtikasifi.cizimhafiza.util.AnswerMatcher
import com.sualtikasifi.cizimhafiza.util.GameConstants
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import com.sualtikasifi.cizimhafiza.util.SoundManager
import com.sualtikasifi.cizimhafiza.util.VibratorHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Mirrors [com.sualtikasifi.cizimhafiza.presentation.game.GameViewModel]'s
 * Drawing→Break→Guessing timer/state-machine logic (kept as a parallel
 * implementation rather than a shared refactor — this repo has no way to
 * run either flow live in this environment, so the safer choice is leaving
 * the already-stable single-player ViewModel untouched). The two
 * differences: the word list comes from the room's shared wordIds instead
 * of a random draw, and finishing submits the result to Firestore instead
 * of the local Room database.
 */
@HiltViewModel
class OnlineGameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val onlineGameRepository: OnlineGameRepository,
    private val getWordsByIdsUseCase: GetWordsByIdsUseCase,
    private val submitGuessUseCase: SubmitGuessUseCase,
    private val vibratorHelper: VibratorHelper,
    private val soundManager: SoundManager,
    private val adManager: AdManager,
    private val settingsRepository: SettingsRepository,
    botRoomEngine: BotRoomEngine
) : ViewModel() {

    val roomCode: String = checkNotNull(savedStateHandle[Screen.ArgRoomCode])

    init {
        // Harmless/no-op for any other room — only ever drives room 130246
        // (see BotRoomEngine) and only starts its listener once per process.
        botRoomEngine.ensureRunning()
    }

    private val _phase = MutableStateFlow<GamePhase>(GamePhase.Loading)
    val phase: StateFlow<GamePhase> = _phase.asStateFlow()

    // Same live badge as the solo GameViewModel's — see its levelProgress
    // for why this is just an observation of lifetimeXp rather than a
    // separately tracked "session" total.
    val levelProgress: StateFlow<LevelProgressState> = settingsRepository.lifetimeXp
        .map { LevelProgressState.forXp(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LevelProgressState.forXp(settingsRepository.lifetimeXp.value)
        )

    // Same reasoning as GameViewModel.selectedFrame — the player's own chosen
    // ring, for the live badge during an online match.
    val selectedFrame: StateFlow<AvatarFrame> = combine(
        settingsRepository.selectedAvatarFrameId,
        settingsRepository.lifetimeXp
    ) { selectedId, xp -> AvatarFrame.resolve(selectedId, LevelProgressState.forXp(xp).level) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AvatarFrame.resolve(
                settingsRepository.selectedAvatarFrameId.value,
                LevelProgressState.forXp(settingsRepository.lifetimeXp.value).level
            )
        )

    // Non-null while showing the "3, 2, 1…" countdown before the first
    // Drawing phase — both on the very first match and on every rematch,
    // since a rematch gets a brand new OnlineGameViewModel just like the
    // first game does. Kept separate from GamePhase (rather than adding a
    // new case there) so the single-player GameViewModel/GameScreen, which
    // shares that sealed interface, doesn't have to care about it.
    private val _startCountdown = MutableStateFlow<Int?>(GameConstants.ONLINE_START_COUNTDOWN_SECONDS)
    val startCountdown: StateFlow<Int?> = _startCountdown.asStateFlow()

    private var words: List<Word> = emptyList()
    private var drawingIndex = 0
    private val results = mutableListOf<DrawingResult>()
    private var currentStrokes = mutableListOf<DrawingStroke>()
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
        viewModelScope.launch {
            // observeRoom's Flow closes with an exception on a Firestore
            // listener error (permission issue, room deleted, etc.) — left
            // unguarded, that would crash the app right as a match is about
            // to start. Falls back to the same "no words" empty-result path
            // already used a few lines below for a genuinely empty room.
            val room = runCatching {
                onlineGameRepository.observeRoom(roomCode)
                    .filterNotNull()
                    .first { it.wordIds.isNotEmpty() }
            }.getOrNull()
            words = room?.let { getWordsByIdsUseCase(it.wordIds) } ?: emptyList()
            if (words.isEmpty()) {
                _startCountdown.value = null
                finishAndSubmit()
            } else {
                for (secondsLeft in GameConstants.ONLINE_START_COUNTDOWN_SECONDS downTo 1) {
                    _startCountdown.value = secondsLeft
                    delay(1_000)
                }
                _startCountdown.value = null
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
        currentStrokes = mutableListOf()
        pendingStroke = emptyList()
        val word = words[drawingIndex]
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

    private fun finishDrawingTurn(word: Word) {
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
            submitGuess("")
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
        if (current.feedback != null) return
        timerJob?.cancel()
        val responseTimeMs = System.currentTimeMillis() - guessShownAtMillis
        val result = results[guessOrder[guessPos]]
        val outcome = submitGuessUseCase(answer, result.word.text, responseTimeMs, result.word.difficulty)

        result.userAnswer = answer
        result.isCorrect = outcome.isCorrect
        result.responseTimeMs = responseTimeMs
        result.pointsAwarded = outcome.pointsAwarded

        // Granted immediately, same reasoning as the solo GameViewModel:
        // the room's match-completion/win bonus is added separately once
        // the round ends (see GameRepositoryImpl.finishSaving), so this is
        // on top of it, not instead of it.
        val liveXp = if (outcome.isCorrect) outcome.xpAwarded else 0
        if (liveXp > 0) settingsRepository.addXp(liveXp)

        _phase.value = current.copy(
            feedback = GuessFeedback(isCorrect = outcome.isCorrect, correctAnswer = result.word.text, xpAwarded = liveXp)
        )

        if (outcome.isCorrect) {
            soundManager.playCorrectGuess()
            vibratorHelper.vibrateSuccess()
        } else {
            soundManager.playWrongGuess()
            vibratorHelper.vibrateError()
        }

        viewModelScope.launch {
            delay(1_200)
            guessPos++
            if (guessPos < guessOrder.size) {
                showCurrentGuess()
            } else {
                finishAndSubmit()
            }
        }
    }

    // --- Finish: submit to the shared room instead of local Room storage ---

    private suspend fun finishAndSubmit() {
        val totalScore = results.sumOf { it.pointsAwarded }
        soundManager.playGameOver()
        val fastest = results.filter { it.isCorrect }.minOfOrNull { it.responseTimeMs }
        val correctCount = results.count { it.isCorrect }
        val wrongCount = results.count { !it.isCorrect }
        val items = results.map { ResultItem(it.word.text, it.isCorrect, it.strokes) }

        // Submitted BEFORE the phase flips to Result: OnlineGameScreen
        // navigates away (and this ViewModel gets cleared, cancelling its
        // viewModelScope) the instant it observes GamePhase.Result. If the
        // phase changed first, the still-in-flight Firestore write for the
        // drawings (results/{uid}) got cut off mid-call — the room doc's
        // score fields (a separate, faster write) landed fine, but the
        // drawings never did, leaving the comparison screen's galleries
        // empty even though the scores showed up correctly.
        runCatching {
            onlineGameRepository.submitResult(
                roomCode = roomCode,
                totalScore = totalScore,
                correctCount = correctCount,
                wrongCount = wrongCount,
                fastestCorrectMs = fastest,
                items = items
            )
        }

        _phase.value = GamePhase.Result(
            totalScore = totalScore,
            correctCount = correctCount,
            wrongCount = wrongCount,
            fastestCorrectSeconds = fastest?.let { it / 1000.0 },
            items = items
        )
    }

    /** Called when the player confirms exiting mid-match (see OnlineGameScreen's exit-confirm dialog). */
    fun leaveRoom() {
        viewModelScope.launch { runCatching { onlineGameRepository.leaveRoom(roomCode) } }
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}
