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
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.AvatarFrame
import com.sualtikasifi.cizimhafiza.domain.model.ResultItem
import com.sualtikasifi.cizimhafiza.presentation.common.LevelAvatar
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedCard
import com.sualtikasifi.cizimhafiza.util.DailyChallengeShareUtil
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedIconButton
import com.sualtikasifi.cizimhafiza.presentation.common.SecondaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.TintedBadge
import com.sualtikasifi.cizimhafiza.presentation.common.StatPill
import com.sualtikasifi.cizimhafiza.presentation.common.StrokeCanvas
import com.sualtikasifi.cizimhafiza.presentation.common.currentWordLanguage
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.AppTheme
import com.sualtikasifi.cizimhafiza.util.DrawingShareUtil
import com.sualtikasifi.cizimhafiza.util.capitalizeForWordLanguage
import com.sualtikasifi.cizimhafiza.util.GameConstants

@Composable
fun ResultScreen(
    state: GamePhase.Result,
    onPlayAgain: () -> Unit,
    onMainMenu: () -> Unit,
    onLevelNextAction: (() -> Unit)? = null,
    nextActionLabel: String? = null,
    onDoubleXp: (() -> Unit)? = null,
    /** Whether this round's doubling ad has already been taken — see GameViewModel.resultXpDoubled. */
    xpDoubled: Boolean = false,
    /** Quick match only: the opponent's own drawings, empty until they load. */
    ghostItems: List<ResultItem> = emptyList(),
    /** Quick match only: go and find a different opponent. */
    onFindAnotherOpponent: (() -> Unit)? = null
) {
    var previewItem by remember { mutableStateOf<ResultItem?>(null) }
    // Quick match only: which side of the match the gallery is showing.
    // Starts on the player's own drawings — they just made them, and their
    // own round is what they came to see first.
    var showingOpponentGallery by remember { mutableStateOf(false) }
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
                                    tint = if (index < stars) AppTheme.tokens.gold else AppTheme.tokens.textFaint,
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
                            contentColor = AppTheme.tokens.success
                        )
                        StatPill(
                            text = "${state.wrongCount}",
                            icon = Icons.Filled.Close,
                            contentColor = MaterialTheme.colorScheme.error
                        )
                        state.fastestCorrectSeconds?.let {
                            StatPill(
                                text = stringResource(R.string.fastest_correct, it),
                                icon = Icons.Filled.Bolt,
                                contentColor = AppTheme.tokens.gold
                            )
                        }
                    }
                }
            }

            state.duelOpponentName?.let { opponentName ->
                Spacer(modifier = Modifier.height(12.dp))
                RaisedCard(corner = 20.dp, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.duel_challenge_sent, opponentName),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                }
            }

            state.ghost?.let { ghost ->
                Spacer(modifier = Modifier.height(12.dp))
                GhostVersusCard(ghost = ghost, playerScore = state.totalScore)
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
                if (state.ghost != null) {
                    // The comparison is the whole point of a quick match, so
                    // it replaces the section heading rather than sitting
                    // under it — the two galleries are the same ten words
                    // drawn twice, and flipping between them in place is what
                    // makes the difference readable.
                    GalleryToggle(
                        opponentName = state.ghost.nickname,
                        opponentReady = ghostItems.isNotEmpty(),
                        showingOpponent = showingOpponentGallery,
                        onSelect = { showingOpponentGallery = it },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.your_drawings),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Sharing is offered for the player's own round only. The
                // opponent's drawings are somebody else's work, handed over
                // to settle a match — not to be passed on.
                if (!showingOpponentGallery) {
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
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Two columns, not three: the drawings are the most satisfying
            // thing on this screen and at a third of the width they were
            // thumbnails you had to tap to actually see.
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                // Falls back to the player's own round while the opponent's
                // drawings are still on their way (see GameViewModel.ghostItems)
                // rather than flashing an empty grid at them.
                items(if (showingOpponentGallery && ghostItems.isNotEmpty()) ghostItems else state.items) { item ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            StrokeCanvas(
                                strokes = item.strokes,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(AppTheme.tokens.canvasPaper)
                                    .border(
                                        1.5.dp,
                                        if (item.isCorrect) AppTheme.tokens.success.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline,
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
                                    .background(if (item.isCorrect) AppTheme.tokens.success else MaterialTheme.colorScheme.error),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (item.isCorrect) Icons.Filled.Check else Icons.Filled.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.surface,
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

            // Offered before the navigation buttons, and only while there is
            // something to double: the round is already over, so this is the
            // one ad in the app the player has nothing at all to lose by
            // watching. Gone for good once taken (see Result.xpDoubled).
            if (onDoubleXp != null && state.xpEarned > 0 && GameConstants.ADMOB_ENABLED) {
                Spacer(modifier = Modifier.height(12.dp))
                if (xpDoubled) {
                    TintedBadge(
                        text = stringResource(R.string.result_xp_doubled, state.xpEarned * 2),
                        container = AppTheme.tokens.gold.copy(alpha = 0.18f),
                        content = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    SecondaryButton(
                        text = stringResource(R.string.result_double_xp, state.xpEarned),
                        onClick = onDoubleXp,
                        // Named and marked as an ad before the tap: AdMob
                        // requires the reward and the fact that a video is
                        // coming to be stated up front.
                        icon = Icons.Filled.PlayCircle,
                        height = 50.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (state.ghost != null) {
                // No "Tekrar Oyna" for a quick match: the opponent's ten
                // words are fixed, so replaying would deal the same round
                // over again with every answer already known. A new opponent
                // is the honest version of the same tap (see
                // GameViewModel.restart).
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton(
                        text = stringResource(R.string.main_menu),
                        onClick = onMainMenu,
                        height = 54.dp,
                        modifier = Modifier.weight(1f)
                    )
                    PrimaryButton(
                        text = stringResource(R.string.quick_match_another_opponent),
                        onClick = onFindAnotherOpponent ?: onMainMenu,
                        icon = Icons.Filled.Refresh,
                        height = 54.dp,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else if (state.duelOpponentName != null) {
                // No "Play Again" here on purpose: restart() would replay
                // with the exact same duel-challenge args still attached
                // (see GameViewModel), silently sending a second challenge
                // to the same friend the instant this round finished —
                // surprising, and not something a single tap should ever
                // do without asking. Challenging again is a fresh, deliberate
                // pick from Friends instead.
                PrimaryButton(
                    text = stringResource(R.string.main_menu),
                    onClick = onMainMenu,
                    height = 54.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
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
                            .background(AppTheme.tokens.canvasPaper)
                    )
                    Text(
                        text = itemToPreview.word.capitalizeForWordLanguage(wordLanguage),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.surface,
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
 * Who won the quick match, and by how much.
 *
 * Both scores sit side by side rather than as "you scored X, they scored Y":
 * the two rounds were the same ten words under the same clock, so the only
 * thing worth reading here is which column is bigger.
 */
@Composable
private fun GhostVersusCard(ghost: GhostMatchSummary, playerScore: Int) {
    val won = playerScore > ghost.opponentScore
    val drew = playerScore == ghost.opponentScore
    val accent = when {
        drew -> MaterialTheme.colorScheme.onSurfaceVariant
        won -> AppTheme.tokens.success
        else -> MaterialTheme.colorScheme.error
    }

    RaisedCard(corner = 22.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(
                    when {
                        drew -> R.string.quick_match_drew
                        won -> R.string.quick_match_won
                        else -> R.string.quick_match_lost
                    }
                ),
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                VersusSide(
                    name = stringResource(R.string.quick_match_you),
                    score = playerScore,
                    highlighted = won,
                    avatar = null
                )
                Text(
                    text = stringResource(R.string.quick_match_versus),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                VersusSide(
                    name = ghost.nickname,
                    score = ghost.opponentScore,
                    highlighted = !won && !drew,
                    avatar = {
                        LevelAvatar(
                            level = ghost.level,
                            frame = AvatarFrame.resolve(ghost.frameId, ghost.level),
                            size = 40.dp
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun VersusSide(
    name: String,
    score: Int,
    highlighted: Boolean,
    avatar: (@Composable () -> Unit)?
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Only the opponent gets a ring. The player already knows what their
        // own looks like, and a second one here would make the card read as
        // two strangers rather than as "you against them".
        avatar?.invoke()
        if (avatar != null) Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "$score",
            style = MaterialTheme.typography.headlineMedium,
            color = if (highlighted) AppTheme.tokens.success else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/**
 * Two chips over the gallery: your ten drawings, or theirs.
 *
 * The opponent's side stays unselectable until their drawings have actually
 * arrived — a chip that switches to the same grid you were already looking at
 * reads as a broken toggle, not as a slow one.
 */
@Composable
private fun GalleryToggle(
    opponentName: String,
    opponentReady: Boolean,
    showingOpponent: Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GalleryChip(
            label = stringResource(R.string.quick_match_gallery_yours),
            selected = !showingOpponent,
            enabled = true,
            onClick = { onSelect(false) }
        )
        GalleryChip(
            label = opponentName,
            selected = showingOpponent,
            enabled = opponentReady,
            onClick = { onSelect(true) }
        )
    }
}

@Composable
private fun GalleryChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val container = when {
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
    val content = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        enabled -> MaterialTheme.colorScheme.onSurface
        else -> AppTheme.tokens.textFaint
    }
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(enabled = enabled && !selected, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
                    contentColor = AppTheme.tokens.gold
                )
            }
            if (daily.streakMultiplierIncreased) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.daily_challenge_streak_multiplier_increased, daily.streakMultiplier),
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
