package com.sualtikasifi.cizimhafiza.presentation.online

import android.app.Activity
import android.os.SystemClock
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
import com.sualtikasifi.cizimhafiza.domain.model.PenSkin
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** SavedStateHandle key for [OnlineGameViewModel]'s process-death recovery checkpoint. */
private const val ONLINE_RECOVERY_KEY = "online_game_recovery"

/**
 * Which sub-phase an in-progress (not yet submitted) match was checkpointed
 * in — see [OnlineGameViewModel]'s recovery snapshot.
 */
@Serializable
private enum class OnlineRecoveryStage { DRAWING, BREAK, GUESSING }

/**
 * Everything needed to resume a not-yet-submitted online match after
 * process death. [words] is only kept to validate the checkpoint against
 * the room's current wordIds on restore (see [OnlineGameViewModel]'s
 * init) — a rematch resets those, and a checkpoint from a match that no
 * longer exists must never be trusted, even though a rematch always gets
 * its own fresh ViewModel/SavedStateHandle in practice.
 */
@Serializable
private data class OnlineActiveMatchSnapshot(
    val words: List<Word>,
    val drawingIndex: Int,
    val results: List<DrawingResult>,
    val inProgressStrokes: List<DrawingStroke>,
    val guessOrder: List<Int>,
    val guessPos: Int,
    val stage: OnlineRecoveryStage,
    val hintUsedThisMatch: Boolean,
    val revealedHintLetter: String?,
    val drawingHintUsedThisMatch: Boolean,
    val currentGuessTotal: Int,
    val currentDrawingTotal: Int
)

/**
 * The full checkpoint written to [SavedStateHandle]: either a not-yet-done
 * match ([active]) or an already-submitted one still sitting on the result
 * phase ([result]) — never both. [result] is only ever captured AFTER
 * [OnlineGameViewModel]'s Firestore submission already succeeded, so
 * restoring it is pure redisplay, never a re-submit.
 */
