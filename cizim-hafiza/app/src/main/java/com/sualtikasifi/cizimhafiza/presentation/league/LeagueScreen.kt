package com.sualtikasifi.cizimhafiza.presentation.league

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
                    // Top padding, not just item spacing: MeRowGlow draws
                    // slightly outside its row's own bounds (a breathing
                    // outline, not a fill), which is invisible between rows
                    // since there's spacedBy space for it to sit in — but
                    // rank #1 has no row above it to borrow that space from,
                    // so without padding here the glow's top edge fell
                    // outside the list's own viewport and got clipped.
                    contentPadding = PaddingValues(top = 10.dp),
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
            title = stringResource(R.string.league_title),
            // Sharing ScreenTopActions' own Row (rather than a second
            // independently-positioned button) is what keeps this level
            // with the back button — a separately aligned/padded button
            // drifted out of line with it.
            trailing = if (uiState.tab == LeagueTab.Global) {
                {
                    RaisedIconButton(
                        icon = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.reports_refresh),
                        onClick = viewModel::refreshGlobal,
                        enabled = !uiState.globalLoading
                    )
                }
            } else null
        )

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

/**
 * This month's prize, shown above the global table so the contest has a
 * point. A gold wash + border set it apart from an ordinary card — the
 * plain white box it used to be read as one more row of chrome, not as
 * something worth chasing. The explainer line underneath is new for the
 * same reason: the card showed WHAT the prize was but never said how to
 * win it, so it read as decoration rather than a stake in the table below.
 *
 * A pen reward gets a full-width painted stroke below the label — a 34dp
 * diagonal square could not show a gradient pen's actual sweep, so players
 * had to take the name on faith; a frame reward's own [LevelAvatar] preview
 * already showed the real artwork, just too small to register.
 */
@Composable
private fun RewardBanner(reward: LeagueReward, modifier: Modifier = Modifier) {
    val gold = AppTheme.tokens.gold
    RaisedCard(
        corner = 18.dp,
        border = gold,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(gold.copy(alpha = 0.20f), Color.Transparent)))
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🏆", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.league_reward_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = gold,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = rewardLabel(reward),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (reward is LeagueReward.Frame) {
                        RewardSwatch(reward = reward, size = 48.dp)
                    }
                }
                if (reward is LeagueReward.Pen) {
                    Spacer(modifier = Modifier.height(10.dp))
                    PenStrokePreview(skin = reward.skin, modifier = Modifier.fillMaxWidth().height(40.dp))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = rewardExplainer(reward),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * A wide, hand-drawn-looking curve painted in the pen's own brush — the same
 * shape [PenSkinPickerSheet]'s swatches use, so a gradient reads as the
 * actual sweep the winner will draw with rather than a short straight line.
 *
 * The curve draws itself on in a loop rather than sitting there fully
 * painted: a reward the player hasn't won yet is a preview, not a finished
 * picture, and drawing is the one thing a pen skin is for.
 */
@Composable
private fun PenStrokePreview(skin: PenSkin, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pen-stroke-preview")
    val cycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pen-stroke-cycle"
    )
    // The cycle is one 0..1 sweep split into three feels: draw on (0-65%),
    // hold so the finished stroke actually registers (65-80%), fade before
    // the next pass starts (80-100%) — a hard restart read as a glitch.
    val drawFraction = (cycle / 0.65f).coerceIn(0f, 1f)
    val fadeFraction = ((cycle - 0.8f) / 0.2f).coerceIn(0f, 1f)
    val strokeAlpha = 1f - fadeFraction
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.04f, size.height * 0.75f)
            cubicTo(
                size.width * 0.28f, size.height * 0.05f,
                size.width * 0.60f, size.height * 1.05f,
                size.width * 0.96f, size.height * 0.25f
            )
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val drawnPath = Path()
        measure.getSegment(0f, measure.length * drawFraction, drawnPath, startWithMoveTo = true)
        drawPath(
            path = drawnPath,
            brush = penBrush(skin, size.width, size.height),
            alpha = strokeAlpha,
            style = Stroke(width = size.minDimension * 0.14f, cap = StrokeCap.Round)
        )
    }
}

