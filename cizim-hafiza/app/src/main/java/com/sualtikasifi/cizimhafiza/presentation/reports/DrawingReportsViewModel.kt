package com.sualtikasifi.cizimhafiza.presentation.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.domain.model.DrawingPoint
import com.sualtikasifi.cizimhafiza.domain.model.DetectorEvent
import com.sualtikasifi.cizimhafiza.domain.model.BugReportEntry
import com.sualtikasifi.cizimhafiza.domain.model.DrawingReport
import com.sualtikasifi.cizimhafiza.domain.model.DrawingStroke
import com.sualtikasifi.cizimhafiza.domain.model.Moderation
import com.sualtikasifi.cizimhafiza.domain.model.PendingRun
import com.sualtikasifi.cizimhafiza.domain.model.ReviewerIdentity
import com.sualtikasifi.cizimhafiza.domain.model.RunPage
import com.sualtikasifi.cizimhafiza.domain.model.WrittenWordDetector
import com.sualtikasifi.cizimhafiza.domain.repository.BugReportRepository
import com.sualtikasifi.cizimhafiza.domain.repository.DetectorEventRepository
import com.sualtikasifi.cizimhafiza.domain.repository.GlobalLeagueRepository
import com.sualtikasifi.cizimhafiza.domain.repository.DrawingReportRepository
import com.sualtikasifi.cizimhafiza.domain.repository.ModerationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * One report as the review screen shows it: the report itself, the drawing
 * decoded back into strokes, and what the detector made of it.
 */
data class ReportedDrawing(
    val report: DrawingReport,
    val strokes: List<DrawingStroke>,
    /**
     * [WrittenWordDetector]'s own score for this drawing, 0..1.
     *
     * Shown next to a human's verdict on purpose. A drawing somebody
     * reported as written that the detector scored low is exactly the case
     * worth looking at — it is the only way to find out where the thresholds
     * sit against real handwriting rather than the synthesised kind they
     * were calibrated on.
     */
    val writingScore: Float
)

/** One refused round, with its sample drawing decoded. */
data class RefusedRound(
    val event: DetectorEvent,
    val strokes: List<DrawingStroke>
)

/** Which section of the inbox is on screen. */
enum class ReportsTab { Queue, Pool, Feedback, Reports, Detector, League }

/**
 * One scrolling list of runs — the queue or the pool — and where it is up to.
 *
 * Paged rather than fetched whole: a row costs a second read for a document
 * holding ten drawings, so a screen that loaded everything up front spent
 * megabytes on rows nobody scrolled to.
 */
data class RunList(
    val runs: List<PendingRun> = emptyList(),
    val cursor: Long? = null,
    val endReached: Boolean = false,
    val loading: Boolean = false,
    /** True only before the first page has ever arrived. */
    val neverLoaded: Boolean = true,
    val failed: Boolean = false
) {
    fun appending(page: RunPage): RunList {
        val fresh = page.runs.filterNot { new -> runs.any { it.id == new.id } }
        val advanced = page.nextCursor != null && page.nextCursor != cursor
        return copy(
            runs = runs + fresh,
            cursor = page.nextCursor ?: cursor,
            // A page that added no row AND did not move the cursor cannot be
            // followed by a different one — a run stored without a createdAt
            // would otherwise leave the list asking for the same page
            // forever. Treat it as the end.
            endReached = page.endReached || (fresh.isEmpty() && !advanced),
            loading = false,
            neverLoaded = false,
            failed = false
        )
    }
}

