package com.sualtikasifi.cizimhafiza.presentation.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.domain.model.DrawingResult
import com.sualtikasifi.cizimhafiza.domain.model.DrawingStroke
import com.sualtikasifi.cizimhafiza.domain.model.Word
import com.sualtikasifi.cizimhafiza.domain.usecase.GetWordsForGameUseCase
import com.sualtikasifi.cizimhafiza.domain.usecase.SaveGameSessionUseCase
import com.sualtikasifi.cizimhafiza.domain.usecase.SubmitGuessUseCase
import com.sualtikasifi.cizimhafiza.presentation.navigation.Screen
import com.sualtikasifi.cizimhafiza.util.GameConstants
import com.sualtikasifi.cizimhafiza.util.VibratorHelper
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val vibratorHelper: VibratorHelper
) : ViewModel() {

    private val wordCount: Int = savedStateHandle.get<String>(Screen.ArgWordCount)?.toIntOrNull() ?: 10
    private val category: String? = savedStateHandle.get<String>(Screen.ArgCategory)
        ?.takeUnless { it == Screen.AllCategoriesArg }

    private val _phase = MutableStateFlow<GamePhase>(GamePhase.Loading)
    val phase: StateFlow<GamePhase> = _phase.asStateFlow()

    private var words: List<Word> = emptyList()
    private var drawingIndex = 0
    private val results = mutableListOf<DrawingResult>()
    private var currentStrokes = mutableListOf<DrawingStroke>()

    private var guessOrder: List<Int> = emptyList()
    private var guessPos = 0
    private var guessShownAtMillis = 0L

    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            words = getWordsForGameUseCase(wordCount, category)
            if (words.isEmpty()) {
                _phase.value = GamePhase.Result(0, 0, 0, null, emptyList())
            } else {
                runDrawingTurn()
            }
        }
    }

    // --- Drawing phase ---

    fun onStrokeFinished(stroke: DrawingStroke) {
        currentStrokes.add(stroke)
        (_phase.value as? GamePhase.Drawing)?.let { current ->
            _phase.value = current.copy(strokes = currentStrokes.toList())
        }
    }

    fun onClearCanvas() {
        currentStrokes.clear()
        (_phase.value as? GamePhase.Drawing)?.let { current ->
            _phase.value = current.copy(strokes = emptyList())
        }
    }

    private fun runDrawingTurn() {
        timerJob?.cancel()
        currentStrokes = mutableListOf()
        val word = words[drawingIndex]
        val totalSeconds = GameConstants.drawingDurationSeconds(word.difficulty)

        timerJob = viewModelScope.launch {
            for (secondsLeft in totalSeconds downTo 1) {
                val isWarning = secondsLeft <= GameConstants.WARNING_THRESHOLD_SECONDS
                if (isWarning && secondsLeft == GameConstants.WARNING_THRESHOLD_SECONDS) {
                    vibratorHelper.vibrateCountdownWarning()
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
        results.add(
            DrawingResult(
                sessionId = 0L,
                wordId = word.id,
                word = word,
                strokes = currentStrokes.toList()
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
        val result = results[guessOrder[guessPos]]
        _phase.value = GamePhase.Guessing(
            guessNumber = guessPos + 1,
            totalGuesses = results.size,
            strokes = result.strokes,
            feedback = null
        )
    }

    fun submitGuess(answer: String) {
        val current = _phase.value as? GamePhase.Guessing ?: return
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
        saveGameSessionUseCase(results)

        val fastest = results.filter { it.isCorrect }.minOfOrNull { it.responseTimeMs }
        _phase.value = GamePhase.Result(
            totalScore = totalScore,
            correctCount = results.count { it.isCorrect },
            wrongCount = results.count { !it.isCorrect },
            fastestCorrectSeconds = fastest?.let { it / 1000.0 },
            items = results.map { ResultItem(it.word.text, it.isCorrect, it.strokes) }
        )
    }

    fun restart() {
        timerJob?.cancel()
        drawingIndex = 0
        results.clear()
        currentStrokes = mutableListOf()
        _phase.value = GamePhase.Loading
        viewModelScope.launch {
            words = getWordsForGameUseCase(wordCount, category)
            if (words.isNotEmpty()) runDrawingTurn()
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}
