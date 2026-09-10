package com.sualtikasifi.cizimhafiza.presentation.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.DrawingReportReason
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedCard
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedIconButton
import com.sualtikasifi.cizimhafiza.presentation.common.ScreenTopActions
import com.sualtikasifi.cizimhafiza.presentation.common.StrokeCanvas
import com.sualtikasifi.cizimhafiza.presentation.common.TopActionsClearance
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.AppTheme
import java.text.DateFormat
import java.util.Date

/**
 * What players have reported, with the drawings actually drawn.
 *
 * This is the whole reason the reports live somewhere the app can read
 * rather than only in Firestore: `strokesJson` is an unreadable wall of
 * numbers in a console, and a report you cannot look at is a report you
 * cannot judge. The app already knows how to render strokes, so the cheapest
 * possible review surface is the app itself.
 *
 * Read-only. Two reports retire a round on their own (see DrawingReports)
 * and firestore.rules makes a report immutable once written, so there is
 * nothing here to approve — this answers "what is happening out there", and
 * shows the detector's score beside each human verdict so the thresholds can
 * be checked against real handwriting.
 */
@Composable
fun DrawingReportsScreen(
    onBack: () -> Unit,
    viewModel: DrawingReportsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .screenBackground()
                    .padding(padding)
                    .padding(horizontal = 18.dp)
            ) {
                Spacer(modifier = Modifier.height(TopActionsClearance))

                when {
                    uiState.isLoading -> Centered { CircularProgressIndicator() }

                    uiState.failed -> Centered {
                        Text(
                            text = stringResource(R.string.reports_load_failed),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    uiState.reports.isEmpty() -> Centered {
                        Text(
                            text = stringResource(R.string.reports_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)
                    ) {
                        items(uiState.reports, key = { it.report.id }) { ReportRow(it) }
                    }
                }
            }

            ScreenTopActions(
                onBack = onBack,
                title = stringResource(R.string.reports_title),
                modifier = Modifier.align(Alignment.TopStart)
            )
            RaisedIconButton(
                icon = Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.reports_refresh),
                onClick = viewModel::refresh,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 18.dp)
            )
        }
    }
}

@Composable
private fun ReportRow(entry: ReportedDrawing) {
    RaisedCard(corner = 20.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StrokeCanvas(
                strokes = entry.strokes,
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(AppTheme.tokens.canvasPaper)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.report.word,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(entry.report.reason.labelRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(6.dp))
                // The detector's own verdict, beside the human's. A WRITTEN
                // report scoring low here is a miss worth understanding.
                Text(
                    text = stringResource(
                        R.string.reports_detector_score,
                        (entry.writingScore * 100).toInt()
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(entry.report.submittedAtMillis)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = entry.report.reportedUid.take(UID_PREVIEW_LENGTH),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

private fun DrawingReportReason.labelRes(): Int = when (this) {
    DrawingReportReason.WRITTEN -> R.string.report_reason_written
    DrawingReportReason.OFFENSIVE -> R.string.report_reason_offensive
    DrawingReportReason.MEANINGLESS -> R.string.report_reason_meaningless
}

/** Enough of a uid to tell two reported players apart at a glance. */
private const val UID_PREVIEW_LENGTH = 10
