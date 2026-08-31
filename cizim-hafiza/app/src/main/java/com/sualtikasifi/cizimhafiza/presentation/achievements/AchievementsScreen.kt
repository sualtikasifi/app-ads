package com.sualtikasifi.cizimhafiza.presentation.achievements

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedIconButton
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import com.sualtikasifi.cizimhafiza.presentation.theme.CorrectGreen
import com.sualtikasifi.cizimhafiza.presentation.theme.GoldAccent
import com.sualtikasifi.cizimhafiza.presentation.theme.TextDark
import kotlinx.coroutines.delay

/**
 * Purely the badge catalog now — rank/level, the pen and frame pickers moved
 * to the main menu's profile card (see MainMenuScreen.LevelBadgeCard), and
 * the raw session stats/recent-games list were dropped entirely rather than
 * kept as a second section, so this screen is unambiguously "the achievements
 * page" the "Başarımlar" tile promises.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    viewModel: AchievementsViewModel = hiltViewModel()
) {
    val achievements by viewModel.achievements.collectAsState()
    val newlyUnlockedIds by viewModel.newlyUnlockedIds.collectAsState()
    val unlockedCount = achievements.count { it.unlocked }
    // Tapping a chip explains what it takes to earn it — before this,
    // nothing in the UI ever spelled out an achievement's condition beyond
    // the small print on the card itself, easy to miss among 51 of them.
    var selectedAchievement by remember { mutableStateOf<AchievementUiItem?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.menu_achievements)) },
                navigationIcon = {
                    RaisedIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.achievements_progress_format, unlockedCount, achievements.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            items(achievements.chunked(3)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { item ->
                        AchievementChip(
                            item = item,
                            isNewlyUnlocked = item.achievement.name in newlyUnlockedIds,
                            onClick = { selectedAchievement = item },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Pad the last, possibly-shorter row so its chips stay
                    // the same width as full rows instead of stretching.
                    repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }

        selectedAchievement?.let { item ->
            AchievementDetailDialog(item = item, onDismiss = { selectedAchievement = null })
        }
    }
}

@Composable
private fun AchievementDetailDialog(item: AchievementUiItem, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text(text = item.achievement.emoji, style = MaterialTheme.typography.headlineLarge) },
        title = {
            Text(
                text = stringResource(item.achievement.titleRes),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (item.unlocked) {
                    Text(
                        text = stringResource(R.string.achievement_unlocked_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = CorrectGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                // The description is already phrased as the unlock
                // condition (e.g. "Toplamda 250 puana ulaştın") — shown
                // under an explicit "how to unlock" label so it reads as
                // an instruction rather than trivia, whether the player is
                // still chasing it or reading it after the fact.
                Text(
                    text = stringResource(
                        if (item.unlocked) R.string.achievement_condition_label else R.string.achievement_unlock_hint_label
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(item.achievement.descriptionRes),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.achievement_xp_reward, item.achievement.xpReward),
                    style = MaterialTheme.typography.titleMedium,
                    color = GoldAccent,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun AchievementChip(
    item: AchievementUiItem,
    onClick: () -> Unit,
    isNewlyUnlocked: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Shimmers for 10s the first time AchievementsScreen is opened after this
    // achievement unlocked (see AchievementsViewModel.newlyUnlockedIds), then
    // settles back to a plain card — a one-time celebration in place instead
    // of a blocking dialog.
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
        onClick = onClick,
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
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
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
