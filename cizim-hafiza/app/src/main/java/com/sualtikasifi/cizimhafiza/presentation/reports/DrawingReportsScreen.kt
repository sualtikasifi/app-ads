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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Undo
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
import com.sualtikasifi.cizimhafiza.domain.model.PendingRun
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedCard
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.SecondaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedIconButton
import com.sualtikasifi.cizimhafiza.presentation.common.ScreenTopActions
import com.sualtikasifi.cizimhafiza.presentation.common.SelectableChip
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

                // Two lines of two rather than four across: a fourth chip on
                // one line leaves each about 80dp, which cuts "Dedektör" in
                // half on a narrow phone.
                listOf(
                    ReportsTab.Queue to ReportsTab.Pool,
                    ReportsTab.Reports to ReportsTab.Detector
                ).forEach { (left, right) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(left, right).forEach { tab ->
                            SelectableChip(
                                label = stringResource(tab.labelRes(), uiState.count(tab)),
                                selected = uiState.tab == tab,
                                onClick = { viewModel.selectTab(tab) },
                                modifier = Modifier.weight(1f),
                                verticalPadding = 10.dp,
                                style = MaterialTheme.typography.bodySmall,
                                fillWidth = true
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))

                val rowCount = uiState.count(uiState.tab)

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

                    rowCount == 0 -> Centered {
                        Text(
                            text = stringResource(
                                when (uiState.tab) {
                                    ReportsTab.Queue -> R.string.reports_queue_empty
                                    ReportsTab.Pool -> R.string.reports_pool_empty
                                    ReportsTab.Reports -> R.string.reports_empty
                                    // Not the same "nothing here" at all: an
                                    // empty detector tab means no round has
                                    // been refused, which is either good news
                                    // or a broken detector.
                                    ReportsTab.Detector -> R.string.reports_detector_empty
                                }
                            ),
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
                        when (uiState.tab) {
                            ReportsTab.Queue -> items(uiState.pending, key = { it.id }) { run ->
                                PendingRunRow(
                                    run = run,
                                    busy = uiState.decidingId != null,
                                    onApprove = { viewModel.approve(run) },
                                    onReject = { viewModel.reject(run) }
                                )
                            }
                            ReportsTab.Pool -> items(uiState.pool, key = { it.id }) { run ->
                                PendingRunRow(
                                    run = run,
                                    busy = uiState.decidingId != null,
                                    // A round that is already live has one
                                    // move left. Rejecting it straight from
                                    // here would take XP for a round this
                                    // screen had already passed, so it goes
                                    // back to the queue and is decided there.
                                    onSendBack = { viewModel.sendBackToQueue(run) }
                                )
                            }
                            ReportsTab.Reports ->
                                items(uiState.reports, key = { it.report.id }) { ReportRow(it) }
                            ReportsTab.Detector ->
                                items(uiState.refusals, key = { it.event.id }) { RefusalRow(it) }
                        }
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

/**
 * One round waiting to be let into the pool: all ten drawings, then the two
 * buttons.
 *
 * The drawings are the whole row. A five-across grid fits ten thumbnails in
 * two lines without scrolling, which is what makes a queue clearable in a
 * sitting — writing is obvious at a glance, so the decision rarely needs a
 * closer look.
 */
@Composable
private fun PendingRunRow(
    run: PendingRun,
    busy: Boolean,
    onApprove: (() -> Unit)? = null,
    onReject: (() -> Unit)? = null,
    onSendBack: (() -> Unit)? = null
) {
    RaisedCard(corner = 20.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = run.nickname,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(
                        R.string.reports_queue_summary,
                        run.level,
                        run.correctCount,
                        run.items.size,
                        run.xpEarned
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            run.items.chunked(QUEUE_THUMBS_PER_ROW).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    row.forEach { item ->
                        StrokeCanvas(
                            strokes = item.strokes,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(MaterialTheme.shapes.small)
                                .background(AppTheme.tokens.canvasPaper)
                        )
                    }
                    // Keeps the last line's thumbnails the same size as the
                    // first's when the round is not a clean multiple.
                    repeat(QUEUE_THUMBS_PER_ROW - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onSendBack != null) {
                    SecondaryButton(
                        text = stringResource(R.string.reports_pool_send_back),
                        onClick = onSendBack,
                        enabled = !busy,
                        icon = Icons.Filled.Undo,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (onReject != null) {
                    SecondaryButton(
                        text = stringResource(R.string.reports_queue_reject),
                        onClick = onReject,
                        enabled = !busy,
                        icon = Icons.Filled.Block,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (onApprove != null) {
                    PrimaryButton(
                        text = stringResource(R.string.reports_queue_approve),
                        onClick = onApprove,
                        enabled = !busy,
                        icon = Icons.Filled.Check,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** Ten drawings in two lines of five — see [PendingRunRow]. */
private const val QUEUE_THUMBS_PER_ROW = 5

/**
 * One round the detector refused.
 *
 * The score list is the useful part — it shows the SHAPE of the round, which
 * is what the decision was actually made on: four or more high scores is a
 * cheat, and a row of low ones with three high would be a threshold sitting
 * too close to the edge.
 */
@Composable
private fun RefusalRow(entry: RefusedRound) {
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
                    text = entry.event.sampleWord,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(
                        R.string.reports_detector_flagged,
                        entry.event.flaggedCount,
                        entry.event.wordCount
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = entry.event.scores.joinToString(" "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entry.event.outcomeLooksRead) {
                    Text(
                        text = stringResource(R.string.reports_detector_fast),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(entry.event.createdAtMillis)),
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

/** The chip label for a tab — the count is its only argument. */
private fun ReportsTab.labelRes(): Int = when (this) {
    ReportsTab.Queue -> R.string.reports_tab_queue
    ReportsTab.Pool -> R.string.reports_tab_pool
    ReportsTab.Reports -> R.string.reports_tab_reports
    ReportsTab.Detector -> R.string.reports_tab_detector
}

/** How many rows a tab holds — used for its chip and for its empty state. */
private fun DrawingReportsUiState.count(tab: ReportsTab): Int = when (tab) {
    ReportsTab.Queue -> pending.size
    ReportsTab.Pool -> pool.size
    ReportsTab.Reports -> reports.size
    ReportsTab.Detector -> refusals.size
}
