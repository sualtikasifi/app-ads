package com.sualtikasifi.cizimhafiza.presentation.online

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.domain.model.DrawingResult
import com.sualtikasifi.cizimhafiza.domain.model.DrawingStroke
import com.sualtikasifi.cizimhafiza.domain.model.ResultItem
import com.sualtikasifi.cizimhafiza.domain.model.Word
import com.sualtikasifi.cizimhafiza.domain.repository.OnlineGameRepository
import com.sualtikasifi.cizimhafiza.domain.usecase.GetWordsByIdsUseCase
import com.sualtikasifi.cizimhafiza.domain.usecase.SubmitGuessUseCase
import com.sualtikasifi.cizimhafiza.presentation.game.GamePhase
import com.sualtikasifi.cizimhafiza.presentation.game.GuessFeedback
import com.sualtikasifi.cizimhafiza.presentation.navigation.Screen
import com.sualtikasifi.cizimhafiza.util.AnswerMatcher
import com.sualtikasifi.cizimhafiza.util.GameConstants
import com.sualtikasifi.cizimhafiza.util.SoundManager
import com.sualtikasifi.cizimhafiza.util.VibratorHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val soundManager: SoundManager
) : ViewModel() {

    val roomCode: String = checkNotNull(savedStateHandle[Screen.ArgRoomCode])

    private val _phase = MutableStateFlow<GamePhase>(GamePhase.Loading)
    val phase: StateFlow<GamePhase> = _phase.asStateFlow()

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

    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            val room = onlineGameRepository.observeRoom(roomCode)
                .filterNotNull()
                .first { it.wordIds.isNotEmpty() }
            words = getWordsByIdsUseCase(room.wordIds)
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

    private fun runDrawingTurn() {
        timerJob?.cancel()
        currentStrokes = mutableListOf()
        pendingStroke = emptyList()
        val word = words[drawingIndex]
        val totalSeconds = GameConstants.drawingDurationSeconds(word.difficulty)

        timerJob = viewModelScope.launch {
            for (secondsLeft in totalSeconds downTo 1) {
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
                    totalSeconds = totalSeconds,
                    isWarning = isWarning,
                    strokes = currentStrokes.toList()
                )
                delay(1_000)
            }
            finishDrawingTurn(word)
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
        timerJob?.cancel()
        guessShownAtMillis = System.currentTimeMillis()
        val result = results[guessOrder[guessPos]]
        val total = GameConstants.GUESS_DURATION_SECONDS

        timerJob = viewModelScope.launch {
            for (secondsLeft in total downTo 1) {
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
                    totalSeconds = total,
                    isWarning = isWarning
                )
                delay(1_000)
            }
            submitGuess("")
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

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}
