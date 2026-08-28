package com.sualtikasifi.cizimhafiza.presentation.stats

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.common.LevelAvatar
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedIconButton
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import com.sualtikasifi.cizimhafiza.presentation.theme.GoldAccent
import com.sualtikasifi.cizimhafiza.presentation.theme.TextDark
import com.sualtikasifi.cizimhafiza.util.placementEmoji
import kotlinx.coroutines.delay
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
    val achievements by viewModel.achievements.collectAsState()
    val newlyUnlockedIds by viewModel.newlyUnlockedIds.collectAsState()
    val dateFormat = remember { SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.getDefault()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.menu_stats)) },
                navigationIcon = {
                    RaisedIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        onClick = onBack
                    ,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        // One LazyColumn for the whole screen (rank card, stat tiles, badge
        // grid and session list alike) rather than fixed content above a
        // scrolling list — the badge catalog is long enough now that a
        // non-scrolling header would push the recent-games list off-screen
        // entirely on shorter phones.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                // The same badge other players see in a lobby —
                                // shown here so its tier is recognisable before
                                // it ever turns up next to someone else's.
                                LevelAvatar(level = progress.level, tier = progress.tier, size = 64.dp)
                                Column {
                                    Text(
                                        text = stringResource(R.string.stats_rank_label),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                    )
                                    Text(
                                        text = "${progress.tier.rank.emoji} ${stringResource(progress.tier.rank.nameRes)}",
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                }
                            }
                            // TEST-ONLY: jumps a whole level at a time to see
                            // how the higher avatar tiers actually look
                            // without weeks of real play. Remove before the
                            // Play Store release — see StatisticsViewModel.testBumpLevel.
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                TestLevelButton(icon = Icons.Filled.Remove, onClick = { viewModel.testBumpLevel(-1) })
                                TestLevelButton(icon = Icons.Filled.Add, onClick = { viewModel.testBumpLevel(1) })
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
                            text = progress.nextTier?.let { next ->
                                stringResource(
                                    R.string.stats_rank_progress,
                                    progress.totalXp,
                                    stringResource(next.rank.nameRes),
                                    progress.xpToNextTier
                                )
                            } ?: stringResource(R.string.stats_rank_maxed, progress.totalXp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            // Best score folded into the same compact tile row as the other
            // two totals — as its own full-width card with a displayLarge
            // number it ate a disproportionate slice of the screen.
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(
                        label = stringResource(R.string.stats_best_score),
                        value = "${stats.bestScore}",
                        modifier = Modifier.weight(1f)
                    )
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
            }

            item {
                Text(
                    text = stringResource(R.string.achievements_section_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            items(achievements.chunked(3)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { item ->
                        AchievementChip(
                            item = item,
                            isNewlyUnlocked = item.achievement.name in newlyUnlockedIds,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Pad the last, possibly-shorter row so its chips stay
                    // the same width as full rows instead of stretching.
                    repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.stats_recent_games),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

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
                        val placement = session.placement
                        val playerCount = session.playerCount
                        Icon(
                            imageVector = if (placement != null) Icons.Filled.People else Icons.Filled.SportsEsports,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = dateFormat.format(Date(session.dateEpochMillis)), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = if (placement != null && playerCount != null) {
                                    stringResource(R.string.stats_online_session_subtitle, playerCount, session.wordCount)
                                } else {
                                    stringResource(R.string.stats_solo_session_subtitle, session.wordCount)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (placement != null) {
                            val resultColor = if (placement == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${session.totalScore}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = resultColor
                                )
                                Text(
                                    text = placementEmoji(placement) ?: stringResource(R.string.stats_placement_format, placement),
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

@Composable
private fun AchievementChip(
    item: AchievementUiItem,
    isNewlyUnlocked: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Shimmers for 10s the first time StatisticsScreen is opened after this
    // achievement unlocked (see StatisticsViewModel.newlyUnlockedIds), then
    // settles back to a plain card — same one-time celebration the removed
    // AchievementUnlockedDialog popup used to give, just in-place instead of
    // a blocking dialog.
    var shimmering by remember(item.achievement.name) { mutableStateOf(isNewlyUnlocked) }
    LaunchedEffect(item.achievement.name, isNewlyUnlocked) {
        if (isNewlyUnlocked) {
            delay(10_000)
            shimmering = false
        }
    }
    val border = if (shimmering) {
        val pulse by rememberInfiniteTransition(label = "achievementShimmer").animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "achievementShimmerAlpha"
        )
        BorderStroke(2.dp, GoldAccent.copy(alpha = pulse))
    } else {
        null
    }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CardWhite, contentColor = TextDark),
        border = border,
        modifier = modifier.alpha(if (item.unlocked) 1f else 0.4f)
    ) {
        // Fixed height + both axes centered: titles run one or two lines, so
        // without this the chips in a row ended up different heights with
        // their emoji/label sitting at different offsets instead of centered.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = item.achievement.emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(item.achievement.titleRes),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(2.dp))
            // Shown whether locked or not — it's part of what makes a
            // locked achievement worth chasing, not just a surprise reward.
            Text(
                text = stringResource(R.string.achievement_xp_reward, item.achievement.xpReward),
                style = MaterialTheme.typography.labelSmall,
                color = GoldAccent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CardWhite, contentColor = TextDark),
        modifier = modifier
    ) {
        // Fixed height + identical content (no icon on any of the three)
        // so all three tiles in the row line up exactly.
        Column(
            modifier = Modifier.fillMaxWidth().height(92.dp).padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

/** TEST-ONLY control — see StatisticsViewModel.testBumpLevel. Delete with it. */
@Composable
private fun TestLevelButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp)
        )
    }
}