/**
 * What a frame prize actually looks like — the real artwork via
 * [LevelAvatar], since (unlike a pen) there is no gradient a static swatch
 * would otherwise flatten. Pen rewards get [PenStrokePreview] instead.
 *
 * A soft gold halo breathes behind it and the frame itself gently bobs in
 * size — the same "not won yet, but look" energy as the pen's self-drawing
 * curve, so neither reward preview reads as a plain product photo.
 */
@Composable
private fun RewardSwatch(reward: LeagueReward.Frame, size: androidx.compose.ui.unit.Dp) {
    val transition = rememberInfiniteTransition(label = "frame-reward-preview")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "frame-pulse"
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size * 1.7f)) {
        Box(
            modifier = Modifier
                .size(size * (1.35f + 0.15f * pulse))
                .background(
                    Brush.radialGradient(
                        listOf(AppTheme.tokens.gold.copy(alpha = 0.16f + 0.22f * pulse), Color.Transparent)
                    ),
                    CircleShape
                )
        )
        LevelAvatar(
            level = 1,
            frame = reward.frame,
            size = size,
            modifier = Modifier.scale(0.96f + 0.08f * pulse)
        )
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

/**
 * "Global sıralamada ay sonunda ilk 3'e gir, Ayaz Kalemi'ni kazan!" — names
 * the actual prize rather than saying "bu ödülü" (this prize), which read as
 * filler beside a card that was already showing the prize right above it.
 * Turkish possessive/accusative suffixes ("Kalemi'ni", "Çerçevesi'ni") are
 * fixed per reward TYPE regardless of the specific skin/month, so this stays
 * two plain string templates rather than a general grammar rule.
 */
@Composable
private fun rewardExplainer(reward: LeagueReward): String = when (reward) {
    is LeagueReward.Pen -> stringResource(R.string.league_reward_explainer_pen, stringResource(reward.skin.labelRes))
    is LeagueReward.Frame -> {
        val monthLabel = reward.periodLabel?.let { LeaguePeriod.monthYearLabel(it) }
            ?: stringResource(R.string.league_reward_kind_frame)
        stringResource(R.string.league_reward_explainer_frame, monthLabel)
    }
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
            Column {
                if (reward is LeagueReward.Pen) {
                    PenStrokePreview(skin = reward.skin, modifier = Modifier.fillMaxWidth().height(40.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (reward is LeagueReward.Frame) {
                        RewardSwatch(reward = reward, size = 44.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Text(
                        text = stringResource(R.string.league_prize_won_body, rewardLabel(reward)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
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
    // "Which row is even me" among up to 25 look-alike rows was the actual
    // problem — a glow around the small avatar circle didn't fix that any
    // more than the row's own border already did, since both need the eye
    // to already be looking at that one row to notice. A glow around the
    // row's own frame is what actually catches a scrolling eye, so this
    // Box (a no-op for every other row) exists only to hold that glow
    // behind the card below it.
    Box(modifier = Modifier.fillMaxWidth()) {
    if (entry.isMe) {
        MeRowGlow(corner = 18.dp, modifier = Modifier.matchParentSize())
    }
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
}

/**
 * A soft, breathing gold outline just outside the row's own card frame —
 * "which row is even me" scrolling past up to 25 look-alike rows, solved by
 * making the FRAME itself catch the eye rather than the small avatar
 * circle inside it. Deliberately not [CurrentPositionGlow]'s orbiting-
 * sparkle halo: that one is tuned for a small circular node, and the same
 * treatment around a full-width rectangular row read as disconnected from
 * the card's own shape rather than hugging it.
 */
@Composable
private fun MeRowGlow(corner: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "me-row-glow")
    val pulse by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "me-row-glow-pulse"
    )
    // Resolved here, not inside the Canvas draw lambda below — that lambda
    // runs in DrawScope, not composition, so AppTheme.tokens (a Composable
    // getter) can't be read from inside it.
    val glowColor = AppTheme.tokens.gold
    Canvas(modifier = modifier) {
        val strokeWidth = (2.dp + 2.5.dp * pulse).toPx()
        drawRoundRect(
            color = glowColor.copy(alpha = 0.30f + 0.35f * pulse),
            cornerRadius = CornerRadius(corner.toPx() + strokeWidth / 2f),
            style = Stroke(width = strokeWidth),
            topLeft = Offset(-strokeWidth / 2f, -strokeWidth / 2f),
            size = Size(size.width + strokeWidth, size.height + strokeWidth)
        )
    }
}
