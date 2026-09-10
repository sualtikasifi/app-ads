package com.sualtikasifi.cizimhafiza.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.DrawingReportReason
import com.sualtikasifi.cizimhafiza.presentation.theme.AppTheme

/** Where a report has got to, so the button can say so rather than looking dead. */
enum class ReportSendState { Idle, Sending, Sent, Failed }

/**
 * The in-app way to report somebody else's drawing.
 *
 * Offered wherever another player's work is shown to someone who did not
 * make it — the Hızlı Eşleş opponent gallery and an online room's results.
 * That is a Play requirement for an app that shows user-generated content to
 * strangers, and it is also the only source of real labelled examples for
 * [com.sualtikasifi.cizimhafiza.domain.model.WrittenWordDetector], which is
 * currently tuned against synthesised handwriting.
 *
 * Three reasons and no free-text box. A typed explanation would need reading
 * before it could be acted on, and nobody is reading these at this scale —
 * the whole point is that two reports retire a round by themselves. Fixed
 * reasons are also what makes the reports countable later.
 */
@Composable
fun ReportDrawingDialog(
    word: String,
    sendState: ReportSendState,
    onReport: (DrawingReportReason) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        RaisedCard(corner = 24.dp, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconWell(icon = Icons.Filled.Flag)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.report_drawing_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.report_drawing_subtitle, word),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                when (sendState) {
                    ReportSendState.Sending -> CircularProgressIndicator(modifier = Modifier.size(32.dp))

                    ReportSendState.Sent -> Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = AppTheme.tokens.success
                        )
                        Text(
                            text = stringResource(R.string.report_drawing_sent),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    else -> Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReasonRow(
                            icon = Icons.Filled.Gesture,
                            label = stringResource(R.string.report_reason_written),
                            onClick = { onReport(DrawingReportReason.WRITTEN) }
                        )
                        ReasonRow(
                            icon = Icons.Filled.ReportProblem,
                            label = stringResource(R.string.report_reason_offensive),
                            onClick = { onReport(DrawingReportReason.OFFENSIVE) }
                        )
                        ReasonRow(
                            icon = Icons.Filled.Brush,
                            label = stringResource(R.string.report_reason_meaningless),
                            onClick = { onReport(DrawingReportReason.MEANINGLESS) }
                        )
                        if (sendState == ReportSendState.Failed) {
                            Text(
                                text = stringResource(R.string.report_drawing_failed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                SecondaryButton(
                    text = stringResource(
                        if (sendState == ReportSendState.Sent) R.string.close else R.string.report_drawing_cancel
                    ),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ReasonRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    RaisedCard(corner = 18.dp, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
