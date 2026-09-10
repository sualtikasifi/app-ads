package com.sualtikasifi.cizimhafiza.presentation.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.domain.model.DrawingPoint
import com.sualtikasifi.cizimhafiza.domain.model.DrawingReport
import com.sualtikasifi.cizimhafiza.domain.model.DrawingStroke
import com.sualtikasifi.cizimhafiza.domain.model.WrittenWordDetector
import com.sualtikasifi.cizimhafiza.domain.repository.DrawingReportRepository
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

data class DrawingReportsUiState(
    val reports: List<ReportedDrawing> = emptyList(),
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
    private val drawingReportRepository: DrawingReportRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DrawingReportsUiState())
    val uiState: StateFlow<DrawingReportsUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = DrawingReportsUiState(isLoading = true)
        viewModelScope.launch {
            drawingReportRepository.recentReports(REPORTS_SHOWN)
                .onSuccess { reports ->
                    _uiState.value = DrawingReportsUiState(
                        reports = reports.map { it.withDrawing() },
                        isLoading = false
                    )
                }
                .onFailure {
                    _uiState.value = DrawingReportsUiState(isLoading = false, failed = true)
                }
        }
    }

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
        val strokes = runCatching {
            if (strokesJson.isBlank()) {
                emptyList()
            } else {
                json.decodeFromString<List<List<DrawingPoint>>>(strokesJson)
            }
        }.getOrDefault(emptyList())
        return ReportedDrawing(
            report = this,
            strokes = strokes,
            writingScore = WrittenWordDetector.writingScore(strokes, word.count { it.isLetter() })
        )
    }

    private companion object {
        /** Enough to see a pattern, few enough to stay one cheap read. */
        const val REPORTS_SHOWN = 60
    }
}
