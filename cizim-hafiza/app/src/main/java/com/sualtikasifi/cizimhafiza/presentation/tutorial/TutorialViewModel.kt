package com.sualtikasifi.cizimhafiza.presentation.tutorial

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.Difficulty
import com.sualtikasifi.cizimhafiza.domain.model.DrawingStroke
import com.sualtikasifi.cizimhafiza.domain.model.Word
import com.sualtikasifi.cizimhafiza.presentation.game.GamePhase
import com.sualtikasifi.cizimhafiza.presentation.game.GuessFeedback
import com.sualtikasifi.cizimhafiza.util.AnswerMatcher
import com.sualtikasifi.cizimhafiza.util.GameConstants
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A full-screen coaching card shown between turns. While one of these is on
 * screen the tutorial's own timer is not running at all, so a first-time
 * player can read at their own pace without a countdown ticking underneath.
 */
data class TutorialCoach(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val emoji: String,
    @StringRes val buttonRes: Int,
    // Set only on the intro card, which shows the Karalak mascot instead of
    // an emoji glyph; every other card falls back to [emoji].
    @DrawableRes val imageRes: Int? = null
)

/**
 * A miniature, self-contained 3-word run of the real game loop for first
 * launch: draw three words, then guess them back.
 *
 * Deliberately NOT built on [com.sualtikasifi.cizimhafiza.presentation.game.GameViewModel]:
 * nothing here touches Room, the word pool, scoring, achievements or ads —
 * the three words are hardcoded and no result is ever persisted, so a
 * practice run can't pollute "Son Oyunlar" or unlock real achievements. It
 * only reuses [GamePhase] so the actual DrawingScreen/GuessScreen
 * composables can be rendered unchanged.
 */
