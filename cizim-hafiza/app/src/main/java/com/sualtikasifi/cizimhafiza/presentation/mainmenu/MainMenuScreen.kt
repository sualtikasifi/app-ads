package com.sualtikasifi.cizimhafiza.presentation.mainmenu

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.util.DailyChallengeState
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.common.IconWell
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedCard
import com.sualtikasifi.cizimhafiza.domain.model.AvatarFrame
import com.sualtikasifi.cizimhafiza.domain.model.DailyChallenge
import com.sualtikasifi.cizimhafiza.domain.model.LevelProgressState
import com.sualtikasifi.cizimhafiza.presentation.common.LevelAvatar
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedIconButton
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.AppTheme
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import com.sualtikasifi.cizimhafiza.presentation.theme.GoldAccent
import com.sualtikasifi.cizimhafiza.presentation.theme.OrangeContainer
import com.sualtikasifi.cizimhafiza.presentation.theme.Teal
import com.sualtikasifi.cizimhafiza.presentation.theme.TealContainer

/** The one gap used between every major section of the menu, so the page reads as evenly spaced top to bottom. */
private val SECTION_GAP = 10.dp

@Composable
fun MainMenuScreen(
    onPlay: () -> Unit,
    onPlayOnline: () -> Unit,
    onLevels: () -> Unit,
    onStatistics: () -> Unit,
    onSettings: () -> Unit,
    onBotTraining: () -> Unit,
    onDailyChallenge: () -> Unit,
    viewModel: MainMenuViewModel = hiltViewModel()
) {
    val hasUnseenAchievement by viewModel.hasUnseenAchievement.collectAsState()
    val dailyState by viewModel.dailyState.collectAsState()
    val levelProgress by viewModel.levelProgress.collectAsState()
    val selectedFrame by viewModel.selectedFrame.collectAsState()

    // The app can sit in the background across midnight; without this the
    // menu would still be showing "done for today" on a day whose challenge
    // is actually waiting to be played.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshDaily()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // A fixed screen, not a scrolling one: the menu is the app's home base,
    // opened dozens of times a session, and every scroll gesture on it is a
    // small tax on getting to "Oyna". Fitting the daily-challenge card and
    // level badge in without scrolling meant trimming sizes throughout
    // rather than letting any one element claim its old, roomier size.
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .padding(padding)
                .padding(horizontal = 22.dp, vertical = 12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                RaisedIconButton(
                    icon = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.menu_settings),
                    onClick = onSettings,
                    size = 40.dp
                )
            }

            // Everything below the settings icon is centred as one block in
            // whatever room is left, with a single fixed gap (SECTION_GAP)
            // between every major section — a fixed gap here plus a
            // weight(1f) Box around just the level badge used to leave that
            // one gap sized by leftover space instead of matching the
            // others, which read as uneven spacing down the page.
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo medallion: the mark on a tinted disc so it reads as an
                // object on the page rather than a sticker floating on cream.
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .background(OrangeContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.karalak_logo_mark),
                        contentDescription = null,
                        modifier = Modifier.size(60.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.app_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(SECTION_GAP))

                // A proper card — the same colored-container treatment as
                // DailyChallengeCard below it — rather than the badge just
                // floating loose on the page: this is the player's own
                // chosen ring (see StatisticsScreen's picker), the thing all
                // the frame-unlock and sparkle work is actually for, and it
                // deserves to look like a deliberate piece of the menu, not
                // a sticker.
                LevelBadgeCard(progress = levelProgress, frame = selectedFrame)

                Spacer(modifier = Modifier.height(SECTION_GAP))

                DailyChallengeCard(state = dailyState, onPlay = onDailyChallenge)

                Spacer(modifier = Modifier.height(SECTION_GAP))

                PrimaryButton(
                    text = stringResource(R.string.menu_play),
                    onClick = onPlay,
                    icon = Icons.Filled.PlayArrow,
                    height = 54.dp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(SECTION_GAP))

                // 2×2 grid rather than four stacked bars: the same
                // destinations fit without scrolling on a small phone, and
                // each tile gets a color of its own so the menu isn't a wall
                // of orange.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MenuTile(
                        icon = Icons.Filled.People,
                        label = stringResource(R.string.menu_play_online),
                        tint = Teal,
                        container = TealContainer,
                        onClick = onPlayOnline,
                        modifier = Modifier.weight(1f)
                    )
                    MenuTile(
                        icon = Icons.Filled.Map,
                        label = stringResource(R.string.menu_levels),
                        tint = MaterialTheme.colorScheme.primary,
                        container = OrangeContainer,
                        onClick = onLevels,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(SECTION_GAP))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MenuTile(
                        icon = Icons.Filled.BarChart,
                        label = stringResource(R.string.menu_stats),
                        tint = GoldAccent,
                        container = Color(0xFFF8EBD0),
                        onClick = onStatistics,
                        showBadge = hasUnseenAchievement,
                        modifier = Modifier.weight(1f)
                    )
                    MenuTile(
                        icon = Icons.Filled.SmartToy,
                        label = stringResource(R.string.menu_bot_training),
                        tint = Color(0xFF7B68C4),
                        container = Color(0xFFE7E3F7),
                        onClick = onBotTraining,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    container: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBadge: Boolean = false
) {
    Box(modifier = modifier) {
        RaisedCard(
            onClick = onClick,
            corner = 24.dp,
            face = CardWhite,
            edge = AppTheme.tokens.edge,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                IconWell(icon = icon, tint = tint, container = container, size = 40.dp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
        // A new, not-yet-viewed achievement (see MainMenuViewModel) —
        // cleared the next time StatisticsScreen opens.
        if (showBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(12.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape)
            )
        }
    }
}

/**
 * The player's level/frame, framed in the same [RaisedCard] + [OrangeContainer]
 * language as [DailyChallengeCard] right below it — rank name, level number
 * and a slim XP sliver alongside the badge, instead of the avatar floating
 * alone on the page.
 */
@Composable
private fun LevelBadgeCard(progress: LevelProgressState, frame: AvatarFrame) {
    RaisedCard(corner = 22.dp, face = OrangeContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LevelAvatar(level = progress.level, frame = frame, size = 64.dp)
                Column {
                    Text(
                        text = "${progress.tier.rank.emoji} ${stringResource(progress.tier.rank.nameRes)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.avatar_frame_locked_level, progress.level),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress.progressFraction },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        }
    }
}

/**
 * The menu's first call to action: today's challenge, or — once it's done —
 * the streak it just extended.
 *
 * Deliberately shows the streak in both states. Before playing it's what's
 * at stake; after playing it's the reward, and seeing it tick up is most of
 * the reason to come back tomorrow.
 */
@Composable
private fun DailyChallengeCard(state: DailyChallengeState, onPlay: () -> Unit) {
    val available = state.isAvailableToday
    val todayResult = state.todayResult

    RaisedCard(
        corner = 22.dp,
        face = if (available) OrangeContainer else CardWhite,
        onClick = if (available) onPlay else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (available) "🎯" else "✅",
                style = MaterialTheme.typography.headlineSmall
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.daily_challenge_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (available) {
                        stringResource(R.string.daily_challenge_ready, DailyChallenge.WORD_COUNT)
                    } else {
                        stringResource(
                            R.string.daily_challenge_done,
                            todayResult?.correctCount ?: 0,
                            DailyChallenge.WORD_COUNT
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!available) {
                    Text(
                        text = stringResource(R.string.daily_challenge_resets_in, midnightCountdownText()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            if (state.currentStreak > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "🔥", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = state.currentStreak.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * "HH:MM:SS" until local midnight, ticking every second. Local time, not
 * UTC: the daily challenge itself resets on [java.time.LocalDate]'s day
 * boundary (see DailyChallenge/DailyChallengeRepository), which is the
 * device's local calendar day — the countdown has to agree with the exact
 * moment the card it's showing will actually flip to "ready" again.
 */
@Composable
private fun midnightCountdownText(): String {
    var remaining by remember {
        mutableStateOf(java.time.Duration.between(java.time.LocalDateTime.now(), nextMidnight()))
    }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            remaining = java.time.Duration.between(java.time.LocalDateTime.now(), nextMidnight())
        }
    }
    val total = remaining.seconds.coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

private fun nextMidnight(): java.time.LocalDateTime =
    java.time.LocalDate.now().plusDays(1).atStartOfDay()
