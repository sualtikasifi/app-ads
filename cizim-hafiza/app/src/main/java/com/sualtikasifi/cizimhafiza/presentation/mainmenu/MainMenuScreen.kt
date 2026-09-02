package com.sualtikasifi.cizimhafiza.presentation.mainmenu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.util.DailyChallengeState
import kotlinx.coroutines.delay
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.common.IconWell
import com.sualtikasifi.cizimhafiza.presentation.common.PillShape
import com.sualtikasifi.cizimhafiza.presentation.common.SecondaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedCard
import com.sualtikasifi.cizimhafiza.presentation.common.TintedBadge
import com.sualtikasifi.cizimhafiza.presentation.common.penBrush
import com.sualtikasifi.cizimhafiza.domain.model.AvatarFrame
import com.sualtikasifi.cizimhafiza.domain.model.DailyChallenge
import com.sualtikasifi.cizimhafiza.domain.model.LevelProgressState
import com.sualtikasifi.cizimhafiza.domain.model.XpAwards
import com.sualtikasifi.cizimhafiza.domain.model.LevelTier
import com.sualtikasifi.cizimhafiza.domain.model.PlayerLevel
import com.sualtikasifi.cizimhafiza.domain.model.PenSkin
import com.sualtikasifi.cizimhafiza.presentation.common.LevelAvatar
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedIconButton
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.AppTheme
import com.sualtikasifi.cizimhafiza.presentation.theme.Teal

/** The one gap used between every major section of the menu, so the page reads as evenly spaced top to bottom. */
private val SECTION_GAP = 11.dp

