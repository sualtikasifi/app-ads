package com.sualtikasifi.cizimhafiza.presentation.reportbug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.BugReport
import com.sualtikasifi.cizimhafiza.domain.model.BugReportCategory
import com.sualtikasifi.cizimhafiza.presentation.common.IconWell
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedCard
import com.sualtikasifi.cizimhafiza.presentation.common.ScreenHeader
import com.sualtikasifi.cizimhafiza.presentation.common.SectionLabel
import com.sualtikasifi.cizimhafiza.presentation.common.SelectableChip
import com.sualtikasifi.cizimhafiza.presentation.common.TintedBadge
import com.sualtikasifi.cizimhafiza.presentation.common.appTextFieldColors
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.CorrectGreen
import com.sualtikasifi.cizimhafiza.util.asString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_DESCRIPTION_LENGTH = 2000

@Composable
fun ReportBugScreen(
    onBack: () -> Unit,
    viewModel: ReportBugViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val myReports by viewModel.myReports.collectAsState()

    // A scrollable list rather than a fixed Column: reports used to be
    // strictly write-only (send it, see a "thanks" card, never hear
    // anything again), so there was nothing below the form worth scrolling
    // to. Once a developer reply can show up here, the history needs room
    // to grow past one screen.
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .screenBackground()
            .padding(padding)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        item {
            ScreenHeader(title = stringResource(R.string.report_bug_title), onBack = onBack)
            Spacer(modifier = Modifier.height(18.dp))
        }

        item {
            if (uiState.isSubmitted) {
                RaisedCard(corner = 22.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconWell(icon = Icons.Filled.CheckCircle, tint = CorrectGreen)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.report_bug_success),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Column {
                    // A short intro card, same language as CreateDuelScreen's
                    // — gives the form a proper "what is this for" framing
                    // instead of dropping straight into a bare text field.
                    RaisedCard(corner = 22.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            IconWell(icon = Icons.Filled.Feedback)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.report_bug_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))

                    SectionLabel(text = stringResource(R.string.report_bug_category_label))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SelectableChip(
                            label = stringResource(R.string.report_bug_category_suggestion),
                            selected = uiState.category == BugReportCategory.SUGGESTION,
                            onClick = { viewModel.onCategorySelected(BugReportCategory.SUGGESTION) },
                            fillWidth = true,
                            modifier = Modifier.weight(1f)
                        )
                        SelectableChip(
                            label = stringResource(R.string.report_bug_category_complaint),
                            selected = uiState.category == BugReportCategory.COMPLAINT,
                            onClick = { viewModel.onCategorySelected(BugReportCategory.COMPLAINT) },
                            fillWidth = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))

                    SectionLabel(text = stringResource(R.string.report_bug_description_label))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.description,
                        onValueChange = { if (it.length <= MAX_DESCRIPTION_LENGTH) viewModel.onDescriptionChanged(it) },
                        placeholder = { Text(stringResource(R.string.report_bug_placeholder)) },
                        minLines = 6,
                        colors = appTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp)
                    )
                    Text(
                        text = stringResource(R.string.report_bug_char_count_format, uiState.description.length, MAX_DESCRIPTION_LENGTH),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        textAlign = TextAlign.End
                    )
                    uiState.errorMessage?.let { message ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = message.asString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    PrimaryButton(
                        text = stringResource(
                            if (uiState.isSubmitting) R.string.loading_hint else R.string.report_bug_submit
                        ),
                        onClick = viewModel::submit,
                        enabled = uiState.description.isNotBlank() && !uiState.isSubmitting,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (myReports.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = stringResource(R.string.report_bug_history_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            items(myReports, key = { it.id }) { report ->
                ReportHistoryCard(report)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
    }
}

@Composable
private fun ReportHistoryCard(report: BugReport) {
    val dateFormat = remember(report.submittedAtMillis) { SimpleDateFormat("d MMMM yyyy", Locale.getDefault()) }
    RaisedCard(corner = 18.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TintedBadge(
                    text = stringResource(
                        if (report.category == BugReportCategory.SUGGESTION) {
                            R.string.report_bug_category_suggestion
                        } else {
                            R.string.report_bug_category_complaint
                        }
                    )
                )
                Text(
                    text = dateFormat.format(Date(report.submittedAtMillis)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = report.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (report.isAnswered) {
                Row(verticalAlignment = Alignment.Top) {
                    IconWell(icon = Icons.Filled.Check, tint = CorrectGreen, size = 26.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.report_bug_reply_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = CorrectGreen
                        )
                        Text(
                            text = report.reply.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.height(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.report_bug_awaiting_reply),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