data class DrawingReportsUiState(
    // Opens on the queue: it is the section with work waiting in it, and a
    // round sitting unreviewed is a round nobody can be matched against.
    val tab: ReportsTab = ReportsTab.Queue,
    val queue: RunList = RunList(),
    val pool: RunList = RunList(),
    /** The row a decision is currently running for — its buttons go quiet. */
    val decidingId: String? = null,
    /** The run whose name is being edited, or null while no dialog is open. */
    val renaming: PendingRun? = null,
    /**
     * A decision that did not go through.
     *
     * Deliberately separate from a load failure. It used to share one flag,
     * and since a load failure replaces the whole list with an error message,
     * one refused write made every row on the screen appear to vanish — which
     * reads as "my rounds were deleted" rather than "that tap did nothing".
     */
    val decisionFailed: Boolean = false,
    /** What Firestore actually said — the difference between a diagnosis and a guess. */
    val decisionError: String? = null,
    /** Who this device is to the rules, so a refusal can be read rather than guessed at. */
    val identity: ReviewerIdentity? = null,
    val feedback: List<BugReportEntry> = emptyList(),
    val feedbackLoading: Boolean = false,
    val feedbackLoaded: Boolean = false,
    val feedbackFailed: Boolean = false,
    val reports: List<ReportedDrawing> = emptyList(),
    val refusals: List<RefusedRound> = emptyList(),
    val evidenceLoading: Boolean = false,
    val evidenceLoaded: Boolean = false,
    val evidenceFailed: Boolean = false,
    /** The cosmetic currently set as this month's league prize, if any. */
    val weekRewardId: String? = null,
    val leagueLoading: Boolean = false,
    val leagueLoaded: Boolean = false,
    val leagueFailed: Boolean = false
) {
    fun listFor(tab: ReportsTab): RunList? = when (tab) {
        ReportsTab.Queue -> queue
        ReportsTab.Pool -> pool
        else -> null
    }
}

/**
 * The reviewer's inbox: the approval queue, the live pool, player reports and
 * the detector's own refusals.
 *
 * Each section loads only when it is opened, and only three runs at a time.
 * Loading all four up front cost several megabytes of Firestore reads every
 * time the screen was opened, nearly all of it for rows never looked at.
 */