@Composable
fun MainMenuScreen(
    onPlay: () -> Unit,
    onPlayOnline: () -> Unit,
    onLevels: () -> Unit,
    onAchievements: () -> Unit,
    onSettings: () -> Unit,
    onBotTraining: () -> Unit,
    onDailyChallenge: () -> Unit,
    viewModel: MainMenuViewModel = hiltViewModel()
) {
    val hasUnseenAchievement by viewModel.hasUnseenAchievement.collectAsState()
    val dailyState by viewModel.dailyState.collectAsState()
    val levelProgress by viewModel.levelProgress.collectAsState()
    val selectedFrame by viewModel.selectedFrame.collectAsState()
    val avatarFrameItems by viewModel.avatarFrameItems.collectAsState()
    val penSkinItems by viewModel.penSkinItems.collectAsState()
    val selectedPen = penSkinItems.firstOrNull { it.selected }?.skin ?: PenSkin.DEFAULT
    var framePickerOpen by remember { mutableStateOf(false) }
    var penPickerOpen by remember { mutableStateOf(false) }
    var rankLadderOpen by remember { mutableStateOf(false) }
    val streakToast by viewModel.streakToast.collectAsState()
    // The system back gesture on the menu used to close the app outright,
    // with no way to take it back — easy to trigger by accident mid-swipe
    // and, on a game, more destructive than it looks.
    var exitPromptOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = !exitPromptOpen) { exitPromptOpen = true }
    val context = LocalContext.current
    val activity = context as? Activity

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
                .padding(horizontal = 22.dp)
                .padding(top = 20.dp, bottom = 12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                RaisedIconButton(
                    icon = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.menu_settings),
                    onClick = onSettings,
                    size = 40.dp
                )
            }

            // Centred as one block when it fits, scrollable when it does
            // not. It used to be neither: a weight(1f) column simply clipped
            // whatever ran past the bottom, so on a shorter phone — or once
            // the level card grew a rank pill and the daily card grew a
            // streak flame — the last row of tiles was cut in half with no
            // way to reach it. heightIn(min = maxHeight) keeps the centred
            // look on a roomy screen and lets the same content scroll on a
            // cramped one, rather than trading one for the other.
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo medallion: the mark on a tinted disc, ringed in white
                // so its edge stays crisp against the textured collage
                // background instead of blending into it.
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.karalak_logo_mark),
                        contentDescription = null,
                        modifier = Modifier.size(54.dp)
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

                Spacer(modifier = Modifier.height(SECTION_GAP))

                // A proper card — the same colored-container treatment as
                // DailyChallengeCard below it — rather than the badge just
                // floating loose on the page: this is the player's own
                // chosen ring (see StatisticsScreen's picker), the thing all
                // the frame-unlock and sparkle work is actually for, and it
                // deserves to look like a deliberate piece of the menu, not
                // a sticker.
                LevelBadgeCard(
                    progress = levelProgress,
                    frame = selectedFrame,
                    pen = selectedPen,
                    onFrameClick = { framePickerOpen = true },
                    onPenClick = { penPickerOpen = true },
                    onRankClick = { rankLadderOpen = true }
                )

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
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        onClick = onPlayOnline,
                        modifier = Modifier.weight(1f)
                    )
                    MenuTile(
                        icon = Icons.Filled.Map,
                        label = stringResource(R.string.menu_levels),
                        tint = MaterialTheme.colorScheme.primary,
                        container = MaterialTheme.colorScheme.primaryContainer,
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
                        icon = Icons.Filled.EmojiEvents,
                        label = stringResource(R.string.menu_achievements),
                        tint = AppTheme.tokens.gold,
                        container = Color(0xFFF8EBD0),
                        onClick = onAchievements,
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

        if (exitPromptOpen) {
            AlertDialog(
                onDismissRequest = { exitPromptOpen = false },
                title = { Text(stringResource(R.string.exit_confirm_title)) },
                text = { Text(stringResource(R.string.exit_confirm_message)) },
                confirmButton = {
                    TextButton(onClick = { exitPromptOpen = false; activity?.finish() }) {
                        Text(stringResource(R.string.exit_confirm_yes))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { exitPromptOpen = false }) {
                        Text(stringResource(R.string.exit_confirm_no))
                    }
                }
            )
        }

        // The streak the player just lost, offered back for an ad. Shown the
        // moment the menu opens, because that is exactly when they find out
        // it broke — see DailyChallengeRepository.repairStreak.
        if (dailyState.rescuableStreak > 0) {
            StreakRescueDialog(
                lostStreak = dailyState.rescuableStreak,
                onRescue = { activity?.let(viewModel::rescueStreak) },
                onDismiss = viewModel::dismissRescuePrompt
            )
        }

        streakToast?.let { toast ->
            LaunchedEffect(toast) {
                delay(2_500)
                viewModel.consumeStreakToast()
            }
            Box(
                modifier = Modifier.fillMaxSize().padding(bottom = 40.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                TintedBadge(
                    text = stringResource(
                        when (toast) {
                            StreakToast.Rescued -> R.string.streak_rescue_done
                        }
                    ),
                    container = MaterialTheme.colorScheme.surface,
                    content = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (rankLadderOpen) {
            RankLadderSheet(progress = levelProgress, onDismiss = { rankLadderOpen = false })
        }

        if (framePickerOpen) {
            AvatarFramePickerSheet(
                items = avatarFrameItems,
                onSelect = { viewModel.selectAvatarFrame(it); framePickerOpen = false },
                onDismiss = { framePickerOpen = false }
            )
        }

        if (penPickerOpen) {
            PenSkinPickerSheet(
                items = penSkinItems,
                onSelect = { viewModel.selectPenSkin(it); penPickerOpen = false },
                onDismiss = { penPickerOpen = false }
            )
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
            face = AppTheme.tokens.cardWarm,
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
 * The player's level/frame, framed in the same [RaisedCard] + [MaterialTheme.colorScheme.primaryContainer]
 * language as [DailyChallengeCard] right below it — rank name, level number,
 * a slim XP sliver and now (moved off the achievements page, since this is
 * the "profile" the level number lives on) the frame and pen pickers
 * themselves, instead of the avatar floating alone on the page.
 */
@Composable
private fun LevelBadgeCard(
    progress: LevelProgressState,
    frame: AvatarFrame,
    pen: PenSkin,
    onFrameClick: () -> Unit,
    onPenClick: () -> Unit,
    onRankClick: () -> Unit
) {
    val penChangeLabel = stringResource(R.string.pen_change_cd)
    RaisedCard(corner = 22.dp, face = MaterialTheme.colorScheme.primaryContainer, raise = 7.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // The player's own chosen ring — tap it to change (see
                // AvatarFramePickerSheet below).
                Box {
                    LevelAvatar(
                        level = progress.level,
                        frame = frame,
                        size = 64.dp,
                        modifier = Modifier.clickable(onClick = onFrameClick)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimary)
                            .clickable(onClick = onFrameClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.avatar_frame_change_cd),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    // Rank and level are two different ladders and used to be
                    // conflated: the bar below tracks the LEVEL, but its
                    // caption quoted the distance to the next RANK, so the
                    // number under a nearly-full bar could read in the
                    // thousands. Each now states its own distance, next to
                    // the thing it belongs to.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${progress.tier.rank.emoji} ${stringResource(progress.tier.rank.nameRes)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val next = progress.nextTier
                        TintedBadge(
                            modifier = Modifier.clickable(onClick = onRankClick),
                            text = if (next != null) {
                                stringResource(
                                    R.string.level_next_rank_pill,
                                    next.rank.emoji,
                                    stringResource(next.rank.nameRes),
                                    progress.xpToNextTier
                                )
                            } else {
                                stringResource(R.string.level_top_rank_pill)
                            },
                            container = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                            content = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = stringResource(R.string.avatar_frame_locked_level, progress.level),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // The pen's own entry point — a labelled chip showing the
                    // current stroke, rather than a second edit badge on the
                    // avatar (which would be ambiguous with the frame's).
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(PillShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            .clickable(onClick = onPenClick)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .semantics { contentDescription = penChangeLabel }
                    ) {
                        Canvas(modifier = Modifier.size(width = 22.dp, height = 10.dp)) {
                            val path = Path().apply {
                                moveTo(0f, size.height * 0.8f)
                                cubicTo(
                                    size.width * 0.3f, -size.height * 0.2f,
                                    size.width * 0.7f, size.height * 1.2f,
                                    size.width, size.height * 0.2f
                                )
                            }
                            drawPath(
                                path = path,
                                brush = penBrush(pen, size.width, size.height),
                                style = Stroke(width = 5f, cap = StrokeCap.Round)
                            )
                        }
                        Text(
                            text = stringResource(pen.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress.progressFraction },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
            )
            Spacer(modifier = Modifier.height(5.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (progress.isMaxLevel) {
                        stringResource(R.string.level_max_reached)
                    } else {
                        stringResource(R.string.level_xp_to_next_level, progress.xpToNextLevel)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.level_total_xp, progress.totalXp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * The pen catalog, mirroring [AvatarFramePickerSheet] exactly. A swatch is a
 * short painted stroke rather than a colour chip: a gradient pen is a sweep
 * along the line, and a flat square cannot show that at all.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PenSkinPickerSheet(
    items: List<PenSkinUiItem>,
    onSelect: (PenSkin) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.pen_picker_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.height(420.dp)
            ) {
                gridItems(items, key = { it.skin.name }) { item ->
                    PenSkinSwatch(item = item, onClick = { if (item.unlocked) onSelect(item.skin) })
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PenSkinSwatch(item: PenSkinUiItem, onClick: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (item.selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.unlocked, onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(34.dp).alpha(if (item.unlocked) 1f else 0.3f)) {
                // A single hand-drawn-looking curve, painted with the pen's
                // own brush so a gradient reads exactly as it will in play.
                val path = Path().apply {
                    moveTo(size.width * 0.08f, size.height * 0.72f)
                    cubicTo(
                        size.width * 0.30f, size.height * 0.05f,
                        size.width * 0.62f, size.height * 1.05f,
                        size.width * 0.92f, size.height * 0.28f
                    )
                }
                drawPath(
                    path = path,
                    brush = penBrush(item.skin, size.width, size.height),
                    style = Stroke(width = 9f, cap = StrokeCap.Round)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(item.skin.labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (item.unlocked) 1f else 0.5f),
                maxLines = 1
            )
            if (!item.unlocked) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(imageVector = Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(12.dp))
                    Text(
                        text = stringResource(R.string.avatar_frame_locked_level, item.skin.unlockLevel),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * A grid of every [AvatarFrame] the player has unlocked so far (plus locked
 * ones ahead, dimmed with the level that opens them), tapping an unlocked
 * one picks it — see MainMenuViewModel.selectAvatarFrame. No level-number
 * face is drawn on the swatches (unlike [LevelAvatar]): this is about
 * choosing the ring, not restating the player's level eleven times over.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AvatarFramePickerSheet(
    items: List<AvatarFrameUiItem>,
    onSelect: (AvatarFrame) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.avatar_frame_picker_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.height(420.dp)
            ) {
                gridItems(items, key = { it.frame.name }) { item ->
                    AvatarFrameSwatch(item = item, onClick = { if (item.unlocked) onSelect(item.frame) })
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AvatarFrameSwatch(item: AvatarFrameUiItem, onClick: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (item.selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.unlocked, onClick = onClick)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(96.dp).padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(item.frame.drawableRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(if (item.unlocked) 1f else 0.35f)
            )
            if (!item.unlocked) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                    Text(
                        text = stringResource(R.string.avatar_frame_locked_level, item.frame.unlockLevel),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
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
        face = if (available) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        raise = 7.dp,
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
                        // No extra alpha on top of the muted color: this was
                        // the faintest text on the home screen.
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (state.currentStreak > 0) {
                StreakFlame(multiplier = XpAwards.dailyStreakMultiplier(state.currentStreak))
            }
        }
    }
}

/**
 * The streak, and what it is worth, in one mark.
 *
 * The multiplier used to be a separate gold pill plus a "play tomorrow for
 * Nx" line beside the card's title — two extra pieces of text restating a
 * number that was already on screen right next to them. It belongs on the
 * flame: the streak count and the multiplier are the same number until the
 * cap, so showing them apart made the card look busier than it is.
 *
 * The flame breathes rather than sitting still. It is the one thing on the
 * home screen that represents something at risk of being lost, and a static
 * emoji reads as a label; a moving one reads as alive.
 */
@Composable
private fun StreakFlame(multiplier: Int) {
    val transition = rememberInfiniteTransition(label = "streakFlame")
    val scale by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(880, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "streakFlameScale"
    )
    val glow by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(880, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "streakFlameGlow"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(AppTheme.tokens.gold.copy(alpha = glow), CircleShape)
            )
            Text(
                text = "🔥",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
            )
        }
        // Only the multiplier. Below the cap the streak count and the
        // multiplier are the same number, so printing both stacked them into
        // "3" over "3x XP" — the same fact twice, the top line adding
        // nothing. What the player is actually protecting is the multiplier.
        Text(
            text = stringResource(R.string.daily_challenge_multiplier_badge, multiplier),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
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

/**
 * The one deliberate way back from a broken streak.
 *
 * Deliberately a modal: someone who has just lost a 60-day streak will not
 * go looking for a button, and the offer expires within a couple of days
 * (see DailyChallengeRepository.MAX_RESCUE_GAP_DAYS).
 *
 * The action is a full button carrying a play icon and the word "ad",
 * because it opens a rewarded video. AdMob's policies require the reward
 * and the fact that an ad is coming to be stated before the tap, and a bare
 * line of tappable text stated neither — it did not even look like a
 * control.
 */
@Composable
private fun StreakRescueDialog(lostStreak: Int, onRescue: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        RaisedCard(corner = 28.dp, raise = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "🔥", style = MaterialTheme.typography.displaySmall)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.streak_rescue_title, lostStreak),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.streak_rescue_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                PrimaryButton(
                    text = stringResource(R.string.streak_rescue_action),
                    onClick = onRescue,
                    icon = Icons.Filled.PlayCircle,
                    height = 54.dp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                SecondaryButton(
                    text = stringResource(R.string.streak_rescue_dismiss),
                    onClick = onDismiss,
                    height = 46.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Every rank, the level it opens at, and where the player currently stands.
 *
 * The card only ever showed the next rank and the XP left to it, which told
 * a player what was immediately ahead but nothing about the shape of the
 * climb — how many ranks exist, how far apart they are, what the top one is
 * called. A ladder someone can look at is what turns a number into a goal.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RankLadderSheet(progress: LevelProgressState, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.rank_ladder_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(
                    R.string.rank_ladder_subtitle,
                    stringResource(progress.tier.rank.nameRes)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
            )
            LevelTier.entries.forEach { tier ->
                val reached = progress.level >= tier.minLevel
                val isCurrent = tier == progress.tier
                val xpAway = (PlayerLevel.totalXpForLevel(tier.minLevel) - progress.totalXp).coerceAtLeast(0)
                RaisedCard(
                    corner = 18.dp,
                    face = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            // A rank still ahead is dimmed rather than hidden:
                            // the point of the list is seeing what is coming.
                            .alpha(if (reached) 1f else 0.55f)
                    ) {
                        Text(text = tier.rank.emoji, style = MaterialTheme.typography.titleLarge)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(tier.rank.nameRes),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.rank_ladder_unlock_level, tier.minLevel),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = when {
                                isCurrent -> stringResource(R.string.rank_ladder_current)
                                reached -> stringResource(R.string.rank_ladder_reached)
                                else -> stringResource(R.string.rank_ladder_remaining, xpAway)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
