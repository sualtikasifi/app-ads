package com.sualtikasifi.cizimhafiza.presentation.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.ResultItem
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedCard
import com.sualtikasifi.cizimhafiza.util.DailyChallengeShareUtil
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedIconButton
import com.sualtikasifi.cizimhafiza.presentation.common.SecondaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.StatPill
import com.sualtikasifi.cizimhafiza.presentation.common.StrokeCanvas
import com.sualtikasifi.cizimhafiza.presentation.common.currentWordLanguage
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.AppTheme
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import com.sualtikasifi.cizimhafiza.presentation.theme.CorrectGreen
import com.sualtikasifi.cizimhafiza.presentation.theme.GoldAccent
import com.sualtikasifi.cizimhafiza.presentation.theme.WrongRed
import com.sualtikasifi.cizimhafiza.util.DrawingShareUtil
import com.sualtikasifi.cizimhafiza.util.capitalizeForWordLanguage

@Composable
fun ResultScreen(
    state: GamePhase.Result,
    onPlayAgain: () -> Unit,
    onMainMenu: () -> Unit,
    onLevelNextAction: (() -> Unit)? = null,
    nextActionLabel: String? = null
) {
    var previewItem by remember { mutableStateOf<ResultItem?>(null) }
    val context = LocalContext.current
    val wordLanguage = currentWordLanguage()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .padding(padding)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- Score hero -------------------------------------------------
            RaisedCard(corner = 28.dp, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.game_over),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${state.totalScore}",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.total_score, state.totalScore),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    state.levelStars?.let { stars ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(3) { index ->
                                Icon(
                                    imageVector = if (index < stars) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                    contentDescription = stringResource(R.string.stars_content_description, stars),
                                    tint = if (index < stars) GoldAccent else AppTheme.tokens.textFaint,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatPill(
                            text = "${state.correctCount}",
                            icon = Icons.Filled.Check,
                            contentColor = CorrectGreen
                        )
                        StatPill(
                            text = "${state.wrongCount}",
                            icon = Icons.Filled.Close,
                            contentColor = WrongRed
                        )
                        state.fastestCorrectSeconds?.let {
                            StatPill(
                                text = stringResource(R.string.fastest_correct, it),
                                icon = Icons.Filled.Bolt,
                                contentColor = GoldAccent
                            )
                        }
                    }
                }
            }

            state.daily?.let { daily ->
                Spacer(modifier = Modifier.height(12.dp))
                DailyChallengeResultCard(
                    daily = daily,
                    correctFlags = state.items.map { it.isCorrect },
                    onShare = {
                        DailyChallengeShareUtil.shareResult(
                            context = context,
                            correctFlags = state.items.map { it.isCorrect },
                            streak = daily.streak
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- Gallery ----------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.your_drawings),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                RaisedIconButton(
                    icon = Icons.Filled.Share,
                    contentDescription = stringResource(R.string.share_all_drawings),
                    onClick = {
                        DrawingShareUtil.shareAllResults(
                            context = context,
                            totalScore = state.totalScore,
                            correctCount = state.correctCount,
                            wrongCount = state.wrongCount,
                            fastestCorrectSeconds = state.fastestCorrectSeconds,
                            items = state.items
                        )
                    },
                    size = 42.dp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(state.items) { item ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            StrokeCanvas(
                                strokes = item.strokes,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(CardWhite)
                                    .border(
                                        1.5.dp,
                                        if (item.isCorrect) CorrectGreen.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline,
                                        MaterialTheme.shapes.medium
                                    )
                                    .clickable { previewItem = item }
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(5.dp)
                                    .size(21.dp)
                                    .clip(CircleShape)
                                    .background(if (item.isCorrect) CorrectGreen else WrongRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (item.isCorrect) Icons.Filled.Check else Icons.Filled.Close,
                                    contentDescription = null,
                                    tint = CardWhite,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Text(
                            text = item.word.capitalizeForWordLanguage(wordLanguage),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth().padding(top = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(
                    text = stringResource(R.string.main_menu),
                    onClick = onMainMenu,
                    height = 54.dp,
                    modifier = Modifier.weight(1f)
                )
                PrimaryButton(
                    text = stringResource(R.string.play_again),
                    onClick = onPlayAgain,
                    height = 54.dp,
                    modifier = Modifier.weight(1f)
                )
            }

            if (onLevelNextAction != null && nextActionLabel != null) {
                Spacer(modifier = Modifier.height(10.dp))
                PrimaryButton(
                    text = nextActionLabel,
                    onClick = onLevelNextAction,
                    height = 54.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    val itemToPreview = previewItem
    if (itemToPreview != null) {
        Dialog(
            onDismissRequest = { previewItem = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            BackHandler { previewItem = null }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.94f))
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    StrokeCanvas(
                        strokes = itemToPreview.strokes,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.large)
                            .background(CardWhite)
                    )
                    Text(
                        text = itemToPreview.word.capitalizeForWordLanguage(wordLanguage),
                        style = MaterialTheme.typography.headlineSmall,
                        color = CardWhite,
                        modifier = Modifier.padding(top = 18.dp, bottom = 20.dp)
                    )
                    PrimaryButton(
                        text = stringResource(R.string.share_drawing),
                        onClick = { DrawingShareUtil.shareDrawing(context, itemToPreview.word, itemToPreview.strokes) },
                        icon = Icons.Filled.Share,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                RaisedIconButton(
                    icon = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.close),
                    onClick = { previewItem = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                )
            }
        }
    }
}

/**
 * The daily-challenge half of the result screen: the streak that was just
 * extended, the XP it paid, and the one action that turns a private result
 * into something a friend sees.
 *
 * The ✅/❌ row is shown here as well as on the share card so what gets
 * posted is exactly what the player is looking at — no surprises about what
 * they're about to reveal.
 */
@Composable
private fun DailyChallengeResultCard(
    daily: DailyResultSummary,
    correctFlags: List<Boolean>,
    onShare: () -> Unit
) {
    RaisedCard(corner = 24.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.daily_challenge_result_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = correctFlags.joinToString(" ") { if (it) "✅" else "❌" },
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatPill(text = "🔥 ${daily.streak}", icon = null, contentColor = MaterialTheme.colorScheme.primary)
                StatPill(
                    text = stringResource(R.string.daily_challenge_xp_earned, daily.xpEarned),
                    icon = null,
                    contentColor = GoldAccent
                )
            }
            if (daily.streakBonusIncreased) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.daily_challenge_streak_bonus_increased, daily.newStreakBonusPerDay),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            SecondaryButton(
                text = stringResource(R.string.daily_challenge_share),
                onClick = onShare,
                icon = Icons.Filled.Share,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