@HiltViewModel
class TutorialViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    // Negative ids so these can never collide with a real word's Room id
    // (which DrawingScreen keys its canvas on).
    private val words = listOf(
        Word(id = -1, text = "kitap", category = "", difficulty = Difficulty.EASY),
        Word(id = -2, text = "köpek", category = "", difficulty = Difficulty.EASY),
        Word(id = -3, text = "elma", category = "", difficulty = Difficulty.EASY)
    )

    private val _phase = MutableStateFlow<GamePhase>(GamePhase.Loading)
    val phase: StateFlow<GamePhase> = _phase.asStateFlow()

    private val _coach = MutableStateFlow<TutorialCoach?>(INTRO_COACH)
    val coach: StateFlow<TutorialCoach?> = _coach.asStateFlow()

    private val _isFinished = MutableStateFlow(false)
    val isFinished: StateFlow<Boolean> = _isFinished.asStateFlow()

    private var drawingIndex = 0
    private var guessIndex = 0
    private val strokesPerWord = mutableMapOf<Int, List<DrawingStroke>>()
    private var currentStrokes = mutableListOf<DrawingStroke>()
    private var pendingStroke: DrawingStroke = emptyList()
    private var timerJob: Job? = null

    /** Advances past whichever coaching card is currently showing. */
    fun dismissCoach() {
        _coach.value = null
        when {
            drawingIndex < words.size -> startDrawingTurn()
            guessIndex < words.size -> showGuess()
            else -> _isFinished.value = true
        }
    }

    // --- Drawing ---

    private fun startDrawingTurn() {
        timerJob?.cancel()
        currentStrokes = mutableListOf()
        pendingStroke = emptyList()
        val word = words[drawingIndex]

        // First word is untimed on purpose — the very first thing a new
        // player does shouldn't be racing a clock they've never seen.
        if (drawingIndex == 0) {
            _phase.value = drawingPhase(word, secondsLeft = 0, totalSeconds = 0, isUntimed = true)
            return
        }

        val total = if (drawingIndex == 1) TUTORIAL_TIMED_DRAW_SECONDS else TUTORIAL_DRAW_SECONDS
        timerJob = viewModelScope.launch {
            for (secondsLeft in total downTo 1) {
                _phase.value = drawingPhase(
                    word = word,
                    secondsLeft = secondsLeft,
                    totalSeconds = total,
                    isUntimed = false,
                    isWarning = secondsLeft <= GameConstants.WARNING_THRESHOLD_SECONDS
                )
                delay(1_000)
            }
            finishDrawingTurn()
        }
    }

    private fun drawingPhase(
        word: Word,
        secondsLeft: Int,
        totalSeconds: Int,
        isUntimed: Boolean,
        isWarning: Boolean = false
    ) = GamePhase.Drawing(
        word = word,
        wordNumber = drawingIndex + 1,
        totalWords = words.size,
        secondsLeft = secondsLeft,
        totalSeconds = totalSeconds,
        isWarning = isWarning,
        strokes = currentStrokes.toList(),
        isUntimed = isUntimed,
        // No whole-match clock and no rewarded-ad hint during the tutorial —
        // both would be noise while someone is still learning the basics.
        matchSecondsRemaining = null,
        hintUsed = true
    )

    fun onStrokeFinished(wordId: Int, stroke: DrawingStroke) {
        if (words.getOrNull(drawingIndex)?.id != wordId) return
        currentStrokes.add(stroke)
        pendingStroke = emptyList()
        refreshDrawingStrokes()
    }

    fun onStrokeProgress(wordId: Int, points: DrawingStroke) {
        if (words.getOrNull(drawingIndex)?.id != wordId) return
        pendingStroke = points
    }

    fun onClearCanvas() {
        currentStrokes.clear()
        pendingStroke = emptyList()
        refreshDrawingStrokes()
    }

    fun onEraseStroke(stroke: DrawingStroke) {
        if (!currentStrokes.remove(stroke)) return
        refreshDrawingStrokes()
    }

    fun onUndoLastStroke() {
        if (currentStrokes.isEmpty()) return
        currentStrokes.removeAt(currentStrokes.lastIndex)
        refreshDrawingStrokes()
    }

    private fun refreshDrawingStrokes() {
        (_phase.value as? GamePhase.Drawing)?.let { current ->
            _phase.value = current.copy(strokes = currentStrokes.toList())
        }
    }

    /** Untimed first word only — the "Sonraki Kelime" button. */
    fun advanceUntimedDrawing() {
        val current = _phase.value as? GamePhase.Drawing ?: return
        if (!current.isUntimed) return
        finishDrawingTurn()
    }

    private fun finishDrawingTurn() {
        timerJob?.cancel()
        val word = words[drawingIndex]
        strokesPerWord[word.id] = currentStrokes.toList() +
            listOfNotNull(pendingStroke.takeIf { it.size >= 2 })
        pendingStroke = emptyList()
        drawingIndex++
        _coach.value = when (drawingIndex) {
            1 -> TIMED_COACH
            2 -> LAST_WORD_COACH
            else -> GUESS_INTRO_COACH
        }
    }

    // --- Guessing ---

    private fun showGuess() {
        timerJob?.cancel()
        val word = words[guessIndex]
        // No countdown in the tutorial's guessing phase either: a first-time
        // player typing their first answer shouldn't be auto-skipped.
        _phase.value = GamePhase.Guessing(
            guessNumber = guessIndex + 1,
            totalGuesses = words.size,
            strokes = strokesPerWord[word.id].orEmpty(),
            feedback = null,
            secondsLeft = 0,
            totalSeconds = 0,
            isWarning = false,
            hintUsed = true
        )
    }

    fun onAnswerChanged(text: String) {
        val current = _phase.value as? GamePhase.Guessing ?: return
        if (current.feedback != null || text.isBlank()) return
        if (AnswerMatcher.normalize(text) == AnswerMatcher.normalize(words[guessIndex].text)) {
            submitGuess(text)
        }
    }

    fun submitGuess(answer: String) {
        val current = _phase.value as? GamePhase.Guessing ?: return
        if (current.feedback != null) return
        val word = words[guessIndex]
        val isCorrect = AnswerMatcher.isCorrect(answer, word.text, GameConstants.ANSWER_LEVENSHTEIN_TOLERANCE)
        _phase.value = current.copy(
            feedback = GuessFeedback(isCorrect = isCorrect, correctAnswer = word.text)
        )
        viewModelScope.launch {
            delay(1_400)
            guessIndex++
            if (guessIndex < words.size) showGuess() else _coach.value = FINALE_COACH
        }
    }

    /** Marks the tutorial done so it never auto-starts again. Also used by "Atla". */
    fun completeTutorial() {
        timerJob?.cancel()
        settingsRepository.tutorialCompleted = true
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }

    private companion object {
        // Word 2 (the one this coach card introduces) is short on purpose —
        // just enough to notice the countdown exists.
        const val TUTORIAL_TIMED_DRAW_SECONDS = 12

        // Comfortably longer than a real EASY word's timer for the final word.
        const val TUTORIAL_DRAW_SECONDS = 20

        val INTRO_COACH = TutorialCoach(
            R.string.tutorial_intro_title, R.string.tutorial_intro_body, "👋", R.string.tutorial_intro_button,
            imageRes = R.drawable.tutorial_dino_wave
        )
        val TIMED_COACH = TutorialCoach(
            R.string.tutorial_timed_title, R.string.tutorial_timed_body, "⏱️", R.string.tutorial_generic_button,
            imageRes = R.drawable.tutorial_dino_watch
        )
        val LAST_WORD_COACH = TutorialCoach(
            R.string.tutorial_last_word_title, R.string.tutorial_last_word_body, "🎨", R.string.tutorial_generic_button
        )
        val GUESS_INTRO_COACH = TutorialCoach(
            R.string.tutorial_guess_title, R.string.tutorial_guess_body, "🤔", R.string.tutorial_generic_button
        )
        val FINALE_COACH = TutorialCoach(
            R.string.tutorial_finale_title, R.string.tutorial_finale_body, "🎉", R.string.tutorial_finale_button
        )
    }
}
