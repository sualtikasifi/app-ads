package com.sualtikasifi.cizimhafiza.presentation.stats

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.AvatarFrame
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
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sualtikasifi.cizimhafiza.domain.model.PenSkin
import com.sualtikasifi.cizimhafiza.presentation.common.penBrush
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.sualtikasifi.cizimhafiza.presentation.common.PillShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val stats by viewModel.statistics.collectAsState()
    val progress by viewModel.playerProgress.collectAsState()
    val selectedFrame by viewModel.selectedFrame.collectAsState()
    val avatarFrameItems by viewModel.avatarFrameItems.collectAsState()
    val penSkinItems by viewModel.penSkinItems.collectAsState()
    val selectedPen = penSkinItems.firstOrNull { it.selected }?.skin ?: PenSkin.DEFAULT
    val penChangeLabel = stringResource(R.string.pen_change_cd)
    val achievements by viewModel.achievements.collectAsState()
    val newlyUnlockedIds by viewModel.newlyUnlockedIds.collectAsState()
    val dateFormat = remember { SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.getDefault()) }
    var framePickerOpen by remember { mutableStateOf(false) }
    var penPickerOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.menu_stats)) },
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
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                // The same badge other players see in a lobby —
                                // shown here so its tier is recognisable before
                                // it ever turns up next to someone else's. The
                                // ring itself (unlike the tier name below it)
                                // is the player's own pick — tap it to change.
                                Box {
                                    LevelAvatar(
                                        level = progress.level,
                                        frame = selectedFrame,
                                        size = 88.dp,
                                        modifier = Modifier.clickable { framePickerOpen = true }
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.onPrimary)
                                            .clickable { framePickerOpen = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = stringResource(R.string.avatar_frame_change_cd),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
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
                                    Spacer(modifier = Modifier.height(8.dp))
                                    // The pen's own entry point. A second edit
                                    // badge on the avatar would be ambiguous
                                    // (two pencils, one ring), so the pen gets
                                    // a labelled chip that shows the current
                                    // stroke rather than competing for the
                                    // same tap target.
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .clip(PillShape)
                                            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f))
                                            .clickable { penPickerOpen = true }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                            .semantics { contentDescription = penChangeLabel }
                                    ) {
                                        Canvas(modifier = Modifier.size(width = 26.dp, height = 12.dp)) {
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
                                                brush = penBrush(selectedPen, size.width, size.height),
                                                style = Stroke(width = 6f, cap = StrokeCap.Round)
                                            )
                                        }
                                        Text(
                                            text = stringResource(selectedPen.labelRes),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
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

/**
 * The pen catalog, mirroring [AvatarFramePickerSheet] exactly. A swatch is a
 * short painted stroke rather than a colour chip: a gradient pen is a sweep
 * along the line, and a flat square cannot show that at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
        colors = CardDefaults.cardColors(containerColor = CardWhite),
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
                color = TextDark.copy(alpha = if (item.unlocked) 1f else 0.5f),
                maxLines = 1
            )
            if (!item.unlocked) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(imageVector = Icons.Filled.Lock, contentDescription = null, tint = TextDark, modifier = Modifier.size(12.dp))
                    Text(
                        text = stringResource(R.string.avatar_frame_locked_level, item.skin.unlockLevel),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDark
                    )
                }
            }
        }
    }
}

/**
 * A grid of every [AvatarFrame] the player has unlocked so far (plus locked
 * ones ahead, dimmed with the level that opens them), tapping an unlocked
 * one picks it — see StatisticsViewModel.selectAvatarFrame. No level-number
 * face is drawn on the swatches (unlike [LevelAvatar]): this is about
 * choosing the ring, not restating the player's level eleven times over.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
        colors = CardDefaults.cardColors(containerColor = CardWhite),
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
                    Icon(imageVector = Icons.Filled.Lock, contentDescription = null, tint = TextDark, modifier = Modifier.size(16.dp))
                    Text(
                        text = stringResource(R.string.avatar_frame_locked_level, item.frame.unlockLevel),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDark
                    )
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
