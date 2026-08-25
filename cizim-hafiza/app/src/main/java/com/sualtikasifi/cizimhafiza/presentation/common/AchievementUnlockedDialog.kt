package com.sualtikasifi.cizimhafiza.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.Achievement

/**
 * Shown once on a result screen when [achievements] is non-empty — pure UI,
 * shared between the single-player and online result screens since there's
 * nothing mode-specific about celebrating an unlock (unlike the ViewModels
 * that produce this list, which stay deliberately separate/parallel).
 */
@Composable
fun AchievementUnlockedDialog(achievements: List<Achievement>, onDismiss: () -> Unit) {
    if (achievements.isEmpty()) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_achievement_title)) },
        text = {
            Column {
                achievements.forEachIndexed { index, achievement ->
                    if (index > 0) Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(text = achievement.emoji, style = MaterialTheme.typography.headlineSmall)
                        Column {
                            Text(text = stringResource(achievement.titleRes), style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = stringResource(achievement.descriptionRes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.achievement_dialog_confirm))
            }
        }
    )
}
