package com.sualtikasifi.cizimhafiza.presentation.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import com.sualtikasifi.cizimhafiza.presentation.theme.TextDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val stats by viewModel.statistics.collectAsState()
    val progress by viewModel.playerProgress.collectAsState()
    val dateFormat = remember { SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.getDefault()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_stats)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = progress.rank.emoji, style = MaterialTheme.typography.displaySmall)
                        Column {
                            Text(
                                text = stringResource(R.string.stats_rank_label),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                            )
                            Text(
                                text = stringResource(progress.rank.nameRes),
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { progress.progressFraction },
                        color = MaterialTheme.colorScheme.onPrimary,
                        trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f),
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = progress.nextRank?.let { next ->
                            stringResource(
                                R.string.stats_rank_progress,
                                progress.lifetimeScore,
                                stringResource(next.nameRes),
                                next.minScore - progress.lifetimeScore
                            )
                        } ?: stringResource(R.string.stats_rank_maxed, progress.lifetimeScore),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = CardWhite, contentColor = TextDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = stringResource(R.string.stats_best_score), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "${stats.bestScore}", style = MaterialTheme.typography.displayLarge)
                    }
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.height(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    label = stringResource(R.string.stats_total_games),
                    value = "${stats.sessions.size}",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = stringResource(R.string.stats_words_drawn),
                    value = "${stats.totalWordsPlayed}",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(text = stringResource(R.string.stats_recent_games), style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(stats.sessions) { session ->
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = CardWhite, contentColor = TextDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val opponentName = session.opponentName
                            val opponentScore = session.opponentScore
                            Icon(
                                imageVector = if (opponentName != null) Icons.Filled.People else Icons.Filled.SportsEsports,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = dateFormat.format(Date(session.dateEpochMillis)), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = if (opponentName != null) {
                                        stringResource(R.string.stats_online_session_subtitle, opponentName, session.wordCount)
                                    } else {
                                        stringResource(R.string.stats_solo_session_subtitle, session.wordCount)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (opponentName != null && opponentScore != null) {
                                val resultColor = when {
                                    session.totalScore > opponentScore -> MaterialTheme.colorScheme.primary
                                    session.totalScore < opponentScore -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${session.totalScore} - $opponentScore",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = resultColor
                                    )
                                    Text(
                                        text = when {
                                            session.totalScore > opponentScore -> stringResource(R.string.stats_won)
                                            session.totalScore < opponentScore -> stringResource(R.string.stats_lost)
                                            else -> stringResource(R.string.stats_tied)
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = resultColor
                                    )
                                }
                            } else {
                                Text(
                                    text = "${session.totalScore}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CardWhite, contentColor = TextDark),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = value, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