@Serializable
private data class OnlineGameRecovery(
    val active: OnlineActiveMatchSnapshot? = null,
    val result: GamePhase.Result? = null
)

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
    private val savedStateHandle: SavedStateHandle,
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

    /**
     * Who the player is playing *with*, in a 2v2 room. Null in a
     * free-for-all.
     *
     * Team mode used to exist only either side of the match: teams were
     * picked in the lobby and totalled on the result screen, and everything
     * in between looked exactly like a free-for-all. A player could go
     * through a whole 2v2 without ever being told which side they were on or
     * who their partner was — and since the round is played solo and only
     * the scores combine, there was nothing else to infer it from.
     */
    private val _teamInfo = MutableStateFlow<TeamInfo?>(null)
    val teamInfo: StateFlow<TeamInfo?> = _teamInfo.asStateFlow()

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


    /**
     * The player's chosen cosmetic pen (see domain.model.PenSkin). Resolved
     * against the live level for the same reason selectedFrame is: the stored
     * name is only a preference, not proof the pen has been earned.
     */
    val selectedPen: StateFlow<PenSkin> = combine(
        settingsRepository.selectedPenSkinId,
        settingsRepository.lifetimeXp
    ) { selectedId, xp -> PenSkin.resolve(selectedId, LevelProgressState.forXp(xp).level) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PenSkin.resolve(
                settingsRepository.selectedPenSkinId.value,
                LevelProgressState.forXp(settingsRepository.lifetimeXp.value).level
            )
        )

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

    private val recoveryJson = Json { ignoreUnknownKeys = true }

    init {
        val recovery = savedStateHandle.get<String>(ONLINE_RECOVERY_KEY)?.let {
            runCatching { recoveryJson.decodeFromString<OnlineGameRecovery>(it) }.getOrNull()
        }
        if (recovery?.result != null) {
            // Already submitted to Firestore before death (finishAndSubmit
            // always submits first — see its own comment) — redisplay only,
            // no room fetch needed.
            _startCountdown.value = null
            _phase.value = recovery.result
        } else {
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

                // A checkpoint only resumes the exact match it was taken in —
                // a rematch reuses the room code but resets wordIds, and this
                // is the one place that difference is checkable before the
                // stale checkpoint's word list is trusted for anything.
                val active = recovery?.active?.takeIf { room != null && it.words.map { w -> w.id } == room.wordIds }
                if (active != null) {
                    _startCountdown.value = null
                    resumeFrom(active)
                    return@launch
                }

                if (room?.teamMode == true) {
                    val myUid = onlineGameRepository.currentUid
                    val mine = room.players.firstOrNull { it.uid == myUid }
                    _teamInfo.value = mine?.teamId?.let { teamId ->
                        TeamInfo(
                            teamId = teamId,
                            mateName = room.players
                                .firstOrNull { it.uid != myUid && it.teamId == teamId }
                                ?.displayName
                        )
                    }
                }

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
    }

    /** Rebuilds every in-memory field from a checkpoint and resumes the exact sub-phase it was taken in. */
    private fun resumeFrom(snapshot: OnlineActiveMatchSnapshot) {
        words = snapshot.words
        drawingIndex = snapshot.drawingIndex
        results.clear()
        results.addAll(snapshot.results)
        currentStrokes = snapshot.inProgressStrokes.toMutableList()
        pendingStroke = emptyList()
        guessOrder = snapshot.guessOrder
        guessPos = snapshot.guessPos
        hintUsedThisMatch = snapshot.hintUsedThisMatch
        revealedHintLetter = snapshot.revealedHintLetter
        drawingHintUsedThisMatch = snapshot.drawingHintUsedThisMatch
        currentGuessTotal = snapshot.currentGuessTotal
        currentDrawingTotal = snapshot.currentDrawingTotal

        val word = words.getOrNull(drawingIndex)
        when (snapshot.stage) {
            OnlineRecoveryStage.DRAWING -> {
                if (word == null) { viewModelScope.launch { finishAndSubmit() }; return }
                val startSecondsLeft = currentDrawingTotal.takeIf { it > 0 }
                    ?: GameConstants.drawingDurationSeconds(word.difficulty)
                runDrawingCountdown(word, startSecondsLeft = startSecondsLeft)
            }
            OnlineRecoveryStage.BREAK -> runBreak()
            OnlineRecoveryStage.GUESSING -> {
                if (guessPos !in guessOrder.indices) {
                    viewModelScope.launch { finishAndSubmit() }
                    return
                }
                guessShownAtMillis = SystemClock.elapsedRealtime()
                val startSecondsLeft = currentGuessTotal.takeIf { it > 0 } ?: GameConstants.GUESS_DURATION_SECONDS
                runGuessCountdown(startSecondsLeft = startSecondsLeft)
            }
        }
    }

    private fun saveActiveSnapshot(stage: OnlineRecoveryStage) {
        val snapshot = OnlineActiveMatchSnapshot(
            words = words,
            drawingIndex = drawingIndex,
            results = results.toList(),
            inProgressStrokes = currentStrokes.toList(),
            guessOrder = guessOrder,
            guessPos = guessPos,
            stage = stage,
            hintUsedThisMatch = hintUsedThisMatch,
            revealedHintLetter = revealedHintLetter,
            drawingHintUsedThisMatch = drawingHintUsedThisMatch,
            currentGuessTotal = currentGuessTotal,
            currentDrawingTotal = currentDrawingTotal
        )
        savedStateHandle[ONLINE_RECOVERY_KEY] = recoveryJson.encodeToString(OnlineGameRecovery(active = snapshot))
    }

    private fun saveResultSnapshot(result: GamePhase.Result) {
        savedStateHandle[ONLINE_RECOVERY_KEY] = recoveryJson.encodeToString(OnlineGameRecovery(result = result))
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
        saveActiveSnapshot(OnlineRecoveryStage.DRAWING)
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
        saveActiveSnapshot(OnlineRecoveryStage.DRAWING)
    }

    /** Eraser tool: removes one whole stroke the player dragged over (see DrawableCanvas). */
    fun onEraseStroke(stroke: DrawingStroke) {
        if (!currentStrokes.remove(stroke)) return
        (_phase.value as? GamePhase.Drawing)?.let { current ->
            _phase.value = current.copy(strokes = currentStrokes.toList())
        }
        saveActiveSnapshot(OnlineRecoveryStage.DRAWING)
    }

    fun onUndoLastStroke() {
        if (currentStrokes.isEmpty()) return
        currentStrokes.removeAt(currentStrokes.lastIndex)
        (_phase.value as? GamePhase.Drawing)?.let { current ->
            _phase.value = current.copy(strokes = currentStrokes.toList())
        }
        saveActiveSnapshot(OnlineRecoveryStage.DRAWING)
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
        saveActiveSnapshot(OnlineRecoveryStage.DRAWING)
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
        saveActiveSnapshot(OnlineRecoveryStage.BREAK)
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
        guessShownAtMillis = SystemClock.elapsedRealtime()
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
        saveActiveSnapshot(OnlineRecoveryStage.GUESSING)
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
        val adStartedAt = SystemClock.elapsedRealtime()
        adManager.maybeShowRewarded(activity) { earned ->
            // See GameViewModel.useHint: the ad's own duration is pushed out
            // of the answer clock, so a hint never costs the speed bonus —
            // or, here, the fastestCorrectMs stat opponents are ranked on.
            guessShownAtMillis += SystemClock.elapsedRealtime() - adStartedAt
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
        val responseTimeMs = SystemClock.elapsedRealtime() - guessShownAtMillis
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

        // Advanced (and checkpointed) right away — see GameViewModel.submitGuess
        // for why: liveXp above is already an irreversible side effect, so a
        // process death during the feedback delay below must never resume by
        // re-asking this already-scored word.
        guessPos++
        revealedHintLetter = null
        currentGuessTotal = GameConstants.GUESS_DURATION_SECONDS
        saveActiveSnapshot(OnlineRecoveryStage.GUESSING)

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

        val resultPhase = GamePhase.Result(
            totalScore = totalScore,
            correctCount = correctCount,
            wrongCount = wrongCount,
            fastestCorrectSeconds = fastest?.let { it / 1000.0 },
            items = items
        )
        _phase.value = resultPhase
        // submitResult above already ran (and is what matters — see its own
        // comment); this only ever redisplays that outcome after a process
        // death, never re-submits.
        saveResultSnapshot(resultPhase)
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

/** The player's side and partner in a 2v2 room — see OnlineGameViewModel.teamInfo. */
data class TeamInfo(val teamId: String, val mateName: String?)