@HiltViewModel
class DrawingReportsViewModel @Inject constructor(
    private val drawingReportRepository: DrawingReportRepository,
    private val detectorEventRepository: DetectorEventRepository,
    private val moderationRepository: ModerationRepository,
    private val bugReportRepository: BugReportRepository,
    private val globalLeagueRepository: GlobalLeagueRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DrawingReportsUiState())
    val uiState: StateFlow<DrawingReportsUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        _uiState.value = _uiState.value.copy(identity = moderationRepository.identity())
        loadMore(ReportsTab.Queue)
    }

    fun selectTab(tab: ReportsTab) {
        _uiState.value = _uiState.value.copy(tab = tab, decisionFailed = false)
        when (tab) {
            ReportsTab.Queue, ReportsTab.Pool ->
                if (_uiState.value.listFor(tab)?.neverLoaded == true) loadMore(tab)
            ReportsTab.Feedback -> if (!_uiState.value.feedbackLoaded) loadFeedback()
            ReportsTab.League -> if (!_uiState.value.leagueLoaded) loadLeagueConfig()
            else -> if (!_uiState.value.evidenceLoaded) loadEvidence()
        }
    }

    /**
     * Reads which cosmetic is currently set as the week's prize.
     *
     * Taken from the published table rather than the config document: the
     * table is what players actually see, so showing anything else here
     * would be showing the panel a value nobody is playing for. It lags a
     * config change by up to one rebuild, which is why [setWeekReward]
     * reports the pending state rather than re-reading.
     */
    private fun loadLeagueConfig() {
        _uiState.value = _uiState.value.copy(leagueLoading = true, leagueFailed = false)
        viewModelScope.launch {
            globalLeagueRepository.table(forceRefresh = true)
                .onSuccess { table ->
                    _uiState.value = _uiState.value.copy(
                        weekRewardId = table.rewardId,
                        leagueLoading = false,
                        leagueLoaded = true
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(leagueLoading = false, leagueFailed = true)
                }
        }
    }

    /**
     * Sets the prize for the weeks from here on.
     *
     * The published table keeps the old value until the scheduled function
     * next rebuilds it (up to six hours), so the panel shows the new pick
     * immediately and says so — a picker that appeared to ignore the tap for
     * six hours would be indistinguishable from a broken one.
     */
    fun setWeekReward(rewardId: String) {
        _uiState.value = _uiState.value.copy(leagueLoading = true, leagueFailed = false)
        viewModelScope.launch {
            globalLeagueRepository.setWeekReward(rewardId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(weekRewardId = rewardId, leagueLoading = false)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(leagueLoading = false, leagueFailed = true)
                }
        }
    }

    /** Throws the current section away and reads it again from the top. */
    fun refresh() {
        when (val tab = _uiState.value.tab) {
            ReportsTab.Queue -> {
                _uiState.value = _uiState.value.copy(queue = RunList())
                loadMore(tab)
            }
            ReportsTab.Pool -> {
                _uiState.value = _uiState.value.copy(pool = RunList())
                loadMore(tab)
            }
            ReportsTab.Feedback -> {
                _uiState.value = _uiState.value.copy(feedbackLoaded = false)
                loadFeedback()
            }
            else -> {
                _uiState.value = _uiState.value.copy(evidenceLoaded = false)
                loadEvidence()
            }
        }
    }

    /**
     * Reads the next page of a run list.
     *
     * Called from the list itself when the reviewer reaches the last row, so
     * scrolling is what pays for the next three rows and nothing else does.
     */
    fun loadMore(tab: ReportsTab) {
        val current = _uiState.value.listFor(tab) ?: return
        if (current.loading || current.endReached) return
        update(tab) { it.copy(loading = true) }
        viewModelScope.launch {
            val result = when (tab) {
                ReportsTab.Pool ->
                    moderationRepository.poolRuns(Moderation.REVIEW_PAGE_SIZE, current.cursor)
                else ->
                    moderationRepository.pendingRuns(Moderation.REVIEW_PAGE_SIZE, current.cursor)
            }
            update(tab) { list ->
                result.fold(
                    onSuccess = { list.appending(it) },
                    onFailure = { list.copy(loading = false, neverLoaded = false, failed = true) }
                )
            }
        }
    }

    /**
     * Marks one report seen from the panel. Updated in place rather than by
     * re-running [loadFeedback]: a full reload would cost another
     * [FEEDBACK_SHOWN]-document read for a change to a single field the
     * panel already knows.
     */
    fun markSeen(reportId: String) {
        viewModelScope.launch {
            bugReportRepository.markSeen(reportId).onSuccess {
                _uiState.value = _uiState.value.copy(
                    feedback = _uiState.value.feedback.map { entry ->
                        if (entry.report.id == reportId) {
                            entry.copy(report = entry.report.copy(seenAtMillis = System.currentTimeMillis()))
                        } else {
                            entry
                        }
                    }
                )
            }
        }
    }

    private fun loadFeedback() {
        if (_uiState.value.feedbackLoading) return
        _uiState.value = _uiState.value.copy(feedbackLoading = true)
        viewModelScope.launch {
            val result = bugReportRepository.allReports(FEEDBACK_SHOWN)
            _uiState.value = _uiState.value.copy(
                feedback = result.getOrNull().orEmpty(),
                feedbackLoading = false,
                feedbackLoaded = true,
                feedbackFailed = result.isFailure
            )
        }
    }

    private fun loadEvidence() {
        if (_uiState.value.evidenceLoading) return
        _uiState.value = _uiState.value.copy(evidenceLoading = true)
        viewModelScope.launch {
            // These two together: the point of the pair is to compare them —
            // a human's verdict against the detector's — so loading them apart
            // would only mean two waits to see one picture.
            val reports = drawingReportRepository.recentReports(REPORTS_SHOWN)
            val refusals = detectorEventRepository.recentEvents(REPORTS_SHOWN)
            _uiState.value = _uiState.value.copy(
                reports = reports.getOrNull().orEmpty().map { it.withDrawing() },
                refusals = refusals.getOrNull().orEmpty().map { it.withDrawing() },
                evidenceLoading = false,
                evidenceLoaded = true,
                evidenceFailed = reports.isFailure && refusals.isFailure
            )
        }
    }

    fun approve(run: PendingRun) = decide(run, movesToPool = true) {
        moderationRepository.approve(run.id)
    }

    /**
     * Pulls an approved round back out of the pool.
     *
     * The row moves to the queue rather than disappearing, so a mistaken
     * approval can be redecided in the same sitting.
     */
    fun sendBackToQueue(run: PendingRun) = decide(run, movesToPool = false) {
        moderationRepository.sendBackToQueue(run.id)
    }

    /**
     * Rejects the round and takes back exactly the XP it paid — the figure
     * stored with the round, not a guess.
     */
    fun reject(run: PendingRun) = decide(run, movesToPool = false, keepsRun = false) {
        moderationRepository.reject(run.id, run.xpEarned)
    }

    fun startRename(run: PendingRun) {
        _uiState.value = _uiState.value.copy(renaming = run, decisionFailed = false)
    }

    fun cancelRename() {
        _uiState.value = _uiState.value.copy(renaming = null)
    }

    /**
     * Renames a run in place, in whichever list it is showing in.
     *
     * The row is rewritten locally on success for the same reason decisions
     * are: the reviewer is going to rename several in a row, and a reload
     * between each would lose their place in the list.
     */
    fun rename(run: PendingRun, nickname: String) {
        val trimmed = nickname.trim()
        if (trimmed.isEmpty()) return
        val inPool = _uiState.value.pool.runs.any { it.id == run.id }
        _uiState.value = _uiState.value.copy(renaming = null, decidingId = run.id)
        viewModelScope.launch {
            val result = moderationRepository.rename(run.id, trimmed, inPool)
            val state = _uiState.value
            if (result.isFailure) {
                _uiState.value = state.copy(
                    decidingId = null,
                    decisionFailed = true,
                    decisionError = result.exceptionOrNull()?.describe()
                )
                return@launch
            }
            val renamed = { list: RunList ->
                list.copy(
                    runs = list.runs.map {
                        if (it.id == run.id) it.copy(nickname = trimmed) else it
                    }
                )
            }
            _uiState.value = state.copy(
                queue = renamed(state.queue),
                pool = renamed(state.pool),
                decidingId = null
            )
        }
    }

    fun dismissDecisionFailure() {
        _uiState.value = _uiState.value.copy(decisionFailed = false, decisionError = null)
    }

    /**
     * The short form of a failure, for a one-line banner.
     *
     * The class name is kept alongside the message because Firestore's own
     * message for a refused write is a sentence about permissions that reads
     * the same whether the rules are missing, stale, or simply not about this
     * account — while the type tells them apart at a glance.
     */
    private fun Throwable.describe(): String =
        listOfNotNull(this::class.simpleName, message).joinToString(": ").take(240)

    /**
     * Runs one decision and moves the row to wherever it now belongs.
     *
     * Moved locally rather than by reloading: a reviewer works through these
     * one after another, and a round trip between every tap would make the
     * screen unusable. A refused write leaves both lists exactly as they were
     * and raises a banner — never a blank screen.
     */
    private fun decide(
        run: PendingRun,
        movesToPool: Boolean,
        keepsRun: Boolean = true,
        action: suspend () -> Result<Unit>
    ) {
        if (_uiState.value.decidingId != null) return
        _uiState.value = _uiState.value.copy(
            decidingId = run.id,
            decisionFailed = false,
            decisionError = null
        )
        viewModelScope.launch {
            val result = action()
            val state = _uiState.value
            if (result.isFailure) {
                _uiState.value = state.copy(
                    decidingId = null,
                    decisionFailed = true,
                    decisionError = result.exceptionOrNull()?.describe()
                )
                return@launch
            }
            val without = { list: RunList ->
                list.copy(runs = list.runs.filterNot { it.id == run.id })
            }
            val with = { list: RunList ->
                if (list.runs.any { it.id == run.id }) list
                else list.copy(runs = listOf(run) + list.runs)
            }
            _uiState.value = state.copy(
                queue = if (keepsRun && !movesToPool) with(without(state.queue)) else without(state.queue),
                pool = if (keepsRun && movesToPool) with(without(state.pool)) else without(state.pool),
                decidingId = null
            )
        }
    }

    private fun update(tab: ReportsTab, transform: (RunList) -> RunList) {
        val state = _uiState.value
        _uiState.value = when (tab) {
            ReportsTab.Pool -> state.copy(pool = transform(state.pool))
            else -> state.copy(queue = transform(state.queue))
        }
    }

    private fun DetectorEvent.withDrawing(): RefusedRound =
        RefusedRound(event = this, strokes = decodeStrokes(sampleStrokesJson))

    /**
     * A report whose evidence would not decode still gets a row.
     *
     * The drawing is dropped at write time when it is too large to store
     * (DrawingReportRepositoryImpl), and an older report may predate the
     * field entirely — in both cases the reason and the reported player are
     * still worth seeing, so an empty drawing is a blank canvas rather than
     * a missing row.
     */
    private fun DrawingReport.withDrawing(): ReportedDrawing {
        val strokes = decodeStrokes(strokesJson)
        return ReportedDrawing(
            report = this,
            strokes = strokes,
            writingScore = WrittenWordDetector.writingScore(strokes, word.count { it.isLetter() })
        )
    }

    private fun decodeStrokes(raw: String): List<DrawingStroke> = runCatching {
        if (raw.isBlank()) emptyList() else json.decodeFromString<List<List<DrawingPoint>>>(raw)
    }.getOrDefault(emptyList())

    private companion object {
        /** Enough to see a pattern, few enough to stay one cheap read. */
        const val REPORTS_SHOWN = 60

        /** Plain text, no drawings — a page of these is cheap. */
        const val FEEDBACK_SHOWN = 50
    }
}
