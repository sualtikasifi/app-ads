package com.sualtikasifi.cizimhafiza.presentation.league

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.sualtikasifi.cizimhafiza.presentation.theme.AppTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import java.time.LocalDate
import com.sualtikasifi.cizimhafiza.domain.model.AvatarFrame
import com.sualtikasifi.cizimhafiza.domain.model.LeagueEntry
import com.sualtikasifi.cizimhafiza.domain.model.LeaguePeriod
import com.sualtikasifi.cizimhafiza.domain.model.LeagueReward
import com.sualtikasifi.cizimhafiza.domain.model.LeagueTable
import com.sualtikasifi.cizimhafiza.domain.model.PenSkin
import com.sualtikasifi.cizimhafiza.presentation.common.LevelAvatar
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedCard
import com.sualtikasifi.cizimhafiza.presentation.common.SecondaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.SelectableChip
import com.sualtikasifi.cizimhafiza.presentation.common.penBrush
import com.sualtikasifi.cizimhafiza.presentation.common.TintedBadge
import com.sualtikasifi.cizimhafiza.presentation.common.EmptyState
import com.sualtikasifi.cizimhafiza.presentation.common.LoadingRows
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedIconButton
import com.sualtikasifi.cizimhafiza.presentation.common.ScreenTopActions
import com.sualtikasifi.cizimhafiza.presentation.common.TopActionsClearance
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground

/**
 * Two leaderboards that reset on the first of every month — see
 * domain.model.LeaguePeriod for why a month, and why not lifetime.
 *
 * The friends table is built on this device from each friend's profile; the
 * global one is a single document published by a scheduled function every
 * hour (see functions/src/index.ts). That difference is visible on
 * purpose: the global tab says when it was last rebuilt, because a table
 * that is not live should not pretend to be.
 */
@Composable
fun LeagueScreen(
    onBack: () -> Unit,
    viewModel: LeagueViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val table = uiState.table

    // No title bar: the back button floats directly on the page's own
    // background instead of sitting in a separate, differently-colored strip.
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .padding(padding)
                .padding(horizontal = 16.dp)
                // Clears the floating back button (see ScreenTopActions).
                .padding(top = TopActionsClearance)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LeagueTab.entries.forEach { tab ->
                    SelectableChip(
                        label = stringResource(tab.labelRes()),
                        selected = uiState.tab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        modifier = Modifier.weight(1f),
                        verticalPadding = 10.dp,
                        fillWidth = true
                    )
                }
            }

            val shownTable = if (uiState.tab == LeagueTab.Friends) table else uiState.global?.table

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                shownTable?.let {
                    TintedBadge(
                        text = if (it.daysRemaining <= 0) {
                            stringResource(R.string.league_resets_today)
                        } else {
                            stringResource(R.string.league_resets_in, it.daysRemaining)
                        }
                    )
                }
            }

            if (uiState.tab == LeagueTab.Global) {
                // Falls back to the month's own frame so the prize is on
                // screen from the first day, rather than only after the
                // scheduled rebuild has stamped it into the table.
                val reward = LeagueReward.find(uiState.global?.rewardId)
                    ?: LeagueReward.forPeriod(LeaguePeriod.periodIdFor(LocalDate.now()))
                reward?.let { reward ->
                    RewardBanner(reward = reward, modifier = Modifier.padding(bottom = 8.dp))
                }
            }

            when {
                // Row-shaped placeholders rather than a centred spinner: the
                // table is what arrives, so the wait should look like the
                // table arriving, not like the screen deciding what to be.
                uiState.tab == LeagueTab.Friends && uiState.isLoading -> LoadingRows(count = 5, height = 62.dp)
                uiState.tab == LeagueTab.Global && uiState.globalLoading && uiState.global == null ->
                    LoadingRows(count = 5, height = 62.dp)
                uiState.tab == LeagueTab.Global && uiState.globalFailed && uiState.global == null ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            EmptyState(
                                emoji = "📡",
                                message = stringResource(R.string.league_global_failed),
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            SecondaryButton(
                                text = stringResource(R.string.reports_load_more),
                                onClick = viewModel::refreshGlobal,
                                icon = Icons.Filled.Refresh
                            )
                        }
                    }
                shownTable == null || shownTable.entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        emoji = "🏅",
                        message = stringResource(
                            if (uiState.tab == LeagueTab.Friends) R.string.league_empty
                            else R.string.league_global_empty
                        ),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(shownTable.entries, key = { _, entry -> entry.uid }) { index, entry ->
                        LeagueRow(rank = index + 1, entry = entry)
                    }
                    if (uiState.tab == LeagueTab.Global) {
                        item(key = "rebuilt-note") {
                            Text(
                                text = stringResource(R.string.league_global_refresh_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
        ScreenTopActions(
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopStart),
            title = stringResource(R.string.league_title)
        )
        if (uiState.tab == LeagueTab.Global) {
            RaisedIconButton(
                icon = Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.reports_refresh),
                onClick = viewModel::refreshGlobal,
                enabled = !uiState.globalLoading,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 16.dp)
            )
        }

        uiState.justWon?.let { reward ->
            PrizeWonDialog(
                reward = reward,
                rank = uiState.global?.myLastPeriodWin?.rank ?: 0,
                onDismiss = viewModel::dismissPrize
            )
        }
        }
    }
}

