package com.sualtikasifi.cizimhafiza.presentation.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.domain.model.DrawingPoint
import com.sualtikasifi.cizimhafiza.domain.model.DetectorEvent
import com.sualtikasifi.cizimhafiza.domain.model.DrawingReport
import com.sualtikasifi.cizimhafiza.domain.model.DrawingStroke
import com.sualtikasifi.cizimhafiza.domain.model.Moderation
import com.sualtikasifi.cizimhafiza.domain.model.PendingRun
import com.sualtikasifi.cizimhafiza.domain.model.WrittenWordDetector
import com.sualtikasifi.cizimhafiza.domain.repository.DetectorEventRepository
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
enum class ReportsTab { Queue, Pool, Reports, Detector }

data class DrawingReportsUiState(
    // Opens on the queue: it is the half with work waiting in it, and a round
    // sitting unreviewed is a round nobody can be matched against.
    val tab: ReportsTab = ReportsTab.Queue,
    val pending: List<PendingRun> = emptyList(),
    /** What is already live, so an approval can be taken back. */
    val pool: List<PendingRun> = emptyList(),
    /** The row a decision is currently running for — its buttons go quiet. */
    val decidingId: String? = null,
    val reports: List<ReportedDrawing> = emptyList(),
    val refusals: List<RefusedRound> = emptyList(),
    val isLoading: Boolean = true,
    val failed: Boolean = false
)

/**
 * Reads the report inbox. Read-only, on purpose.
 *
 * There is nothing to approve here: a round leaves the pool once two
 * different players have reported it (see DrawingReports), with no developer
 * in the loop, and firestore.rules makes a report immutable once written. So
 * this screen exists to SEE what is happening — which players attract
 * reports, and how the detector scored the drawings a human objected to —
 * rather than to act on it.
 */
@HiltViewModel
class DrawingReportsViewModel @Inject constructor(
    private val drawingReportRepository: DrawingReportRepository,
    private val detectorEventRepository: DetectorEventRepository,
    private val moderationRepository: ModerationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DrawingReportsUiState())
    val uiState: StateFlow<DrawingReportsUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        refresh()
    }

    fun selectTab(tab: ReportsTab) {
        _uiState.value = _uiState.value.copy(tab = tab)
    }

    fun refresh() {
        val tab = _uiState.value.tab
        _uiState.value = DrawingReportsUiState(tab = tab, isLoading = true)
        viewModelScope.launch {
            // Both halves in one pass. They are two small reads and the point
            // of the screen is to compare them — a human's verdict against
            // the detector's — so loading them apart would only mean two
            // waits to see one picture.
            val pending = moderationRepository.pendingRuns(Moderation.REVIEW_PAGE_SIZE)
            val pool = moderationRepository.poolRuns(Moderation.REVIEW_PAGE_SIZE)
            val reports = drawingReportRepository.recentReports(REPORTS_SHOWN)
            val refusals = detectorEventRepository.recentEvents(REPORTS_SHOWN)
            _uiState.value = DrawingReportsUiState(
                tab = tab,
                pending = pending.getOrNull().orEmpty(),
                pool = pool.getOrNull().orEmpty(),
                reports = reports.getOrNull().orEmpty().map { it.withDrawing() },
                refusals = refusals.getOrNull().orEmpty().map { it.withDrawing() },
                isLoading = false,
                // Only a total failure is worth an error: one section loading
                // is still worth showing.
                failed = pending.isFailure && pool.isFailure &&
                    reports.isFailure && refusals.isFailure
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

    /**
     * Runs one decision and drops the row on success.
     *
     * The row is removed locally rather than by reloading the whole queue:
     * a reviewer works through these one after another, and a full refresh
     * between every tap would make the screen unusable.
     */
    private fun decide(
        run: PendingRun,
        movesToPool: Boolean,
        keepsRun: Boolean = true,
        action: suspend () -> Result<Unit>
    ) {
        if (_uiState.value.decidingId != null) return
        _uiState.value = _uiState.value.copy(decidingId = run.id)
        viewModelScope.launch {
            val done = action().isSuccess
            val state = _uiState.value
            // Both lists are rebuilt, not just the one that was tapped: every
            // decision either moves the round between the queue and the pool
            // or destroys it, and showing only half of that would leave the
            // other tab claiming the round is still there.
            val withoutRun = { list: List<PendingRun> -> list.filterNot { it.id == run.id } }
            _uiState.value = state.copy(
                pending = when {
                    !done -> state.pending
                    keepsRun && !movesToPool -> listOf(run) + withoutRun(state.pending)
                    else -> withoutRun(state.pending)
                },
                pool = when {
                    !done -> state.pool
                    keepsRun && movesToPool -> listOf(run) + withoutRun(state.pool)
                    else -> withoutRun(state.pool)
                },
                decidingId = null,
                // A failed decision is worth saying out loud: silently leaving
                // the row would look like the tap did nothing.
                failed = !done
            )
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
    }
}