/** This week's prize, shown above the global table so the contest has a point. */
@Composable
private fun RewardBanner(reward: LeagueReward, modifier: Modifier = Modifier) {
    RaisedCard(corner = 16.dp, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🏆", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.league_reward_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = rewardLabel(reward),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            RewardSwatch(reward = reward, size = 34.dp)
        }
    }
}

/**
 * What the prize actually looks like. A pen is drawn as a stroke in its own
 * colours — the thing the winner will see in their own drawings — rather
 * than a colour chip, which says nothing about a gradient.
 */
@Composable
private fun RewardSwatch(reward: LeagueReward, size: androidx.compose.ui.unit.Dp) {
    when (reward) {
        is LeagueReward.Pen -> Canvas(modifier = Modifier.size(size).aspectRatio(1f)) {
            val brush = penBrush(reward.skin, this.size.width, this.size.height)
            drawLine(
                brush = brush,
                start = androidx.compose.ui.geometry.Offset(0f, this.size.height),
                end = androidx.compose.ui.geometry.Offset(this.size.width, 0f),
                strokeWidth = this.size.minDimension * 0.22f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
        is LeagueReward.Frame -> LevelAvatar(level = 1, frame = reward.frame, size = size)
    }
}

@Composable
private fun rewardLabel(reward: LeagueReward): String = when (reward) {
    is LeagueReward.Pen -> stringResource(reward.skin.labelRes)
    // Frames have never been named anywhere in the app — the artwork is the
    // label — so the prize is described by its kind, plus the month it
    // belongs to, which is the one thing that tells them apart.
    is LeagueReward.Frame -> listOfNotNull(
        stringResource(R.string.league_reward_kind_frame),
        reward.periodLabel
    ).joinToString(" · ")
}

/** Shown once, the first time a won prize is actually handed over. */
@Composable
private fun PrizeWonDialog(reward: LeagueReward, rank: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            PrimaryButton(text = stringResource(R.string.close), onClick = onDismiss)
        },
        title = { Text(text = stringResource(R.string.league_prize_won_title, rank)) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RewardSwatch(reward = reward, size = 44.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.league_prize_won_body, rewardLabel(reward)),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    )
}

private fun LeagueTab.labelRes(): Int = when (this) {
    LeagueTab.Friends -> R.string.league_tab_friends
    LeagueTab.Global -> R.string.league_tab_global
}

/**
 * Medal colors per podium place — a border/icon accent and a matching, fully
 * opaque pastel card face for each. The face was a 12%-alpha tint of the
 * accent at first, which read as barely-there grey smudges rather than gold/
 * silver/bronze; a solid pastel plus a deeper accent circle behind the medal
 * emoji is what actually reads as colorful at a glance.
 */
private val SilverAccent = androidx.compose.ui.graphics.Color(0xFF8B94A3)
private val BronzeAccent = androidx.compose.ui.graphics.Color(0xFFB9713F)
private val GoldFace = androidx.compose.ui.graphics.Color(0xFFFFF0C2)
private val SilverFace = androidx.compose.ui.graphics.Color(0xFFE7EAF0)
private val BronzeFace = androidx.compose.ui.graphics.Color(0xFFF7DFC9)

@Composable
private fun LeagueRow(rank: Int, entry: LeagueEntry) {
    // The top 3 get a gold/silver/bronze rank chip AND a tinted card, so the
    // three rows that will actually win something are unmistakable at a
    // glance rather than only readable by comparing rank numbers.
    val medalColor = when (rank) {
        1 -> AppTheme.tokens.gold
        2 -> SilverAccent
        3 -> BronzeAccent
        else -> null
    }
    val faceColor = when (rank) {
        1 -> GoldFace
        2 -> SilverFace
        3 -> BronzeFace
        else -> null
    }
    val rankColor = medalColor ?: MaterialTheme.colorScheme.onSurfaceVariant
    RaisedCard(
        corner = 18.dp,
        face = faceColor ?: MaterialTheme.colorScheme.surface,
        border = when {
            entry.isMe -> MaterialTheme.colorScheme.primary
            medalColor != null -> medalColor
            else -> null
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                if (rank <= 3 && medalColor != null) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(medalColor.copy(alpha = 0.28f), androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (rank) {
                                1 -> "🥇"
                                2 -> "🥈"
                                else -> "🥉"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.league_rank_format, rank),
                        style = MaterialTheme.typography.titleMedium,
                        // SemiBold rather than Normal off the podium: Quicksand's
                        // Normal weight is its thinnest, and a rank digit is the
                        // smallest, most-scanned element in the row.
                        fontWeight = FontWeight.SemiBold,
                        color = rankColor
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            LevelAvatar(level = entry.level, frame = AvatarFrame.resolve(entry.frameId, entry.level), size = 40.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (entry.isMe) stringResource(R.string.online_you_label, entry.nickname) else entry.nickname,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.league_xp_format, entry.periodXp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
